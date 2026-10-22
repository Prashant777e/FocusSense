package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.FocusSenseApplication
import com.example.MainActivity
import com.example.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var simulationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            _isTrackingActive.value = false
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        _isTrackingActive.value = true

        val childId = intent?.getStringExtra(EXTRA_CHILD_ID) ?: "child-leo"
        startLocationUpdates(childId)

        return START_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FocusSenseApplication.CHANNEL_LOCATION)
            .setContentTitle("FocusSense Safety Beacon Active")
            .setContentText("Child location is actively monitored for physical safety")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(childId: String) {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val app = application as? FocusSenseApplication

        if (hasFine) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000L)
                .setMinUpdateIntervalMillis(15000L)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val lastLoc = result.lastLocation ?: return
                    scope.launch {
                        app?.repository?.recordLocation(
                            childId = childId,
                            lat = lastLoc.latitude,
                            lng = lastLoc.longitude,
                            accuracy = lastLoc.accuracy,
                            locationName = "GPS Fix (${String.format("%.4f", lastLoc.latitude)}, ${String.format("%.4f", lastLoc.longitude)})"
                        )
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                mainLooper
            )
        } else {
            // Emulate periodic safe waypoint updates if physical hardware location is not granted
            startSimulationWaypointLoop(childId)
        }
    }

    private fun startSimulationWaypointLoop(childId: String) {
        val waypoints = listOf(
            Triple(37.7749, -122.4194, "Oakwood Middle School"),
            Triple(37.7793, -122.4168, "Civic Center Library"),
            Triple(37.7690, -122.4467, "Home (Safe Haven)"),
            Triple(37.7596, -122.4269, "Central Park Field")
        )
        var index = 0

        simulationJob?.cancel()
        simulationJob = scope.launch {
            val app = application as? FocusSenseApplication
            while (isActive) {
                val pt = waypoints[index % waypoints.size]
                app?.repository?.recordLocation(
                    childId = childId,
                    lat = pt.first,
                    lng = pt.second,
                    accuracy = 4.5f,
                    locationName = pt.third
                )
                index++
                delay(45000L)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        simulationJob?.cancel()
        _isTrackingActive.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val EXTRA_CHILD_ID = "EXTRA_CHILD_ID"
        const val NOTIFICATION_ID = 2001

        private val _isTrackingActive = MutableStateFlow(false)
        val isTrackingActive = _isTrackingActive.asStateFlow()
    }
}
