package com.example.ai

import android.content.Context
import com.example.BuildConfig
import com.example.FocusSenseApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Detailed step in the hierarchical multi-tier execution trace.
 * Used for research inspection and ablation profiling.
 */
data class LayerTraceStep(
    val layerNumber: Int,
    val layerName: String,
    val executionTimeMs: Long,
    val decision: String, // "PASSED_SAFE", "EMERGENCY_FLAGGED", "RESOLVED_ON_EDGE", "ESCALATED_TO_L2", "ESCALATED_TO_L3", "CLOUD_DELIBERATED"
    val confidence: Float,
    val note: String
)

/**
 * Academic evaluation modes allowing researchers to perform ablation studies
 * comparing individual stages against the cascaded edge-cloud architecture.
 */
enum class ThreatAblationMode(val displayName: String, val shortDesc: String) {
    CASCADED_TRI_LAYER(
        "Cascaded Tri-Layer (Proposed)",
        "L1 Rules (<1ms) -> L2 Dual Edge MLs (~18ms) -> L3 Cloud LLM (~380ms)"
    ),
    RULE_ONLY(
        "Ablation 1: Rule-Based Only (L1)",
        "Zero-latency deterministic keyword/regex heuristics only"
    ),
    ON_DEVICE_ML_ONLY(
        "Ablation 2: On-Device ML Only (L2)",
        "100% Dual Edge Classifiers (Model 1 LogReg + Model 2 TFLite Ensemble) with zero cloud egress"
    ),
    CLOUD_LLM_ONLY(
        "Ablation 3: Cloud LLM Only (L3)",
        "Direct unbuffered API call to Gemini 3.5 Flash cloud reasoning"
    )
}

data class ThreatAnalysisResult(
    val isFlagged: Boolean,
    val threatCategory: String,
    val confidenceScore: Float,
    val aiAnalysisSummary: String,
    val parentActionGuidance: String,
    val detectionEngine: String,
    val severityLevel: String = "LOW",
    val recommendedAction: String = "LOG_ONLY",
    val layerTrace: List<LayerTraceStep> = emptyList(),
    val privacyTransmission: String = "Edge-Confined (Zero Cloud Transmission)",
    val linguisticTriggers: List<String> = emptyList(),
    val cognitiveRiskRadar: Map<String, Float> = emptyMap(),
    val restorativeDialogueScript: String = "",
    val ablationMode: ThreatAblationMode = ThreatAblationMode.CASCADED_TRI_LAYER,
    val energyImpactMicroJoules: Long = 120L
)

