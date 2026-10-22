package com.example.ui.screens.child

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.util.ChildPermissionManager
import com.example.util.ChildPermissionStatus

@Composable
fun ChildPermissionWizardScreen(
    childName: String,
    onContinueToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionStatus by remember {
        mutableStateOf(ChildPermissionManager.checkAllPermissions(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = ChildPermissionManager.checkAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus = ChildPermissionManager.checkAllPermissions(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionStatus = ChildPermissionManager.checkAllPermissions(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Shield & Progress Summary
        item {
            PermissionHeroHeader(
                childName = childName,
                status = permissionStatus,
                onRefresh = {
                    permissionStatus = ChildPermissionManager.checkAllPermissions(context)
                }
            )
        }

        // 2. Step 1: Accessibility Service (AI Sentinel)
        item {
            PermissionStepCard(
                title = "AI Sentinel (Accessibility)",
                icon = Icons.Default.Accessibility,
                isGranted = permissionStatus.isAccessibilityEnabled,
                description = "Scans screen text in real-time to protect against cyberbullying and grooming.",
                actionLabel = "Enable",
                onAction = { ChildPermissionManager.openAccessibilitySettings(context) },
                testTag = "perm_btn_accessibility"
            )
        }

        // 3. Step 2: Usage Access (Screen Time)
        item {
            PermissionStepCard(
                title = "Usage Access (Screen Time)",
                icon = Icons.Default.DataUsage,
                isGranted = permissionStatus.isUsageAccessGranted,
                description = "Enforces study timetables and curfew limits by detecting active apps.",
                actionLabel = "Grant",
                onAction = { ChildPermissionManager.openUsageAccessSettings(context) },
                testTag = "perm_btn_usage"
            )
        }

        // 4. Step 3: GPS Location
        item {
            PermissionStepCard(
                title = "Live GPS & Safety Beacon",
                icon = Icons.Default.LocationOn,
                isGranted = permissionStatus.isLocationGranted,
                description = "Shares safety location telemetry with the parent dashboard.",
                actionLabel = "Allow",
                onAction = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                testTag = "perm_btn_location"
            )
        }

        // 5. Step 4: Battery Optimization
        item {
            PermissionStepCard(
                title = "Background Battery Exemption",
                icon = Icons.Default.BatteryChargingFull,
                isGranted = permissionStatus.isBatteryExempted,
                description = "Prevents system task killers from pausing child protection guards.",
                actionLabel = "Exempt",
                onAction = { ChildPermissionManager.openBatteryOptimizationSettings(context) },
                testTag = "perm_btn_battery"
            )
        }

        // 6. Step 5: Screen Overlay
        item {
            PermissionStepCard(
                title = "Curfew Blocker (Overlay)",
                icon = Icons.Default.Layers,
                isGranted = permissionStatus.isOverlayGranted,
                description = "Displays the focus blocker screen over restricted apps.",
                actionLabel = "Allow",
                onAction = { ChildPermissionManager.openOverlaySettings(context) },
                testTag = "perm_btn_overlay"
            )
        }

        // 7. Step 6: Notifications
        item {
            PermissionStepCard(
                title = "Guardian Notifications",
                icon = Icons.Default.Notifications,
                isGranted = permissionStatus.isNotificationGranted,
                description = "Sends friendly study break and curfew warnings.",
                actionLabel = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                testTag = "perm_btn_notifications"
            )
        }

        // 8. Bottom Action
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (permissionStatus.allCrucialGranted) EmeraldSafeBg else AmberWarningBg
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (permissionStatus.allCrucialGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (permissionStatus.allCrucialGranted) EmeraldSafe else AmberWarning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (permissionStatus.isFullyProtected) {
                                "Shield Fully Active & Armed!"
                            } else if (permissionStatus.allCrucialGranted) {
                                "Core Protection Ready (${permissionStatus.grantedCount}/${permissionStatus.totalCount})"
                            } else {
                                "Complete Setup for Full Protection"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (permissionStatus.allCrucialGranted) EmeraldSafe else AmberWarning,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onContinueToDashboard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (permissionStatus.allCrucialGranted) EmeraldSafe else IndigoPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("perm_btn_continue")
                    ) {
                        Text(
                            text = if (permissionStatus.isFullyProtected) {
                                "Open Child Dashboard"
                            } else {
                                "Continue to Dashboard"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionHeroHeader(
    childName: String,
    status: ChildPermissionStatus,
    onRefresh: () -> Unit
) {
    val progressColor by animateColorAsState(
        targetValue = when {
            status.isFullyProtected -> EmeraldSafe
            status.allCrucialGranted -> IndigoPrimary
            else -> AmberWarning
        },
        label = "progressColor"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(progressColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Device Protection Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Guarding $childName's Phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("perm_btn_refresh")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Status",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${status.grantedCount} of ${status.totalCount} Active",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = progressColor
                )
                Text(
                    text = "${(status.progressPercent * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { status.progressPercent },
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun PermissionStepCard(
    title: String,
    icon: ImageVector,
    isGranted: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGranted) EmeraldSafeBg else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isGranted) EmeraldSafe else AmberWarning)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGranted) "Permission Active" else "Action Required",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGranted) EmeraldSafe else AmberWarning
                    )
                }

                if (!isGranted) {
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag(testTag)
                    ) {
                        Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        color = EmeraldSafeBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSafe,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Configured",
                                color = EmeraldSafe,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
