package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.repository.FocusSenseRepository

class FocusSenseApplication : Application() {

    lateinit var repository: FocusSenseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = FocusSenseRepository(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Sentinel Alerts Channel
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "FocusSense Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent notifications regarding flagged vulnerabilities and safety concerns"
                enableVibration(true)
            }

            // Location Beacon Foreground Channel
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION,
                "FocusSense Safety Beacon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location safety tracker for child oversight"
            }

            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(locationChannel)
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "focussense_alerts"
        const val CHANNEL_LOCATION = "focussense_location"

        lateinit var instance: FocusSenseApplication
            private set
    }
}
