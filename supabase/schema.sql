-- ============================================================
-- Safe Companion v0.2 — Supabase schema
-- Paste this whole file into: Supabase Dashboard → SQL Editor → Run
-- ============================================================

create extension if not exists pgcrypto;

-- ---------- Tables ----------

create table if not exists public.children (
  id uuid primary key default gen_random_uuid(),
  family_uid uuid not null,
  display_name text not null,
  age_band text not null default 'AGE_11_13',
  language text not null default 'he',
  policy jsonb not null default '{
    "sensitivity": "MEDIUM",
    "min_notify_severity": 3,
    "retention_days": 14,
    "cloud_ai_allowed": true,
    "categories": ["bullying","suspicious_adult","grooming","sexual","violence_hate","personal_info","scam_phishing","meet_offline","self_disclosure","self_harm"]
  }'::jsonb,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  family_uid uuid not null,
  child_id uuid not null references public.children(id) on delete cascade,
  platform text not null default 'android',
  app_version text,
  token_hash text not null,
  enrolled_at timestamptz not null default now(),
  last_seen timestamptz,
  perm_status jsonb not null default '{}'::jsonb,
  active boolean not null default true
);

create table if not exists public.enroll_codes (
  code text primary key,
  family_uid uuid not null,
  child_id uuid not null references public.children(id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '15 minutes',
  used boolean not null default false
);

create table if not exists public.events (
  id uuid primary key default gen_random_uuid(),
  family_uid uuid not null,
  child_id uuid not null references public.children(id) on delete cascade,
  device_id uuid references public.devices(id) on delete set null,
  created_at timestamptz not null default now(),
  source text not null default 'auto',           -- auto | manual_report | help_request
  category text not null,
  severity int not null default 2,               -- 1..5
  confidence real,
  score int,
  conversation_title text,
  conversation_type text default 'unknown',      -- private | group | unknown
  excerpt text,
  signals text,
  summary_en text,
  summary_he text,
  recommendation_en text,
  recommendation_he text,
  ai_status text not null default 'none',        -- none | pending | done | failed
  ai_model text,
  seen boolean not null default false,
  retention_expires_at timestamptz not null default now() + interval '14 days'
);

create table if not exists public.audit (
  id uuid primary key default gen_random_uuid(),
  family_uid uuid not null,
  actor text not null,
  action text not null,
  target text,
  at timestamptz not null default now(),
  result text
);

create index if not exists idx_events_family_created on public.events (family_uid, created_at desc);
create index if not exists idx_devices_token on public.devices (token_hash);

-- ---------- Row-Level Security ----------
-- Supervisors (authenticated users) may touch ONLY their family's rows.
-- Supervised devices have no user; they act via SECURITY DEFINER functions below.

alter table public.children enable row level security;
alter table public.devices enable row level security;
alter table public.enroll_codes enable row level security;
alter table public.events enable row level security;
alter table public.audit enable row level security;

drop policy if exists children_family on public.children;
create policy children_family on public.children
  for all to authenticated
  using (family_uid = auth.uid()) with check (family_uid = auth.uid());

drop policy if exists devices_family on public.devices;
create policy devices_family on public.devices
  for all to authenticated
  using (family_uid = auth.uid()) with check (family_uid = auth.uid());

drop policy if exists codes_family on public.enroll_codes;
create policy codes_family on public.enroll_codes
  for all to authenticated
  using (family_uid = auth.uid()) with check (family_uid = auth.uid());

drop policy if exists events_family on public.events;
create policy events_family on public.events
  for all to authenticated
  using (family_uid = auth.uid()) with check (family_uid = auth.uid());

drop policy if exists audit_family on public.audit;
create policy audit_family on public.audit
  for all to authenticated
  using (family_uid = auth.uid()) with check (family_uid = auth.uid());

-- ---------- Device-facing RPCs (SECURITY DEFINER) ----------
-- The child device holds only a random token; these functions validate it.

-- 1) Exchange a valid enrollment code for a device identity + token.
create or replace function public.claim_enrollment(
  p_code text,
  p_platform text default 'android',
  p_app_version text default null
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_code record;
  v_token text;
  v_device_id uuid;
  v_child record;
begin
  select * into v_code from enroll_codes
   where code = p_code and used = false and expires_at > now();
  if not found then
    return jsonb_build_object('ok', false, 'error', 'invalid_or_expired_code');
  end if;

  v_token := encode(gen_random_bytes(24), 'hex');

  insert into devices (family_uid, child_id, platform, app_version, token_hash, last_seen)
  values (v_code.family_uid, v_code.child_id, coalesce(p_platform,'android'),
          p_app_version, encode(digest(v_token, 'sha256'), 'hex'), now())
  returning id into v_device_id;

  update enroll_codes set used = true where code = p_code;

  select * into v_child from children where id = v_code.child_id;

  insert into audit (family_uid, actor, action, target, result)
  values (v_code.family_uid, 'device:' || v_device_id, 'enrolled', 'child:' || v_child.display_name, 'ok');

  return jsonb_build_object(
    'ok', true,
    'device_id', v_device_id,
    'device_token', v_token,
    'child_id', v_child.id,
    'child_name', v_child.display_name,
    'age_band', v_child.age_band,
    'policy', v_child.policy
  );
end $$;

-- 2) Submit a safety event from an enrolled device.
create or replace function public.submit_event(
  p_device_token text,
  p_event jsonb
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_dev record;
  v_child record;
  v_id uuid;
  v_retention int;
begin
  select * into v_dev from devices
   where token_hash = encode(digest(p_device_token, 'sha256'), 'hex') and active = true;
  if not found then
    return jsonb_build_object('ok', false, 'error', 'unknown_device');
  end if;

  select * into v_child from children where id = v_dev.child_id;
  v_retention := coalesce((v_child.policy->>'retention_days')::int, 14);

  insert into events (
    family_uid, child_id, device_id, source, category, severity, confidence, score,
    conversation_title, conversation_type, excerpt, signals,
    summary_en, summary_he, ai_status, retention_expires_at
  ) values (
    v_dev.family_uid, v_dev.child_id, v_dev.id,
    coalesce(p_event->>'source','auto'),
    coalesce(p_event->>'category','unknown'),
    coalesce((p_event->>'severity')::int, 2),
    (p_event->>'confidence')::real,
    (p_event->>'score')::int,
    p_event->>'conversation_title',
    coalesce(p_event->>'conversation_type','unknown'),
    p_event->>'excerpt',
    p_event->>'signals',
    p_event->>'summary_en',
    p_event->>'summary_he',
    case when coalesce((v_child.policy->>'cloud_ai_allowed')::boolean, true)
         then 'pending' else 'none' end,
    now() + (v_retention || ' days')::interval
  ) returning id into v_id;

  update devices set last_seen = now() where id = v_dev.id;

  return jsonb_build_object('ok', true, 'event_id', v_id);
end $$;

-- 3) Heartbeat: report status, pull the current policy (parent may have changed it).
create or replace function public.device_heartbeat(
  p_device_token text,
  p_status jsonb default '{}'::jsonb
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_dev record;
  v_child record;
begin
  select * into v_dev from devices
   where token_hash = encode(digest(p_device_token, 'sha256'), 'hex') and active = true;
  if not found then
    return jsonb_build_object('ok', false, 'error', 'unknown_device');
  end if;

  update devices set last_seen = now(), perm_status = coalesce(p_status,'{}'::jsonb)
   where id = v_dev.id;

  select * into v_child from children where id = v_dev.child_id;

  return jsonb_build_object(
    'ok', true,
    'child_name', v_child.display_name,
    'age_band', v_child.age_band,
    'policy', v_child.policy,
    'active', v_dev.active
  );
end $$;

-- Devices call these with the public anon key.
grant execute on function public.claim_enrollment(text, text, text) to anon, authenticated;
grant execute on function public.submit_event(text, jsonb) to anon, authenticated;
grant execute on function public.device_heartbeat(text, jsonb) to anon, authenticated;
