package com.example.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.FocusSenseApplication

/**
 * Restarts child background tracking gracefully after device reboot when an authenticated
 * child session and location permission are active.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val app = context.applicationContext as? FocusSenseApplication ?: return
        val prefs = context.getSharedPreferences("focussense_session_prefs", Context.MODE_PRIVATE)
        val savedUserId = prefs.getString("saved_user_id", null)
        val activeUser = app.repository.currentUser.value

        val isChildRole = activeUser?.role.equals("child", ignoreCase = true)
        val childId = activeUser?.userId?.ifBlank { savedUserId.orEmpty() } ?: savedUserId.orEmpty()
        if (!isChildRole || childId.isBlank()) {
            return
        }

        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            Log.i("BootCompletedReceiver", "Skipping location service auto-start: location permission not granted")
            return
        }

        try {
            val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                this.action = LocationTrackingService.ACTION_START
                putExtra(LocationTrackingService.EXTRA_CHILD_ID, childId)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w("BootCompletedReceiver", "Unable to start LocationTrackingService on boot: ${e.message}")
        }
    }
}
