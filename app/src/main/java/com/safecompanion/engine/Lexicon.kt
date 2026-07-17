package com.safecompanion.engine

/**
 * A detection rule. [regex] is matched (case-insensitive) against the message
 * text. A match contributes [weight] points to its [category].
 *
 * Weights are calibrated so that:
 *   - a single strong indicator (~60) alone can raise a MEDIUM alert,
 *   - weak indicators (~15-20) only matter when several accumulate,
 * which is what keeps isolated words from triggering false alerts.
 */
data class Rule(
    val id: String,
    val category: Category,
    val weight: Int,
    val regex: Regex
)

object Lexicon {

    private fun opts() = setOf(RegexOption.IGNORE_CASE)

    /** Build a rule from literal phrases (Hebrew or English), matched as substrings. */
    private fun lit(id: String, category: Category, weight: Int, vararg phrases: String): Rule {
        val body = phrases.joinToString("|") { Regex.escape(it) }
        return Rule(id, category, weight, Regex(body, opts()))
    }

    /** Build a rule from a raw regex (for URLs / boundaries). */
    private fun rx(id: String, category: Category, weight: Int, pattern: String): Rule =
        Rule(id, category, weight, Regex(pattern, opts()))

    val rules: List<Rule> = listOf(

        // ---------- BULLYING / threats ----------
        lit("bully_die", Category.BULLYING, 65,
            "kill yourself", "kys", "go die", "you should die",
            "לך תמות", "תמות כבר", "מגיע לך למות"),
        lit("bully_hate", Category.BULLYING, 45,
            "i hate you", "everyone hates you", "nobody likes you", "no one likes you",
            "כולם שונאים אותך", "אף אחד לא אוהב אותך", "אני שונא אותך", "אני שונאת אותך"),
        lit("bully_insult", Category.BULLYING, 35,
            "you're stupid", "you are stupid", "you're an idiot", "loser", "you're ugly",
            "you are ugly", "you're fat", "worthless", "shut up",
            "טמבל", "מפגר", "מפגרת", "לוזר", "מכוער", "מכוערת", "שמנה", "מגעיל", "מגעילה",
            "סתום ת", "תסתמי", "חתיכת אפס"),
        lit("bully_threat", Category.BULLYING, 55,
            "i'll beat you", "i will beat you", "i'll hurt you", "i'll find you", "watch your back",
            "אני אכסח אותך", "אני אפוצץ אותך", "אני אמצא אותך", "תיזהר לך", "אתה גמור", "את גמורה"),

        // ---------- SEXUAL ----------
        lit("sex_nudes", Category.SEXUAL, 75,
            "send nudes", "send nude", "send a nude", "naked photo", "naked pic", "nude pic",
            "send pics of your body", "picture without clothes",
            "תשלחי תמונה עירומה", "תמונה בלי בגדים", "תמונה עירום", "שלחי ערומה", "תמונת עירום"),
        lit("sex_explicit", Category.SEXUAL, 55,
            "horny", "are you a virgin", "touch yourself", "show me your body",
            "sexy body", "want to have sex",
            "את בתולה", "תזדייני", "תזדיין", "לגעת בעצמך", "תראי לי את הגוף", "רוצה סקס"),
        lit("sex_words", Category.SEXUAL, 30,
            "boobs", "dick pic", "your tits",
            "שדיים", "זין שלי", "לזיין אותך", "כוסון"),

        // ---------- GROOMING / secrecy / manipulation ----------
        lit("groom_secret", Category.GROOMING, 60,
            "don't tell your parents", "dont tell your parents", "don't tell anyone",
            "keep this a secret", "our little secret", "this is our secret",
            "delete this chat", "delete these messages", "promise you won't tell",
            "אל תספרי להורים", "אל תספר להורים", "אל תספרי לאף אחד", "אל תספר לאף אחד",
            "שמרי בסוד", "שמור בסוד", "הסוד שלנו", "רק בינינו", "תמחקי את השיחה", "תמחק את ההודעות",
            "תבטיחי שלא תספרי", "תבטיח שלא תספר"),
        lit("groom_flatter", Category.GROOMING, 35,
            "you're so mature", "you are so mature", "mature for your age", "so special",
            "you can tell me anything", "no one understands you like i do", "i'll buy you",
            "בוגרת מהגיל שלך", "בוגר מהגיל שלך", "את מיוחדת", "אתה מיוחד",
            "אפשר לספר לי הכל", "אף אחד לא מבין אותך כמוני", "אני אקנה לך"),
        lit("groom_trust", Category.GROOMING, 20,
            "trust me", "just between us", "you can trust me",
            "תסמכי עליי", "תסמוך עליי", "זה נשאר בינינו"),

        // ---------- PERSONAL INFO requests ----------
        lit("pi_password", Category.PERSONAL_INFO, 60,
            "what's your password", "whats your password", "send your password", "your pin code",
            "מה הסיסמה שלך", "תשלחי לי סיסמה", "תשלח לי סיסמה", "מה הקוד שלך"),
        lit("pi_money", Category.PERSONAL_INFO, 55,
            "send money", "send me money", "gift card", "credit card number", "bank account",
            "buy me a gift card",
            "תשלחי כסף", "תשלח כסף", "כרטיס אשראי", "מספר אשראי", "העברה בביט", "תעביר לי כסף"),
        lit("pi_location", Category.PERSONAL_INFO, 45,
            "what's your address", "whats your address", "where do you live", "send your location",
            "share your location", "your home address",
            "מה הכתובת שלך", "איפה את גרה", "איפה אתה גר", "תשלחי מיקום", "תשלח מיקום", "שלח לי לוקיישן"),
        lit("pi_photo", Category.PERSONAL_INFO, 40,
            "send a photo of you", "send me your picture", "send a selfie", "send pic of yourself",
            "תשלחי לי תמונה שלך", "תשלח לי תמונה שלך", "תשלחי סלפי"),
        lit("pi_phone", Category.PERSONAL_INFO, 20,
            "your phone number", "what's your number", "whats your number",
            "מה המספר שלך", "תני לי מספר טלפון", "תן לי מספר טלפון"),

        // ---------- SUSPICIOUS ADULT (age / isolation probing) ----------
        lit("adult_age", Category.SUSPICIOUS_ADULT, 30,
            "how old are you", "what's your age", "how old r u", "what grade are you in",
            "בת כמה את", "בן כמה אתה", "מה הגיל שלך", "באיזו כיתה את", "באיזו כיתה אתה"),
        lit("adult_alone", Category.SUSPICIOUS_ADULT, 40,
            "are you home alone", "are your parents home", "are you alone", "is anyone with you",
            "את לבד בבית", "אתה לבד בבית", "ההורים בבית", "יש עוד מישהו איתך"),
        lit("adult_school", Category.SUSPICIOUS_ADULT, 25,
            "what school do you go to", "which school", "do you have a boyfriend",
            "do you have a girlfriend", "you look older",
            "באיזה בית ספר את", "באיזה בית ספר אתה", "יש לך חבר", "יש לך חברה", "את נראית מבוגרת יותר"),

        // ---------- SCAM / PHISHING ----------
        lit("scam_prize", Category.SCAM_PHISHING, 45,
            "you won", "you have won", "claim your prize", "free gift", "you've been selected",
            "congratulations you won", "free iphone", "free robux", "free v-bucks", "gift card code",
            "זכית", "זכיתם", "קבל את הפרס", "מתנה חינם", "נבחרת לזכות", "ברכות זכית", "רובוקס חינם"),
        rx("scam_shortlink", Category.SCAM_PHISHING, 35,
            """https?://(?:bit\.ly|tinyurl\.com|t\.co|cutt\.ly|is\.gd|goo\.gl|rb\.gy|shorturl)\S*"""),
        rx("scam_anylink", Category.SCAM_PHISHING, 15, """https?://\S+"""),
        lit("scam_verify", Category.SCAM_PHISHING, 35,
            "verify your account", "confirm your account", "your account will be blocked",
            "click here to", "log in to claim", "enter your code",
            "אמת את החשבון", "החשבון שלך ייחסם", "לחץ כאן", "לחצי כאן", "הזן את הקוד", "הזיני את הקוד"),

        // ---------- MEET OFFLINE ----------
        lit("meet_basic", Category.MEET_OFFLINE, 45,
            "let's meet", "lets meet", "meet me", "come to my house", "come to my place",
            "meet in person", "where can we meet", "i'll pick you up", "i will pick you up",
            "בוא ניפגש", "בואי ניפגש", "תגיעי אליי", "תגיע אליי", "ניפגש פנים אל פנים",
            "אני אאסוף אותך", "בוא אליי הביתה", "בואי אליי הביתה"),
        lit("meet_alone", Category.MEET_OFFLINE, 45,
            "come alone", "don't tell anyone we're meeting", "meet me alone", "just the two of us",
            "בואי לבד", "בוא לבד", "אל תספרי שאנחנו נפגשים", "אל תספר שאנחנו נפגשים", "רק שנינו"),

        // ---------- VIOLENCE / HATE ----------
        lit("viol_threat", Category.VIOLENCE_HATE, 55,
            "i'll kill", "i will kill you", "bring a knife", "i'll shoot", "i'll stab",
            "אני אהרוג", "אני אדקור", "אביא סכין", "אני אירה"),
        lit("hate_generic", Category.VIOLENCE_HATE, 40,
            "go back to your country", "you people", "kill all",
            "לך חזרה למדינה שלך", "כל ה", "מוות ל"),

        // ---------- SELF DISCLOSURE (child sharing sensitive info) ----------
        lit("disc_address", Category.SELF_DISCLOSURE, 40,
            "my address is", "i live at", "my home address is", "my school is", "i'm home alone",
            "im home alone", "my password is",
            "הכתובת שלי היא", "אני גר ב", "אני גרה ב", "בית הספר שלי הוא", "אני לבד בבית",
            "הסיסמה שלי היא"),

        // ---------- SELF-HARM / DISTRESS ----------
        lit("harm_self", Category.SELF_HARM, 70,
            "i want to die", "i want to kill myself", "kill myself", "cut myself", "hurt myself",
            "no reason to live", "i hate my life", "end it all", "want to disappear",
            "אני רוצה למות", "לשים סוף לחיים", "לפגוע בעצמי", "לחתוך את עצמי",
            "אין לי סיבה לחיות", "שונא את החיים שלי", "שונאת את החיים שלי", "רוצה להיעלם")
    )
}
