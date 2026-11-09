package com.example.ui.screens.child

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PineAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.SageAccent
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

    // In-app simulation toggle to effortlessly verify and demo full protection
    var simulatedFullProtection by remember { mutableStateOf(false) }

    val effectiveStatus = if (simulatedFullProtection) {
        ChildPermissionStatus(
            isAccessibilityEnabled = true,
            isUsageAccessGranted = true,
            isLocationGranted = true,
            isBackgroundLocationGranted = true,
            isBatteryExempted = true,
            isOverlayGranted = true,
            isNotificationGranted = true
        )
    } else {
        permissionStatus
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
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sleek Modern Hero Header (Zero Cartoon Character, Modern Tech & Privacy Aesthetic)
        item {
            ModernPermissionHeroCard(
                childName = childName,
                status = effectiveStatus,
                onRefresh = {
                    permissionStatus = ChildPermissionManager.checkAllPermissions(context)
                },
                onQuickDemoToggle = {
                    simulatedFullProtection = !simulatedFullProtection
                },
                isSimulated = simulatedFullProtection
            )
        }

        // 2. Shield Level & Security Status Meter
        item {
            ModernSecurityStatusBar(status = effectiveStatus)
        }

        // 3. Step 1: AI Screen Safety (Accessibility)
        item {
            ModernPermissionStepCard(
                stepIndex = "01",
                badgeLabel = "Core Protection",
                title = "AI Screen Safety",
                permissionSystemName = "Accessibility Service",
                icon = Icons.Default.Visibility,
                accentColor = IndigoPrimary,
                isGranted = effectiveStatus.isAccessibilityEnabled,
                description = "Scans on-screen text in real time to filter cyberbullying, toxic interactions, and explicit content across apps.",
                privacyNote = "Processed entirely on-device. Personal messages are never sent to external servers.",
                actionLabel = "Enable Shield",
                onAction = { ChildPermissionManager.openAccessibilitySettings(context) },
                testTag = "perm_btn_accessibility"
            )
        }

        // 4. Step 2: App Usage & Screen Time (Usage Access)
        item {
            ModernPermissionStepCard(
                stepIndex = "02",
                badgeLabel = "Time Management",
                title = "App Limits & Curfew",
                permissionSystemName = "Usage Access",
                icon = Icons.Default.DataUsage,
                accentColor = PurpleAccent,
                isGranted = effectiveStatus.isUsageAccessGranted,
                description = "Monitors daily screen time and automatically enforces scheduled curfews and study hours set by parents.",
                privacyNote = "Tracks app open time only. In-app activity contents remain strictly private.",
                actionLabel = "Grant Access",
                onAction = { ChildPermissionManager.openUsageAccessSettings(context) },
                testTag = "perm_btn_usage"
            )
        }

        // 5. Step 3: Live GPS Safety Beacon (Location)
        item {
            ModernPermissionStepCard(
                stepIndex = "03",
                badgeLabel = "Safety Beacon",
                title = "Live GPS Location",
                permissionSystemName = "Location (Fine & Coarse)",
                icon = Icons.Default.LocationOn,
                accentColor = EmeraldLight,
                isGranted = effectiveStatus.isLocationGranted,
                description = "Shares real-time coordinates and geofence alerts so parents know when you arrive safely at school, home, or activities.",
                privacyNote = "Encrypted telemetry only accessible by paired parent accounts.",
                actionLabel = "Allow Location",
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

        // 6. Step 4: Background Persistence (Battery Exemption)
        item {
            ModernPermissionStepCard(
                stepIndex = "04",
                badgeLabel = "Reliability",
                title = "Background Persistence",
                permissionSystemName = "Battery Optimization Exemption",
                icon = Icons.Default.BatteryChargingFull,
                accentColor = AmberWarning,
                isGranted = effectiveStatus.isBatteryExempted,
                description = "Prevents the Android operating system from killing FocusSense background protection when the phone sleeps.",
                privacyNote = "Engineered for negligible battery draw (< 1.5% daily average).",
                actionLabel = "Exempt App",
                onAction = { ChildPermissionManager.openBatteryOptimizationSettings(context) },
                testTag = "perm_btn_battery"
            )
        }

        // 7. Step 5: Screen Overlay Blocker (Draw Over Apps)
        item {
            ModernPermissionStepCard(
                stepIndex = "05",
                badgeLabel = "Focus Lock",
                title = "Curfew Block Overlay",
                permissionSystemName = "Display Over Other Apps",
                icon = Icons.Default.Layers,
                accentColor = SageAccent,
                isGranted = effectiveStatus.isOverlayGranted,
                description = "Displays the focus shield overlay whenever a restricted app is launched during active study or bedtime hours.",
                privacyNote = "Only overlays restricted apps during scheduled curfew windows.",
                actionLabel = "Allow Overlay",
                onAction = { ChildPermissionManager.openOverlaySettings(context) },
                testTag = "perm_btn_overlay"
            )
        }

        // 8. Step 6: Notifications (POST_NOTIFICATIONS)
        item {
            ModernPermissionStepCard(
                stepIndex = "06",
                badgeLabel = "Alerts",
                title = "Focus Alerts & Reminders",
                permissionSystemName = "Notification Services",
                icon = Icons.Default.Notifications,
                accentColor = EmeraldVibrant,
                isGranted = effectiveStatus.isNotificationGranted,
                description = "Delivers study session timers, break intervals, and parent messages straight to your status bar.",
                privacyNote = "High-priority productivity alerts only; no advertising or spam.",
                actionLabel = "Enable Alerts",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                testTag = "perm_btn_notifications"
            )
        }

        // 9. Modern Bottom Action Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (effectiveStatus.isFullyProtected) EmeraldSafeBg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (effectiveStatus.isFullyProtected) Modifier.border(1.5.dp, EmeraldSafe, RoundedCornerShape(20.dp))
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (effectiveStatus.isFullyProtected) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (effectiveStatus.isFullyProtected) EmeraldSafe else IndigoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (effectiveStatus.isFullyProtected) {
                                "All Protections Configured & Active"
                            } else {
                                "Protection Status: ${effectiveStatus.grantedCount}/${effectiveStatus.totalCount} Configured"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (effectiveStatus.isFullyProtected) EmeraldSafe else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onContinueToDashboard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (effectiveStatus.isFullyProtected) EmeraldSafe else IndigoPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("perm_btn_continue")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (effectiveStatus.isFullyProtected) {
                                    "Continue to Study Dashboard"
                                } else {
                                    "Continue to Dashboard"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// SLEEK MODERN HERO CARD (CLEAN, SOPHISTICATED TECH & PRIVACY AESTHETIC)
// -------------------------------------------------------------------------
@Composable
private fun ModernPermissionHeroCard(
    childName: String,
    status: ChildPermissionStatus,
    onRefresh: () -> Unit,
    onQuickDemoToggle: () -> Unit,
    isSimulated: Boolean
) {
    val isComplete = status.isFullyProtected

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sleek gradient decorative header strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(
                        Brush.horizontalGradient(
                            if (isComplete) listOf(EmeraldVibrant, EmeraldDark, SageAccent)
                            else listOf(EmeraldVibrant, PineAccent, EmeraldLight)
                        )
                    )
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // Top Action Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Pill
                    Surface(
                        color = if (isComplete) EmeraldSafeBg else AmberWarningBg,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isComplete) EmeraldSafe else AmberWarning)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isComplete) "Shield Active • Protected" else "Setup In Progress",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isComplete) EmeraldSafe else AmberWarning
                            )
                        }
                    }

                    // Utility Buttons: Demo & Refresh
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onQuickDemoToggle,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = IndigoPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSimulated) "Reset" else "Demo All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("perm_btn_refresh")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Status",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hero Content: Icon, Title & Subtitle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    if (isComplete) listOf(EmeraldSafe, EmeraldDark)
                                    else listOf(EmeraldPrimary, PineAccent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Security & Permissions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Configure device permissions for $childName to activate real-time distraction protection, focus lockouts, and location safety.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// SLEEK SECURITY STATUS BAR (DYNAMIC PROGRESSION)
// -------------------------------------------------------------------------
@Composable
private fun ModernSecurityStatusBar(status: ChildPermissionStatus) {
    val progressColor by animateColorAsState(
        targetValue = when {
            status.isFullyProtected -> EmeraldSafe
            status.allCrucialGranted -> IndigoPrimary
            else -> AmberWarning
        },
        label = "progressColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = progressColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Readiness",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${status.grantedCount} of ${status.totalCount} Active • ${(status.progressPercent * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { status.progressPercent },
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

// -------------------------------------------------------------------------
// MODERN PERMISSION STEP CARD (CLEAN, INFORMATIVE, ZERO CARTOONS)
// -------------------------------------------------------------------------
@Composable
private fun ModernPermissionStepCard(
    stepIndex: String,
    badgeLabel: String,
    title: String,
    permissionSystemName: String,
    icon: ImageVector,
    accentColor: Color,
    isGranted: Boolean,
    description: String,
    privacyNote: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isGranted) 1.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGranted) Modifier.border(1.5.dp, EmeraldSafe.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Icon, Index, Category Badge, Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek Icon Container
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isGranted) EmeraldSafe.copy(alpha = 0.12f)
                            else accentColor.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) EmeraldSafe else accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (isGranted) EmeraldSafe.copy(alpha = 0.12f) else accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Step $stepIndex",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGranted) EmeraldSafe else accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = permissionSystemName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Informative Description
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Privacy Reassurance Tag
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = privacyNote,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action / Status Footer
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
                        text = if (isGranted) "Permission Granted" else "Action Required",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGranted) EmeraldSafe else AmberWarning
                    )
                }

                if (!isGranted) {
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag(testTag)
                    ) {
                        Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        color = EmeraldSafeBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
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
