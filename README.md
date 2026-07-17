# Safe Companion — WhatsApp Child-Safety Companion (v0.2)

A transparent, privacy-conscious child-safety **early-warning system**. One Android app runs
in two modes — **Supervisor** (parent) and **Supervised** (child) — linked through a family
account, with on-device rule pre-screening plus optional Claude AI analysis. Its goal is to
detect *meaningful* risk and summarize it for the parent, not to expose every message.

New in v0.2 (rebuilt to the full product spec): dual-mode app, parent accounts, secure
QR-less **enrollment-code** device linking, a multi-child **family dashboard**, a backend
(Supabase) for account/device/alert sync, redaction before anything leaves the child device,
5-level severity + confidence, **Claude AI** second-stage analysis with the key stored only on
the parent phone, manual **Report** + **Ask for help**, a WhatsApp **share-to-report** target,
an offline outbox, per-child policy (categories, sensitivity, retention, notify threshold),
device/permission health, and an audit log.

## Read these first
- **FEASIBILITY.md** — what WhatsApp data is actually accessible per platform (Stage 1).
- **ARCHITECTURE.md** — system design, data flow, trust model, failure handling (Stage 3).
- **SETUP_v2.md** — click-by-click: Supabase → build → parent phone → child phone → test.

## How to build
Same GitHub Actions flow as before (no Android Studio needed). Fill in `BackendConfig.kt` with
your Supabase URL + anon key, push to your repo, download `app-debug.apk` from the Actions run.
Full steps in SETUP_v2.md.

## Privacy posture (spec §17)
On-device first-level analysis; only **redacted safety findings** leave the child device —
never full conversations. Personal identifiers are masked before upload. The parent's AI key
never touches the child device. Data auto-expires per the per-child retention window. Row-Level
Security isolates each family. Transparent supervision indicator on the child device; no
hiding, no anti-uninstall tricks, no encryption bypass.

## Project layout
```
supabase/schema.sql                     backend: tables, RLS, enrollment/event/heartbeat RPCs
app/.../BackendConfig.kt                YOU fill in: Supabase URL + anon key
app/.../engine/                         RuleEngine, Lexicon (HE+EN), Category, Severity
app/.../redact/Redactor.kt              PII masking before upload
app/.../net/                            SupabaseApi (auth+REST+RPC), AnthropicClient (Claude)
app/.../data/                           Room: alerts, context, offline outbox; child pipeline
app/.../service/WaNotificationListener  transparent capture channel
app/.../work/                           child sync, parent sync (+AI+notify), retention
app/.../settings/                       session state + Keystore-backed secrets
app/.../ui/supervisor/                  parent: auth, family dashboard, alerts, policy, AI
app/.../ui/supervised/                  child: enroll, transparency, report, ask-for-help
.github/workflows/build.yml             CI that compiles the APK
```

## Known MVP limitations (documented honestly)
Passive capture = incoming WhatsApp messages only (Android notifications; see FEASIBILITY.md).
No FCM push yet — parent alerts arrive on sync (instant while the app is open, otherwise up to
~15 min in the background). Supervisor mode is Android-only in this version. iOS supervised
monitoring is not feasible in a compliant app; iOS would be manual-report + supervisor only.
