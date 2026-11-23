package com.example.ui.screens.child.youtube

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AllowedYouTubeChannelEntity
import com.example.data.remote.YouTubeChannelSearchResult
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PurpleAccent

@Composable
fun FocusYouTubeScreen(
    childId: String = "child-default",
    channels: List<AllowedYouTubeChannelEntity>,
    searchResults: List<YouTubeChannelSearchResult>,
    isSearching: Boolean,
    onSearchChannels: (String) -> Unit,
    onClearSearch: () -> Unit,
    onAddChannelFromSearch: (YouTubeChannelSearchResult) -> Unit,
    onToggleChannelAllowed: (channelId: String, isAllowed: Boolean) -> Unit,
    onRemoveChannel: (channelId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current active channel loaded in full ReGain view
    var activeChannelForView by remember { mutableStateOf<AllowedYouTubeChannelEntity?>(null) }
    var activeUrlForView by remember { mutableStateOf<String?>(null) }

    // Search query state
    var searchQuery by remember { mutableStateOf("") }
    var shieldBlockedNotice by remember { mutableStateOf<String?>(null) }

    val allowedOnlyChannels = remember(channels) {
        channels.filter { it.isAllowed }.distinctBy { it.channelId }
    }

    if (activeChannelForView != null && activeUrlForView != null && activeChannelForView!!.isAllowed) {
        BackHandler {
            activeChannelForView = null
            activeUrlForView = null
        }
        RealYouTubeChannelFullView(
            channel = activeChannelForView!!,
            allowedChannels = allowedOnlyChannels,
            initialUrl = activeUrlForView!!,
            onClose = {
                activeChannelForView = null
                activeUrlForView = null
            },
            onShortsBlocked = {
                shieldBlockedNotice = "YouTube Shorts are blocked by FocusSense Study Shield."
            },
            onChannelBlocked = { blockedName ->
                shieldBlockedNotice = if (blockedName.isNotBlank()) {
                    "'$blockedName' is blocked. Only your allowed study channels are permitted."
                } else {
                    "Only your allowed study channels and playlists are permitted inside Study YT."
                }
            }
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ReGain Mascot Companion Header
            item {
                ReGainMascotHeader(
                    allowedCount = channels.count { it.isAllowed }
                )
            }

            // 2. Focus Shield Notice
            item {
                ReGainFocusShieldNotice()
            }

            // 3. Search Bar: Search YouTube Channel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Find & Add YouTube Channels",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("yt_channel_search_input"),
                        placeholder = { Text("Search channel (e.g. Striver, take U forward)") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = IndigoPrimary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    onClearSearch()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    onSearchChannels(searchQuery.trim())
                                }
                            },
                            enabled = searchQuery.isNotBlank() && !isSearching,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("yt_btn_search_channels")
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching YouTube...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Search YouTube Channels", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Quick Search Suggestions
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Striver", "take U forward", "Khan Academy", "freeCodeCamp").forEach { suggestion ->
                            FilterChip(
                                selected = searchQuery.equals(suggestion, ignoreCase = true),
                                onClick = {
                                    searchQuery = suggestion
                                    onSearchChannels(suggestion)
                                },
                                label = { Text(suggestion, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 4. Search Results Section (Displays actual YouTube channels with logo)
            if (searchResults.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search Results (${searchResults.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                        TextButton(onClick = onClearSearch) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }

                items(searchResults) { result ->
                    val matchedAllowedChannel = channels.firstOrNull { ch ->
                        ch.isAllowed && (
                            ch.channelId == result.channelId ||
                                (result.handle.isNotBlank() && ch.handle.equals(result.handle, ignoreCase = true)) ||
                                ch.channelName.equals(result.title, ignoreCase = true)
                            )
                    }
                    val alreadyAllowed = matchedAllowedChannel != null

                    YouTubeSearchResultCard(
                        result = result,
                        isAlreadyAllowed = alreadyAllowed,
                        onAdd = {
                            onAddChannelFromSearch(result)
                        },
                        onViewVideos = {
                            if (matchedAllowedChannel != null) {
                                val handleClean = matchedAllowedChannel.handle.removePrefix("@").trim()
                                val safeBaseUrl = matchedAllowedChannel.channelUrl.ifBlank {
                                    "https://m.youtube.com/@$handleClean"
                                }
                                activeChannelForView = matchedAllowedChannel
                                activeUrlForView = if (safeBaseUrl.contains("/playlists")) safeBaseUrl else "$safeBaseUrl/playlists"
                            } else {
                                shieldBlockedNotice = "'${result.title}' is not in your allowed channels. Tap 'Allow Channel' first to watch its study videos."
                            }
                        }
                    )
                }
            }

            // 5. Allowed Channels Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "See Your Allowed Channels",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (allowedOnlyChannels.isEmpty()) "No channels allowed yet — search above to add one!" else "${allowedOnlyChannels.size} active for study",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6. Allowed Channels List
            if (allowedOnlyChannels.isEmpty()) {
                item {
                    ReGainEmptyChannelsPlaceholder(
                        onQuickSearch = { suggested ->
                            searchQuery = suggested
                            onSearchChannels(suggested)
                        }
                    )
                }
            } else {
                items(allowedOnlyChannels) { channel ->
                    AllowedChannelCard(
                        channel = channel,
                        onViewVideos = {
                            if (channel.isAllowed) {
                                val handleClean = channel.handle.removePrefix("@").trim()
                                val url = if (channel.channelUrl.isNotBlank()) {
                                    if (channel.channelUrl.contains("/playlists")) channel.channelUrl else "${channel.channelUrl}/playlists"
                                } else {
                                    "https://m.youtube.com/@$handleClean/playlists"
                                }
                                activeChannelForView = channel
                                activeUrlForView = url
                            } else {
                                shieldBlockedNotice = "'${channel.channelName}' is currently paused from study channels."
                            }
                        },
                        onRemove = {
                            onRemoveChannel(channel.channelId)
                        }
                    )
                }
            }
        }
    }

    // Modal when shorts or unapproved channel distraction is blocked
    shieldBlockedNotice?.let { msg ->
        AlertDialog(
            onDismissRequest = { shieldBlockedNotice = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CoralDangerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CoralDanger, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text("FocusSense Study Shield", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { shieldBlockedNotice = null },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Stay Focused", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// -------------------------------------------------------------------------
// YOUTUBE SEARCH RESULT CARD (SHOWS LOGO, NAME, ALLOW, VIEW VIDEOS)
// -------------------------------------------------------------------------
@Composable
private fun YouTubeSearchResultCard(
    result: YouTubeChannelSearchResult,
    isAlreadyAllowed: Boolean,
    onAdd: () -> Unit,
    onViewVideos: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("yt_result_${result.title.replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real YouTube Channel Logo / Avatar
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (result.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = result.avatarUrl,
                            contentDescription = result.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Subscriptions,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = result.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = result.handle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndigoPrimary
                    )
                    if (result.subscriberCount.isNotBlank()) {
                        Text(
                            text = result.subscriberCount,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (result.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row: "Allow Channel" + "View Videos" (locked until channel is allowed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isAlreadyAllowed) {
                    Surface(
                        color = EmeraldSafeBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Allowed ✓", color = EmeraldSafe, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onViewVideos,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("yt_btn_view_videos_${result.title.replace(" ", "_")}")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.FeaturedPlayList, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Videos", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onAdd,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("yt_btn_allow_${result.title.replace(" ", "_")}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow Channel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onViewVideos,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralDanger),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("yt_btn_view_videos_${result.title.replace(" ", "_")}")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Locked until allowed", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow First", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// ALLOWED CHANNEL CARD
// -------------------------------------------------------------------------
@Composable
private fun AllowedChannelCard(
    channel: AllowedYouTubeChannelEntity,
    onViewVideos: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("yt_allowed_${channel.channelName.replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(IndigoPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (channel.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = channel.avatarUrl,
                                contentDescription = channel.channelName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Subscriptions,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.channelName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = "${channel.handle} • Allowed Study Channel",
                            fontSize = 11.sp,
                            color = EmeraldSafe,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove Channel",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onViewVideos,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("yt_btn_open_allowed_${channel.channelName.replace(" ", "_")}")
            ) {
                Icon(Icons.AutoMirrored.Filled.FeaturedPlayList, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Videos & Playlists ▶", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// -------------------------------------------------------------------------
// REAL YOUTUBE FULL VIEW (DIRECTLY JUMPS INTO YOUTUBER'S PLAYLISTS/VIDEOS)
// -------------------------------------------------------------------------
private class YouTubeShieldJsBridge(
    private val onShortsTriggered: () -> Unit,
    private val onChannelTriggered: (String) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onShortsDetected() {
        onShortsTriggered()
    }

    @android.webkit.JavascriptInterface
    fun onOtherChannelDetected(channelName: String) {
        onChannelTriggered(channelName)
    }
}

// -------------------------------------------------------------------------
// REAL YOUTUBE FULL VIEW (DIRECTLY JUMPS INTO YOUTUBER'S PLAYLISTS/VIDEOS)
// -------------------------------------------------------------------------
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun RealYouTubeChannelFullView(
    channel: AllowedYouTubeChannelEntity,
    allowedChannels: List<AllowedYouTubeChannelEntity> = listOf(channel),
    initialUrl: String,
    onClose: () -> Unit,
    onShortsBlocked: () -> Unit,
    onChannelBlocked: (String) -> Unit = {}
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(channel.channelName) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }

    val handleClean = channel.handle.removePrefix("@").trim()
    val fallbackSafeUrl = remember(initialUrl, handleClean) {
        if (handleClean.isNotBlank()) "https://m.youtube.com/@$handleClean/playlists" else initialUrl
    }
    val allowedTokens = remember(allowedChannels) {
        allowedChannels.filter { it.isAllowed }
            .flatMap { com.example.service.YouTubeContentGuard.extractChannelTokens(it) }
            .distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Player Controls Bar
        Surface(
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (canGoBack && webViewInstance?.canGoBack() == true) {
                                    webViewInstance?.goBack()
                                } else {
                                    onClose()
                                }
                            },
                            modifier = Modifier.testTag("yt_view_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = pageTitle.ifBlank { channel.channelName },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = "Only ${channel.channelName} • Search, Shorts & Other Channels Blocked",
                                fontSize = 11.sp,
                                color = EmeraldSafe,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Quick Navigation Tabs: Playlists vs All Videos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (currentUrl.contains("playlist", ignoreCase = true)) IndigoPrimary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val playlistUrl = "https://m.youtube.com/@$handleClean/playlists"
                            webViewInstance?.loadUrl(playlistUrl)
                        }
                    ) {
                        Text(
                            text = "Playlists",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = if (currentUrl.contains("video", ignoreCase = true)) IndigoPrimary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val videosUrl = "https://m.youtube.com/@$handleClean/videos"
                            webViewInstance?.loadUrl(videosUrl)
                        }
                    ) {
                        Text(
                            text = "All Videos",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        color = EmeraldSafe,
                        trackColor = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                    )
                }
            }
        }

        // Real YouTube Full Screen Web View with FocusSense Distraction-Free Script Injection
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT

                        var lastBlockedAtMs = 0L

                        fun returnToSafeChannel(view: WebView?) {
                            if (view == null) return
                            view.stopLoading()
                            val history = view.copyBackForwardList()
                            var targetUrl: String? = null
                            for (idx in history.currentIndex - 1 downTo 0) {
                                val candidate = history.getItemAtIndex(idx)?.url.orEmpty()
                                val guardResult = com.example.service.YouTubeContentGuard.checkStudyWebViewUrl(candidate, allowedChannels)
                                val isWatch = candidate.contains("/watch", ignoreCase = true)
                                if (guardResult == com.example.service.WebViewNavigationAction.ALLOW && !isWatch) {
                                    targetUrl = candidate
                                    break
                                }
                            }
                            view.loadUrl(targetUrl ?: fallbackSafeUrl)
                        }

                        fun notifyBlockedOnce(action: () -> Unit) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastBlockedAtMs > 700L) {
                                lastBlockedAtMs = now
                                action()
                            }
                        }

                        val bridge = YouTubeShieldJsBridge(
                            onShortsTriggered = {
                                post {
                                    returnToSafeChannel(this)
                                    notifyBlockedOnce { onShortsBlocked() }
                                }
                            },
                            onChannelTriggered = { detectedName ->
                                post {
                                    returnToSafeChannel(this)
                                    notifyBlockedOnce { onChannelBlocked(detectedName) }
                                }
                            }
                        )
                        addJavascriptInterface(bridge, "AndroidFocusBridge")

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank() && !title.contains("YouTube", ignoreCase = true)) {
                                    pageTitle = title
                                }
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 85
                                if (newProgress > 25) {
                                    injectReGainDistractionBlocker(view, allowedTokens)
                                }
                            }
                        }

                        fun handleUrlGuard(view: WebView?, url: String, isMainFrame: Boolean = true): Boolean {
                            val decision = com.example.service.YouTubeContentGuard.checkStudyWebViewUrl(url, allowedChannels)
                            return when (decision) {
                                com.example.service.WebViewNavigationAction.BLOCK_SHORTS -> {
                                    if (isMainFrame) {
                                        view?.stopLoading()
                                        notifyBlockedOnce { onShortsBlocked() }
                                    }
                                    true
                                }
                                com.example.service.WebViewNavigationAction.BLOCK_OTHER_CHANNEL -> {
                                    if (isMainFrame) {
                                        view?.stopLoading()
                                        val blockedLabel = if (url.contains("/results", ignoreCase = true)) {
                                            "YouTube Search"
                                        } else {
                                            url.substringAfter("youtube.com/", url)
                                                .substringBefore("/")
                                                .substringBefore("?")
                                                .ifBlank { "YouTube Home" }
                                        }
                                        notifyBlockedOnce { onChannelBlocked(blockedLabel) }
                                    }
                                    true
                                }
                                com.example.service.WebViewNavigationAction.ALLOW -> false
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                val isMainFrame = request?.isForMainFrame ?: true
                                if (handleUrlGuard(view, url, isMainFrame)) return true
                                if (isMainFrame) currentUrl = url
                                return false
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                val safeUrl = url.orEmpty()
                                if (handleUrlGuard(view, safeUrl, isMainFrame = true)) {
                                    returnToSafeChannel(view)
                                    return
                                }
                                currentUrl = safeUrl
                                canGoBack = view?.canGoBack() == true
                                injectReGainDistractionBlocker(view, allowedTokens)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                val safeUrl = url.orEmpty()
                                if (handleUrlGuard(view, safeUrl, isMainFrame = true)) {
                                    returnToSafeChannel(view)
                                    return
                                }
                                isLoading = true
                                currentUrl = safeUrl
                                canGoBack = view?.canGoBack() == true
                                injectReGainDistractionBlocker(view, allowedTokens)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                val safeUrl = url.orEmpty()
                                if (handleUrlGuard(view, safeUrl, isMainFrame = true)) {
                                    returnToSafeChannel(view)
                                    return
                                }
                                currentUrl = safeUrl
                                canGoBack = view?.canGoBack() == true
                                injectReGainDistractionBlocker(view, allowedTokens)
                            }
                        }

                        loadUrl(initialUrl)
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                }
            )
        }
    }
}

@Composable
private fun ReGainMascotHeader(allowedCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(IndigoPrimary, PurpleAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Study Lounge",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = EmeraldSafeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ Mindful Study Lounge",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSafe
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Distraction-Free Channels",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Search any educator (e.g. Striver). Allow their channel to explore full playlists and videos with Shorts blocked.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ReGainFocusShieldNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(IndigoPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Strict Study Shield Active",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "🚫 Shorts: BLOCKED • 🚫 Other Channels: BLOCKED • 📺 Playlists: ON",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReGainEmptyChannelsPlaceholder(
    onQuickSearch: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(IndigoPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Subscriptions,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "No Channels Allowed Yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Search for your favorite teacher above (e.g. Striver, take U forward). Inspect the channel logo, tap 'Allow', and view all their playlists.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onQuickSearch("Striver") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier.testTag("yt_btn_try_striver")
            ) {
                Text("Search 'Striver'", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

private fun injectReGainDistractionBlocker(
    webView: WebView?,
    allowedTokens: List<String> = emptyList()
) {
    if (webView == null) return
    val jsonTokens = org.json.JSONArray(allowedTokens).toString()
    val js = """
        (function() {
            var css = `
                [tab-identifier="FEshorts"],
                [aria-label*="Shorts"],
                [title*="Shorts"],
                a[href*="/shorts/"],
                ytm-reel-shelf-renderer,
                ytd-reel-shelf-renderer,
                ytd-rich-shelf-renderer[is-shorts],
                .ytm-reel-shelf-renderer,
                .reel-shelf,
                ytm-mobile-topbar-renderer button[aria-label*="Search" i],
                ytm-mobile-topbar-renderer [icon-name="SEARCH"],
                ytm-searchbox,
                ytd-searchbox,
                #search-icon-legacy,
                #search-button,
                form[action="/results"],
                ytm-home-logo,
                ytm-mobile-topbar-renderer a[href="/"],
                ytm-pivot-bar-renderer,
                ytm-pivot-bar-item-renderer {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                    pointer-events: none !important;
                }
            `;
            var style = document.getElementById('regain-focus-blocker');
            if (!style) {
                style = document.createElement('style');
                style.id = 'regain-focus-blocker';
                style.type = 'text/css';
                style.appendChild(document.createTextNode(css));
                (document.head || document.documentElement).appendChild(style);
            }

            var allowed = $jsonTokens;
            window.__focusAllowedTokens = allowed;

            function isAllowedText(txt) {
                var list = window.__focusAllowedTokens || [];
                if (!txt || list.length === 0) return false;
                var lower = txt.toLowerCase();
                var compact = lower.replace(/[^a-z0-9]/g, '');
                for (var i = 0; i < list.length; i++) {
                    var tok = list[i].toLowerCase();
                    if (tok.length < 2) continue;
                    if (lower.indexOf(tok) !== -1) return true;
                    var tokCompact = tok.replace(/[^a-z0-9]/g, '');
                    if (tokCompact.length >= 3 && compact.indexOf(tokCompact) !== -1) return true;
                }
                return false;
            }

            function pauseAllMedia() {
                try {
                    document.querySelectorAll('video, audio').forEach(function(m) {
                        m.pause();
                        m.muted = true;
                    });
                } catch (e) {}
            }

            var lastTriggerTs = 0;
            function triggerShorts() {
                pauseAllMedia();
                var now = Date.now();
                if (now - lastTriggerTs < 650) return;
                lastTriggerTs = now;
                if (window.AndroidFocusBridge) window.AndroidFocusBridge.onShortsDetected();
            }

            function triggerChannel(name) {
                pauseAllMedia();
                var now = Date.now();
                if (now - lastTriggerTs < 650) return;
                lastTriggerTs = now;
                if (window.AndroidFocusBridge) window.AndroidFocusBridge.onOtherChannelDetected(name || 'Other Channel');
            }

            function checkBlockedUrl(rawUrl) {
                if (!rawUrl) return null;
                var lower = String(rawUrl).toLowerCase();
                if (lower.indexOf('/shorts/') !== -1 || lower.indexOf('/shorts?') !== -1 || lower.endsWith('/shorts')) {
                    return { type: 'shorts', label: 'Shorts' };
                }
                if (lower.indexOf('youtube.com/results') !== -1 || lower.startsWith('/results')) {
                    return { type: 'channel', label: 'YouTube Search' };
                }
                if (lower.indexOf('/feed/') !== -1 || lower.indexOf('/gaming') !== -1 || lower.indexOf('/hashtag') !== -1) {
                    return { type: 'channel', label: 'Unapproved Feed' };
                }
                try {
                    var parsed = new URL(lower, window.location.origin);
                    var p = (parsed.pathname || '').replace(/^\/+|\/+$/g, '');
                    if (p === '' && parsed.hostname.indexOf('youtube.com') !== -1) {
                        return { type: 'channel', label: 'YouTube Home' };
                    }
                    if (p.startsWith('@') || p.startsWith('channel/') || p.startsWith('c/') || p.startsWith('user/')) {
                        var slug = p.startsWith('@') ? p.split('/')[0] : (p.split('/')[1] || '');
                        if (slug && !isAllowedText(slug)) {
                            return { type: 'channel', label: slug };
                        }
                    }
                } catch (e) {}
                return null;
            }

            var watchTrack = { id: '', mountedAt: 0 };

            function inspectCurrentPage() {
                var href = (window.location.href || '');
                var urlBlock = checkBlockedUrl(href);
                if (urlBlock) {
                    if (urlBlock.type === 'shorts') triggerShorts();
                    else triggerChannel(urlBlock.label);
                    return;
                }

                var lowerHref = href.toLowerCase();
                if (lowerHref.indexOf('/playlist') !== -1) {
                    var plOwner = document.querySelector('ytm-playlist-header-renderer a[href*="/@"], ytm-playlist-header-renderer a[href*="/channel/"], ytm-playlist-header-renderer .playlist-header-owner-name, #owner-text a');
                    if (plOwner) {
                        var plName = (plOwner.innerText || plOwner.textContent || '').trim();
                        var plHref = (plOwner.getAttribute('href') || '').toLowerCase();
                        if ((plName || plHref) && !isAllowedText(plName) && !isAllowedText(plHref)) {
                            triggerChannel(plName || 'Other Playlist');
                            return;
                        }
                    }
                }

                if (lowerHref.indexOf('/watch') !== -1) {
                    var videoId = '';
                    try { videoId = new URLSearchParams(window.location.search).get('v') || ''; } catch (e) {}
                    if (watchTrack.id !== videoId) {
                        watchTrack = { id: videoId, mountedAt: 0 };
                    }

                    // 1. Fresh ytInitialPlayerResponse matching the exact active videoId
                    var vd = window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.videoDetails;
                    if (vd && (!videoId || vd.videoId === videoId)) {
                        var vdAuthor = vd.author || '';
                        var vdChannelId = vd.channelId || '';
                        if ((vdAuthor || vdChannelId) && !isAllowedText(vdAuthor) && !isAllowedText(vdChannelId)) {
                            triggerChannel(vdAuthor || vdChannelId);
                            return;
                        }
                        if (isAllowedText(vdAuthor) || isAllowedText(vdChannelId)) {
                            return;
                        }
                    }

                    // 2. Active video owner DOM header (excludes related/up-next cards)
                    var ownerSelectors = [
                        'ytm-slim-owner-renderer',
                        'ytm-video-owner-renderer',
                        'ytm-slim-video-metadata-section-renderer ytm-channel-name',
                        '#owner-name',
                        'ytd-video-owner-renderer ytd-channel-name',
                        'ytd-channel-name'
                    ].join(', ');
                    var ownerEls = document.querySelectorAll(ownerSelectors);
                    var foundAnyOwnerInfo = false;
                    for (var j = 0; j < ownerEls.length; j++) {
                        var el = ownerEls[j];
                        if (el.closest('ytm-item-section-renderer[section-identifier="related-items"], ytm-watch-next-secondary-results-renderer, #related')) {
                            continue;
                        }
                        var oText = (el.innerText || el.textContent || '').trim();
                        var linkEl = el.tagName === 'A' ? el : el.querySelector('a[href]');
                        var oHref = linkEl ? (linkEl.getAttribute('href') || '') : '';
                        if (oText || oHref) {
                            foundAnyOwnerInfo = true;
                            if (isAllowedText(oText) || isAllowedText(oHref)) {
                                return;
                            }
                            triggerChannel(oText || oHref);
                            return;
                        }
                    }

                    // 3. Fail-closed safety check once watch metadata container has mounted
                    var watchMounted = document.querySelector('ytm-slim-video-metadata-section-renderer, ytm-watch, ytd-watch-flexy');
                    if (watchMounted && !foundAnyOwnerInfo) {
                        if (watchTrack.mountedAt === 0) {
                            watchTrack.mountedAt = Date.now();
                        } else if (Date.now() - watchTrack.mountedAt > 3000) {
                            triggerChannel('Unverified Video');
                        }
                    }
                } else {
                    watchTrack = { id: '', mountedAt: 0 };
                }
            }

            if (!window.__focusSenseHooked) {
                window.__focusSenseHooked = true;

                var origPushState = history.pushState;
                history.pushState = function(state, title, url) {
                    var blocked = checkBlockedUrl(url);
                    if (blocked) {
                        if (blocked.type === 'shorts') triggerShorts();
                        else triggerChannel(blocked.label);
                        return;
                    }
                    var res = origPushState.apply(this, arguments);
                    setTimeout(inspectCurrentPage, 150);
                    return res;
                };

                var origReplaceState = history.replaceState;
                history.replaceState = function(state, title, url) {
                    var blocked = checkBlockedUrl(url);
                    if (blocked) {
                        if (blocked.type === 'shorts') triggerShorts();
                        else triggerChannel(blocked.label);
                        return;
                    }
                    var res = origReplaceState.apply(this, arguments);
                    setTimeout(inspectCurrentPage, 150);
                    return res;
                };

                document.addEventListener('submit', function(e) {
                    var form = e.target;
                    var action = (form && form.getAttribute && form.getAttribute('action')) || '';
                    if (action.toLowerCase().indexOf('results') !== -1 || (form && form.querySelector && form.querySelector('input[name="search_query"]'))) {
                        e.preventDefault();
                        e.stopPropagation();
                        triggerChannel('YouTube Search');
                    }
                }, true);

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    if (!target || !target.closest) return;
                    if (target.closest('ytm-searchbox, ytd-searchbox, form[action="/results"], button[aria-label*="Search" i]')) {
                        e.preventDefault();
                        e.stopPropagation();
                        triggerChannel('YouTube Search');
                        return;
                    }
                    var anchor = target.closest('a[href]');
                    if (anchor) {
                        var blocked = checkBlockedUrl(anchor.getAttribute('href'));
                        if (blocked) {
                            e.preventDefault();
                            e.stopPropagation();
                            if (blocked.type === 'shorts') triggerShorts();
                            else triggerChannel(blocked.label);
                        }
                    }
                }, true);

                document.addEventListener('play', function() { setTimeout(inspectCurrentPage, 100); }, true);
                window.addEventListener('yt-navigate-finish', function() { setTimeout(inspectCurrentPage, 150); });
                window.addEventListener('yt-page-data-updated', function() { setTimeout(inspectCurrentPage, 150); });
                window.addEventListener('popstate', function() { setTimeout(inspectCurrentPage, 150); });
                setInterval(inspectCurrentPage, 400);
            }
            inspectCurrentPage();
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}