class ThreatEvaluationEngine(private val customContext: Context? = null) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        @Volatile
        private var classifier1Instance: ThreatClassifier? = null

        @Volatile
        private var classifier2Instance: CyberbullyingEnsembleClassifier? = null

        /**
         * Singleton accessor for Model 1 (ThreatClassifier - TF-IDF + Logistic Regression).
         */
        fun getClassifier1(context: Context? = null): ThreatClassifier? {
            return classifier1Instance ?: synchronized(this) {
                classifier1Instance ?: run {
                    val ctx = context ?: try {
                        FocusSenseApplication.instance
                    } catch (_: Exception) {
                        null
                    }
                    ctx?.let {
                        try {
                            ThreatClassifier(it.applicationContext).also { instance ->
                                classifier1Instance = instance
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }
            }
        }

        /**
         * Singleton accessor for Model 2 (CyberbullyingEnsembleClassifier - TFLite Deep Learning Ensemble).
         */
        fun getClassifier2(context: Context? = null): CyberbullyingEnsembleClassifier? {
            return classifier2Instance ?: synchronized(this) {
                classifier2Instance ?: run {
                    val ctx = context ?: try {
                        FocusSenseApplication.instance
                    } catch (_: Exception) {
                        null
                    }
                    ctx?.let {
                        try {
                            CyberbullyingEnsembleClassifier(it.applicationContext).also { instance ->
                                classifier2Instance = instance
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }
            }
        }
    }

    /**
     * Primary layered evaluation method implementing the 3-Layer Hierarchical Cascade:
     * Layer 1: Rule-Based Deterministic Fast Filter (<1ms)
     *      ↓ (if ambiguous / suspicious / high-entropy)
     * Layer 2: Dual Edge ML Engine (Model 1 TF-IDF LogReg + Model 2 TFLite Cyberbullying Ensemble)
     *      ↓ (if confidence < 0.75 / low-confidence fallback)
     * Layer 3: Server-Side Cloud Gemini 3.5 Flash Neural Reasoning (~380ms)
     */
    suspend fun evaluateContent(
        appName: String,
        contentTitle: String,
        extractedText: String,
        serverUrl: String = "https://parental-controll.onrender.com",
        forceOfflineOnly: Boolean = false,
        ablationMode: ThreatAblationMode = ThreatAblationMode.CASCADED_TRI_LAYER
    ): ThreatAnalysisResult = withContext(Dispatchers.IO) {
        val trace = mutableListOf<LayerTraceStep>()
        val textToEvaluate = "$contentTitle $extractedText".trim()

        // -----------------------------------------------------------------
        // CASE: Ablation Mode 3 - Direct Cloud LLM Only
        // -----------------------------------------------------------------
        if (ablationMode == ThreatAblationMode.CLOUD_LLM_ONLY) {
            val tStart = System.currentTimeMillis()
            val apiKey = BuildConfig.GEMINI_API_KEY.ifBlank { "YOUR_GEMINI_API_KEY" }
            val cloudRes = evaluateWithGemini(apiKey, appName, contentTitle, extractedText)
            val elapsed = System.currentTimeMillis() - tStart
            if (cloudRes != null) {
                trace.add(
                    LayerTraceStep(
                        layerNumber = 3,
                        layerName = "Layer 3: Direct Cloud LLM",
                        executionTimeMs = elapsed,
                        decision = if (cloudRes.isFlagged) "FLAGGED" else "SAFE",
                        confidence = cloudRes.confidenceScore,
                        note = "Direct unbuffered cloud query (Ablation Mode 3). Full cloud transmission."
                    )
                )
                return@withContext cloudRes.copy(
                    layerTrace = trace,
                    ablationMode = ablationMode,
                    privacyTransmission = "Cloud Transmitted (100% LLM Ingress)",
                    energyImpactMicroJoules = 1450L
                )
            }
        }

        // -----------------------------------------------------------------
        // STAGE 1: Layer 1 - Rule-Based Deterministic Fast Filter (<1ms)
        // -----------------------------------------------------------------
        val tL1Start = System.nanoTime()
        val l1Result = evaluateLayer1RuleFilter(textToEvaluate)
        val l1ElapsedMs = ((System.nanoTime() - tL1Start) / 1_000_000L).coerceAtLeast(1L)

        if (ablationMode == ThreatAblationMode.RULE_ONLY) {
            trace.add(
                LayerTraceStep(
                    layerNumber = 1,
                    layerName = "Layer 1: Deterministic Rule Engine",
                    executionTimeMs = l1ElapsedMs,
                    decision = if (l1Result.isDefinitiveEmergency) "EMERGENCY_FLAGGED" else if (l1Result.isDefinitiveSafe) "PASSED_SAFE" else "HEURISTIC_FLAG",
                    confidence = l1Result.confidence,
                    note = "Ablation Mode 1: Static keyword & regex matching only."
                )
            )
            return@withContext buildLayer1TerminalResult(l1Result, textToEvaluate, trace, ablationMode, l1ElapsedMs)
        }

        if (l1Result.isDefinitiveEmergency) {
            trace.add(
                LayerTraceStep(
                    layerNumber = 1,
                    layerName = "Layer 1: Deterministic Fast Filter",
                    executionTimeMs = l1ElapsedMs,
                    decision = "EMERGENCY_FLAGGED",
                    confidence = 0.98f,
                    note = "Critical crisis triggers detected. Triage resolved in <1ms without cloud/ML latency."
                )
            )
            return@withContext buildLayer1TerminalResult(l1Result, textToEvaluate, trace, ablationMode, l1ElapsedMs)
        }

        if (l1Result.isDefinitiveSafe) {
            trace.add(
                LayerTraceStep(
                    layerNumber = 1,
                    layerName = "Layer 1: Deterministic Fast Filter",
                    executionTimeMs = l1ElapsedMs,
                    decision = "PASSED_SAFE",
                    confidence = 0.02f,
                    note = "Whitelisted educational semantic context verified. Zero cloud transmission."
                )
            )
            return@withContext buildLayer1TerminalResult(l1Result, textToEvaluate, trace, ablationMode, l1ElapsedMs)
        }

        trace.add(
            LayerTraceStep(
                layerNumber = 1,
                layerName = "Layer 1: Deterministic Fast Filter",
                executionTimeMs = l1ElapsedMs,
                decision = "ESCALATED_TO_L2",
                confidence = l1Result.confidence,
                note = "Ambiguous syntax detected. Escalate to Dual On-Device ML Classifiers."
            )
        )

        // -----------------------------------------------------------------
        // STAGE 2: Layer 2 - Dual On-Device ML Engines (Model 1 + Model 2)
        // -----------------------------------------------------------------
        val tL2Start = System.nanoTime()
        val l2Result = evaluateLayer2MobileBertML(appName, contentTitle, extractedText)
        val l2ElapsedMs = ((System.nanoTime() - tL2Start) / 1_000_000L).coerceAtLeast(1L)

        val predictionEscalates = l2Result.confidenceScore < ThreatClassifier.THRESHOLD

        if (ablationMode == ThreatAblationMode.ON_DEVICE_ML_ONLY || forceOfflineOnly || !predictionEscalates) {
            trace.add(
                LayerTraceStep(
                    layerNumber = 2,
                    layerName = "Layer 2: Dual Edge ML Engines",
                    executionTimeMs = l2ElapsedMs,
                    decision = if (l2Result.isFlagged) "RESOLVED_ON_EDGE" else "PASSED_ON_EDGE",
                    confidence = l2Result.confidenceScore,
                    note = if (!predictionEscalates) {
                        "High edge ML confidence (${(l2Result.confidenceScore * 100).toInt()}% >= ${(ThreatClassifier.THRESHOLD * 100).toInt()}%). Resolved on edge."
                    } else if (forceOfflineOnly) {
                        "Offline forced mode: resolved on edge device."
                    } else {
                        "Ablation Mode 2: 100% on-device dual classifiers."
                    }
                )
            )
            return@withContext l2Result.copy(
                layerTrace = trace,
                ablationMode = ablationMode,
                privacyTransmission = "Edge-Confined (Zero Cloud Transmission)",
                energyImpactMicroJoules = 65L
            )
        }

        // Low confidence (< 0.75 threshold) -> Escalate to Layer 3 Gemini Cloud AI
        trace.add(
            LayerTraceStep(
                layerNumber = 2,
                layerName = "Layer 2: Dual Edge ML Engines",
                executionTimeMs = l2ElapsedMs,
                decision = "ESCALATED_TO_L3",
                confidence = l2Result.confidenceScore,
                note = "Low edge classifier confidence (${(l2Result.confidenceScore * 100).toInt()}% < ${(ThreatClassifier.THRESHOLD * 100).toInt()}%). Escalating to Cloud Gemini LLM."
            )
        )

        // -----------------------------------------------------------------
        // STAGE 3: Layer 3 - Server-Side / Cloud Gemini 3.5 Flash Reasoning
        // -----------------------------------------------------------------
        val tL3Start = System.currentTimeMillis()
        val apiKey = BuildConfig.GEMINI_API_KEY.ifBlank { "YOUR_GEMINI_API_KEY" }

        var finalL3Result: ThreatAnalysisResult? = null
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY" && apiKey != "your_api_key_here") {
            try {
                finalL3Result = evaluateWithGemini(apiKey, appName, contentTitle, extractedText)
            } catch (_: Exception) {}
        }

        if (finalL3Result == null && serverUrl.isNotBlank()) {
            try {
                finalL3Result = evaluateWithRenderServer(serverUrl, appName, contentTitle, extractedText)
            } catch (_: Exception) {}
        }

        val l3ElapsedMs = System.currentTimeMillis() - tL3Start

        if (finalL3Result != null) {
            trace.add(
                LayerTraceStep(
                    layerNumber = 3,
                    layerName = "Layer 3: Cloud Gemini 3.5 Flash AI",
                    executionTimeMs = l3ElapsedMs,
                    decision = "CLOUD_DELIBERATED",
                    confidence = finalL3Result.confidenceScore,
                    note = "Cloud multimodal deliberation completed for low-confidence edge result."
                )
            )
            return@withContext finalL3Result.copy(
                detectionEngine = "Cascaded Tri-Layer (L1 Rules -> L2 Dual ML -> L3 Cloud Gemini LLM)",
                layerTrace = trace,
                ablationMode = ablationMode,
                privacyTransmission = "Cloud Deliberated (Escalated due to L2 Ambiguity)",
                cognitiveRiskRadar = l2Result.cognitiveRiskRadar,
                linguisticTriggers = l1Result.matchedKeywords,
                energyImpactMicroJoules = 420L
            )
        }

        // If cloud is unreachable, fall back to Layer 2 Dual ML result
        trace.add(
            LayerTraceStep(
                layerNumber = 3,
                layerName = "Layer 3: Cloud Server (Fallback)",
                executionTimeMs = l3ElapsedMs,
                decision = "EDGE_FALLBACK",
                confidence = l2Result.confidenceScore,
                note = "Cloud unreachable / offline. Gracefully resolved using Layer 2 Dual Edge ML."
            )
        )

        return@withContext l2Result.copy(
            detectionEngine = "Layer 2: On-Device Dual ML (Offline Edge Fallback)",
            layerTrace = trace,
            ablationMode = ablationMode,
            privacyTransmission = "Edge-Confined (Zero Cloud Transmission - Network Fallback)",
            energyImpactMicroJoules = 65L
        )
    }

    // =========================================================================
    // LAYER 1: Rule-Based Deterministic Fast Filter Implementation (<1ms)
    // =========================================================================
    private data class Layer1Evaluation(
        val isDefinitiveEmergency: Boolean,
        val isDefinitiveSafe: Boolean,
        val matchedKeywords: List<String>,
        val primaryCategory: String,
        val confidence: Float
    )

    private fun evaluateLayer1RuleFilter(text: String): Layer1Evaluation {
        val lower = text.lowercase()

        val emergencyCrisisTriggers = listOf(
            "kill myself", "end my life", "i want to die", "commit suicide",
            "do suicide", "suicide", "hate myself", "want to kill",
            "cut my wrist", "harm myself", "school shooting", "bomb the school", "mass shooting"
        )
        val matchedEmergency = emergencyCrisisTriggers.filter { lower.contains(it) }
        if (matchedEmergency.isNotEmpty()) {
            return Layer1Evaluation(
                isDefinitiveEmergency = true,
                isDefinitiveSafe = false,
                matchedKeywords = matchedEmergency,
                primaryCategory = if (matchedEmergency.any { it.contains("school") || it.contains("bomb") }) "Violence & Weapons" else "Self-Harm Risk",
                confidence = 0.98f
            )
        }

        val explicitAdultTriggers = listOf(
            "porn", "nude", "naked", "xxx", "sexy", "nsfw", "adult content",
            "sex video", "sex", "strip", "erotic", "onlyfans", "leaked tapes", "pornography"
        )
        val matchedExplicit = explicitAdultTriggers.filter { lower.contains(it) }
        if (matchedExplicit.isNotEmpty()) {
            return Layer1Evaluation(
                isDefinitiveEmergency = true,
                isDefinitiveSafe = false,
                matchedKeywords = matchedExplicit,
                primaryCategory = "Explicit & Adult Content",
                confidence = 0.98f
            )
        }

        val safeAcademicTriggers = listOf(
            "khan academy", "mitochondria", "pythagorean theorem", "calculus homework",
            "biology presentation", "wikipedia org", "duolingo lesson", "periodic table"
        )
        val matchedSafe = safeAcademicTriggers.filter { lower.contains(it) }

        val generalThreatTriggers = listOf(
            "meet me alone", "don't tell your mom", "don't tell your parents", "secret between us",
            "send photos", "cheat on test", "leak exam", "porn", "nude", "vape underage",
            "beat him up", "punch in the face", "hate you ugly", "kill yourself"
        )
        val matchedGeneral = generalThreatTriggers.filter { lower.contains(it) }

        if (matchedSafe.isNotEmpty() && matchedGeneral.isEmpty()) {
            return Layer1Evaluation(
                isDefinitiveEmergency = false,
                isDefinitiveSafe = true,
                matchedKeywords = matchedSafe,
                primaryCategory = "Safe",
                confidence = 0.02f
            )
        }

        val allHits = matchedGeneral + matchedSafe
        return Layer1Evaluation(
            isDefinitiveEmergency = false,
            isDefinitiveSafe = false,
            matchedKeywords = allHits,
            primaryCategory = if (matchedGeneral.isNotEmpty()) "Suspicious Pattern" else "Ambiguous",
            confidence = if (matchedGeneral.isNotEmpty()) 0.65f else 0.30f
        )
    }

    private fun buildLayer1TerminalResult(
        l1: Layer1Evaluation,
        text: String,
        trace: List<LayerTraceStep>,
        mode: ThreatAblationMode,
        latencyMs: Long
    ): ThreatAnalysisResult {
        val isFlag = l1.isDefinitiveEmergency || (!l1.isDefinitiveSafe && l1.matchedKeywords.isNotEmpty())
        val category = if (l1.isDefinitiveSafe) "Safe" else l1.primaryCategory
        val severity = if (l1.isDefinitiveEmergency) "CRITICAL" else if (isFlag) "HIGH" else "LOW"
        val action = if (l1.isDefinitiveEmergency) "INSTANT_BLOCK" else if (isFlag) "PARENT_ALERT" else "LOG_ONLY"

        return ThreatAnalysisResult(
            isFlagged = isFlag,
            threatCategory = category,
            confidenceScore = l1.confidence,
            aiAnalysisSummary = if (isFlag) "Deterministic Rule Match: Triggered by keywords ${l1.matchedKeywords.take(3)}." else "Deterministic Whitelist Match: Verified educational context.",
            parentActionGuidance = if (isFlag) "Immediate review required: Keywords matched deterministic safety triggers." else "No action required.",
            detectionEngine = "Layer 1: Deterministic Rule Engine",
            severityLevel = severity,
            recommendedAction = action,
            layerTrace = trace,
            privacyTransmission = "Edge-Confined (Zero Cloud Transmission)",
            linguisticTriggers = l1.matchedKeywords,
            cognitiveRiskRadar = mapOf(
                "Physical Harm" to if (l1.isDefinitiveEmergency) 0.95f else 0.1f,
                "Peer Toxicity" to 0.1f,
                "Predatory Grooming" to 0.1f,
                "Crisis & Distress" to if (l1.isDefinitiveEmergency) 0.95f else 0.05f,
                "Academic Integrity" to 0.05f
            ),
            restorativeDialogueScript = generateRestorativeScript(category, isFlag),
            ablationMode = mode,
            energyImpactMicroJoules = 5L
        )
    }

    // =========================================================================
    // LAYER 2: On-Device Dual ML Classifiers (Model 1 LogReg + Model 2 TFLite)
    // =========================================================================
    fun evaluateLayer2MobileBertML(
        appName: String,
        contentTitle: String,
        extractedText: String
    ): ThreatAnalysisResult {
        val textToEvaluate = "$contentTitle $extractedText".trim()
        val m1 = getClassifier1(customContext)
        val m2 = getClassifier2(customContext)

        if (m1 == null && m2 == null) {
            return ThreatAnalysisResult(
                isFlagged = false,
                threatCategory = "Safe",
                confidenceScore = 0.50f,
                aiAnalysisSummary = "Dual classifiers unavailable; defaulted to safe evaluation.",
                parentActionGuidance = "No action required.",
                detectionEngine = "Layer 2: On-Device Dual Classifiers (Fallback)",
                severityLevel = "LOW",
                recommendedAction = "LOG_ONLY",
                privacyTransmission = "Edge-Confined"
            )
        }

        val pred1 = if (m1 != null) {
            runBlocking(Dispatchers.Default) { m1.classify(textToEvaluate) }
        } else {
            Prediction("UNKNOWN", 0f, true)
        }

        val pred2 = if (m2 != null) {
            runBlocking(Dispatchers.Default) { m2.classify(textToEvaluate) }
        } else {
            Model2Prediction("not_cyberbullying", 0f, false)
        }

        return combineModelPredictions(pred1, pred2, appName, contentTitle, extractedText)
    }

    private fun combineModelPredictions(
        pred1: Prediction,
        pred2: Model2Prediction,
        appName: String,
        contentTitle: String,
        extractedText: String
    ): ThreatAnalysisResult {
        val isM1Harmful = ThreatClassifier.HARMFUL.contains(pred1.label)
        val isM1Flagged = isM1Harmful && !pred1.escalate
        val isM2Flagged = pred2.isCyberbullying

        val isFlagged = isM1Flagged || isM2Flagged

        // Map Model 1 category
        val m1Category = when (pred1.label) {
            "CYBERBULLYING" -> "Cyberbullying & Harassment"
            "GROOMING" -> "Predatory Grooming & Stranger Risk"
            "ADULT_CONTENT" -> "Explicit & Adult Content"
            "SELF_HARM_VIOLENCE" -> "Self-Harm & Crisis"
            "SCAM_PHISHING" -> "Scam & Phishing"
            "SAFE_EDUCATIONAL" -> "Safe"
            "SAFE_GENERAL", "SOCIAL_NORMAL" -> "Safe"
            else -> if (isM1Flagged) "Suspicious Content" else "Safe"
        }

        // Map Model 2 cyberbullying subcategory tag
        val m2SubtypeTag = when (pred2.label.lowercase()) {
            "ethnicity" -> "Ethnicity / Racism"
            "gender" -> "Gender / Sexism"
            "religion" -> "Religious Harassment"
            "age" -> "Ageism / Age Harassment"
            "not_cyberbullying" -> ""
            else -> pred2.label.replaceFirstChar { it.uppercase() }
        }

        // Format combined Category Name showing both model labels when appropriate
        val combinedCategory = when {
            isM1Flagged && isM2Flagged && m2SubtypeTag.isNotBlank() ->
                "$m1Category [$m2SubtypeTag]"
            isM1Flagged ->
                if (m2SubtypeTag.isNotBlank()) "$m1Category [$m2SubtypeTag]" else m1Category
            isM2Flagged && m2SubtypeTag.isNotBlank() ->
                "Cyberbullying & Harassment [$m2SubtypeTag]"
            else -> "Safe"
        }

        val maxConfidence = maxOf(pred1.confidence, pred2.confidence)

        val summary = when {
            isM1Flagged && isM2Flagged ->
                "FocusSense AI Sentinel detected $m1Category and confirmed $m2SubtypeTag cyberbullying."
            isM1Flagged ->
                "FocusSense AI Sentinel detected $m1Category."
            isM2Flagged ->
                "FocusSense AI Sentinel flagged $m2SubtypeTag cyberbullying."
            else ->
                "Standard safe digital interaction evaluated by FocusSense AI Engine."
        }

        val guidance = when {
            pred1.label == "GROOMING" ->
                "Immediate check: Verify who the child is communicating with; enforce no private meetups."
            pred1.label == "SELF_HARM_VIOLENCE" ->
                "High priority: Provide warm, non-judgmental support and check on child's emotional well-being."
            pred1.label == "CYBERBULLYING" || isM2Flagged ->
                "Review conversation thread with child regarding $m2SubtypeTag cyberbullying and consider muting/reporting toxic accounts."
            pred1.label == "ADULT_CONTENT" ->
                "Reiterate digital boundaries and review browser and app permissions."
            pred1.label == "SCAM_PHISHING" ->
                "Warn child not to share personal credentials, OTPs, or payment information."
            else ->
                "No action necessary. Activity conforms to healthy digital guidelines."
        }

        val severity = when {
            pred1.label == "GROOMING" || (pred1.label == "SELF_HARM_VIOLENCE" && maxConfidence > 0.85f) -> "CRITICAL"
            isFlagged && maxConfidence > 0.85f -> "HIGH"
            isFlagged -> "MEDIUM"
            else -> "LOW"
        }

        val action = when {
            severity == "CRITICAL" -> "INSTANT_BLOCK"
            severity == "HIGH" -> "PARENT_ALERT"
            isFlagged -> "WARN_CHILD"
            else -> "LOG_ONLY"
        }

        val riskRadar = mapOf(
            "Physical Harm" to if (pred1.label == "SELF_HARM_VIOLENCE") maxConfidence else 0.1f,
            "Peer Toxicity" to if (pred1.label == "CYBERBULLYING" || isM2Flagged) maxConfidence else 0.1f,
            "Predatory Grooming" to if (pred1.label == "GROOMING") maxConfidence else 0.1f,
            "Crisis & Distress" to if (pred1.label == "SELF_HARM_VIOLENCE") maxConfidence else 0.05f,
            "Academic Integrity" to if (pred1.label == "SAFE_EDUCATIONAL") 0.05f else 0.1f
        )

        val triggers = mutableListOf<String>()
        if (isM1Flagged) triggers.add("M1: ${pred1.label}")
        if (isM2Flagged) triggers.add("M2 Subtype: ${pred2.label}")

        return ThreatAnalysisResult(
            isFlagged = isFlagged,
            threatCategory = combinedCategory,
            confidenceScore = maxConfidence,
            aiAnalysisSummary = summary,
            parentActionGuidance = guidance,
            detectionEngine = "FocusSense AI Threat Sentinel",
            severityLevel = severity,
            recommendedAction = action,
            privacyTransmission = "Edge-Confined (Zero Cloud Transmission)",
            linguisticTriggers = if (triggers.isNotEmpty()) triggers else listOf("Safe"),
            cognitiveRiskRadar = riskRadar,
            restorativeDialogueScript = generateRestorativeScript(combinedCategory, isFlagged),
            energyImpactMicroJoules = 65L
        )
    }

    // =========================================================================
    // LAYER 3: Server-Side Cloud Gemini 3.5 Flash Neural Reasoning (~380ms)
    // =========================================================================
    private fun evaluateWithGemini(
        apiKey: String,
        appName: String,
        contentTitle: String,
        extractedText: String
    ): ThreatAnalysisResult? {
        val prompt = """
            You are FocusSense AI, an expert parental protection sentinel and child psychology research platform.
            Analyze the following text extracted in real-time from a child's mobile screen:
            App: $appName
            Title / Context: $contentTitle
            Extracted Text: "$extractedText"

            Evaluate across these 7 critical safety categories:
            1. "Predatory Grooming & Stranger Risk" (secrecy, isolation, requests for private meetups or photos)
            2. "Cyberbullying & Harassment" (hostile peer attacks, slurs, insulting, demeaning)
            3. "Self-Harm & Mental Distress" (suicide, cutting, wanting to die, emotional crisis)
            4. "Violence, Weapons & Threats" (guns, knives, attacks, murder, assault)
            5. "Explicit & Adult Content" (pornography, adult sites, explicit chats)
            6. "Substance Abuse & Drugs" (narcotics, vaping, pills, illicit drugs)
            7. "Academic Dishonesty" (exam cheating, paper bots)
            8. "Safe" (normal friendly chats, studies, gaming)

            Respond strictly in valid JSON with these fields:
            {
              "isFlagged": boolean,
              "threatCategory": "Predatory Grooming & Stranger Risk" | "Cyberbullying & Harassment" | "Self-Harm & Mental Distress" | "Violence, Weapons & Threats" | "Explicit & Adult Content" | "Substance Abuse & Drugs" | "Academic Dishonesty" | "Safe",
              "confidenceScore": float between 0.0 and 1.0,
              "severityLevel": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
              "recommendedAction": "LOG_ONLY" | "WARN_CHILD" | "PARENT_ALERT" | "INSTANT_BLOCK",
              "aiAnalysisSummary": "1-2 sentence concise explanation of why this was flagged or marked safe",
              "parentActionGuidance": "1 sentence practical advice for the parent",
              "restorativeDialogueScript": "1-2 sentence empathetic, non-accusatory conversation starter that a parent can say directly to their child",
              "linguisticTriggers": ["trigger_word1", "trigger_word2"]
            }
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val genConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }
            put("generationConfig", genConfig)
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val responseBody = response.body?.string() ?: return null
        val rootJson = JSONObject(responseBody)
        val textCandidate = rootJson.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text") ?: return null

        val parsed = JSONObject(textCandidate)
        val isFlagged = parsed.optBoolean("isFlagged", false)
        val category = parsed.optString("threatCategory", if (isFlagged) "Suspicious Activity" else "Safe")
        val confidence = parsed.optDouble("confidenceScore", 0.9).toFloat()
        val severity = parsed.optString("severityLevel", if (isFlagged) "HIGH" else "LOW")
        val action = parsed.optString("recommendedAction", if (isFlagged) "PARENT_ALERT" else "LOG_ONLY")
        val summary = parsed.optString("aiAnalysisSummary", "Deeply evaluated by Gemini 3.5 Flash Reasoning Engine.")
        val guidance = parsed.optString("parentActionGuidance", "Review activity log gently with child.")
        val restorativeScript = parsed.optString("restorativeDialogueScript", generateRestorativeScript(category, isFlagged))

        val triggersArray = parsed.optJSONArray("linguisticTriggers")
        val triggersList = mutableListOf<String>()
        if (triggersArray != null) {
            for (i in 0 until triggersArray.length()) {
                triggersList.add(triggersArray.getString(i))
            }
        }

        return ThreatAnalysisResult(
            isFlagged = isFlagged,
            threatCategory = category,
            confidenceScore = confidence,
            aiAnalysisSummary = summary,
            parentActionGuidance = guidance,
            detectionEngine = "Layer 3: Cloud Gemini 3.5 Flash AI",
            severityLevel = severity,
            recommendedAction = action,
            privacyTransmission = "Cloud Deliberated (Escalated to Gemini 3.5 Flash)",
            linguisticTriggers = triggersList,
            cognitiveRiskRadar = mapOf(
                "Physical Harm" to if (category.contains("Violence")) 0.9f else 0.1f,
                "Peer Toxicity" to if (category.contains("Cyberbullying")) 0.9f else 0.1f,
                "Predatory Grooming" to if (category.contains("Grooming") || category.contains("Stranger")) 0.95f else 0.1f,
                "Crisis & Distress" to if (category.contains("Self-Harm") || category.contains("Distress")) 0.95f else 0.05f,
                "Academic Integrity" to if (category.contains("Academic")) 0.85f else 0.05f
            ),
            restorativeDialogueScript = restorativeScript,
            energyImpactMicroJoules = 380L
        )
    }

    private fun evaluateWithRenderServer(
        serverUrl: String,
        appName: String,
        contentTitle: String,
        extractedText: String
    ): ThreatAnalysisResult? {
        return try {
            val cleanUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            val endpoint = "${cleanUrl}api/ai/evaluate"
            val jsonPayload = JSONObject().apply {
                put("child_id", "active_child")
                put("package_name", "com.scraped.app")
                put("app_name", appName)
                put("content_title", contentTitle)
                put("extracted_text", extractedText)
            }
            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return null
                val obj = JSONObject(responseBody)
                val threatDetected = obj.optBoolean("threat_detected", false)
                val threatCategory = obj.optString("threat_category", "Safe")
                val confidence = obj.optDouble("confidence_score", 0.0).toFloat()
                val summary = obj.optString("ai_analysis_summary", "")
                val modelUsed = obj.optString("model_used", "Layer 3: Cloud Server (FastAPI)")
                val severity = obj.optString("severity_level", if (threatDetected) "HIGH" else "LOW")
                val recommendedAction = obj.optString("recommended_action", if (threatDetected) "PARENT_ALERT" else "LOG_ONLY")
                val parentGuidance = obj.optString("parent_action_guidance", if (threatDetected) {
                    "Live alert: FocusSense identified suspicious patterns in $appName."
                } else {
                    "Safe browsing."
                })
                ThreatAnalysisResult(
                    isFlagged = threatDetected,
                    threatCategory = threatCategory,
                    confidenceScore = confidence,
                    aiAnalysisSummary = summary,
                    parentActionGuidance = parentGuidance,
                    detectionEngine = modelUsed,
                    severityLevel = severity,
                    recommendedAction = recommendedAction,
                    privacyTransmission = "Cloud Deliberated (Render Backend)",
                    restorativeDialogueScript = generateRestorativeScript(threatCategory, threatDetected),
                    energyImpactMicroJoules = 320L
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // ACADEMIC INNOVATION: Restorative Child Psychology Dialogue Generator
    // =========================================================================
    fun generateRestorativeScript(category: String, isThreat: Boolean): String {
        if (!isThreat) {
            return "Positive Reinforcement: 'You're doing great with your study time today! Let me know if you need any snack breaks or help with your assignments.'"
        }
        return when {
            category.contains("Stranger", ignoreCase = true) || category.contains("Grooming", ignoreCase = true) ->
                "Empathetic Opener: 'Hey, I know making new online friends can feel exciting, but no safe adult or friend will ever ask you to keep secrets from family. Can we talk about who you were chatting with?'"

            category.contains("Bullying", ignoreCase = true) || category.contains("Harassment", ignoreCase = true) ->
                "Supportive Opener: 'I saw someone sent you some really hurtful words. You didn't do anything wrong, and nobody has the right to treat you that way. How are you feeling right now?'"

            category.contains("Self-Harm", ignoreCase = true) || category.contains("Crisis", ignoreCase = true) ->
                "Crisis Opener: 'I love you so much and you matter deeply to our family. It looks like things have felt really heavy lately. I am here to listen without judgment or anger.'"

            category.contains("Violence", ignoreCase = true) || category.contains("Weapons", ignoreCase = true) ->
                "De-escalation Opener: 'I noticed some heated words and references to fights. Are you feeling angry or unsafe with someone right now? Let's figure out a peaceful way to handle it together.'"

            category.contains("Academic", ignoreCase = true) || category.contains("Cheating", ignoreCase = true) ->
                "Guidance Opener: 'Tests and homework can get really stressful, especially when deadlines pile up. Are you feeling stuck on a particular subject? Let's work on it together instead of risking your grade.'"

            else ->
                "Open Dialogue: 'Hey, I noticed something unusual came up on your screen today. Let's look over it together so I can understand what happened from your perspective.'"
        }
    }
}
