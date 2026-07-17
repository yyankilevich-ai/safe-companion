package com.safecompanion.redact

/**
 * Removes direct personal identifiers from an excerpt before it leaves the
 * child device. The parent still understands the concern; the raw identifier
 * does not travel or get stored.
 */
object Redactor {

    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phone = Regex("""\+?\d[\d\s\-()]{6,}\d""")
    private val israeliId = Regex("""\b\d{9}\b""")
    private val urlCreds = Regex("""(https?://)[^/\s]*@""")

    fun redact(text: String): String = text
        .replace(email, "[EMAIL]")
        .replace(urlCreds, "$1")
        .replace(phone, "[PHONE]")
        .replace(israeliId, "[ID_NUMBER]")
        .take(600)
}
