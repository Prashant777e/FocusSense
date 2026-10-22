package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.data.repository.ActiveAppBlockInfo
import com.example.data.repository.FocusSenseRepository
import com.example.data.repository.SentinelEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FocusSenseViewModel(
    private val repository: FocusSenseRepository
) : ViewModel() {

    // Current User Session
    val currentUser: StateFlow<UserEntity?> = repository.currentUser
    val isSessionReady: StateFlow<Boolean> = repository.isSessionReady
    val selectedChildId: StateFlow<String> = repository.selectedChildId
    val isNetworkConnected: StateFlow<Boolean> = repository.isNetworkConnected
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val serverUrl: StateFlow<String> = repository.serverUrl
    val eventStream = repository.eventStream

    // Pomodoro Child Focus Lock
    val pomodoroState = repository.pomodoroState

    fun startPomodoroFocus(packageName: String, appName: String, durationMinutes: Int) {
        repository.startPomodoro(packageName, appName, durationMinutes)
    }

    fun pausePomodoroFocus() {
        repository.pausePomodoro()
    }

    fun resumePomodoroFocus() {
        repository.resumePomodoro()
    }

    fun stopPomodoroFocus() {
        repository.stopPomodoro()
    }

    fun updateServerUrl(url: String) {
        repository.updateServerUrl(url)
    }

    suspend fun testServerConnection(): Pair<Boolean, String> {
        return repository.testServerConnection()
    }

    val familyGroup: StateFlow<FamilyGroupEntity?> = repository.familyGroup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val childrenUsers: StateFlow<List<UserEntity>> = repository.childrenUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parentUsers: StateFlow<List<UserEntity>> = repository.parentUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDevices: StateFlow<List<DeviceEntity>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unsyncedLogsCount: StateFlow<Int> = repository.unsyncedLogsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedLocationCount: StateFlow<Int> = repository.unsyncedLocationCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Dynamic child-dependent flows
    val activityLogs: StateFlow<List<ActivityLogEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getLogsForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flaggedAlerts: StateFlow<List<ActivityLogEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getFlaggedLogsForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleRules: StateFlow<List<ScheduleRuleEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getRulesForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedApps: StateFlow<List<com.example.data.model.InstalledAppEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getInstalledApps(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestLocation: StateFlow<LocationPointEntity?> = selectedChildId
        .flatMapLatest { childId -> repository.getLatestLocationForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val locationHistory: StateFlow<List<LocationPointEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getLocationHistoryForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Simulation feedback state
    private val _isAnalyzingContent = MutableStateFlow(false)
    val isAnalyzingContent: StateFlow<Boolean> = _isAnalyzingContent.asStateFlow()

    private val _lastSimulatedLog = MutableStateFlow<ActivityLogEntity?>(null)
    val lastSimulatedLog: StateFlow<ActivityLogEntity?> = _lastSimulatedLog.asStateFlow()

    private val _activeRestrictionOverlay = MutableStateFlow<ActiveAppBlockInfo?>(null)
    val activeRestrictionOverlay: StateFlow<ActiveAppBlockInfo?> = _activeRestrictionOverlay.asStateFlow()

    // Auth & Onboarding State
    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.childrenUsers.collectLatest { children ->
                if (children.isNotEmpty() && repository.selectedChildId.value.isBlank()) {
                    repository.setSelectedChild(children.first().userId)
                }
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun signUpParent(
        name: String,
        familyName: String,
        email: String,
        password: String,
        pin: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.signUpParent(name, familyName, email, password, pin)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Sign up failed. Please try again." }
            )
        }
    }

    fun signInParent(
        email: String,
        password: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.signInParent(email, password)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Sign in failed. Please try again." }
            )
        }
    }

    fun pairChildDevice(
        parentEmail: String,
        parentPassword: String,
        childName: String,
        deviceName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.pairChildDevice(parentEmail, parentPassword, childName, deviceName)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Pairing failed. Please check parent credentials." }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }

    fun launchDemoParent() {
        viewModelScope.launch {
            repository.launchDemoParent()
        }
    }

    fun launchDemoChild() {
        viewModelScope.launch {
            repository.launchDemoChild()
        }
    }

    fun switchUser(user: UserEntity) {
        repository.switchUser(user)
        if (user.role == "child") {
            repository.setSelectedChild(user.userId)
        }
    }

    fun selectChild(childId: String) {
        repository.setSelectedChild(childId)
    }

    fun toggleNetwork(connected: Boolean) {
        repository.toggleNetworkSimulation(connected)
    }

    fun syncData() {
        viewModelScope.launch {
            repository.syncOfflineQueue()
        }
    }

    fun acknowledgeAlert(logId: String) {
        viewModelScope.launch {
            repository.acknowledgeAlert(logId)
        }
    }

    fun removeLog(logId: String) {
        viewModelScope.launch {
            repository.removeVulnerableLog(logId)
        }
    }

    fun addScheduleRule(
        name: String,
        category: String,
        startTime: String,
        endTime: String,
        days: String,
        restrictedPackages: String
    ) {
        viewModelScope.launch {
            val effectiveChildId = selectedChildId.value.ifBlank {
                childrenUsers.value.firstOrNull()?.userId ?: "child-default"
            }
            val rule = ScheduleRuleEntity(
                ruleId = UUID.randomUUID().toString(),
                childId = effectiveChildId,
                ruleName = name,
                category = category,
                startTime = startTime,
                endTime = endTime,
                dayOfWeek = days,
                restrictedPackages = restrictedPackages,
                isActive = true
            )
            repository.addScheduleRule(rule)
        }
    }

    fun updateScheduleRule(rule: ScheduleRuleEntity) {
        viewModelScope.launch {
            repository.updateScheduleRule(rule)
        }
    }

    fun deleteScheduleRule(ruleId: String) {
        viewModelScope.launch {
            repository.deleteScheduleRule(ruleId)
        }
    }

    fun toggleRuleActive(ruleId: String, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleRuleActive(ruleId, isActive)
        }
    }

    fun refreshInstalledApps(context: android.content.Context) {
        viewModelScope.launch {
            repository.scanAndSyncInstalledApps(context, selectedChildId.value)
        }
    }

    fun toggleAppBlock(packageName: String, isBlocked: Boolean) {
        viewModelScope.launch {
            repository.toggleAppBlock(selectedChildId.value, packageName, isBlocked)
        }
    }

    fun syncSchedules() {
        viewModelScope.launch {
            repository.syncSchedulesWithServer(selectedChildId.value)
        }
    }

    fun verifyParentPin(pin: String): Boolean {
        // Parent PIN defaults to "1234"
        val parent = parentUsers.value.firstOrNull()
        val expectedPin = parent?.pin?.takeIf { it.isNotBlank() } ?: "1234"
        return pin == expectedPin
    }

    fun updateParentPin(newPin: String) {
        viewModelScope.launch {
            val parent = parentUsers.value.firstOrNull() ?: return@launch
            repository.updateParentPin(parent.userId, newPin)
        }
    }

    fun simulateContentExtraction(
        appName: String,
        title: String,
        text: String,
        packageName: String = "com.simulation.app"
    ) {
        viewModelScope.launch {
            _isAnalyzingContent.value = true
            val log = repository.processExtractedContent(
                childId = selectedChildId.value,
                packageName = packageName,
                appName = appName,
                contentTitle = title,
                extractedText = text
            )
            _lastSimulatedLog.value = log
            _isAnalyzingContent.value = false
        }
    }

    fun dismissLastSimulatedLog() {
        _lastSimulatedLog.value = null
    }

    fun simulateRestrictedAppLaunch(packageName: String, appName: String) {
        viewModelScope.launch {
            val childId = selectedChildId.value.ifBlank {
                childrenUsers.value.firstOrNull()?.userId ?: "child-default"
            }
            val restriction = repository.checkAppRestriction(childId, packageName)
            if (restriction.isBlocked) {
                _activeRestrictionOverlay.value = restriction.copy(restrictedAppName = appName)
            } else {
                _activeRestrictionOverlay.value = ActiveAppBlockInfo(
                    isBlocked = false,
                    ruleName = "Access Currently Allowed",
                    category = "Active Status: Allowed",
                    endTime = "No Active Curfew Window",
                    restrictedAppName = appName
                )
            }
        }
    }

    fun dismissRestrictionOverlay() {
        _activeRestrictionOverlay.value = null
    }

    fun recordManualLocation(lat: Double, lng: Double, locationName: String) {
        viewModelScope.launch {
            repository.recordLocation(
                childId = selectedChildId.value,
                lat = lat,
                lng = lng,
                accuracy = 4.0f,
                locationName = locationName
            )
        }
    }
}

class FocusSenseViewModelFactory(
    private val repository: FocusSenseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusSenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FocusSenseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
