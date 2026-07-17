package com.safecompanion.engine

/**
 * Safety categories the engine can flag. Each carries bilingual display names
 * and short explanation templates used to describe a concern to the parent.
 *
 * These map directly to the project's required safety categories.
 */
enum class Category(
    val id: String,
    val titleEn: String,
    val titleHe: String,
    val explainEn: String,
    val explainHe: String
) {
    BULLYING(
        "bullying",
        "Bullying or threats",
        "בריונות או איומים",
        "Language that looks like bullying, humiliation, insults or threats aimed at the child.",
        "שפה שנראית כמו בריונות, השפלה, עלבונות או איומים המכוונים לילד/ה."
    ),
    SUSPICIOUS_ADULT(
        "suspicious_adult",
        "Possible unknown adult",
        "מבוגר לא מוכר אפשרי",
        "Signs of an unfamiliar person probing the child's age, school or personal life.",
        "סימנים לכך שאדם לא מוכר מנסה לברר את הגיל, בית הספר או חייו האישיים של הילד/ה."
    ),
    GROOMING(
        "grooming",
        "Grooming or manipulation",
        "טיפוח (grooming) או מניפולציה",
        "A concerning pattern: requests for secrecy, flattery, pressure, or 'this is just between us'.",
        "דפוס מדאיג: בקשות לשמור בסוד, חנופה, לחץ או 'זה נשאר רק בינינו'."
    ),
    SEXUAL(
        "sexual",
        "Sexual content or requests",
        "תוכן או בקשות מיניות",
        "Sexual language, requests for intimate photos, or sexual pressure.",
        "שפה מינית, בקשות לתמונות אינטימיות או לחץ מיני."
    ),
    VIOLENCE_HATE(
        "violence_hate",
        "Violence or hate",
        "אלימות או שנאה",
        "Violent, hateful or dangerous content directed at or shown to the child.",
        "תוכן אלים, שנאתי או מסוכן המכוון לילד/ה או שנחשף אליו."
    ),
    PERSONAL_INFO(
        "personal_info",
        "Request for personal info",
        "בקשה למידע אישי",
        "Someone asking for a password, address, location, money, or private photos.",
        "מישהו מבקש סיסמה, כתובת, מיקום, כסף או תמונות פרטיות."
    ),
    SCAM_PHISHING(
        "scam_phishing",
        "Scam or suspicious link",
        "הונאה או קישור חשוד",
        "Possible scam, impersonation, prize bait, or a suspicious link.",
        "הונאה אפשרית, התחזות, פיתוי בפרסים או קישור חשוד."
    ),
    MEET_OFFLINE(
        "meet_offline",
        "Pressure to meet in person",
        "לחץ להיפגש פנים אל פנים",
        "Someone pushing to meet the child offline, alone, or without telling anyone.",
        "מישהו דוחף להיפגש עם הילד/ה מחוץ לרשת, לבד או בלי לספר לאף אחד."
    ),
    SELF_DISCLOSURE(
        "self_disclosure",
        "Sharing sensitive info",
        "שיתוף מידע רגיש",
        "The child may be sharing highly sensitive personal information.",
        "ייתכן שהילד/ה משתף/ת מידע אישי רגיש מאוד."
    ),
    SELF_HARM(
        "self_harm",
        "Emotional distress",
        "מצוקה רגשית",
        "Language suggesting the child may be in emotional distress or at risk of self-harm.",
        "שפה שמרמזת שהילד/ה עשוי/ה להיות במצוקה רגשית או בסיכון לפגיעה עצמית."
    );

    companion object {
        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}
