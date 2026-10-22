package com.example.util

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.FocusSenseAccessibilityService

data class ChildPermissionStatus(
    val isAccessibilityEnabled: Boolean,
    val isUsageAccessGranted: Boolean,
    val isLocationGranted: Boolean,
    val isBackgroundLocationGranted: Boolean,
    val isBatteryExempted: Boolean,
    val isOverlayGranted: Boolean,
    val isNotificationGranted: Boolean
) {
    val totalCount: Int = 6

    val grantedCount: Int
        get() {
            var count = 0
            if (isAccessibilityEnabled) count++
            if (isUsageAccessGranted) count++
            if (isLocationGranted) count++
            if (isBatteryExempted) count++
            if (isOverlayGranted) count++
            if (isNotificationGranted) count++
            return count
        }

    val isFullyProtected: Boolean
        get() = grantedCount == totalCount

    val allCrucialGranted: Boolean
        get() = isAccessibilityEnabled && isUsageAccessGranted && isLocationGranted

    val progressPercent: Float
        get() = (grantedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
}

object ChildPermissionManager {

    fun checkAllPermissions(context: Context): ChildPermissionStatus {
        return ChildPermissionStatus(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context),
            isUsageAccessGranted = isUsageAccessGranted(context),
            isLocationGranted = isLocationPermissionGranted(context),
            isBackgroundLocationGranted = isBackgroundLocationGranted(context),
            isBatteryExempted = isBatteryOptimizationIgnored(context),
            isOverlayGranted = canDrawOverlays(context),
            isNotificationGranted = isNotificationPermissionGranted(context)
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (FocusSenseAccessibilityService.isServiceRunning.value) {
            return true
        }
        val expectedService = ComponentName(context, FocusSenseAccessibilityService::class.java).flattenToString()
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val isGlobalAccessibilityOn = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            isGlobalAccessibilityOn && (enabledServices.contains(expectedService) || enabledServices.contains(context.packageName))
        } catch (e: Exception) {
            false
        }
    }

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isLocationPermissionGranted(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isBackgroundLocationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Settings Navigation Helpers
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallback)
            } catch (err: Exception) {
                openAppSettings(context)
            }
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallback)
            } catch (err: Exception) {
                openAppSettings(context)
            }
        }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
