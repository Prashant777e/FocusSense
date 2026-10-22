package com.example.service

import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

data class NodeTextData(
    val text: String,
    val viewId: String? = null,
    val isPassword: Boolean = false,
    val isEditable: Boolean = false
)

data class ScraperFilterResult(
    val isIgnored: Boolean,
    val reason: String,
    val cleanText: String,
    val extractedUrls: List<String>,
    val detectedKeywords: List<String>,
    val candidateCategory: String,
    val needsEscalation: Boolean,
    val rawNodesCount: Int,
    val noiseNodesFiltered: Int
)

object ScraperFilterEngine {

    // Package Whitelist / Ignore List (Tier 1: Noise Elimination)
    private val IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.touchtype.swiftkey",
        "com.samsung.android.honeyboard",
        "com.android.inputmethod",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.mi.android.globallauncher",
        "com.oppo.launcher",
        "com.huawei.android.launcher",
        "com.teslacoilsw.launcher",
        "android",
        "com.android.settings"
    )

    // Common system UI strings to discard (battery, clock, signals, navigation)
    private val NOISY_SYSTEM_PATTERNS = listOf(
        Pattern.compile("^\\d{1,2}:\\d{2}(\\s?[APap][Mm])?$"), // 12:45, 9:00 AM
        Pattern.compile("^\\d{1,3}%$"),                        // 100%, 85%
        Pattern.compile("^(4G|5G|LTE|WiFi|Volte|VoLTE|KB/s|MB/s)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(Home|Back|Recent apps|Overview|Search|Cancel|OK|Done)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^[\\p{Punct}\\s]+$")                  // Only punctuation / whitespace
    )

    // Browser URL Patterns
    private val URL_PATTERN = Pattern.compile(
        "(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://[^\\s]+)",
        Pattern.CASE_INSENSITIVE
    )

    // Tier 2: Heuristic Trigger Keywords
    private val VIOLENCE_AND_THREAT_KEYWORDS = listOf(
        "how to threat", "threat someone", "threaten someone", "threaten", "threatening", "threat",
        "how to kill", "kill someone", "kill", "murder", "assassinate", "stab", "shoot someone", "shoot",
        "gun", "guns", "knife", "knives", "weapon", "weapons", "bomb", "explosive", "attack someone",
        "attack", "hurt someone", "harm someone", "beat up", "poison someone", "poison", "strangle",
        "mass shooting", "school shooting", "die", "death threat", "punch in the face", "beat them",
        "harm others", "hit someone", "how to hurt"
    )

    private val STRANGER_KEYWORDS = listOf(
        "don't tell your mom", "dont tell your mom", "don't tell your parents", "dont tell your parents",
        "keep it a secret", "our secret", "meet me alone", "meet me at", "skatepark behind",
        "send me photos", "how old are you", "give me your address", "where do you live",
        "free robux code", "what is your phone number", "meet up after school", "come over to my place",
        "are you home alone", "send selfie"
    )

    private val BULLYING_KEYWORDS = listOf(
        "kill yourself", "nobody likes you", "loser", "don't show up", "ugly",
        "hate you", "freak", "stupid idiot", "disappear", "jump off",
        "shut up trash", "get out of our group", "worthless", "go die"
    )

    private val EXPLICIT_KEYWORDS = listOf(
        "nude", "porn", "xxx", "leaked tapes", "strip chat", "onlyfans bypass",
        "drugs buy", "vape delivery", "buy weed"
    )

    private val SELF_HARM_KEYWORDS = listOf(
        "i want to die", "end my life", "suicide method", "cut myself", "no reason to live",
        "harm myself", "bleed out", "goodbye note"
    )

    private val ACADEMIC_CHEATING_KEYWORDS = listOf(
        "bypass turnitin", "cheat sheet exam", "write my essay bot", "hack quiz answers",
        "test leak 2026", "buy exam questions", "bypass plagiarism"
    )

    // Regex for standalone critical danger words (word boundary avoids false positives like 'skill')
    private val CRITICAL_WORD_REGEX = Pattern.compile(
        "\\b(kill|kills|killing|threat|threats|threaten|threatens|threatening|murder|murders|stab|stabs|shoot|shoots|shooting|gun|guns|knife|knives|bomb|bombs|suicide|poison)\\b",
        Pattern.CASE_INSENSITIVE
    )

    fun isIgnoredPackage(packageName: String, ownPackage: String): Boolean {
        if (packageName == ownPackage) return true
        if (IGNORED_PACKAGES.contains(packageName)) return true
        if (packageName.contains("launcher") || packageName.contains("keyboard") || packageName.contains("systemui")) {
            return true
        }
        return false
    }

    fun isKnownBrowser(packageName: String): Boolean {
        return packageName.contains("chrome") ||
                packageName.contains("firefox") ||
                packageName.contains("browser") ||
                packageName.contains("opera") ||
                packageName.contains("edge") ||
                packageName.contains("brave")
    }

    fun filterAndClassifyWindow(
        packageName: String,
        ownPackage: String,
        className: String?,
        nodes: List<NodeTextData>
    ): ScraperFilterResult {
        // 1. Package check
        if (isIgnoredPackage(packageName, ownPackage)) {
            return ScraperFilterResult(
                isIgnored = true,
                reason = "Ignored package: $packageName",
                cleanText = "",
                extractedUrls = emptyList(),
                detectedKeywords = emptyList(),
                candidateCategory = "Safe",
                needsEscalation = false,
                rawNodesCount = nodes.size,
                noiseNodesFiltered = nodes.size
            )
        }

        var noiseFiltered = 0
        val retainedStrings = mutableListOf<String>()
        val extractedUrls = mutableListOf<String>()

        for (node in nodes) {
            // Privacy Shield: Never scrape password fields
            if (node.isPassword) {
                noiseFiltered++
                continue
            }

            val text = node.text.trim()

            // Discard empty or single-character noise
            if (text.length <= 1) {
                noiseFiltered++
                continue
            }

            // Discard system clocks, battery percent, signal indicators
            var isNoisy = false
            for (pattern in NOISY_SYSTEM_PATTERNS) {
                if (pattern.matcher(text).matches()) {
                    isNoisy = true
                    break
                }
            }
            if (isNoisy) {
                noiseFiltered++
                continue
            }

            // Extract Browser URLs
            val urlMatcher = URL_PATTERN.matcher(text)
            while (urlMatcher.find()) {
                val url = urlMatcher.group()
                if (!extractedUrls.contains(url)) {
                    extractedUrls.add(url)
                }
            }

            retainedStrings.add(text)
        }

        val cleanCombinedText = retainedStrings.joinToString(" ")
        val lowerText = cleanCombinedText.lowercase()

        // 2. Tier 2: Heuristic Trigger Evaluation
        val detectedKeywords = mutableListOf<String>()
        var candidateCategory = "Safe"
        var needsEscalation = false

        // Check standalone critical words via regex word boundaries (kill, threat, murder, shoot, etc.)
        val criticalMatcher = CRITICAL_WORD_REGEX.matcher(lowerText)
        while (criticalMatcher.find()) {
            val matchedWord = criticalMatcher.group()
            if (!detectedKeywords.contains(matchedWord)) {
                detectedKeywords.add(matchedWord)
            }
            if (!needsEscalation) {
                candidateCategory = when (matchedWord.lowercase()) {
                    "suicide" -> "Self-Harm Risk"
                    else -> "Violence & Threats"
                }
                needsEscalation = true
            }
        }

        fun checkCategory(keywords: List<String>, categoryName: String) {
            for (k in keywords) {
                if (lowerText.contains(k)) {
                    if (!detectedKeywords.contains(k)) {
                        detectedKeywords.add(k)
                    }
                    if (!needsEscalation) {
                        candidateCategory = categoryName
                        needsEscalation = true
                    }
                }
            }
        }

        // Priority order for evaluation
        checkCategory(VIOLENCE_AND_THREAT_KEYWORDS, "Violence & Threats")
        checkCategory(SELF_HARM_KEYWORDS, "Self-Harm Risk")
        checkCategory(STRANGER_KEYWORDS, "Stranger Risk")
        checkCategory(BULLYING_KEYWORDS, "Cyberbullying")
        checkCategory(EXPLICIT_KEYWORDS, "Explicit Content")
        checkCategory(ACADEMIC_CHEATING_KEYWORDS, "Academic Distraction")

        // CRITICAL SAFETY RULE: If a danger keyword was detected (e.g. "kill", "threat", "how to threat someone"),
        // NEVER drop it even if the text is short (< 15 chars)!
        if (cleanCombinedText.length < 15 && !needsEscalation) {
            return ScraperFilterResult(
                isIgnored = true,
                reason = "Below minimum threshold (${cleanCombinedText.length} chars)",
                cleanText = cleanCombinedText,
                extractedUrls = extractedUrls,
                detectedKeywords = emptyList(),
                candidateCategory = "Safe",
                needsEscalation = false,
                rawNodesCount = nodes.size,
                noiseNodesFiltered = noiseFiltered
            )
        }

        return ScraperFilterResult(
            isIgnored = false,
            reason = if (needsEscalation) "Heuristic trigger matched: $candidateCategory" else "Benign browsing content",
            cleanText = cleanCombinedText,
            extractedUrls = extractedUrls,
            detectedKeywords = detectedKeywords,
            candidateCategory = candidateCategory,
            needsEscalation = needsEscalation,
            rawNodesCount = nodes.size,
            noiseNodesFiltered = noiseFiltered
        )
    }
}
