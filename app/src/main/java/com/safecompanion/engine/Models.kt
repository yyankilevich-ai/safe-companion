package com.safecompanion.engine

enum class Severity(val level: Int) {
    LOW(1), MEDIUM(2), HIGH(3);

    companion object {
        fun fromLevel(l: Int): Severity = entries.firstOrNull { it.level == l } ?: LOW
    }
}

enum class Sensitivity { LOW, MEDIUM, HIGH }

/** Age bands change how protective the thresholds are. */
enum class AgeBand { UNDER_11, AGE_11_13, AGE_14_17 }

/** A single matched indicator inside the text. */
data class RiskSignal(
    val category: Category,
    val ruleId: String,
    val matchedText: String,
    val weight: Int
)

/** Outcome of analyzing one message in its conversation context. */
data class AnalysisResult(
    val isAlert: Boolean,
    val category: Category?,
    val severity: Severity,
    val score: Int,
    val signals: List<RiskSignal>,
    val explanationEn: String,
    val explanationHe: String,
    /** A minimized snippet — only the matched fragments, never the full conversation. */
    val redactedSnippet: String
)
