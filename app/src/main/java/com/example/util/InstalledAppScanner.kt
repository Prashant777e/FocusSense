package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.model.InstalledAppEntity

object InstalledAppScanner {

    fun getInstalledLauncherApps(context: Context, childId: String): List<InstalledAppEntity> {
        val pm = context.packageManager
        val result = mutableListOf<InstalledAppEntity>()
        val seenPackages = mutableSetOf<String>()

        // 1. Query Launcher Activities via intent
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || seenPackages.contains(pkg)) {
                continue
            }
            seenPackages.add(pkg)

            val appName = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast('.')
            }

            val isSystem = (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val category = categorizeApp(pkg, appName)

            result.add(
                InstalledAppEntity(
                    id = "${childId}_$pkg",
                    childId = childId,
                    packageName = pkg,
                    appName = appName,
                    category = category,
                    isBlocked = false,
                    isSystemApp = isSystem,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }

        // 2. Query all installed packages directly to catch any launchable applications
        try {
            val allPackages = pm.getInstalledPackages(0)
            for (pkgInfo in allPackages) {
                val pkg = pkgInfo.packageName
                if (pkg == context.packageName || seenPackages.contains(pkg)) {
                    continue
                }
                // Only include if it has a launch intent (i.e. user can launch it)
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    seenPackages.add(pkg)
                    val appInfo = pkgInfo.applicationInfo
                    val appName = try {
                        appInfo?.loadLabel(pm)?.toString() ?: pkg.substringAfterLast('.')
                    } catch (_: Exception) {
                        pkg.substringAfterLast('.')
                    }
                    val isSystem = appInfo != null && ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                    val category = categorizeApp(pkg, appName)

                    result.add(
                        InstalledAppEntity(
                            id = "${childId}_$pkg",
                            childId = childId,
                            packageName = pkg,
                            appName = appName,
                            category = category,
                            isBlocked = false,
                            isSystemApp = isSystem,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: If on an emulator where package visibility might be restricted, only include packages that ACTUALLY exist on this device
        if (result.isEmpty()) {
            val commonPackages = listOf(
                "com.android.chrome" to "Google Chrome",
                "com.google.android.youtube" to "YouTube",
                "com.android.settings" to "Settings",
                "com.google.android.apps.messaging" to "Messages",
                "com.android.calculator2" to "Calculator",
                "com.android.camera2" to "Camera",
                "com.android.deskclock" to "Clock",
                "com.android.contacts" to "Contacts"
            )
            for ((pkg, label) in commonPackages) {
                // Verify whether the package actually exists on this device
                val exists = try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
                if (exists && !seenPackages.contains(pkg)) {
                    seenPackages.add(pkg)
                    result.add(
                        InstalledAppEntity(
                            id = "${childId}_$pkg",
                            childId = childId,
                            packageName = pkg,
                            appName = label,
                            category = categorizeApp(pkg, label),
                            isBlocked = false,
                            isSystemApp = true,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        return result.sortedBy { it.appName.lowercase() }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun categorizeApp(pkg: String, name: String): String {
        val lowerPkg = pkg.lowercase()
        val lowerName = name.lowercase()
        return when {
            lowerPkg.contains("youtube") || lowerPkg.contains("netflix") || lowerPkg.contains("twitch") ||
            lowerPkg.contains("disney") || lowerPkg.contains("primevideo") || lowerPkg.contains("hulu") ||
            lowerPkg.contains("spotify") || lowerPkg.contains("music") || lowerPkg.contains("hotstar") -> "Streaming & Video"

            lowerPkg.contains("instagram") || lowerPkg.contains("tiktok") || lowerPkg.contains("musically") ||
            lowerPkg.contains("snapchat") || lowerPkg.contains("facebook") || lowerPkg.contains("twitter") ||
            lowerPkg.contains("reddit") || lowerPkg.contains("threads") || lowerPkg.contains("bereal") -> "Social Media"

            lowerPkg.contains("discord") || lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") ||
            lowerPkg.contains("messenger") || lowerPkg.contains("signal") || lowerPkg.contains("wechat") ||
            lowerPkg.contains("viber") || lowerPkg.contains("line") -> "Messaging & Chat"

            lowerPkg.contains("roblox") || lowerPkg.contains("minecraft") || lowerPkg.contains("game") ||
            lowerPkg.contains("pubg") || lowerPkg.contains("supercell") || lowerPkg.contains("epicgames") ||
            lowerPkg.contains("fortnite") || lowerName.contains("game") || lowerPkg.contains("clash") -> "Gaming"

            lowerPkg.contains("chrome") || lowerPkg.contains("firefox") || lowerPkg.contains("browser") ||
            lowerPkg.contains("opera") || lowerPkg.contains("brave") || lowerPkg.contains("edge") -> "Web Browser"

            lowerPkg.contains("settings") || lowerPkg.contains("system") -> "System Controls"

            else -> "General App"
        }
    }
}
