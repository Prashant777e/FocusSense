package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.PurpleAccent

@Composable
fun FocusSenseTopBar(
    currentUser: UserEntity?,
    isNetworkConnected: Boolean,
    isSyncing: Boolean,
    unsyncedCount: Int,
    children: List<UserEntity>,
    selectedChildId: String,
    onSelectChild: (String) -> Unit,
    onToggleNetwork: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSwitchRoleRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showChildDropdown by remember { mutableStateOf(false) }

    Surface(
        color = Navy800,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Row 1: App Identity, Role, Network status, and Role Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("app_identity_row")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IndigoPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "FocusSense Logo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "FocusSense",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val roleLabel = if (currentUser?.role == "parent") "PARENT PORTAL" else "CHILD SENTINEL"
                            val roleColor = if (currentUser?.role == "parent") PurpleAccent else EmeraldSafe
                            Text(
                                text = roleLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = roleColor
                            )
                        }
                    }
                }

                // Action controls: Network state toggle, sync, role switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Network Connectivity Indicator & Toggle
                    IconButton(
                        onClick = { onToggleNetwork(!isNetworkConnected) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("network_toggle_button")
                    ) {
                        if (isNetworkConnected) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Online Mode - Tap to simulate offline",
                                tint = EmeraldSafe,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline Mode - Tap to go online",
                                tint = AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Zero Data-Loss Sync Badge
                    if (unsyncedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AmberWarningBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSyncNow() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("sync_queue_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = AmberWarning
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync Queue",
                                        tint = AmberWarning,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$unsyncedCount queued",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Active Profile Chip (Primary account switching is located in bottom Navigation Bar)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSwitchRoleRequested() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("top_bar_profile_chip")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (currentUser?.role == "child") Icons.Default.Lock else Icons.Default.Person,
                                contentDescription = "Active Profile",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentUser?.name?.take(10) ?: "Profile",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Row 2: In Parent Mode, show child selector tabs (Leo, Maya)
            if (currentUser?.role == "parent" && children.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Monitoring:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    children.forEach { child ->
                        val isSelected = child.userId == selectedChildId
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) IndigoPrimary else Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectChild(child.userId) }
                                .padding(end = 8.dp)
                                .testTag("child_selector_${child.userId}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else EmeraldSafe)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = child.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreatCategoryBadge(
    category: String?,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val cat = category.orEmpty()
    val isUnsafe = cat.contains("Grooming", ignoreCase = true) ||
            cat.contains("Stranger", ignoreCase = true) ||
            cat.contains("Bullying", ignoreCase = true) ||
            cat.contains("Harassment", ignoreCase = true) ||
            cat.contains("Self-Harm", ignoreCase = true) ||
            cat.contains("Crisis", ignoreCase = true) ||
            cat.contains("Violence", ignoreCase = true) ||
            cat.contains("Explicit", ignoreCase = true) ||
            cat.contains("Adult", ignoreCase = true) ||
            cat.contains("Scam", ignoreCase = true) ||
            cat.contains("Phishing", ignoreCase = true) ||
            cat.contains("Suspicious", ignoreCase = true) ||
            cat.contains("Threat", ignoreCase = true)

    val (bg, textCol, label) = when {
        cat.contains("Self-Harm", ignoreCase = true) || cat.contains("Crisis", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Self-Harm Concern")
        cat.contains("Grooming", ignoreCase = true) || cat.contains("Stranger", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Predatory Grooming Risk")
        cat.contains("Bullying", ignoreCase = true) || cat.contains("Harassment", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Cyberbullying")
        cat.contains("Explicit", ignoreCase = true) || cat.contains("Adult", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Explicit / Adult Content")
        cat.contains("Scam", ignoreCase = true) || cat.contains("Phishing", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Scam & Phishing")
        cat.contains("Violence", ignoreCase = true) || cat.contains("Threat", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Violence & Threats")
        isUnsafe ->
            Triple(CoralDangerBg, CoralDanger, cat)
        cat.contains("Academic", ignoreCase = true) ->
            Triple(AmberWarningBg, AmberWarning, "Academic Distraction")
        else ->
            Triple(EmeraldSafeBg, EmeraldSafe, "Safe Content")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textCol)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label (${(confidence * 100).toInt()}%)",
                color = textCol,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
