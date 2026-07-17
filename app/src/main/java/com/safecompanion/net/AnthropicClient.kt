package com.safecompanion.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Claude API client. Runs ONLY on the supervisor (parent) device, with the
 * parent's own API key, against excerpts that were already redacted on the
 * child device. Synchronous — call from Dispatchers.IO.
 */
object AnthropicClient {

    private val json = "application/json; charset=utf-8".toMediaType()

    data class AiVerdict(
        val category: String,
        val severity: Int,        // 1..5
        val confidence: Double,   // 0..1
        val summaryEn: String,
        val summaryHe: String,
        val recommendationEn: String,
        val recommendationHe: String
    )

    private const val SYSTEM_PROMPT = """You are a child-safety analyst inside a transparent parental-safety app. You receive a short, redacted excerpt from a WhatsApp conversation involving a child, plus metadata and the on-device rule engine's preliminary finding. Assess whether it indicates a real safety concern.

Categories: bullying, suspicious_adult, grooming, sexual, violence_hate, personal_info, scam_phishing, meet_offline, self_disclosure, self_harm, none.

Severity scale: 1=informational, 2=low, 3=medium, 4=high, 5=critical (immediate credible danger).

Rules:
- Consider context; single crude words between friends are often banter — judge patterns and intent.
- Use cautious wording ("possible", "may include"); never present the assessment as fact.
- summary: 1-2 sentences a parent instantly understands. recommendation: one concrete, calm next step for the parent.
- Answer in BOTH English and Hebrew.
- Output ONLY a JSON object, no markdown, with exactly these keys:
{"category": "...", "severity": N, "confidence": 0.0-1.0, "summary_en": "...", "summary_he": "...", "recommendation_en": "...", "recommendation_he": "..."}"""

    fun analyze(
        apiKey: String,
        model: String,
        ageBand: String,
        conversationTitle: String?,
        excerpt: String,
        ruleCategory: String,
        ruleSeverity: Int,
        signals: String?
    ): AiVerdict {
        val userMsg = JSONObject()
            .put("child_age_band", ageBand)
            .put("conversation", conversationTitle ?: "unknown")
            .put("redacted_excerpt", excerpt)
            .put("rule_engine_category", ruleCategory)
            .put("rule_engine_severity", ruleSeverity)
            .put("rule_engine_signals", signals ?: "")
            .toString()

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 800)
            .put("system", SYSTEM_PROMPT)
            .put(
                "messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", userMsg)
                )
            )

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(json))
            .build()

        SupabaseApi.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = try {
                    JSONObject(text).optJSONObject("error")?.optString("message") ?: text.take(200)
                } catch (_: Exception) { text.take(200) }
                throw SupabaseApi.ApiException(resp.code, msg)
            }
            val content = JSONObject(text).getJSONArray("content")
            val answer = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") answer.append(block.optString("text"))
            }
            val raw = answer.toString().trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val o = JSONObject(raw)
            return AiVerdict(
                category = o.optString("category", "none"),
                severity = o.optInt("severity", 2).coerceIn(1, 5),
                confidence = o.optDouble("confidence", 0.5).coerceIn(0.0, 1.0),
                summaryEn = o.optString("summary_en"),
                summaryHe = o.optString("summary_he"),
                recommendationEn = o.optString("recommendation_en"),
                recommendationHe = o.optString("recommendation_he")
            )
        }
    }
}
