package com.example.ui.screens.child

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ActivityLogEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.RestrictedAppInfo
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.data.repository.FocusSenseRepository
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PurpleAccent
import com.example.util.ChildPermissionManager
import com.example.util.InstalledAppScanner
import java.util.Locale

@Composable
fun ChildDashboardScreen(
    currentChild: UserEntity?,
    scheduleRules: List<ScheduleRuleEntity>,
    latestLocation: LocationPointEntity?,
    isAnalyzing: Boolean,
    lastSimulatedLog: ActivityLogEntity?,
    pomodoroState: FocusSenseRepository.PomodoroState? = null,
    onStartPomodoro: (pkg: String, name: String, mins: Int) -> Unit = { _, _, _ -> },
    onPausePomodoro: () -> Unit = {},
    onResumePomodoro: () -> Unit = {},
    onStopPomodoro: () -> Unit = {},
    onSimulateContentExtraction: (appName: String, title: String, text: String, pkg: String) -> Unit = { _, _, _, _ -> },
    onSimulateRestrictedLaunch: (pkg: String, name: String) -> Unit = { _, _ -> },
    onDismissSimulatedLog: () -> Unit = {},
    onRequestParentUnlock: () -> Unit,
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

    // Default to Permissions/Safety (Tab 2) if core permissions are missing
    var selectedTab by remember {
        mutableIntStateOf(if (!permissionStatus.allCrucialGranted) 2 else 0)
    }

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Schedule") },
                    label = { Text("Schedule", fontSize = 10.sp) },
                    modifier = Modifier.testTag("child_tab_schedule")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Focus") },
                    label = { Text("Focus", fontSize = 10.sp) },
                    modifier = Modifier.testTag("child_tab_focus")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Box {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Safety",
                                tint = if (permissionStatus.isFullyProtected) EmeraldSafe else CoralDanger
                            )
                            if (!permissionStatus.isFullyProtected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CoralDanger)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    },
                    label = {
                        Text(
                            text = if (permissionStatus.isFullyProtected) "Safety" else "Setup",
                            fontSize = 10.sp,
                            fontWeight = if (!permissionStatus.isFullyProtected) FontWeight.Bold else FontWeight.Normal,
                            color = if (permissionStatus.isFullyProtected) EmeraldSafe else CoralDanger
                        )
                    },
                    modifier = Modifier.testTag("child_tab_permissions")
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onRequestParentUnlock,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Parent Unlock",
                            tint = PurpleAccent
                        )
                    },
                    label = {
                        Text(
                            text = "Exit",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurpleAccent
                        )
                    },
                    modifier = Modifier.testTag("child_tab_switch_account")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Persistent Top Warning Banner if permissions are missing
            if (selectedTab != 2 && !permissionStatus.isFullyProtected) {
                Surface(
                    color = if (permissionStatus.allCrucialGranted) AmberWarningBg else CoralDangerBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTab = 2 }
                        .testTag("child_permission_alert_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (permissionStatus.allCrucialGranted) Icons.Default.Warning else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (permissionStatus.allCrucialGranted) AmberWarning else CoralDanger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Setup Incomplete (${permissionStatus.grantedCount}/${permissionStatus.totalCount} active) - Tap to configure",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (permissionStatus.allCrucialGranted) AmberWarning else CoralDanger
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (permissionStatus.allCrucialGranted) AmberWarning else CoralDanger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> ChildScheduleTab(
                        childName = currentChild?.name ?: "Student",
                        rules = scheduleRules
                    )
                    1 -> ChildFocusShieldTab(
                        rules = scheduleRules,
                        pomodoroState = pomodoroState,
                        onStartPomodoro = onStartPomodoro,
                        onPausePomodoro = onPausePomodoro,
                        onResumePomodoro = onResumePomodoro,
                        onStopPomodoro = onStopPomodoro
                    )
                    2 -> Column(modifier = Modifier.fillMaxSize()) {
                        ChildPermissionWizardScreen(
                            childName = currentChild?.name ?: "Student",
                            onContinueToDashboard = { selectedTab = 0 }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: CHILD SCHEDULE & ACTIVE TIMETABLE
// -------------------------------------------------------------
@Composable
private fun ChildScheduleTab(
    childName: String,
    rules: List<ScheduleRuleEntity>
) {
    var filterCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Homework", "Curfew", "Bedtime", "Education")

    val filteredRules = if (filterCategory == "All") {
        rules
    } else {
        rules.filter { it.category.contains(filterCategory, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Hello, $childName 👋",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Here are your active study timetables and daily curfew rules.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = filterCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { filterCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        if (filteredRules.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No active schedules for '$filterCategory'",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            items(filteredRules) { rule ->
                ChildRuleCard(rule = rule)
            }
        }
    }
}

@Composable
private fun ChildRuleCard(rule: ScheduleRuleEntity) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (rule.isActive) EmeraldSafeBg else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (rule.isActive) Icons.Default.HourglassBottom else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = if (rule.isActive) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = rule.ruleName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${rule.startTime} - ${rule.endTime} • ${rule.dayOfWeek}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Restricted: ${rule.restrictedPackages}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }

            Surface(
                color = if (rule.isActive) EmeraldSafeBg else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (rule.isActive) "Active" else "Paused",
                    color = if (rule.isActive) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: FOCUS SHIELD & POMODORO STUDY TIMER
// -------------------------------------------------------------
@Composable
private fun ChildFocusShieldTab(
    rules: List<ScheduleRuleEntity>,
    pomodoroState: FocusSenseRepository.PomodoroState? = null,
    onStartPomodoro: (pkg: String, name: String, mins: Int) -> Unit = { _, _, _ -> },
    onPausePomodoro: () -> Unit = {},
    onResumePomodoro: () -> Unit = {},
    onStopPomodoro: () -> Unit = {}
) {
    val context = LocalContext.current
    val detectedApps = remember {
        val scanned = InstalledAppScanner.getInstalledLauncherApps(context, "child-default")
        scanned.map {
            RestrictedAppInfo(
                packageName = it.packageName,
                appName = it.appName,
                iconName = "app",
                category = it.category,
                isInstalledOnDevice = true
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Study Focus & Pomodoro",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Boost productivity by locking distractions during study sessions.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ChildPomodoroFocusCard(
                pomodoroState = pomodoroState,
                installedApps = detectedApps,
                onStartPomodoro = onStartPomodoro,
                onPausePomodoro = onPausePomodoro,
                onResumePomodoro = onResumePomodoro,
                onStopPomodoro = onStopPomodoro
            )
        }
    }
}

@Composable
private fun ChildPomodoroFocusCard(
    pomodoroState: FocusSenseRepository.PomodoroState?,
    installedApps: List<RestrictedAppInfo>,
    onStartPomodoro: (pkg: String, name: String, mins: Int) -> Unit,
    onPausePomodoro: () -> Unit,
    onResumePomodoro: () -> Unit,
    onStopPomodoro: () -> Unit
) {
    var selectedMins by remember { mutableIntStateOf(25) }
    var selectedAppPkg by remember { mutableStateOf("*") }
    var selectedAppName by remember { mutableStateOf("All Distracting Apps") }
    var showAppDropdown by remember { mutableStateOf(false) }

    val remainingSecs = pomodoroState?.remainingSeconds ?: (selectedMins * 60L)
    val minsLeft = remainingSecs / 60
    val secsLeft = remainingSecs % 60
    val timerDisplay = String.format(Locale.getDefault(), "%02d:%02d", minsLeft, secsLeft)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CoralDangerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Pomodoro",
                        tint = CoralDanger,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pomodoro Focus Timer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Apps auto-unlock when your study session ends.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Timer Circle Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timerDisplay,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pomodoroState?.isRunning == true) CoralDanger else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (pomodoroState?.isRunning == true) "Focus Session Active • ${pomodoroState.appName} Paused" else "Select Focus Time & Start",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (pomodoroState == null) {
                // Duration Selection Chips
                Text(
                    text = "Select Duration:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val isSelected = selectedMins == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMins = mins },
                            label = { Text("${mins}m") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralDanger,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // App Selector
                Box {
                    OutlinedButton(
                        onClick = { showAppDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Target App: $selectedAppName",
                            fontSize = 13.sp
                        )
                    }

                    DropdownMenu(
                        expanded = showAppDropdown,
                        onDismissRequest = { showAppDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Distracting Apps (*)") },
                            onClick = {
                                selectedAppPkg = "*"
                                selectedAppName = "All Distracting Apps"
                                showAppDropdown = false
                            }
                        )
                        installedApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.appName) },
                                onClick = {
                                    selectedAppPkg = app.packageName
                                    selectedAppName = app.appName
                                    showAppDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onStartPomodoro(selectedAppPkg, selectedAppName, selectedMins)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralDanger),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Pomodoro Focus (${selectedMins}m)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                // Active Pomodoro Controls (Pause / Resume / End Early)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pomodoroState.isRunning) {
                        OutlinedButton(
                            onClick = onPausePomodoro,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause")
                        }
                    } else {
                        Button(
                            onClick = onResumePomodoro,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralDanger),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume")
                        }
                    }

                    OutlinedButton(
                        onClick = onStopPomodoro,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "End")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End Focus")
                    }
                }
            }
        }
    }
}
