# Stage 3 — MVP Architecture (v0.2)

One Android application, two modes, one Supabase backend, optional Claude analysis.

```
CHILD PHONE (Supervised mode)                      PARENT PHONE (Supervisor mode)
┌──────────────────────────────┐                   ┌──────────────────────────────┐
│ WhatsApp notification        │                   │ Email/password account       │
│   ↓ NotificationListener     │                   │ Family dashboard             │
│ Rule engine (local, HE+EN)   │                   │   children / devices /       │
│   ↓ only if flagged          │                   │   alerts / policy / health   │
│ Redactor (strip PII)         │    Supabase       │   ↓ sync (poll + WorkManager)│
│   ↓                          │  ┌───────────┐    │ Claude API call (key stored  │
│ Outbox (Room, offline queue) │→ │ enrollment │ →  │  ONLY here) classifies the   │
│   ↓ submit_event RPC         │  │ events     │    │  redacted excerpt            │
│ Heartbeat + policy pull      │  │ RLS + RPCs │    │   ↓                          │
│ Manual report / Ask for help │  └───────────┘    │ Local notification to parent │
│ Visible supervision status   │                   │ (lock-screen-safe wording)   │
└──────────────────────────────┘                   └──────────────────────────────┘
```

## Trust & identity model
- **Supervisor account** = Supabase email/password user (GoTrue). All family rows carry
  `family_uid` = that user's id. Row-Level Security restricts every table to
  `family_uid = auth.uid()` — one family can never read another family's rows.
- **Supervised device** has NO user account and NO parent credentials. At enrollment it
  exchanges a short-lived 6-digit code (created by the parent, 15-min expiry, single-use)
  for a random **device token**; only the SHA-256 hash is stored server-side. All child
  writes go through `SECURITY DEFINER` RPCs that validate the token — the device cannot
  read anything, not even its own family's data.
- **Claude API key** is entered on the supervisor phone, stored in EncryptedSharedPreferences
  (Android Keystore-backed), and never synced anywhere. AI analysis runs on the parent
  device against already-redacted excerpts. (§7.3's "backend proxy" alternative is a later
  stage; this design keeps the key off both the child device and the server.)

## Data flow for one message
1. WhatsApp posts a notification on the child phone → listener extracts chat title + text.
2. Local rule engine scores it with conversation context (Room-cached recent messages).
3. Below threshold → nothing leaves the device; counter incremented only.
4. Flagged → Redactor replaces phone numbers/emails/URLs' hosts in the excerpt; only
   matched fragments + minimal metadata form the event.
5. Event → Room outbox → `submit_event` RPC when online (WorkManager retries).
6. Server stamps `retention_expires_at` from the child's policy.
7. Parent app sync: fetches new events; if policy allows cloud AI and a key is configured,
   sends the redacted excerpt to Claude → category, 5-level severity, confidence,
   bilingual summary + recommended next step → patches the event.
8. If final severity ≥ the child's notify threshold → local notification: *"Child-safety
   alert for <name>. Open Safe Companion to review."* Details only inside the app.

## Severity model (5 levels, per spec §10)
1 Informational · 2 Low · 3 Medium · 4 High · 5 Critical.
Rule engine maps its LOW/MED/HIGH to 2/3/4; Claude may assign 1–5 and override with its
confidence attached. All alert wording is "possible / may include" — never asserted fact.

## Failure handling (§18)
- Child offline → outbox queues; parent dashboard shows `last_seen` staleness per device.
- Notification access revoked → heartbeat reports `perm_status`; dashboard shows
  "Protection limited" for that device.
- AI unavailable/no key/quota → event keeps rule-based severity, marked `ai_status=failed`;
  alert still delivered. The system never silently drops an event.
- Expired parent session → refresh-token flow; if that fails, re-login prompt.

## Retention & audit
- Events auto-expire: parent sync deletes rows past `retention_expires_at` (server-side
  scheduled deletion is a later hardening step). Child-side context messages purge daily.
- `audit` table records enrollment, policy changes, alert views, device removal.

## Known MVP limitations (documented honestly in-app)
- No FCM push: parent alerts arrive on sync (instant while dashboard open; ≤ ~15 min via
  background WorkManager). FCM is the next infrastructure step.
- Passive capture covers incoming messages only (see FEASIBILITY.md).
- Supervisor mode is Android-only in this MVP.
- Anon key + RLS is Supabase's intended public-client model; the SQL enforces it.
