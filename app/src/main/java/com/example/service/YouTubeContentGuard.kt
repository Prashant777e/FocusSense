package com.example.service

import com.example.data.model.AllowedYouTubeChannelEntity
import java.util.Locale

enum class YouTubeBlockReason {
    NONE,
    SHORTS_BLOCKED,
    UNAPPROVED_CHANNEL
}

data class YouTubeGuardDecision(
    val shouldBlock: Boolean,
    val reason: YouTubeBlockReason = YouTubeBlockReason.NONE,
    val title: String = "",
    val detail: String = "",
    val detectedChannel: String? = null
)

enum class WebViewNavigationAction {
    ALLOW,
    BLOCK_SHORTS,
    BLOCK_OTHER_CHANNEL
}

object YouTubeContentGuard {

    private val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.google.android.youtube.tv",
        "com.vanced.android.youtube",
        "app.revanced.android.youtube",
        "app.rvx.android.youtube"
    )

    // View IDs that appear ONLY inside the active Shorts player/reel viewer (never on bottom nav tabs)
    private val SHORTS_ACTIVE_VIEW_IDS = listOf(
        "reel_recycler",
        "reel_player",
        "reel_watch",
        "reel_progress_bar",
        "reel_main_container",
        "reel_channel_bar",
        "reel_dyn_",
        "reel_vod_button",
        "reel_sound_metadata",
        "shorts_player",
        "shorts_video",
        "shorts_container"
    )

    private val SHORTS_EXPLICIT_LABELS = listOf(
        "remix this short",
        "shorts player",
        "pause short",
        "play short",
        "more shorts"
    )

    // View IDs indicating an open Watch player or Channel page
    private val WATCH_OR_CHANNEL_VIEW_IDS = listOf(
        "watch_player",
        "player_fragment",
        "player_view",
        "watch_while_time_bar",
        "time_bar_current_time",
        "video_metadata",
        "channel_name",
        "owner_name",
        "channel_title",
        "channel_handle",
        "channel_header",
        "c4_header",
        "subscribers_count",
        "subscribe_button",
        "subscribed_button"
    )

    // Markers below the active video header where "Up next" / comments / unrelated channels begin
    private val RELATED_SECTION_MARKERS = setOf(
        "up next",
        "comments",
        "all",
        "related",
        "from your search",
        "for you",
        "recommended"
    )

    private val HANDLE_REGEX = Regex("@([A-Za-z0-9_.-]{2,})")
    private val SUBSCRIBE_REGEX = Regex("(?:subscribed to|unsubscribe from|subscribe to)\\s+([^.,\\n]+)", RegexOption.IGNORE_CASE)
    private val GO_TO_CHANNEL_REGEX = Regex("go to (?:channel\\s+)?([^.,\\n]+)", RegexOption.IGNORE_CASE)

    fun isYouTubePackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return YOUTUBE_PACKAGES.contains(lower) || lower.contains("android.youtube")
    }

    /**
     * Produces normalized comparison tokens (channel name, @handle, handle without @, compact slug, UC channel ID)
     * for reliable matching across native YouTube, mobile web, and search results.
     */
    fun extractChannelTokens(channel: AllowedYouTubeChannelEntity): Set<String> {
        val tokens = mutableSetOf<String>()

        fun addToken(raw: String?) {
            val cleaned = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (cleaned.length >= 2) {
                tokens.add(cleaned)
                val noAt = cleaned.removePrefix("@")
                if (noAt.length >= 2) {
                    tokens.add(noAt)
                }
                val compact = noAt.replace(Regex("[^a-z0-9]"), "")
                if (compact.length >= 3) {
                    tokens.add(compact)
                }
            }
        }

        addToken(channel.channelName)
        addToken(channel.handle)
        if (channel.channelId.startsWith("UC", ignoreCase = true)) {
            addToken(channel.channelId)
        }

        // Parse handle or channel segment from channelUrl e.g. https://m.youtube.com/@takeUforward/playlists
        val url = channel.channelUrl.trim()
        if (url.isNotBlank()) {
            val path = url.substringAfter("youtube.com/", "")
                .substringBefore("?")
                .substringBefore("#")
                .trim('/')
            val firstSegment = path.substringBefore("/")
            if (firstSegment.startsWith("@") || firstSegment.startsWith("UC", ignoreCase = true)) {
                addToken(firstSegment)
            } else if (path.startsWith("c/") || path.startsWith("channel/") || path.startsWith("user/")) {
                addToken(path.substringAfter("/").substringBefore("/"))
            }
        }

        return tokens
    }

    fun matchesAllowedChannel(candidateText: String, allowedTokens: Set<String>): Boolean {
        if (allowedTokens.isEmpty() || candidateText.isBlank()) return false
        val lower = candidateText.lowercase(Locale.ROOT)
        val compact = lower.replace(Regex("[^a-z0-9]"), "")

        for (token in allowedTokens) {
            if (token.length < 3 && !token.startsWith("@")) continue
            if (lower.contains(token)) return true
            if (compact.isNotBlank() && token.length >= 4 && !token.contains(" ") && compact.contains(token)) {
                return true
            }
        }
        return false
    }

    /**
     * Evaluates an open native YouTube screen (`com.google.android.youtube`) using Accessibility nodes.
     */
    fun evaluateNativeYouTube(
        nodes: List<NodeTextData>,
        allowedChannels: List<AllowedYouTubeChannelEntity>,
        enforceWhitelist: Boolean
    ): YouTubeGuardDecision {
        if (nodes.isEmpty()) return YouTubeGuardDecision(shouldBlock = false)

        // 1. Always check for active YouTube Shorts player when whitelist/shield is active
        if (enforceWhitelist && isNativeShortsActive(nodes)) {
            return YouTubeGuardDecision(
                shouldBlock = true,
                reason = YouTubeBlockReason.SHORTS_BLOCKED,
                title = "YouTube Shorts Blocked",
                detail = "FocusSense paused YouTube Shorts so you stay focused on your study channels."
            )
        }

        if (!enforceWhitelist) {
            return YouTubeGuardDecision(shouldBlock = false)
        }

        val activeAllowed = allowedChannels.filter { it.isAllowed }
        val allowedTokens = activeAllowed.flatMap { extractChannelTokens(it) }.toSet()

        // 2. Skip blocking while the child is actively typing inside an editable search bar
        //    and no video watch player or channel page is open
        val hasEditableFocus = nodes.any {
            it.isEditable || it.viewId?.contains("search_edit_text", ignoreCase = true) == true
        }
        val hasExplicitWatchOrChannelView = nodes.any { node ->
            val vid = node.viewId?.lowercase(Locale.ROOT).orEmpty()
            WATCH_OR_CHANNEL_VIEW_IDS.any { vid.contains(it) }
        }

        // Gather primary header nodes before "Up next" / "Comments" / "Related" recommendations
        val primaryNodes = mutableListOf<NodeTextData>()
        for (node in nodes) {
            val tLower = node.text.trim().lowercase(Locale.ROOT)
            if (primaryNodes.size > 6 && RELATED_SECTION_MARKERS.contains(tLower)) {
                break
            }
            primaryNodes.add(node)
            if (primaryNodes.size >= 35) break
        }

        val primaryText = primaryNodes.joinToString(" | ") { it.text }
        val primaryLower = primaryText.lowercase(Locale.ROOT)

        val hasSubscribeAction = primaryNodes.any {
            val t = it.text.trim()
            t.equals("Subscribe", ignoreCase = true) ||
                t.equals("Subscribed", ignoreCase = true) ||
                SUBSCRIBE_REGEX.containsMatchIn(t)
        }
        val hasChannelStats = primaryNodes.any {
            val t = it.text.lowercase(Locale.ROOT)
            (t.contains("subscriber") || t.contains("videos")) && HANDLE_REGEX.containsMatchIn(primaryText)
        }

        val isWatchOrChannelContext = hasExplicitWatchOrChannelView || hasSubscribeAction || hasChannelStats

        if (hasEditableFocus && !isWatchOrChannelContext) {
            return YouTubeGuardDecision(shouldBlock = false)
        }

        // If user is simply browsing YouTube Home / Subscriptions tab without an open video or channel, don't false-trigger
        if (!isWatchOrChannelContext) {
            return YouTubeGuardDecision(shouldBlock = false)
        }

        // 3. Extract explicit creator identifiers from primary nodes
        val creatorCandidates = mutableListOf<String>()
        for (node in primaryNodes) {
            val vid = node.viewId?.lowercase(Locale.ROOT).orEmpty()
            val text = node.text.trim()
            if (vid.contains("channel_name") ||
                vid.contains("owner_name") ||
                vid.contains("channel_title") ||
                vid.contains("channel_handle")
            ) {
                creatorCandidates.add(text)
            }

            SUBSCRIBE_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()?.let {
                if (it.isNotBlank()) creatorCandidates.add(it)
            }
            GO_TO_CHANNEL_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()?.let {
                if (it.isNotBlank() && !it.equals("channel", ignoreCase = true)) {
                    creatorCandidates.add(it)
                }
            }
            HANDLE_REGEX.find(text)?.value?.let { handleMatch ->
                creatorCandidates.add(handleMatch)
            }
        }

        // Check whether ANY allowed channel matches either explicit creator candidates or primary header text
        if (allowedTokens.isNotEmpty()) {
            val creatorMatched = creatorCandidates.any { matchesAllowedChannel(it, allowedTokens) } ||
                matchesAllowedChannel(primaryLower, allowedTokens)
            if (creatorMatched) {
                return YouTubeGuardDecision(shouldBlock = false)
            }
        }

        // We are on an active Watch Video or Channel Page, and NO allowed channel matched!
        val detectedCreator = creatorCandidates.firstOrNull { it.length >= 2 }
        return YouTubeGuardDecision(
            shouldBlock = true,
            reason = YouTubeBlockReason.UNAPPROVED_CHANNEL,
            title = "Channel Not Allowed",
            detail = buildString {
                if (!detectedCreator.isNullOrBlank()) {
                    append("'$detectedCreator' is not in your allowed study channels.")
                } else {
                    append("This YouTube channel is not in your allowed study channels.")
                }
            },
            detectedChannel = detectedCreator
        )
    }

    /**
     * Evaluates YouTube opened inside a web browser (Chrome, Firefox, Edge, Brave, etc.).
     */
    fun evaluateBrowserYouTube(
        urls: List<String>,
        nodes: List<NodeTextData>,
        allowedChannels: List<AllowedYouTubeChannelEntity>,
        enforceWhitelist: Boolean
    ): YouTubeGuardDecision {
        if (!enforceWhitelist) return YouTubeGuardDecision(shouldBlock = false)

        val youtubeUrls = urls.filter {
            val l = it.lowercase(Locale.ROOT)
            l.contains("youtube.com") || l.contains("youtu.be")
        }

        if (youtubeUrls.isEmpty()) {
            return YouTubeGuardDecision(shouldBlock = false)
        }

        // 1. Check Shorts URL
        for (url in youtubeUrls) {
            val l = url.lowercase(Locale.ROOT)
            if (l.contains("/shorts/") || l.contains("/shorts?") || l.endsWith("/shorts")) {
                return YouTubeGuardDecision(
                    shouldBlock = true,
                    reason = YouTubeBlockReason.SHORTS_BLOCKED,
                    title = "YouTube Shorts Blocked",
                    detail = "YouTube Shorts are blocked in all browsers to protect your focus."
                )
            }
        }

        val activeAllowed = allowedChannels.filter { it.isAllowed }
        val allowedTokens = activeAllowed.flatMap { extractChannelTokens(it) }.toSet()

        // 2. Check explicit channel URLs, feeds, and unapproved search URLs (e.g. m.youtube.com/@mrbeast or m.youtube.com/results?search_query=gta)
        for (url in youtubeUrls) {
            val decision = checkWebUrlAgainstAllowedTokens(url, allowedTokens, strictStudyWebView = false)
            if (decision == WebViewNavigationAction.BLOCK_OTHER_CHANNEL) {
                val searchQuery = extractSearchQuery(url)
                val label = when {
                    searchQuery.isNotBlank() -> "Search: $searchQuery"
                    else -> HANDLE_REGEX.find(url)?.value
                        ?: url.substringAfter("youtube.com/").substringBefore("/").substringBefore("?")
                }
                return YouTubeGuardDecision(
                    shouldBlock = true,
                    reason = YouTubeBlockReason.UNAPPROVED_CHANNEL,
                    title = "Channel Not Allowed",
                    detail = "'$label' is not in your allowed study channels.",
                    detectedChannel = label
                )
            }
        }

        // 3. Inspect browser DOM/accessibility text on active YouTube pages (handles watch URLs where Chrome hides ?v= or Shorts DOM)
        if (nodes.isNotEmpty()) {
            return evaluateNativeYouTube(
                nodes = nodes,
                allowedChannels = activeAllowed,
                enforceWhitelist = true
            )
        }

        return YouTubeGuardDecision(shouldBlock = false)
    }

    /**
     * Strict guard for FocusSense's in-app Study Player WebView (`RealYouTubeChannelFullView`).
     * Allows only whitelisted channel pages, playlists, `/watch` video pages (additionally verified by DOM/JS),
     * and YouTube auth/static endpoints. Blocks all in-player `/results` search, Home, feeds, gaming, Shorts,
     * unapproved channels, and external websites.
     */
    fun checkStudyWebViewUrl(
        url: String,
        allowedChannels: List<AllowedYouTubeChannelEntity>
    ): WebViewNavigationAction {
        val allowedTokens = allowedChannels
            .filter { it.isAllowed }
            .flatMap { extractChannelTokens(it) }
            .toSet()
        return checkWebUrlAgainstAllowedTokens(url, allowedTokens, strictStudyWebView = true)
    }

    /**
     * Checks URLs loaded inside browser URL bars or WebViews against Shorts and allowed channel tokens.
     */
    fun checkWebUrl(
        url: String,
        allowedChannels: List<AllowedYouTubeChannelEntity>
    ): WebViewNavigationAction {
        val allowedTokens = allowedChannels
            .filter { it.isAllowed }
            .flatMap { extractChannelTokens(it) }
            .toSet()
        return checkWebUrlAgainstAllowedTokens(url, allowedTokens, strictStudyWebView = false)
    }

    private fun extractSearchQuery(url: String): String {
        val queryPart = url.substringAfter("?", "")
        if (queryPart.isBlank()) return ""
        val raw = queryPart.split("&")
            .firstOrNull { it.startsWith("search_query=", ignoreCase = true) || it.startsWith("q=", ignoreCase = true) }
            ?.substringAfter("=")
            ?: return ""
        return try {
            java.net.URLDecoder.decode(raw, "UTF-8").trim()
        } catch (_: Exception) {
            raw.replace("+", " ").trim()
        }
    }

    private fun checkWebUrlAgainstAllowedTokens(
        url: String,
        allowedTokens: Set<String>,
        strictStudyWebView: Boolean
    ): WebViewNavigationAction {
        val trimmed = url.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.isBlank() || lower == "about:blank") return WebViewNavigationAction.ALLOW

        // 1. Block all Shorts URLs unconditionally
        if (lower.contains("/shorts/") ||
            lower.contains("/shorts?") ||
            lower.endsWith("/shorts") ||
            lower.contains("#shorts") ||
            lower.contains("&shorts") ||
            lower.contains("feed/shorts")
        ) {
            return WebViewNavigationAction.BLOCK_SHORTS
        }

        // Block external app intents inside Study WebView
        if (strictStudyWebView && !lower.startsWith("http://") && !lower.startsWith("https://")) {
            return WebViewNavigationAction.BLOCK_OTHER_CHANNEL
        }

        val host = lower.substringAfter("://", lower)
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")

        // In strict Study Player WebView, permit only YouTube and Google/YouTube auth/consent/media support hosts
        if (!host.contains("youtube.com") && !host.contains("youtu.be")) {
            if (!strictStudyWebView) return WebViewNavigationAction.ALLOW
            val isSupportHost = host == "accounts.google.com" ||
                host == "consent.google.com" ||
                host == "myaccount.google.com" ||
                host.endsWith(".googlevideo.com") ||
                host.endsWith(".ytimg.com") ||
                host.endsWith(".ggpht.com") ||
                host.endsWith(".gstatic.com")
            return if (isSupportHost) WebViewNavigationAction.ALLOW else WebViewNavigationAction.BLOCK_OTHER_CHANNEL
        }

        // Block dedicated gaming / music subdomains
        if (host.startsWith("gaming.youtube") || host.startsWith("music.youtube")) {
            return WebViewNavigationAction.BLOCK_OTHER_CHANNEL
        }

        // 2. Parse YouTube URL path
        val path = lower.substringAfter("youtube.com/", "")
            .substringBefore("?")
            .substringBefore("#")
            .trim('/')

        // Allow internal API, static assets, consent, and auth endpoints
        val isInternalOrAuthPath = path.startsWith("youtubei/") ||
            path.startsWith("s/") ||
            path.startsWith("api/") ||
            path == "sw.js" ||
            path == "gen_204" ||
            path == "generate_204" ||
            path == "error_204" ||
            path == "iframe_api" ||
            path.startsWith("signin") ||
            path.startsWith("logout") ||
            path.startsWith("account") ||
            host.startsWith("consent.youtube") ||
            host.startsWith("accounts.youtube")
        if (isInternalOrAuthPath) {
            return WebViewNavigationAction.ALLOW
        }

        // In strict Study Player WebView, root homepage (m.youtube.com/ or youtube.com/) is blocked
        if (path.isBlank()) {
            return if (strictStudyWebView && !host.contains("youtu.be")) {
                WebViewNavigationAction.BLOCK_OTHER_CHANNEL
            } else {
                WebViewNavigationAction.ALLOW
            }
        }

        // 3. Block search URLs (/results?search_query=...)
        if (path == "results" || path.startsWith("results/")) {
            if (strictStudyWebView) {
                return WebViewNavigationAction.BLOCK_OTHER_CHANNEL
            }
            val query = extractSearchQuery(trimmed)
            return if (query.isNotBlank() && matchesAllowedChannel(query, allowedTokens)) {
                WebViewNavigationAction.ALLOW
            } else {
                WebViewNavigationAction.BLOCK_OTHER_CHANNEL
            }
        }

        // 4. Check channel URL path patterns: /@handle, /channel/UC..., /c/name, /user/name
        val channelSlug = when {
            path.startsWith("@") -> path.substringBefore("/")
            path.startsWith("channel/") -> path.substringAfter("channel/").substringBefore("/")
            path.startsWith("c/") -> path.substringAfter("c/").substringBefore("/")
            path.startsWith("user/") -> path.substringAfter("user/").substringBefore("/")
            else -> null
        }

        if (!channelSlug.isNullOrBlank()) {
            val slugClean = channelSlug.removePrefix("@").replace(Regex("[^a-z0-9]"), "")
            val matches = allowedTokens.any { token ->
                val tokenClean = token.removePrefix("@").replace(Regex("[^a-z0-9]"), "")
                token == channelSlug ||
                    token == channelSlug.removePrefix("@") ||
                    (slugClean.length >= 3 && tokenClean == slugClean)
            }
            return if (matches) WebViewNavigationAction.ALLOW else WebViewNavigationAction.BLOCK_OTHER_CHANNEL
        }

        // 5. Block trending / gaming / feed / hashtags / explore tabs
        if (path.startsWith("feed/") ||
            path == "feed" ||
            path.startsWith("gaming") ||
            path.startsWith("hashtag") ||
            path.startsWith("podcasts") ||
            path.startsWith("playables") ||
            path.startsWith("sports") ||
            path.startsWith("fashion") ||
            path.startsWith("redirect")
        ) {
            return WebViewNavigationAction.BLOCK_OTHER_CHANNEL
        }

        // 6. In strict Study Player WebView, allow only /watch and /playlist (verified by player owner check)
        if (strictStudyWebView) {
            val isWatchOrPlaylist = path == "watch" ||
                path.startsWith("watch/") ||
                path == "playlist" ||
                path.startsWith("playlist/") ||
                path.startsWith("embed/")
            return if (isWatchOrPlaylist) WebViewNavigationAction.ALLOW else WebViewNavigationAction.BLOCK_OTHER_CHANNEL
        }

        return WebViewNavigationAction.ALLOW
    }

    internal fun isNativeShortsActive(nodes: List<NodeTextData>): Boolean {
        for (node in nodes) {
            val vid = node.viewId?.lowercase(Locale.ROOT).orEmpty()
            // Never trigger on the Home bottom navigation tab icon
            if (vid.contains("pivot_bar") || vid.contains("shorts_pivot")) {
                continue
            }
            if (SHORTS_ACTIVE_VIEW_IDS.any { vid.contains(it) }) {
                return true
            }
        }

        // Fallback accessibility content description / text fingerprint when viewId is obfuscated
        var hasRemixShort = false
        var hasShortCommentOrLike = false
        for (node in nodes) {
            val t = node.text.trim().lowercase(Locale.ROOT)
            if (SHORTS_EXPLICIT_LABELS.any { t.contains(it) }) {
                return true
            }
            if (t == "remix" || t.contains("remix this")) {
                hasRemixShort = true
            }
            if (t.contains("like this video") || t.contains("dislike this video") || t.contains("open comments")) {
                hasShortCommentOrLike = true
            }
        }
        // Only treat Remix+Like fingerprint as Shorts if no full video time bar / player view exists
        if (hasRemixShort && hasShortCommentOrLike) {
            val hasNormalPlayerBar = nodes.any {
                val vid = it.viewId?.lowercase(Locale.ROOT).orEmpty()
                vid.contains("time_bar") || vid.contains("watch_while_time_bar")
            }
            if (!hasNormalPlayerBar) {
                return true
            }
        }

        return false
    }

    /**
     * Parses channel hints encoded in a YouTube Study Whitelist schedule package pattern.
     * Supports formats like:
     *   - "com.google.android.youtube:whitelist"
     *   - "com.google.android.youtube:whitelist:@takeUforward|Khan Academy"
     */
    fun parseRuleWhitelistChannels(restrictedPackages: String): List<String> {
        return restrictedPackages.split(",")
            .map { it.trim() }
            .filter { it.startsWith("com.google.android.youtube:whitelist", ignoreCase = true) }
            .flatMap { token ->
                val suffix = token.substringAfter(":whitelist", "")
                    .removePrefix(":")
                    .trim()
                if (suffix.isBlank()) emptyList()
                else suffix.split("|").map { it.trim() }.filter { it.isNotBlank() }
            }
    }

    fun isYouTubeWhitelistToken(pkgPattern: String): Boolean {
        val lower = pkgPattern.trim().lowercase(Locale.ROOT)
        return lower == "youtube:whitelist" ||
            lower.startsWith("com.google.android.youtube:whitelist")
    }
}
