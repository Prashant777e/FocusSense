package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserEntity
import com.example.data.repository.SentinelEvent
import com.example.ui.components.AppRestrictionOverlay
import com.example.ui.components.FocusSenseTopBar
import com.example.ui.dialogs.ParentPinDialog
import com.example.ui.screens.auth.RoleSelectionAuthScreen
import com.example.ui.screens.child.ChildDashboardScreen
import com.example.ui.screens.parent.ParentDashboardScreen
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Navy800
import com.example.ui.theme.PurpleAccent
import com.example.ui.viewmodel.FocusSenseViewModel
import com.example.ui.viewmodel.FocusSenseViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FocusSenseViewModel by viewModels {
        FocusSenseViewModelFactory((application as FocusSenseApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                FocusSenseApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FocusSenseApp(viewModel: FocusSenseViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isSessionReady by viewModel.isSessionReady.collectAsStateWithLifecycle()

    if (!isSessionReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy800),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(IndigoPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "FocusSense",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "FocusSense",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = EmeraldSafe, modifier = Modifier.size(28.dp))
            }
        }
        return
    }

    // 1. Initial State: If user is not yet logged in / onboarded, show Role Selection & Auth
    if (currentUser == null) {
        RoleSelectionAuthScreen(viewModel = viewModel)
        return
    }

    val selectedChildId by viewModel.selectedChildId.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val childrenUsers by viewModel.childrenUsers.collectAsStateWithLifecycle()
    val parentUsers by viewModel.parentUsers.collectAsStateWithLifecycle()
    val isNetworkConnected by viewModel.isNetworkConnected.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val unsyncedLogs by viewModel.unsyncedLogsCount.collectAsStateWithLifecycle()
    val unsyncedLocs by viewModel.unsyncedLocationCount.collectAsStateWithLifecycle()

    val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val flaggedAlerts by viewModel.flaggedAlerts.collectAsStateWithLifecycle()
    val scheduleRules by viewModel.scheduleRules.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val locationHistory by viewModel.locationHistory.collectAsStateWithLifecycle()
    val allDevices by viewModel.allDevices.collectAsStateWithLifecycle()

    val isAnalyzing by viewModel.isAnalyzingContent.collectAsStateWithLifecycle()
    val lastSimulatedLog by viewModel.lastSimulatedLog.collectAsStateWithLifecycle()
    val activeRestrictionOverlay by viewModel.activeRestrictionOverlay.collectAsStateWithLifecycle()
    val pomodoroState by viewModel.pomodoroState.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(false) }
    var showProfileSwitchDialog by remember { mutableStateOf(false) }
    var pendingSwitchUser by remember { mutableStateOf<UserEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Initial scan of installed apps and schedule sync on startup + background refresh loop
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshInstalledApps(context)
            viewModel.syncSchedules()
            delay(20000)
        }
    }

    LaunchedEffect(selectedChildId) {
        if (selectedChildId.isNotBlank()) {
            viewModel.refreshInstalledApps(context)
            viewModel.syncSchedules()
        }
    }

    // Listen to real-time sentinel notifications & alerts
    LaunchedEffect(Unit) {
        viewModel.eventStream.collectLatest { event ->
            when (event) {
                is SentinelEvent.ContentFlagged -> {
                    snackbarHostState.showSnackbar("⚠️ Threat Flagged: ${event.threatResult.threatCategory} in ${event.log.appName}")
                    sendParentSystemNotification(
                        context = context,
                        title = "⚠️ Child Threat Alert: ${event.threatResult.threatCategory}",
                        message = "${event.log.appName}: \"${event.log.extractedText.take(100)}\""
                    )
                }
                is SentinelEvent.SyncCompleted -> {
                    snackbarHostState.showSnackbar("✓ Zero Data-Loss sync complete: data sent to server.")
                }
                else -> {}
            }
        }
    }

    val selectedChild = childrenUsers.find { it.userId == selectedChildId } ?: childrenUsers.firstOrNull()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FocusSenseTopBar(
                currentUser = currentUser,
                isNetworkConnected = isNetworkConnected,
                isSyncing = isSyncing,
                unsyncedCount = unsyncedLogs + unsyncedLocs,
                children = childrenUsers,
                selectedChildId = selectedChildId,
                onSelectChild = { viewModel.selectChild(it) },
                onToggleNetwork = { viewModel.toggleNetwork(it) },
                onSyncNow = { viewModel.syncData() },
                onSwitchRoleRequested = {
                    if (currentUser?.role == "child") {
                        // Tamper prevention: require Parent PIN before exiting child mode
                        showPinDialog = true
                    } else {
                        showProfileSwitchDialog = true
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentUser?.role == "parent") {
                ParentDashboardScreen(
                    selectedChild = selectedChild,
                    activityLogs = activityLogs,
                    flaggedAlerts = flaggedAlerts,
                    scheduleRules = scheduleRules,
                    latestLocation = latestLocation,
                    locationHistory = locationHistory,
                    devices = allDevices,
                    unsyncedCount = unsyncedLogs + unsyncedLocs,
                    onAcknowledgeAlert = { viewModel.acknowledgeAlert(it) },
                    onRemoveLog = { viewModel.removeLog(it) },
                    onAddRule = { name, category, start, end, days, apps ->
                        viewModel.addScheduleRule(name, category, start, end, days, apps)
                    },
                    onToggleRule = { ruleId, active -> viewModel.toggleRuleActive(ruleId, active) },
                    onDeleteRule = { viewModel.deleteScheduleRule(it) },
                    onManualLocationPing = { lat, lng, name ->
                        viewModel.recordManualLocation(lat, lng, name)
                    },
                    serverUrl = serverUrl,
                    onUpdateServerUrl = { viewModel.updateServerUrl(it) },
                    onTestServer = { viewModel.testServerConnection() },
                    onSyncNow = { viewModel.syncData() },
                    installedApps = installedApps,
                    onToggleAppBlock = { pkg, isBlocked -> viewModel.toggleAppBlock(pkg, isBlocked) },
                    onRefreshInstalledApps = { viewModel.refreshInstalledApps(context) },
                    onSwitchAccountRequested = { showProfileSwitchDialog = true }
                )
            } else {
                ChildDashboardScreen(
                    currentChild = currentUser,
                    scheduleRules = scheduleRules,
                    latestLocation = latestLocation,
                    isAnalyzing = isAnalyzing,
                    lastSimulatedLog = lastSimulatedLog,
                    pomodoroState = pomodoroState,
                    onStartPomodoro = { pkg, name, mins ->
                        viewModel.startPomodoroFocus(pkg, name, mins)
                    },
                    onPausePomodoro = { viewModel.pausePomodoroFocus() },
                    onResumePomodoro = { viewModel.resumePomodoroFocus() },
                    onStopPomodoro = { viewModel.stopPomodoroFocus() },
                    onSimulateContentExtraction = { app, title, text, pkg ->
                        viewModel.simulateContentExtraction(app, title, text, pkg)
                    },
                    onSimulateRestrictedLaunch = { pkg, name ->
                        viewModel.simulateRestrictedAppLaunch(pkg, name)
                    },
                    onDismissSimulatedLog = { viewModel.dismissLastSimulatedLog() },
                    onRequestParentUnlock = { showPinDialog = true }
                )
            }

            // Interactive Full-Screen App Restriction Overlay
            activeRestrictionOverlay?.let { blockInfo ->
                AppRestrictionOverlay(
                    blockInfo = blockInfo,
                    onDismiss = { viewModel.dismissRestrictionOverlay() },
                    onParentOverrideRequested = {
                        viewModel.dismissRestrictionOverlay()
                        showPinDialog = true
                    }
                )
            }
        }
    }

    // Parent PIN Verification Dialog
    if (showPinDialog) {
        val parentPin = parentUsers.firstOrNull()?.pin?.takeIf { it.isNotBlank() } ?: "1234"
        ParentPinDialog(
            onDismiss = { showPinDialog = false },
            expectedPin = parentPin,
            onPinVerified = {
                showPinDialog = false
                if (pendingSwitchUser != null) {
                    viewModel.switchUser(pendingSwitchUser!!)
                    pendingSwitchUser = null
                } else {
                    showProfileSwitchDialog = true
                }
            }
        )
    }

    // Role / Profile Switcher Modal
    if (showProfileSwitchDialog) {
        Dialog(onDismissRequest = { showProfileSwitchDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device & Account Options",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showProfileSwitchDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    if (allUsers.isNotEmpty()) {
                        Text(
                            text = "Switch active profile on this device:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        allUsers.forEach { user ->
                            val isCurrent = user.userId == currentUser?.userId
                            val roleColor = if (user.role == "parent") PurpleAccent else EmeraldSafe

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) IndigoPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        showProfileSwitchDialog = false
                                        viewModel.switchUser(user)
                                    }
                                    .testTag("profile_item_${user.userId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(roleColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (user.role == "parent") Icons.Default.Person else Icons.Default.Face,
                                                contentDescription = null,
                                                tint = roleColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = user.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${user.role.uppercase()} • ${user.email}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isCurrent) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = IndigoPrimary
                                        ) {
                                            Text(
                                                text = "Active",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign Out Button
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            showProfileSwitchDialog = false
                            viewModel.signOut()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_signout_button"),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Sign Out / Switch Device Role", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun sendParentSystemNotification(context: Context, title: String, message: String) {
    try {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FocusSenseApplication.CHANNEL_ALERTS,
                "FocusSense Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager?.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FocusSenseApplication.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager?.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
