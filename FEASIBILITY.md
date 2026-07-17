# Stage 1 — Platform Feasibility Assessment (v0.2)

What WhatsApp-related information a transparent, compliant child-safety app can actually
access, per platform. This governs everything the MVP promises.

## Android (standard personal device) — PRIMARY TARGET

| Channel | Available? | Notes |
|---|---|---|
| Notification access (NotificationListenerService) | ✅ Yes | User grants "Notification access" manually in system Settings; revocable anytime; fully visible. Captures sender/chat title + message text of *incoming* messages **while the chat is not open on screen**. Text may be truncated; media arrives as placeholders ("📷 Photo"). This is the only supported channel for passive text visibility. |
| Share-to-app (ACTION_SEND) | ✅ Yes | Child selects a message/text in WhatsApp → Share → Safe Companion. Deliberate, transparent, works on all Android versions. Basis of "Report conversation". |
| Manual report inside the app | ✅ Yes | Paste or type; always available. |
| Accessibility service reading the screen | ⚠️ Technically possible, NOT used | Play Store policy restricts accessibility use to genuine accessibility purposes; high abuse potential; conflicts with the project's own transparency rules. Excluded from MVP. |
| Reading WhatsApp database / backups | ❌ No | Sandboxed + encrypted; accessing it would break §6 prohibitions. |
| Official WhatsApp API for personal chats | ❌ Does not exist | WhatsApp Business API is for business messaging, not monitoring personal accounts. |

**Consequences for MVP:** passive coverage = incoming-message notifications only. Outgoing
messages from the child are NOT visible passively (WhatsApp doesn't notify about them) —
they are covered only via share/manual report. Detection therefore focuses on what others
send to the child, plus child-initiated reports.

## iOS

| Channel | Available? |
|---|---|
| Reading other apps' notifications | ❌ Not permitted by iOS — no equivalent of NotificationListenerService for third-party apps. |
| Share extension | ✅ Yes — child can share a message to the app for analysis. |
| Screen Time / Family Controls frameworks | ⚠️ Category-level controls (app limits) only; no message content. |
| Passive WhatsApp text monitoring | ❌ Not possible in a compliant app. |

**Consequence:** iOS can only be a *supervisor* platform (parent dashboard) plus manual
child reporting — not passive monitoring. MVP targets Android for the supervised device.
(The supervisor mode in this MVP is also Android; an iOS supervisor app is a later stage.)

## Managed / child accounts
Google Family Link provides install approval and screen-time control but no message
content access; it complements, not replaces, this app.

## Legal & store notes (not legal advice)
- Transparent operation, visible supervision indicator, and no anti-uninstall tricks keep
  the app aligned with Play's stalkerware policy (which requires exactly this posture).
- Parental monitoring of a minor child's device is generally lawful for guardians in most
  jurisdictions incl. Israel, but disclosure to the child is both ethically required by this
  project and the safest legal posture. Distribution beyond personal/family use would
  require store review, a privacy policy URL, and a Data Safety declaration.

## Bottom line for the MVP
Android supervised device using: notification listener (passive incoming), share-target +
manual report (deliberate), on-device rule pre-screen, redaction before upload, cloud AI
second-stage via parent-controlled key. Everything else in §6's "must not" list stays out.
