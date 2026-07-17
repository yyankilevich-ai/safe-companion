# Quick Test Today — MacroDroid (no compiling)
# בדיקה מהירה עוד היום — MacroDroid (בלי בנייה)

Use this to validate the **detection idea** on your WhatsApp self-group within ~10 minutes,
while the real app is being built on GitHub. It is intentionally simple: it pops a warning
notification when a risky phrase appears in a WhatsApp notification. The polished dashboard,
severity, context scoring and privacy controls live in the real app — this is just a taste.

זה נועד לבדוק את **רעיון הזיהוי** על קבוצת הוואטסאפ שלך תוך ~10 דקות. זו גרסה פשוטה בכוונה:
היא מקפיצה התראת אזהרה כשמופיע ביטוי מסוכן בהתראת וואטסאפ. לוח הבקרה, החומרה, ניתוח ההקשר
ובקרות הפרטיות נמצאים באפליקציה האמיתית — זה רק טעימה.

---

## Setup / התקנה

1. Install **MacroDroid** (free) from the Play Store, open it, and grant the permissions it
   asks for — especially **Notification Access** (Settings inside MacroDroid → it guides you).
   התקן/י את **MacroDroid** מחנות Play ואשר/י את ההרשאות, במיוחד **גישה להתראות**.

2. Tap **Add Macro** (the **+**). / הקש/י **Add Macro**.

3. **Trigger** → **Device Events** → **Notification Received**.
   - **Applications**: select **WhatsApp**.
   - Turn on **Text Content Trigger** (or "Notification Content" → "Text").
   - Enable **Use Regular Expression** (a checkbox / "Regex").
   - Paste the **regex** below into the text/content field.
   טריגר → Notification Received → בחר/י **WhatsApp**, הפעל/י **Regex**, והדבק/י את הביטוי למטה.

4. **Action** → **Notifications** → **Display Notification**.
   - **Title**: `⚠️ Safe Companion`
   - **Text**: `Possible concern in [notification_title]: [notification_text]`
   - (Tap the magic-wand / variable button to insert `[notification_title]` and
     `[notification_text]`.)
   פעולה → Display Notification, עם הכותרת והטקסט שלמעלה (הכנס/י את המשתנים דרך כפתור המשתנים).

5. **Save** the macro (the floppy-disk icon). Make sure the macro is **enabled**.
   שמור/י את המאקרו וודא/י שהוא **פעיל**.

---

## The regex to paste / הביטוי הרגולרי להדבקה

Paste this whole line into the trigger's text field (with Regex enabled). It is
case-insensitive and covers Hebrew + English test phrases:

```
(?i)(kill yourself|kys|nobody likes you|you're a loser|you are stupid|send nudes|naked (photo|pic)|are you a virgin|don't tell your parents|our (little )?secret|keep this a secret|what'?s your (address|password)|send (me )?(your )?(location|money)|gift card|come alone|let'?s meet|meet me alone|you won|claim your prize|https?://(bit\.ly|tinyurl|t\.co|cutt\.ly)|i (hate my life|want to die)|no reason to live|תמות|כולם שונאים אותך|אף אחד לא אוהב אותך|טמבל|מפגר|תמונה עירומה|בלי בגדים|את בתולה|אל תספר(י)? להורים|הסוד שלנו|שמור(י)? בסוד|מה הכתובת שלך|מה הסיסמה|תשלח(י)? (כסף|מיקום)|בוא(י)? לבד|בוא(י)? ניפגש|זכית|לחץ כאן|אני רוצה למות|שונא את החיים)
```

---

## Test / בדיקה

Send the test phrases from **INSTALL_AND_TEST.md → Part C** in your self-group, and
**leave the chat** so WhatsApp posts a notification. You should get a `⚠️ Safe Companion`
notification for the risky ones and nothing for neutral messages.
שלח/י את הודעות הבדיקה, **צא/י מהצ'אט**, ואמור/ה לקבל התראת אזהרה על ההודעות המסוכנות.

### Want category labels? / רוצה תוויות קטגוריה?
Create a **separate macro per category** using a smaller regex (just that category's phrases)
and a different notification title (e.g. `⚠️ Grooming`, `⚠️ Bullying`). That mimics what the
real app does automatically.

## Limits of this quick test / מגבלות הבדיקה המהירה
- No severity, no conversation-context scoring, no age/sensitivity settings, no dashboard,
  no auto-delete — all of that is in the real app.
- It alerts on isolated words (that's exactly what the real engine's context scoring avoids).
- MacroDroid itself has broad permissions; the real app has **no internet permission at all**.
