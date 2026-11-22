package com.example.ui.dialogs

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ActivityLogEntity
import com.example.data.model.RestrictedAppInfo
import com.example.data.model.ScheduleRuleEntity
import com.example.ui.components.ThreatCategoryBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ParentPinDialog(
    onDismiss: () -> Unit,
    onPinVerified: () -> Unit,
    expectedPin: String = "1234"
) {
    var enteredPin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Lock",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Parental Security Lock",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Enter Parent 4-digit PIN to exit child mode or access parental controls.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 6) {
                            enteredPin = it
                            hasError = false
                        }
                    },
                    label = { Text("Parent PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = hasError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parent_pin_input")
                )

                if (hasError) {
                    Text(
                        text = "Incorrect Security PIN. Please enter your parent security PIN.",
                        color = CoralDanger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("pin_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (enteredPin == expectedPin) {
                                onPinVerified()
                            } else {
                                hasError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.testTag("pin_confirm_button")
                    ) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScheduleRuleDialog(
    existingRule: ScheduleRuleEntity? = null,
    installedApps: List<com.example.data.model.InstalledAppEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveRule: (name: String, category: String, start: String, end: String, days: String, apps: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ruleName by remember { mutableStateOf(existingRule?.ruleName ?: "Afternoon Study Focus") }
    var category by remember { mutableStateOf(existingRule?.category ?: "Homework") }
    var startTime by remember { mutableStateOf(existingRule?.startTime ?: "15:30") }
    var endTime by remember { mutableStateOf(existingRule?.endTime ?: "17:30") }
    var selectedDays by remember {
        mutableStateOf(existingRule?.dayOfWeek ?: "Mon,Tue,Wed,Thu,Fri,Sat,Sun")
    }
    var appSearchQuery by remember { mutableStateOf("") }
    var isAllDay by remember {
        mutableStateOf(existingRule?.startTime == "00:00" && (existingRule?.endTime == "23:59" || existingRule?.endTime == "00:00"))
    }

    val existingRawTokens = remember(existingRule) {
        existingRule?.restrictedPackages?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }
    var youtubeStudyMode by remember {
        mutableStateOf(
            existingRawTokens.any { com.example.service.YouTubeContentGuard.isYouTubeWhitelistToken(it) }
        )
    }
    var youtubeAllowedChannelsText by remember {
        mutableStateOf(
            existingRule?.let {
                com.example.service.YouTubeContentGuard.parseRuleWhitelistChannels(it.restrictedPackages)
                    .joinToString(", ")
            } ?: ""
        )
    }

    val availableApps: List<RestrictedAppInfo> = remember(installedApps) {
        if (installedApps.isNotEmpty()) {
            installedApps.map { app ->
                RestrictedAppInfo(
                    packageName = app.packageName,
                    appName = app.appName,
                    iconName = "app",
                    category = app.category,
                    isInstalledOnDevice = true
                )
            }
        } else {
            // Never scan the Parent device's local packages; use common restricted presets until Child syncs
            com.example.data.model.PREDEFINED_RESTRICTED_APPS
        }
    }

    val selectedPackages = remember {
        mutableStateListOf<String>().apply {
            val initial = existingRawTokens.filterNot {
                com.example.service.YouTubeContentGuard.isYouTubeWhitelistToken(it)
            }
            addAll(initial)
        }
    }

    val filteredAvailableApps: List<RestrictedAppInfo> = remember(availableApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) availableApps else {
            availableApps.filter { app ->
                app.appName.contains(appSearchQuery, ignoreCase = true) ||
                app.packageName.contains(appSearchQuery, ignoreCase = true) ||
                app.category.contains(appSearchQuery, ignoreCase = true)
            }
        }
    }

    val categories = listOf("Homework", "Study", "YouTube Study", "Bedtime", "Outdoor", "Free Time")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existingRule == null) "New Schedule Curfew" else "Edit Schedule Rule",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Curfew Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_title_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Category & Focus Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Curfew Time Controls & Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Curfew Window",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val cal = java.util.Calendar.getInstance()
                                val curHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                val curMin = cal.get(java.util.Calendar.MINUTE)
                                val startH = if (curMin < 10) (curHour - 1 + 24) % 24 else curHour
                                val startM = if (curMin < 10) 50 else curMin - 10
                                val endH = (curHour + 2) % 24
                                startTime = String.format("%02d:%02d", startH, startM)
                                endTime = String.format("%02d:%02d", endH, curMin)
                                isAllDay = false
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Active Now", fontSize = 11.sp, color = IndigoPrimary)
                        }

                        TextButton(
                            onClick = {
                                isAllDay = !isAllDay
                                if (isAllDay) {
                                    startTime = "00:00"
                                    endTime = "23:59"
                                } else {
                                    startTime = "15:30"
                                    endTime = "17:30"
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(if (isAllDay) "24/7 (On)" else "24/7 (All Day)", fontSize = 11.sp, color = if (isAllDay) EmeraldSafe else IndigoPrimary)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {
                            startTime = it
                            isAllDay = false
                        },
                        label = { Text("Start (HH:mm)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_start_time_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {
                            endTime = it
                            isAllDay = false
                        },
                        label = { Text("End (HH:mm)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_end_time_input")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Days",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { selectedDays = "Mon,Tue,Wed,Thu,Fri,Sat,Sun" },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Daily", fontSize = 10.sp)
                        }
                        TextButton(
                            onClick = { selectedDays = "Mon,Tue,Wed,Thu,Fri" },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Weekdays", fontSize = 10.sp)
                        }
                        TextButton(
                            onClick = { selectedDays = "Sat,Sun" },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Weekends", fontSize = 10.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = selectedDays,
                    onValueChange = { selectedDays = it },
                    label = { Text("Days (e.g. Mon,Tue,Wed or Daily)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // YouTube Study Whitelist Mode (Allows native YouTube ONLY for study channels & blocks Shorts)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (youtubeStudyMode) EmeraldSafe.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = if (youtubeStudyMode) androidx.compose.foundation.BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.5f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            youtubeStudyMode = !youtubeStudyMode
                            if (youtubeStudyMode) {
                                selectedPackages.removeAll { it.equals("com.google.android.youtube", ignoreCase = true) }
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = if (youtubeStudyMode) EmeraldSafe else IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "YouTube Study Channels Only",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Allows YouTube app only for approved channels; blocks Shorts & other channels.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                            Switch(
                                checked = youtubeStudyMode,
                                onCheckedChange = { enabled ->
                                    youtubeStudyMode = enabled
                                    if (enabled) {
                                        selectedPackages.removeAll { it.equals("com.google.android.youtube", ignoreCase = true) }
                                    }
                                },
                                modifier = Modifier.testTag("youtube_study_whitelist_switch")
                            )
                        }

                        if (youtubeStudyMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = youtubeAllowedChannelsText,
                                onValueChange = { youtubeAllowedChannelsText = it },
                                label = { Text("Allowed Channels (optional, comma-separated)", fontSize = 11.sp) },
                                placeholder = { Text("e.g. take U forward, Khan Academy", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("youtube_whitelist_channels_input")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val selectedCount = selectedPackages.size
                val totalCount = availableApps.size

                Text(
                    text = "Apps to Block on Child Phone ($selectedCount/$totalCount selected)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Real apps detected on child device. When active, opening these apps shows the restriction lock screen.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Search Filter
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search installed apps...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            selectedPackages.clear()
                            selectedPackages.addAll(availableApps.map { it.packageName })
                        }
                    ) {
                        Text("Select All ($totalCount)", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedPackages.clear()
                        }
                    ) {
                        Text("Deselect All", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (availableApps.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No installed apps retrieved yet. Open the app on the child phone to sync packages, or use 'Select All' for a general curfew.",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (app in filteredAvailableApps) {
                            val isChecked = selectedPackages.contains(app.packageName)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChecked) CoralDangerBg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedPackages.add(app.packageName)
                                            else selectedPackages.remove(app.packageName)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (isChecked) CoralDanger else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${app.category} • ${app.packageName}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (ruleName.isNotBlank()) {
                                val finalList = selectedPackages.toMutableList()
                                if (youtubeStudyMode) {
                                    finalList.removeAll { it.equals("com.google.android.youtube", ignoreCase = true) }
                                    val cleanChannels = youtubeAllowedChannelsText
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                    val ytToken = if (cleanChannels.isNotEmpty()) {
                                        "com.google.android.youtube:whitelist:${cleanChannels.joinToString("|")}"
                                    } else {
                                        "com.google.android.youtube:whitelist"
                                    }
                                    finalList.add(ytToken)
                                }
                                val packagesToSave = if (finalList.isNotEmpty()) {
                                    finalList.joinToString(",")
                                } else {
                                    // If no apps selected, default to '*' (all non-essential apps)
                                    "*"
                                }
                                onSaveRule(
                                    ruleName,
                                    category,
                                    startTime,
                                    endTime,
                                    selectedDays,
                                    packagesToSave
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.testTag("save_rule_button")
                    ) {
                        Text("Save Rule")
                    }
                }
            }
        }
    }
}

@Composable
fun LogDetailDialog(
    log: ActivityLogEntity,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onRemove: () -> Unit,
    isParent: Boolean
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(log.recordedAt))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = log.appName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = dateStr,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Threat Banner
                if (log.isFlagged) {
                    ThreatCategoryBadge(
                        category = log.threatCategory,
                        confidence = log.confidenceScore
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldSafe.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Verified Safe Content",
                            color = EmeraldSafe,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Context / Window Title",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = log.contentTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Extracted Text (On-Device Scraping)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log.extractedText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "AI Threat Evaluation Rationale",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndigoPrimary
                )
                Text(
                    text = log.aiAnalysisSummary.ifEmpty { "Evaluated by MobileBERT sentiment & vulnerability heuristics." },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Zero Data-Loss Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sync Status: ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (log.isSynced) "Synced to Cloud DB" else "Stored in Local Room SQLite (Zero Data-Loss)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (log.isSynced) EmeraldSafe else AmberWarning
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (isParent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onRemove,
                            colors = ButtonDefaults.textButtonColors(contentColor = CoralDanger),
                            modifier = Modifier.testTag("remove_vulnerable_log_button")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove Info")
                        }

                        if (!log.isAcknowledged && log.isFlagged) {
                            Button(
                                onClick = onAcknowledge,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                modifier = Modifier.testTag("acknowledge_log_button")
                            ) {
                                Icon(imageVector = Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Acknowledge")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateChildAccountDialog(
    onDismiss: () -> Unit,
    onCreateChild: (username: String, password: String, displayName: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Child Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Assign a username and password for your child to log in on their phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Child Display Name (e.g. Alex)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_display_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.lowercase().trim() },
                    label = { Text("Username / Handle (e.g. alex_c)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_username_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_password_input")
                )

                if (!errorMessage.isNull_or_blank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotBlank() && password.isNotBlank() && displayName.isNotBlank()) {
                        onCreateChild(username, password, displayName)
                    }
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank() && displayName.isNotBlank(),
                modifier = Modifier.testTag("submit_create_child_button")
            ) {
                Text(if (isLoading) "Creating..." else "Create Account")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_create_child_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()

