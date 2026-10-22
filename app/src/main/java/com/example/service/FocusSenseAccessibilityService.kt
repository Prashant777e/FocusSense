package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.FocusSenseApplication
import com.example.MainActivity
import com.example.R
import com.example.data.repository.SentinelEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScrapedContentSample(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val candidateCategory: String = "Safe",
    val detectedKeywords: List<String> = emptyList(),
    val rawNodesCount: Int = 0,
    val noiseFilteredCount: Int = 0,
    val extractedUrls: List<String> = emptyList(),
    val isEscalated: Boolean = false
)

data class ScraperMetrics(
    val totalEventsCount: Long = 0,
    val noisyWindowsFiltered: Long = 0,
    val cleanScrapesProcessed: Long = 0,
    val escalatedThreatsFound: Long = 0
)

class FocusSenseAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastExtractedText = ""
    private var lastAnalyzedTime = 0L
    private var lastPkgName = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true
        Log.i(TAG, "FocusSense Accessibility Sentinel Connected with Tier 1/2 Scraper Engine.")

        val app = application as? FocusSenseApplication ?: return
        scope.launch {
            app.repository.eventStream.collect { event ->
                if (event is SentinelEvent.ContentFlagged) {
                    val analysis = event.threatResult
                    if (analysis.isFlagged) {
                        withContext(Dispatchers.Main) {
                            launchBlockScreen(
                                packageName = event.log.packageName,
                                appName = event.log.appName,
                                ruleName = "Real-Time Safety Sentinel",
                                category = analysis.threatCategory,
                                endTime = "Content Restricted"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return

        if (pkgName != lastPkgName) {
            lastPkgName = pkgName
            lastExtractedText = ""
        }

        _metrics.value = _metrics.value.copy(
            totalEventsCount = _metrics.value.totalEventsCount + 1
        )

        // Tier 1: Ignore keyboards, system bars, launchers, and FocusSense itself
        if (ScraperFilterEngine.isIgnoredPackage(pkgName, packageName)) {
            _metrics.value = _metrics.value.copy(
                noisyWindowsFiltered = _metrics.value.noisyWindowsFiltered + 1
            )
            return
        }

        val app = application as? FocusSenseApplication ?: return
        val currentChildId = app.repository.selectedChildId.value

        // 1. Instant Synchronous Check (0ms latency from in-memory cache)
        val appLabel = getAppLabel(pkgName)
        if (app.repository.isPackageBlockedSync(pkgName)) {
            launchBlockScreen(
                packageName = pkgName,
                appName = appLabel,
                ruleName = "Parent Instant Lock",
                category = "Instant Restriction",
                endTime = "Until Unlocked"
            )
            _lastBlockedApp.value = appLabel
            return
        }

        // 2. Curfew & Schedule Enforcement Check
        scope.launch {
            val restriction = app.repository.checkAppRestriction(currentChildId, pkgName)
            if (restriction.isBlocked) {
                _lastBlockedApp.value = restriction.restrictedAppName
                withContext(Dispatchers.Main) {
                    launchBlockScreen(
                        packageName = pkgName,
                        appName = appLabel,
                        ruleName = restriction.ruleName,
                        category = restriction.category,
                        endTime = restriction.endTime
                    )
                }
            }
        }

        // 2. Debounce rapid scrolling/typing events (1500ms debounce)
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTime < 1500) {
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val rawNodes = mutableListOf<NodeTextData>()
        traverseNodes(rootNode, rawNodes)

        // Tier 1 & Tier 2: Filter and classify window nodes
        val filterResult = ScraperFilterEngine.filterAndClassifyWindow(
            packageName = pkgName,
            ownPackage = packageName,
            className = event.className?.toString(),
            nodes = rawNodes
        )

        if (filterResult.isIgnored) {
            _metrics.value = _metrics.value.copy(
                noisyWindowsFiltered = _metrics.value.noisyWindowsFiltered + 1
            )
            return
        }

        val fullText = filterResult.cleanText

        // Avoid re-processing identical text continuously
        if (fullText == lastExtractedText) {
            return
        }
        lastExtractedText = fullText
        lastAnalyzedTime = now

        val truncatedDisplaySample = if (fullText.length > 250) fullText.take(250) + "..." else fullText

        val sample = ScrapedContentSample(
            packageName = pkgName,
            appName = appLabel,
            title = event.className?.toString() ?: "Active Window",
            text = truncatedDisplaySample,
            timestamp = now,
            candidateCategory = filterResult.candidateCategory,
            detectedKeywords = filterResult.detectedKeywords,
            rawNodesCount = filterResult.rawNodesCount,
            noiseFilteredCount = filterResult.noiseNodesFiltered,
            extractedUrls = filterResult.extractedUrls,
            isEscalated = filterResult.needsEscalation
        )

        _lastScrapedContent.value = sample

        _metrics.value = _metrics.value.copy(
            cleanScrapesProcessed = _metrics.value.cleanScrapesProcessed + 1,
            escalatedThreatsFound = _metrics.value.escalatedThreatsFound + if (filterResult.needsEscalation) 1 else 0
        )

        // Forward to Repository -> Local Room SQLite + Cloud AI evaluation
        scope.launch {
            app.repository.processExtractedContent(
                childId = currentChildId,
                packageName = pkgName,
                appName = appLabel,
                contentTitle = if (filterResult.extractedUrls.isNotEmpty()) {
                    "Browsing: ${filterResult.extractedUrls.first()}"
                } else {
                    "Content from $appLabel"
                },
                extractedText = fullText
            )
        }
    }

    private fun traverseNodes(node: AccessibilityNodeInfo?, output: MutableList<NodeTextData>) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName

        if (!text.isNullOrBlank() && text.length > 1) {
            output.add(
                NodeTextData(
                    text = text,
                    viewId = viewId,
                    isPassword = node.isPassword,
                    isEditable = node.isEditable
                )
            )
        } else if (!desc.isNullOrBlank() && desc.length > 1) {
            output.add(
                NodeTextData(
                    text = desc,
                    viewId = viewId,
                    isPassword = node.isPassword,
                    isEditable = node.isEditable
                )
            )
        }

        for (i in 0 until node.childCount) {
            traverseNodes(node.getChild(i), output)
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when {
                pkg.contains("youtube") -> "YouTube"
                pkg.contains("chrome") -> "Google Chrome"
                pkg.contains("firefox") -> "Firefox"
                pkg.contains("discord") -> "Discord"
                pkg.contains("instagram") -> "Instagram"
                pkg.contains("whatsapp") -> "WhatsApp"
                pkg.contains("snapchat") -> "Snapchat"
                pkg.contains("telegram") -> "Telegram"
                pkg.contains("tiktok") || pkg.contains("musically") -> "TikTok"
                pkg.contains("roblox") -> "Roblox"
                else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

    private var lastBlockedPkg: String = ""
    private var lastBlockLaunchTime: Long = 0L

    private fun launchBlockScreen(
        packageName: String,
        appName: String,
        ruleName: String,
        category: String,
        endTime: String
    ) {
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPkg && (now - lastBlockLaunchTime < 2000)) {
            return
        }
        lastBlockedPkg = packageName
        lastBlockLaunchTime = now

        try {
            val intent = Intent(applicationContext, com.example.ui.screens.blocking.AppBlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(com.example.ui.screens.blocking.AppBlockedActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra(com.example.ui.screens.blocking.AppBlockedActivity.EXTRA_APP_NAME, appName)
                putExtra(com.example.ui.screens.blocking.AppBlockedActivity.EXTRA_RULE_NAME, ruleName)
                putExtra(com.example.ui.screens.blocking.AppBlockedActivity.EXTRA_CATEGORY, category)
                putExtra(com.example.ui.screens.blocking.AppBlockedActivity.EXTRA_END_TIME, endTime)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch block screen: ${e.message}")
        }
    }

    override fun onInterrupt() {
        _isServiceRunning.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
    }

    companion object {
        private const val TAG = "FocusSenseSentinel"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _lastScrapedContent = MutableStateFlow<ScrapedContentSample?>(null)
        val lastScrapedContent = _lastScrapedContent.asStateFlow()

        private val _lastBlockedApp = MutableStateFlow<String?>(null)
        val lastBlockedApp = _lastBlockedApp.asStateFlow()

        private val _metrics = MutableStateFlow(ScraperMetrics())
        val metrics = _metrics.asStateFlow()
    }
}
