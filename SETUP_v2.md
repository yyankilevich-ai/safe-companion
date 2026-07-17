# Safe Companion v0.2 — Setup Guide (Supervisor + Supervised)
# מדריך הקמה — מצב הורה + מצב ילד

This version is a real two-phone system: a **parent (Supervisor)** app and a **child
(Supervised)** app, linked through a free Supabase backend, with optional Claude AI analysis.

Do the steps in order. About 20–30 minutes the first time.

---

## Step 1 — Create the Supabase backend (free, ~5 min)

1. Go to https://supabase.com → **Start your project** → sign in (GitHub login is easiest).
2. Click **New project**. Pick any name, set a database password (save it), choose a region
   near you, and create it. Wait ~2 minutes for it to provision.
3. In the left sidebar open **SQL Editor** → **New query**.
4. Open the file `supabase/schema.sql` from this project, copy **all** of it, paste into the
   editor, and click **Run**. You should see "Success. No rows returned."
5. Open **Project Settings** (gear, bottom-left) → **Data API**. Copy two values:
   - **Project URL** (looks like `https://abcd1234.supabase.co`)
   - Under **API Keys**: the **`anon` / `public`** key (a long string).

> The anon key is meant to live in client apps — the security is enforced by the Row-Level
> Security rules in the SQL you just ran, so one family can never see another's data.

---

## Step 2 — Put those two values into the app

1. Open `app/src/main/java/com/safecompanion/BackendConfig.kt`.
2. Replace the two placeholders with your Project URL and anon key:

```kotlin
const val SUPABASE_URL = "https://abcd1234.supabase.co"
const val SUPABASE_ANON_KEY = "eyJhbGciOiJI...your-long-anon-key..."
```

3. Save. (You'll upload the project to GitHub next; this is the only file you must edit.)

---

## Step 3 — Build the APK (same GitHub flow as before)

1. Put this whole updated project into your GitHub repo (replace the old files). Easiest:
   in your repo, delete the old contents or just **Add file → Upload files** and drop in the
   updated folders, then **Commit**. Make sure `.github/workflows/build.yml` is present.
2. The **Actions** tab runs **Build APK**. Wait for the green check, open the run, and
   download **SafeCompanion-debug-apk** → unzip → `app-debug.apk`.

> The workflow is already the fixed version (uses the runner's Gradle, skips wrapper
> validation), so it builds without the earlier errors.

You'll install the **same APK** on both phones — the app asks which mode to use on first open.

---

## Step 4 — Set up the PARENT phone (Supervisor)

1. Install `app-debug.apk` (tap **Got it** / **Install anyway** past Play Protect, as before).
2. Open **Safe Companion** → choose **I'm the parent (Supervisor)**.
3. Create an account with email + password (min 6 chars). You're taken to the dashboard.
4. Go to the **Family** tab → **+ Child** → enter a name and age band → **Add**.
5. (Recommended) **Settings** tab → paste your **Anthropic API key** (`sk-ant-…`) → **Save key**.
   Get a key at https://console.anthropic.com → Settings → API Keys. Testing costs pennies.
   The key is stored encrypted on the parent phone only and never reaches the child device.

---

## Step 5 — Set up the CHILD phone (Supervised)

1. On the **parent** phone: **Family** tab → the child's card → **Enroll code**. A 6-character
   code appears (valid 15 minutes).
2. On the **child** phone: install the same `app-debug.apk`, open it → choose
   **This is the child's device (Supervised)** → type the code → **Connect device**.
3. The child phone now shows "Safety supervision is on". Tap **Enable** next to
   "Notification access needed" and turn on access for **Safe Companion**.
4. The child's card on the parent dashboard should switch to **Protected · active** within a
   minute (pull **Refresh** on the parent's Alerts tab).

---

## Step 6 — Test it (with a real incoming message)

Remember from last time: WhatsApp only notifies about messages the child **receives**, and
only when the chat is **not open on screen**.

1. From **another** phone/number, send the child phone a test message, e.g. `I will kill you`
   or `don't tell your parents, it's our little secret`.
2. On the child phone, make sure you're **not** sitting in that WhatsApp chat (go to the home
   screen) so the notification fires.
3. On the **parent** phone: open the app → **Alerts** tab → **Refresh**. Within a few seconds
   you should see a new alert with a category and severity. Tap it for the AI summary, the
   recommended step, and the redacted excerpt. A phone notification also arrives if the
   severity meets the child's "notify from severity" policy.

Also try the child-side tools on the child phone: **Report a conversation** (paste any text)
and **I need help** — both create alerts on the parent dashboard immediately.

---

## Troubleshooting / פתרון תקלות

- **"Not linked to a family backend"** on first screen → `BackendConfig.kt` still has the
  placeholders, or the build didn't include your edit. Re-check Step 2 and rebuild.
- **Enrollment says invalid/expired** → codes last 15 min and are single-use; generate a new one.
- **Child shows "Protection limited"** → Notification Access is off on the child phone; re-enable it.
- **Alerts have no AI summary** (says "AI analysis unavailable") → no API key set, or the key/
  quota failed; the rule-based severity still shows. Add a key in the parent's Settings.
- **No alert after a test** → confirm the message was **incoming** and the chat was **closed**,
  and that the category is enabled in the child's **Policy**. Raise **Sensitivity** if needed.
- **Build failed** → open the red step in Actions and send me the last ~30 lines.

Send me screenshots of the parent dashboard and any alert detail, and we'll tune from there.
