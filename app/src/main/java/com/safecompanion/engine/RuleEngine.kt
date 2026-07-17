package com.safecompanion.engine

/**
 * On-device analysis engine. No network, no external services.
 *
 * It scores a message against the [Lexicon], uses the surrounding conversation
 * as weak supporting context (so it does NOT alert on isolated words), applies
 * a few cross-category escalation rules (e.g. secrecy + meeting), then maps the
 * result to a category + severity given the parent's sensitivity and the child's
 * age band.
 */
class RuleEngine(
    private val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    private val ageBand: AgeBand = AgeBand.AGE_11_13,
    private val enabledCategoryIds: Set<String> = Category.entries.map { it.id }.toSet()
) {

    data class Config(
        val sensitivity: Sensitivity,
        val ageBand: AgeBand,
        val enabledCategoryIds: Set<String>
    )

    private val baseAlert = 40
    private val baseMedium = 65
    private val baseHigh = 95

    /**
     * @param currentText the newly-arrived message.
     * @param contextTexts recent prior messages in the same conversation (oldest→newest).
     */
    fun analyze(currentText: String, contextTexts: List<String> = emptyList()): AnalysisResult {
        val current = currentText
        val contextBlob = contextTexts.joinToString("\n")

        val perCategory = linkedMapOf<Category, Int>()
        val signals = mutableListOf<RiskSignal>()
        val matchedFragments = linkedSetOf<String>()

        // Score the current message at full weight.
        for (rule in Lexicon.rules) {
            val m = rule.regex.find(current)
            if (m != null) {
                perCategory[rule.category] = (perCategory[rule.category] ?: 0) + rule.weight
                signals += RiskSignal(rule.category, rule.id, m.value, rule.weight)
                matchedFragments += m.value.trim()
            }
        }

        // Score the surrounding context at reduced weight — supporting evidence only,
        // never enough on its own to raise an alert.
        val contextHits = mutableSetOf<Category>()
        if (contextBlob.isNotBlank()) {
            for (rule in Lexicon.rules) {
                if (rule.regex.containsMatchIn(contextBlob)) {
                    perCategory[rule.category] = (perCategory[rule.category] ?: 0) + (rule.weight / 3)
                    contextHits += rule.category
                }
            }
        }

        applyCombos(perCategory, signals, contextHits)

        // Pick the highest-scoring category that the parent has enabled.
        val ranked = perCategory.entries
            .filter { it.key.id in enabledCategoryIds }
            .sortedByDescending { it.value }

        val top = ranked.firstOrNull()
        if (top == null) {
            return AnalysisResult(false, null, Severity.LOW, 0, emptyList(), "", "", "")
        }

        val score = top.value
        val category = top.key
        val (alertT, medT, highT) = thresholdsFor(category)

        val severity = when {
            score >= highT -> Severity.HIGH
            score >= medT -> Severity.MEDIUM
            score >= alertT -> Severity.LOW
            else -> Severity.LOW
        }
        val isAlert = score >= alertT

        val catSignals = signals.filter { it.category == category }
        val snippet = matchedFragments.take(6).joinToString("  …  ")

        return AnalysisResult(
            isAlert = isAlert,
            category = category,
            severity = severity,
            score = score,
            signals = catSignals.ifEmpty { signals },
            explanationEn = buildExplanation(category, severity, isEnglish = true),
            explanationHe = buildExplanation(category, severity, isEnglish = false),
            redactedSnippet = snippet
        )
    }

    /** Cross-category escalations. These are what make patterns meaningful. */
    private fun applyCombos(
        per: MutableMap<Category, Int>,
        signals: List<RiskSignal>,
        contextHits: Set<Category>
    ) {
        fun has(cat: Category) = per.containsKey(cat) || cat in contextHits
        fun bump(cat: Category, by: Int) { per[cat] = (per[cat] ?: 0) + by }

        val secrecy = signals.any { it.ruleId == "groom_secret" } || Category.GROOMING in contextHits

        // Secrecy + (sexual / meeting / isolation) => classic grooming escalation.
        if (secrecy && (has(Category.SEXUAL) || has(Category.MEET_OFFLINE) || has(Category.SUSPICIOUS_ADULT))) {
            bump(Category.GROOMING, 35)
        }
        // Age/isolation probing + sexual => strong concern.
        if (has(Category.SUSPICIOUS_ADULT) && has(Category.SEXUAL)) {
            bump(Category.SEXUAL, 25)
            bump(Category.SUSPICIOUS_ADULT, 15)
        }
        // A link plus prize/verify bait => scam.
        val hasLink = signals.any { it.ruleId == "scam_anylink" || it.ruleId == "scam_shortlink" }
        val hasBait = signals.any { it.ruleId == "scam_prize" || it.ruleId == "scam_verify" }
        if (hasLink && hasBait) bump(Category.SCAM_PHISHING, 25)
        // Meeting + secrecy => escalate meeting.
        if (has(Category.MEET_OFFLINE) && secrecy) bump(Category.MEET_OFFLINE, 25)
    }

    private data class Thresholds(val alert: Int, val medium: Int, val high: Int)

    private fun thresholdsFor(category: Category): Thresholds {
        val sens = when (sensitivity) {
            Sensitivity.HIGH -> -15
            Sensitivity.LOW -> 15
            Sensitivity.MEDIUM -> 0
        }
        val age = ageAdjust(category)
        return Thresholds(
            (baseAlert + sens + age).coerceAtLeast(15),
            (baseMedium + sens + age).coerceAtLeast(25),
            (baseHigh + sens + age).coerceAtLeast(40)
        )
    }

    private fun ageAdjust(category: Category): Int = when (ageBand) {
        AgeBand.UNDER_11 -> when (category) {
            Category.SEXUAL, Category.MEET_OFFLINE, Category.GROOMING,
            Category.SUSPICIOUS_ADULT, Category.PERSONAL_INFO -> -12
            else -> 0
        }
        AgeBand.AGE_14_17 -> when (category) {
            Category.BULLYING, Category.SEXUAL -> 12
            else -> 0
        }
        AgeBand.AGE_11_13 -> 0
    }

    private fun buildExplanation(category: Category, severity: Severity, isEnglish: Boolean): String {
        val core = if (isEnglish) category.explainEn else category.explainHe
        val prefix = if (isEnglish) {
            when (severity) {
                Severity.HIGH -> "Worth checking soon. "
                Severity.MEDIUM -> "Worth a look. "
                Severity.LOW -> "Minor, shared for awareness. "
            }
        } else {
            when (severity) {
                Severity.HIGH -> "כדאי לבדוק בקרוב. "
                Severity.MEDIUM -> "כדאי להציץ. "
                Severity.LOW -> "עניין קל, לידיעה בלבד. "
            }
        }
        return prefix + core
    }

    companion object {
        fun from(config: Config) =
            RuleEngine(config.sensitivity, config.ageBand, config.enabledCategoryIds)
    }
}
