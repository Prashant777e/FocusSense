# FocusSense — Complete Technical Documentation & Architecture Guide

**FocusSense** is an AI-powered parental oversight, digital wellbeing, and child cognitive-safety Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room SQLite**, **Supabase PostgreSQL REST**, **Android Accessibility & Location Services**, **On-Device Machine Learning (TF-IDF Logistic Regression & TensorFlow Lite Integration)**, and **Google Gemini 3.5 Flash Cloud Reasoning**.

This document provides a complete, 100%-verified engineering guide covering:
1. Android development basics and environment setup required for FocusSense.
2. Exact project configuration, SDK versions, plugins, and active dependencies.
3. Step-by-step construction of the entire project with real code excerpts.
4. Every Android background service and cloud service used, how to declare/access them, and how data flows across devices.
5. Full breakdown of the ML models (`ThreatClassifier` and `CyberbullyingEnsembleClassifier`) and the 3-tier `ThreatEvaluationEngine` cascade.
6. A 100% file-by-file and feature-by-feature inventory of the codebase.

---

## Table of Contents
- [Part 1: Android Basics & Project Setup](#part-1-android-basics--project-setup)
- [Part 2: Exact FocusSense Configuration & Build System](#part-2-exact-focussense-configuration--build-system)
- [Part 3: Step-by-Step Project Construction](#part-3-step-by-step-project-construction)
- [Part 4: Android & Cloud Services — Declaration, Access & Usage](#part-4-android--cloud-services--declaration-access--usage)
- [Part 5: Machine Learning Models & Tri-Tier Cognitive Safety Engine](#part-5-machine-learning-models--tri-tier-cognitive-safety-engine)
- [Part 6: Database Architecture (Room SQLite + Supabase PostgreSQL)](#part-6-database-architecture-room-sqlite--supabase-postgresql)
- [Part 7: Complete Feature-to-File Inventory](#part-7-complete-feature-to-file-inventory)
- [Part 8: Testing, Verification & Troubleshooting](#part-8-testing-verification--troubleshooting)

---

## Part 1: Android Basics & Project Setup

### 1.1 Core Android Concepts Used in FocusSense
Before reading or modifying FocusSense, every developer should understand these core Android building blocks used in this project:

1. **Android Application Class (`android.app.Application`)**:
   - The process-wide entry point initialized before any Activity or Service starts (`FocusSenseApplication.kt`).
   - Used to hold singleton instances (`FocusSenseRepository`) and register OS notification channels (`NotificationChannel`).
2. **Activity (`androidx.activity.ComponentActivity`)**:
   - A single screen host window. FocusSense uses a single main UI host (`MainActivity`) for auth and Parent/Child dashboards, plus a dedicated overlay-style activity (`AppBlockedActivity`) launched whenever a restricted app, curfew, threat, or unapproved YouTube page is intercepted.
3. **Jetpack Compose & Material Design 3**:
   - Declarative Kotlin UI toolkit (`@Composable` functions) replacing legacy XML Views. State updates (`StateFlow` / `collectAsStateWithLifecycle`) automatically recompose only affected UI nodes.
4. **MVVM + Repository Architecture**:
   - **UI (`@Composable`)** observes `StateFlow` exposed by **`FocusSenseViewModel`**, which delegates business logic, cloud syncing, and local storage to **`FocusSenseRepository`**.
5. **Android Services (`Service` and `AccessibilityService`)**:
   - **`LocationTrackingService`** (`android.app.Service`): A foreground service displaying a persistent notification while receiving GPS coordinates via `FusedLocationProviderClient`.
   - **`FocusSenseAccessibilityService`** (`android.accessibilityservice.AccessibilityService`): A system-bound sentinel that inspects active window transitions and on-screen text nodes in real time when enabled by the user in Android Accessibility Settings.
6. **AndroidManifest (`AndroidManifest.xml`)**:
   - Declares `applicationId`-bound components, `<uses-permission>` entries, `<queries>` package-visibility intents, and `<service>` metadata XML.
7. **Room Database (`androidx.room`)**:
   - Compile-time verified SQLite ORM powered by Kotlin Symbol Processing (**KSP**), exposing reactive `Flow<List<...>>` queries and `suspend` DAO mutations.
8. **Kotlin Coroutines & Flow**:
   - Structured concurrency (`Dispatchers.IO`, `Dispatchers.Default`, `viewModelScope`, `StateFlow`, `SharedFlow`) preventing UI-thread blocking during network, ML, and database work.

### 1.2 Prerequisites & How to Set Up an Android Environment
To build and run FocusSense locally:

1. **Install Java Development Kit (JDK 11+)**:
   - `app/build.gradle.kts` compiles with `JavaVersion.VERSION_11` (`sourceCompatibility` & `targetCompatibility`). Android Studio bundles a compatible JetBrains Runtime, or you can set `JAVA_HOME` to JDK 11–21.
2. **Install Android Studio & Android SDK**:
   - Install **Android SDK Platform 36.1** (`compileSdk = release(36) { minorApiLevel = 1 }`), **Android SDK Build-Tools**, and **Platform-Tools (`adb`)**.
   - Minimum supported OS on physical/emulated devices: **Android 7.0 (API 24 / `minSdk = 24`)**, targeting **API 36 (`targetSdk = 36`)**.
   - Configure `ANDROID_SDK_ROOT` in your environment (never commit a machine-specific `local.properties`).
3. **Configure API Secrets via `.env`**:
   - FocusSense uses the **Secrets Gradle Plugin** (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`) configured in `app/build.gradle.kts` to read `.env` (falling back to `.env.example`).
   - Copy `.env.example` to `.env` at the project root and set your Gemini API key:
     ```properties
     GEMINI_API_KEY=your_actual_gemini_api_key
     ```
   - Gradle injects this automatically into `BuildConfig.GEMINI_API_KEY`.
4. **Build & Run Unit Tests from CLI**:
   ```bash
   # Verify debug compilation
   gradle :app:assembleDebug

   # Run local JVM + Robolectric tests (no emulator required)
   gradle :app:testDebugUnitTest
   ```

---

## Part 2: Exact FocusSense Configuration & Build System

### 2.1 Root Project Files
- **`settings.gradle.kts`** (`rootProject.name = "FocusSense"`, includes `:app`):
  Configures `google()`, `mavenCentral()`, and `gradlePluginPortal()` with `FAIL_ON_PROJECT_REPOS`.
- **`build.gradle.kts`** (root):
  Declares five top-level plugins with `apply false`: `android-application`, `kotlin-compose`, `google-devtools-ksp`, `secrets`, and `google-services`.
- **`gradle/libs.versions.toml`**:
  Centralizes all versions in a Gradle Version Catalog:

| Component / Library | Exact Version | Usage in FocusSense |
| :--- | :--- | :--- |
| Android Gradle Plugin (`agp`) | `9.1.1` | Builds `:app` APK & bundles assets |
| Kotlin (`kotlin`) | `2.2.10` | Primary source language + Compose compiler plugin |
| KSP (`googleDevtoolsKsp`) | `2.3.5` | Compile-time Room DAO & Moshi codegen |
| Compose BOM (`composeBom`) | `2024.09.00` | UI, Material 3, Material Icons Extended, Animation |
| Navigation Compose | `2.8.9` | Library included (dashboard tabs use Compose state) |
| Room (`roomRuntime`/`roomKtx`) | `2.7.0` | Offline-first SQLite persistence (`FocusSenseDatabase` v6) |
| Retrofit & Moshi | `2.12.0` / `1.15.2` | Legacy/optional FastAPI REST interface (`FocusSenseApiService`) |
| OkHttp & Logging | `4.10.0` | Supabase REST calls, Gemini API, YouTube search, FastAPI |
| Play Services Location | `21.3.0` | `FusedLocationProviderClient` GPS updates |
| TensorFlow Lite | `2.16.1` | `org.tensorflow.lite.Interpreter` in Model 2 |
| AndroidX Security Crypto | `1.1.0-alpha06` | `EncryptedSharedPreferences` + AES256 `MasterKey` |
| Coil Compose | `2.7.0` | `AsyncImage` channel logos & YouTube search thumbnails |
| Firebase BOM | `34.17.0` | `firebase-ai`, `firebase-appcheck-recaptcha`, `firebase-appcheck-debug` |
| Secrets Gradle Plugin | `2.0.1` | Injects `.env` keys into `BuildConfig` |
| Robolectric & JUnit | `4.16.1` / `4.13.2` | Offline JVM tests with local `.m2` repo & SDK 36 |

### 2.2 App Module Identifiers (`app/build.gradle.kts`)
- **`namespace`**: `"com.example"` (used for `com.example.R` and Kotlin packages).
- **`applicationId`**: `"com.aistudio.focussense.kdqvxz"` (unique Android installation ID).
- **`buildFeatures`**: `compose = true`, `buildConfig = true`.
- **Note on Unused Template Dependencies**: In `app/build.gradle.kts` (lines 95–126), `accompanist-permissions`, `camera-*`, `datastore-preferences`, `firebase-firestore`, `firebase-auth`, `credentials`, and `googleid` are commented out and are **not** used in FocusSense.

---

## Part 3: Step-by-Step Project Construction

This section explains how FocusSense is built from bottom layer to top UI layer.

### Step 1: Initialize Application Context & Notification Channels (`FocusSenseApplication.kt`)
Create a custom `Application` subclass registered in `AndroidManifest.xml` (`android:name=".FocusSenseApplication"`). On startup, it instantiates the singleton `FocusSenseRepository` and registers two notification channels (`CHANNEL_ALERTS = "focussense_alerts"` at `IMPORTANCE_HIGH` and `CHANNEL_LOCATION = "focussense_location"` at `IMPORTANCE_LOW`):

```kotlin
// app/src/main/java/com/example/FocusSenseApplication.kt
class FocusSenseApplication : Application() {
    lateinit var repository: FocusSenseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = FocusSenseRepository(this)
        createNotificationChannels()
    }
}
```

### Step 2: Declare Permissions & Components (`AndroidManifest.xml`)
FocusSense requires network, GPS, foreground service, overlay, notifications, boot, usage stats, and launcher package visibility permissions:
- Normal install-time permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Runtime / special system permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES` plus `<queries>` for `ACTION_MAIN` / `CATEGORY_LAUNCHER`.
- Declared entry points:
  1. `.MainActivity` (`LAUNCHER` activity, `adjustResize`)
  2. `.service.FocusSenseAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`, linked to `@xml/accessibility_service_config`)
  3. `.service.LocationTrackingService` (`foregroundServiceType="location"`)
  4. `.service.BootCompletedReceiver` (`BOOT_COMPLETED` / `QUICKBOOT_POWERON`)
  5. `.ui.screens.blocking.AppBlockedActivity` (`launchMode="singleTop"`, `excludeFromRecents="true"`)

### Step 3: Build Local Encrypted Session & Room SQLite Layer (`data/local/`, `data/model/`)
1. **`SecurePreferences.kt`**: Wraps `EncryptedSharedPreferences` (`AES256_SIV` keys, `AES256_GCM` values) using `MasterKey.KeyScheme.AES256_GCM` with graceful fallback to standard `SharedPreferences`. Stores `jwt_access_token`, `jwt_refresh_token`, `user_id`, `user_role`, `family_id`, `selected_child_id`, and `server_url` (default `"https://parental-controll.onrender.com"`).
2. **`Entities.kt` & `YouTubeModels.kt`**: Defines all 8 Room `@Entity` tables (`FamilyGroupEntity`, `UserEntity`, `DeviceEntity`, `ActivityLogEntity`, `ScheduleRuleEntity`, `LocationPointEntity`, `InstalledAppEntity`, `AllowedYouTubeChannelEntity`).
3. **`FocusSenseDao.kt`**: Defines 8 DAO interfaces (`FamilyGroupDao`, `UserDao`, `DeviceDao`, `ActivityLogDao`, `ScheduleRuleDao`, `LocationDao`, `InstalledAppDao`, `YouTubeChannelDao`).
4. **`FocusSenseDatabase.kt`**: `@Database(..., version = 6, exportSchema = false)` with `MIGRATION_5_6` adding `allowed_youtube_channels` and `fallbackToDestructiveMigration(dropAllTables = true)`.

### Step 4: Build Remote Cloud Clients (`data/remote/`)
1. **`SupabaseClient.kt`**: Uses a dedicated `OkHttpClient` pointing to `SUPABASE_URL = "https://mukkrkppvfxnbbxozvwj.supabase.co/rest/v1"` with `apikey` and `Authorization: Bearer ...` headers (`Prefer: resolution=merge-duplicates,return=representation` for idempotent upserts). Synchronizes users, family groups, devices, schedule rules, activity logs, location logs, installed app inventory, and allowed YouTube channels.
2. **`FocusSenseApiService.kt` + `ApiClient` (`AuthInterceptor.kt`, `TokenAuthenticator.kt`)**: Retrofit + Moshi REST client supporting custom or Render-hosted FastAPI endpoints (`health`, `/api/ai/evaluate`, `/api/ai/config`, `/api/sync`, `/api/schedules`, `/api/devices/{childId}/apps`, `/api/auth/pair`, `/api/auth/refresh`), automatically refreshing expired JWT tokens on HTTP `401`.
3. **`YouTubeSearchService.kt`**: Scrapes real YouTube channel search pages (`https://www.youtube.com/results?search_query=...&sp=EgIQAg%253D%253D`) with a desktop `User-Agent`, parses `ytInitialData` JSON (`channelRenderer` -> handle, title, subscriber count, description, HTTPS logo thumbnail), and also resolves live channel videos and playlists via `YouTubeContentGuard` / HTML `ytInitialData`.

### Step 5: Construct the Central Repository (`FocusSenseRepository.kt`)
`FocusSenseRepository` (1,468 lines) is the single source of truth bridging local Room DAOs, in-memory caches (`cachedActiveRules`, `cachedAllowedChannels`, `instantBlockedPackages`), `ThreatEvaluationEngine`, `InstalledAppScanner`, `SupabaseClient`, and `ApiClient`:
- Automatically pre-seeds demo family (`Sarah` Parent PIN `1234`, `Leo` Child PIN `0000`, `Maya` Child PIN `0000`) via `initializeDemoDataIfNeeded()` on fresh launch.
- Runs a background `init` coroutine loop: refreshes family/rules every `10s`, pushes unsynced logs/locations every `15s`, and (in Parent role) polls paired child logs/location/rules/apps every `20s`.
- Enforces **Role Isolation**: `currentUser.role` must be `"child"` for accessibility interception, threat logging, local app scanning, and GPS uploads; `"parent"` devices never scan their own installed apps as child apps and instead pull the paired child's inventory from Supabase.

### Step 6: Implement Real-Time Monitoring & Guard Services (`service/`)
1. **`FocusSenseAccessibilityService.kt`**: Inspects `AccessibilityEvent`s, executes 0ms synchronous whole-app instant-lock checks, evaluates active curfews/schedules, runs `YouTubeContentGuard` every `350ms` inside YouTube or browsers, filters UI chrome with `ScraperFilterEngine`, and debounces screen text capture every `1500ms` to feed `repository.processExtractedContent(...)`.
2. **`ScraperFilterEngine.kt`**: Filters ignored system/keyboard/launcher/FocusSense packages, strips passwords, system bars, timestamps, and battery UI tokens, extracts embedded URLs, and tags initial threat candidates.
3. **`YouTubeContentGuard.kt`**: Blocks YouTube Shorts players/URLs and unapproved channels across native YouTube (`com.google.android.youtube`, Kids, TV, Vanced/ReVanced/RVX) and Android browsers, while permitting approved study channels via normalized `@handle` / compact slug matching and whitelist rules (`yt_whitelist:...`).
4. **`LocationTrackingService.kt` & `BootCompletedReceiver.kt`**: Foreground GPS tracking service exclusively for `"child"` accounts using 5-minute intervals (`300000L` ms) with fine/coarse hardware location and safe post-reboot resumption when location permissions are granted.

### Step 7: Build the Cascaded ML & AI Engine (`ai/`)
- **`ThreatClassifier.kt`**: Pure Kotlin TF-IDF + Multinomial Logistic Regression classifier (2,804 n-gram features, 8 classes).
- **`CyberbullyingEnsembleClassifier.kt`**: TensorFlow Lite sequence classifier pipeline (`model2/config.json`, `model2/vocab.json`, `model2/labels.json`) with safe fallback when the binary `.tflite` file is not bundled.
- **`ThreatEvaluationEngine.kt`**: Orchestrates Layer 1 Deterministic Rules -> Layer 2 Dual Edge ML -> Layer 3 Cloud Gemini 3.5 Flash / Render FastAPI, producing `ThreatAnalysisResult` with latency `LayerTraceStep`s, 5-axis `cognitiveRiskRadar`, `privacyTransmission`, energy estimate (`energyImpactMicroJoules`), and `restorativeDialogueScript`.

### Step 8: Build Jetpack Compose UI, ViewModel, Theme & Entry Points (`ui/`, `MainActivity.kt`)
- **`FocusSenseViewModel.kt`**: Exposes reactive `StateFlow`s (`currentUser`, `familyMembers`, `scheduleRules`, `flaggedLogs`, `allLogs`, `latestLocation`, `installedApps`, `youtubeChannels`, `isChildLockActive`, `pomodoroSecondsRemaining`, etc.) and handles Pomodoro focus timer ticks, child pairing, role auth, rule CRUD, instant app toggles, YouTube channel search/whitelisting, and GPS pings.
- **`MainActivity.kt`**: Calls `enableEdgeToEdge()`, hosts `FocusSenseApp`, shows splash until `isSessionReady`, routes to `RoleSelectionAuthScreen` when unauthenticated, `ParentDashboardScreen` when `role == "parent"`, or `ChildDashboardScreen` / `ChildPermissionWizardScreen` when `role == "child"`. Also listens to `SentinelEvent`s to show real-time snackbars, high-priority safety notifications, and `AppRestrictionOverlay` banners, plus handles `extra_open_allowed_channels` deep links from `AppBlockedActivity`.

---

## Part 4: Android & Cloud Services — Declaration, Access & Usage

Every service used in FocusSense is documented below with exact file locations, declaration snippets, and access methods.

### 4.1 `FocusSenseAccessibilityService` (Screen Content, App Locker & YouTube Shield)
- **File**: `app/src/main/java/com/example/service/FocusSenseAccessibilityService.kt`
- **Manifest Declaration** (`AndroidManifest.xml`, lines 49–59):
  ```xml
  <service
      android:name=".service.FocusSenseAccessibilityService"
      android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
      android:exported="true">
      <intent-filter>
          <action android:name="android.accessibilityservice.AccessibilityService" />
      </intent-filter>
      <meta-data
          android:name="android.accessibilityservice"
          android:resource="@xml/accessibility_service_config" />
  </service>
  ```
- **XML Capability Config** (`app/src/main/res/xml/accessibility_service_config.xml`):
  Captures `typeWindowStateChanged|typeWindowContentChanged|typeViewTextChanged`, `flagDefault|flagIncludeNotImportantViews|flagRetrieveInteractiveWindows`, `android:canRetrieveWindowContent="true"`, `android:notificationTimeout="100"`.
- **How to Access / Enable It**:
  - Android requires explicit user consent in **Settings > Accessibility > FocusSense**.
  - `ChildPermissionManager.isAccessibilityServiceEnabled(context)` queries `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` for `FocusSenseAccessibilityService`.
  - `ChildPermissionWizardScreen.kt` (and `ChildDashboardScreen.kt`) guides the child through enabling it via `ChildPermissionManager.openAccessibilitySettings(context)`.
  - Inside `onServiceConnected()`, the service flips `_isServiceRunning.value = true` and listens to `app.repository.eventStream` for `SentinelEvent.ContentFlagged` so critical flagged threats immediately trigger `AppBlockedActivity`.
- **How `onAccessibilityEvent` Works**:
  1. Checks `currentUser.role == "child"`; ignores events for parents/logged-out users.
  2. Filters ignored packages (`ScraperFilterEngine.isIgnoredPackage`) — launchers, IME keyboards, system UI, and FocusSense itself.
  3. Performs 0ms synchronous check: `app.repository.isPackageBlockedSync(pkgName)` -> launches `AppBlockedActivity` with `"Parent Instant Lock"`.
  4. Launches async curfew check: `app.repository.checkAppRestriction(currentChildId, pkgName)`.
  5. Every `>= 350ms` inside YouTube packages or browsers, calls `app.repository.checkYouTubeContentRestriction(...)`; if `YouTubeBlockReason.SHORTS_BLOCKED` or `UNAPPROVED_CHANNEL` triggers, logs a safety alert (throttled every `15s`) and launches `AppBlockedActivity`.
  6. Every `>= 1500ms`, traverses `rootInActiveWindow`, strips UI noise via `ScraperFilterEngine.filterAndClassifyWindow(...)`, deduplicates `lastExtractedText`, updates `lastScrapedContent`, and calls `app.repository.processExtractedContent(...)`.

### 4.2 `LocationTrackingService` (Child Foreground GPS Beacon)
- **File**: `app/src/main/java/com/example/service/LocationTrackingService.kt`
- **Manifest Declaration** (`AndroidManifest.xml`, lines 61–64):
  ```xml
  <service
      android:name=".service.LocationTrackingService"
      android:foregroundServiceType="location"
      android:exported="false" />
  ```
- **How to Start / Stop / Access It**:
  - Started automatically from `ChildDashboardScreen.kt` via `ContextCompat.startForegroundService(context, Intent(context, LocationTrackingService::class.java).apply { action = LocationTrackingService.ACTION_START; putExtra(EXTRA_CHILD_ID, childId) })` once `POST_NOTIFICATIONS` / location flow permits.
  - Exposes `LocationTrackingService.isTrackingActive: StateFlow<Boolean>` so UI pills reflect live beacon status.
  - **Role Security Guard** (lines 48–52): If `app?.repository?.currentUser?.value?.role != "child"`, it immediately calls `stopSelf()` and returns `START_NOT_STICKY`.
  - **Location Strategy**: Checks `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. Uses `LocationServices.getFusedLocationProviderClient(this)` every `300000L` ms (5 minutes) and calls `app.repository.recordLocation(...)`. If neither location permission is granted, stops cleanly without emitting fake coordinates.
  - **Reboot Recovery (`BootCompletedReceiver.kt`)**: Registered in `AndroidManifest.xml` for `BOOT_COMPLETED` / `QUICKBOOT_POWERON` to restart `LocationTrackingService` when an active child session and location permission are present.

### 4.3 `SupabaseClient` (Primary Multi-Device Cloud Database Bridge)
- **File**: `app/src/main/java/com/example/data/remote/SupabaseClient.kt`
- **How It Works**: Singleton Kotlin `object SupabaseClient` using `OkHttpClient` (`15s` connect / `15s` read timeouts, `BODY` logging gated behind `BuildConfig.DEBUG`) issuing PostgREST HTTP requests against `https://mukkrkppvfxnbbxozvwj.supabase.co/rest/v1` using `SupabaseClient.API_KEY`.
- **Key Exposed Methods**:
  - `upsertFamilyGroup(group)` -> `POST /fs_family_groups?on_conflict=family_id`
  - `upsertUser(user)` -> `POST /fs_users?on_conflict=user_id`
  - `upsertDevice(device)` -> `POST /fs_devices?on_conflict=device_id`
  - `pushScheduleRule(rule, childId)` / `deleteScheduleRule(ruleId)` / `toggleScheduleRule(ruleId, isActive)` / `fetchScheduleRules(childId)`
  - `pushActivityLog(log, childId)` / `acknowledgeActivityLog(logId)` / `deleteActivityLog(logId)` / `fetchChildLogs(childId)`
  - `pushLocationPoint(point, childId)` / `fetchLatestLocation(childId)`
  - `pushInstalledApps(apps, childId)` / `fetchInstalledApps(childId)`
  - `pushAllowedYouTubeChannels(channels, childId)` / `fetchAllowedYouTubeChannels(childId)`
  - `fetchFamilyMembersByInviteCode(inviteCode)` / `fetchFamilyMembersByFamilyId(familyId)` / `findFamilyGroupByInviteCode(inviteCode)`
- **UUID Sanitization**: `normalizeUuid(id)` maps shorthand demo IDs (`"parent-sarah"`, `"child-default"`, `"family-smith-01"`) to deterministic RFC-4122 UUIDs (e.g., `"child-default"` -> `"a1b2c3d4-0002-4000-8000-000000000002"`) using `UUID.nameUUIDFromBytes` for any arbitrary non-UUID string so PostgreSQL `uuid` columns never reject IDs.

### 4.4 `FocusSenseApiService` & `ApiClient` (Optional Custom / Render FastAPI Server)
- **Files**: `app/src/main/java/com/example/data/remote/FocusSenseApiService.kt`, `AuthInterceptor.kt`, `TokenAuthenticator.kt`
- **Backend Source**: `/server/main.py` (FastAPI Python server with `schema.sql` & `requirements.txt`; `/server_django/` is also present in the repository).
- **How Accessed**: `ApiClient.getService(baseUrl)` caches a `Retrofit` + `MoshiConverterFactory` instance wired with `AuthInterceptor` (injects `Authorization: Bearer <jwt>`), `TokenAuthenticator` (auto-refreshes via `/api/auth/refresh` on `401`), and `HttpLoggingInterceptor`.
- **How Controlled**: In Parent Dashboard -> Tab 3 (`FamilyAndDevicesTab`), parents can enter a custom server URL (saved to `SecurePreferences`), tap **"Test Server"** (`checkHealth()`), or configure Gemini server settings (`GET/POST /api/ai/config`). Note: `hasCustomLegacyServer()` in `FocusSenseRepository.kt` skips calling custom URLs if the URL is blank or contains `ais-dev-` / `ais-pre-`.

### 4.5 `YouTubeSearchService` (Educator Search & ReGain WebView Extraction)
- **File**: `app/src/main/java/com/example/data/remote/YouTubeSearchService.kt`
- **How Accessed**: Called by `FocusSenseViewModel.searchYouTubeChannels(query)` on `Dispatchers.IO`.
- **Implementation**: Fetches YouTube channel-filtered search HTML (`sp=EgIQAg%3D%3D`), extracts the `var ytInitialData = ...;` JSON object, recursively traverses `channelRenderer` nodes to return real `YouTubeChannelSearchResult` items (`channelId`, `title`, `handle`, `description`, `subscriberCount`, `thumbnailUrl`, `channelUrl`), and falls back cleanly if network parsing fails.

---

## Part 5: Machine Learning Models & Tri-Tier Cognitive Safety Engine

FocusSense implements a **3-Layer Hierarchical Cascade** inside `ThreatEvaluationEngine.kt` designed to minimize cloud latency, cellular egress, and energy consumption while maximizing precision on ambiguous threats.

```text
  On-Screen Clean Text ("$contentTitle $extractedText")
         │
         ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ LAYER 1: Deterministic Rule & Keyword Fast Filter (< 1 ms)  │
  │ - Emergency self-harm/violence/explicit triggers -> BLOCK    │
  │ - Educational whitelist (e.g., Khan Academy) -> SAFE         │
  └──────┬───────────────────────────────────────────────────────┘
         │ (Ambiguous syntax -> ESCALATED_TO_L2)
         ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ LAYER 2: Dual On-Device Edge ML Engine (~ 18 ms)             │
  │  • Model 1: ThreatClassifier (TF-IDF + Logistic Regression)  │
  │  • Model 2: CyberbullyingEnsembleClassifier (TFLite Config)  │
  └──────┬───────────────────────────────────────────────────────┘
         │ (If Model 1 confidence < 0.75f THRESHOLD -> ESCALATED_TO_L3)
         ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ LAYER 3: Cloud Reasoning (~ 380 ms)                          │
  │  • Primary: Direct HTTPS POST to Gemini 3.5 Flash REST API   │
  │  • Fallback: Render FastAPI POST /api/ai/evaluate            │
  │  • Offline Fallback: Graceful return of Layer 2 ML decision  │
  └──────────────────────────────────────────────────────────────┘
```

### 5.1 Layer 1: Deterministic Rule Engine (`evaluateLayer1RuleFilter`)
- **File**: `app/src/main/java/com/example/ai/ThreatEvaluationEngine.kt` (lines 364–429)
- **Logic**:
  - Scans lowercase text against `emergencyCrisisTriggers` (e.g., `"kill myself"`, `"commit suicide"`, `"school shooting"`) and `explicitAdultTriggers` -> resolves immediately (`confidence = 0.98f`, `severityLevel = "CRITICAL"`, `recommendedAction = "INSTANT_BLOCK"`).
  - Scans `safeAcademicTriggers` (e.g., `"khan academy"`, `"mitochondria"`, `"pythagorean theorem"`) when no `generalThreatTriggers` are present -> marks `"Safe"` (`confidence = 0.02f`) with **zero cloud egress**.
  - Otherwise flags as suspicious/ambiguous and escalates to Layer 2.

### 5.2 Model 1: On-Device TF-IDF + Multinomial Logistic Regression (`ThreatClassifier.kt`)
- **Code File**: `app/src/main/java/com/example/ai/ThreatClassifier.kt` (lines 1–74)
- **Model Asset Files** (`app/src/main/assets/model/`):
  - `meta.json`: Training metadata (`algorithm: "LogisticRegression"`, `vectorizer: "TfidfVectorizer"`, `num_features: 2804`, `num_classes: 8`, `ngram_range: [1, 2]`, `sublinear_tf: true`, `norm: "l2"`).
  - `vocab.txt`: 2,804 tab-separated unigrams and bigrams paired with their IDF float weights.
  - `labels.txt`: 8 output classes (`ADULT_CONTENT`, `CYBERBULLYING`, `GROOMING`, `SAFE_EDUCATIONAL`, `SAFE_GENERAL`, `SCAM_PHISHING`, `SELF_HARM_VIOLENCE`, `SOCIAL_NORMAL`).
  - `weights.tsv`: 8 × 2,804 trained Logistic Regression coefficient matrix.
  - `intercepts.tsv`: 8 class bias floats.
- **How Integration & Inference Work**:
  1. Instantiated lazily via `ThreatEvaluationEngine.getClassifier1(context)` as a thread-safe `@Volatile` singleton.
  2. Tokenizes lowercase text with `Regex("\\b\\w\\w+\\b")`, extracts unigrams and adjacent bigrams, computes sublinear TF-IDF weights `(1.0 + ln(count)) * idf[i]`, normalizes to L2 unit length, computes dot-products `bias[k] + sum((v / norm) * weights[k][i])`, and applies numerically stable Softmax.
  3. Returns `Prediction(label, confidence, escalate = conf < 0.75f)`. Harmful categories are `CYBERBULLYING`, `GROOMING`, `ADULT_CONTENT`, `SELF_HARM_VIOLENCE`, `SCAM_PHISHING`.

### 5.3 Model 2: Cyberbullying Subtype Classifier (`CyberbullyingEnsembleClassifier.kt`)
- **Code File**: `app/src/main/java/com/example/ai/CyberbullyingEnsembleClassifier.kt` (lines 1–163)
- **TFLite Dependency**: `implementation(libs.tensorflow.lite)` (`org.tensorflow:tensorflow-lite:2.16.1`), importing `org.tensorflow.lite.Interpreter`.
- **Asset Files** (`app/src/main/assets/model2/`):
  - `config.json`: `{"max_len": 50, "pad_id": 0, "unk_id": 1, "padding": "pre", "truncation": "keep_first_max_len_tokens"}`.
  - `vocab.json`: Token-to-integer ID map.
  - `labels.json`: 5 cyberbullying classes (`age`, `ethnicity`, `gender`, `not_cybepprbullying` [mapped with fallback], `religion`).
- **How Integration Works**:
  1. Loads `config.json`, `vocab.json`, and `labels.json` from `context.assets`.
  2. Attempts to memory-map `model2/cyberbullying_ensemble.tflite` via `FileChannel.MapMode.READ_ONLY` into `Interpreter(modelBuffer, options)` with `setNumThreads(2)`.
  3. **Important Runtime Resilience Note**: In the current bundled `assets/model2/` folder, `config.json`, `labels.json`, and `vocab.json` are present while the binary `cyberbullying_ensemble.tflite` weight file is omitted. Because initialization wraps `loadModelFile` in `try { ... } catch (e: Throwable) { interpreter = null }`, when the binary is absent (or under JVM Robolectric tests), `classify(text)` safely returns `Model2Prediction("not_cyberbullying", 0f, false)` so Model 1, Layer 1, and Layer 3 operate seamlessly without crashing. Dropping a trained `cyberbullying_ensemble.tflite` file (input shape `[1, 50]` int32, output shape `[1, 5]` float32 probabilities, cyberbullying threshold `>= 0.45f`) into `app/src/main/assets/model2/` activates full dual-model TFLite fusion immediately.
  4. Preprocessing (`cleanText`) removes URLs, `@mentions`, `#`, `rt`, non-alphanumeric characters, collapses repeated chars `(.)\\1{2,}`, pre-pads/truncates to `maxLen = 50`, and runs synchronized TFLite inference.

### 5.4 Layer 3: Gemini 3.5 Flash Cloud Reasoning & FastAPI Fallback (`evaluateWithGemini`)
- **File**: `app/src/main/java/com/example/ai/ThreatEvaluationEngine.kt` (lines 629–799)
- **Model**: `gemini-3.5-flash` via Google Generative Language REST API:
  `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey`
- **API Key Source**: Reads `BuildConfig.GEMINI_API_KEY` injected from `.env` by the Secrets Gradle Plugin.
- **Prompt & Structured JSON Output**: Prompts Gemini to classify across 8 safety categories (`Predatory Grooming & Stranger Risk`, `Cyberbullying & Harassment`, `Self-Harm & Mental Distress`, `Violence, Weapons & Threats`, `Explicit & Adult Content`, `Substance Abuse & Drugs`, `Academic Dishonesty`, `Safe`) with `responseMimeType = "application/json"` and `temperature = 0.1`, returning `isFlagged`, `threatCategory`, `confidenceScore`, `severityLevel`, `recommendedAction`, `aiAnalysisSummary`, `parentActionGuidance`, `restorativeDialogueScript`, and `linguisticTriggers`.
- **Fallback**: If Gemini fails or the API key is placeholder/unset, falls back to `evaluateWithRenderServer(...)` (`POST {serverUrl}api/ai/evaluate`) or resolves cleanly offline using Layer 2 results.
- **Ablation Modes (`ThreatAblationMode`)**: Supports live benchmarking in Parent Dashboard Tab 3 across `CASCADED_TRI_LAYER`, `RULE_ONLY`, `ON_DEVICE_ML_ONLY`, and `CLOUD_LLM_ONLY`.

---

## Part 6: Database Architecture (Room SQLite + Supabase PostgreSQL)

FocusSense follows an **offline-first** pattern: writes always commit to local Room SQLite first (`FocusSenseDatabase`, version 6), update in-memory caches, and synchronize asynchronously to Supabase PostgreSQL REST tables.

| Feature Domain | Room Table (`Entities.kt`) | DAO (`FocusSenseDao.kt`) | Supabase Table / Storage (`SupabaseClient.kt`) |
| :--- | :--- | :--- | :--- |
| Family Groups | `family_groups` (`FamilyGroupEntity`) | `FamilyGroupDao` | `fs_family_groups` (`invite_code` pairing) |
| Parent & Child Users | `users` (`UserEntity`) | `UserDao` | `fs_users` (role, PIN, `family_id`, age) |
| Paired Devices | `devices` (`DeviceEntity`) | `DeviceDao` | `fs_devices` (token, battery, child ID) |
| AI Alerts & Activity | `activity_logs` (`ActivityLogEntity`) | `ActivityLogDao` | `fs_activity_logs` (`[ACKNOWLEDGED]` sync) |
| Curfews & App Locks | `schedule_rules` (`ScheduleRuleEntity`) | `ScheduleRuleDao` | `fs_schedule_rules` (also stores `YOUTUBE_STUDY` & `APP_INVENTORY` fallbacks) |
| GPS Tracking Trail | `location_history` (`LocationPointEntity`) | `LocationDao` | `fs_location_logs` |
| Child App Inventory | `installed_apps` (`InstalledAppEntity`) | `InstalledAppDao` | `fs_installed_apps` + `fs_schedule_rules` fallback |
| Allowed YouTube Channels | `allowed_youtube_channels` (`AllowedYouTubeChannelEntity`) | `YouTubeChannelDao` | `fs_allowed_youtube_channels` + `fs_schedule_rules` fallback |

---

## Part 7: Complete Feature-to-File Inventory

Every feature in FocusSense and the exact file(s) where it is implemented:

### 7.1 Authentication, Pairing & Role Entry
- **Dual-Role Login, Signup & Family Invite Pairing** -> `app/src/main/java/com/example/ui/screens/auth/RoleSelectionAuthScreen.kt`:
  Supports Parent & Child sign-in/signup, quick demo sign-in (`Sarah` / `Leo` / `Maya`), invite-code family joining across phones via `SupabaseClient`, and PIN verification (`verifyParentPin`, default `"1234"`).
- **App Entry, Splash, Notification Alerts & PIN Switch Dialog** -> `app/src/main/java/com/example/MainActivity.kt`:
  Handles `enableEdgeToEdge()`, session readiness gate, `AppRestrictionOverlay` banner collection on `SentinelEvent`, system alert notifications, role switching with PIN guard (`ParentPinDialog`), and `extra_open_allowed_channels` deep linking.

### 7.2 Parent Dashboard Features
- **Main Parent Console & Tab Navigation** -> `app/src/main/java/com/example/ui/screens/parent/ParentDashboardScreen.kt` (2,717 lines):
  - **Tab 0 — Overview & AI Alerts (`ParentOverviewTab`)**: Child selector chips, add/pair child button, safety summary metrics, Live Activity & Threat Alert cards with Ack (`acknowledgeAlert`), Delete (`removeVulnerableLog`), and Restorative Dialogue prompts.
  - **Tab 1 — App Locker, Curfews & Study Whitelist (`ScheduleRulesTab`)**: Live child installed apps grid (fetched from Supabase from child's phone) with instant Block/Allow toggle (`toggleAppBlock`), plus active timetable curfew cards (`ScheduleRuleCard`) supporting whole-app blocks and `yt_whitelist:` study channel badges.
  - **Tab 2 — Live GPS Location & Trail (`LiveLocationMapTab`)**: Displays latest GPS fix (or honest waiting status before first fix), coordinates, accuracy badge, and waypoint history trail.
  - **Tab 3 — Family, Devices, Zero Data-Loss Sync & Cognitive Safety Lab (`FamilyAndDevicesTab`, `AcademicThreatLabSuite`)**: Custom server URL editor, Connectivity tester, Force Sync button, Interactive Tri-Tier Threat Lab (`CASCADED_TRI_LAYER` vs ablations, 5-axis Risk Radar, Cascade Trace, BibTeX citation modal, Gemini config modal), registered hardware device cards, and Android Studio Project Guide card.
- **All Parent & Child Modals** -> `app/src/main/java/com/example/ui/dialogs/Dialogs.kt`:
  `ParentPinDialog`, `AddScheduleRuleDialog` (multi-app picker + YouTube study channel whitelist attachment + day/time pickers), `AddChildDialog` (auto-generates QR/invite pairing token), `EditChildNameDialog`, and server configuration dialogs.

### 7.3 Child Experience, Permissions & Distraction-Free Study Lounge
- **Child Dashboard** -> `app/src/main/java/com/example/ui/screens/child/ChildDashboardScreen.kt`:
  Displays active protection shield banner (Accessibility & Location beacon status), child-side schedule visibility, interactive **Pomodoro Study Timer** (25:00 countdown / pause / reset synced via `FocusSenseViewModel`), safety tips, and embedded **Study Lounge (`FocusYouTubeScreen`)** tab + PIN-gated parent switch.
- **Child Guided Permission Onboarding** -> `app/src/main/java/com/example/ui/screens/child/ChildPermissionWizardScreen.kt` & `app/src/main/java/com/example/util/ChildPermissionManager.kt`:
  Guides child step-by-step through Accessibility Service, Display Over Other Apps (`SYSTEM_ALERT_WINDOW`), Usage Access (`PACKAGE_USAGE_STATS`), Fine/Background GPS Location, Battery Optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), and Android 13+ Notifications (`POST_NOTIFICATIONS`).
- **Installed App Scanner** -> `app/src/main/java/com/example/util/InstalledAppScanner.kt`:
  Queries `PackageManager` launchable apps (exclusively on child devices) to build `InstalledAppEntity` lists synced to parents.
- **Distraction-Free YouTube Study Lounge (In-App WebView)** -> `app/src/main/java/com/example/ui/screens/child/youtube/FocusYouTubeScreen.kt`:
  Searches educators (`YouTubeSearchService`), displays channel logos/subscribers (`Coil`), toggles allowed study channels, and opens **`RealYouTubeChannelFullView`** inside an embedded Android `WebView`:
  - Overrides `shouldOverrideUrlLoading` with `YouTubeContentGuard.classifyWebViewNavigation(...)` to block `/shorts/` URLs and unapproved channel URLs.
  - Injects CSS + JS via `injectReGainDistractionBlocker(...)` to hide Shorts tabs, `ytm-reel-shelf-renderer` / `ytd-reel-shelf-renderer`, and watch active page URL / video owner headers via `window.AndroidFocusBridge` (`@JavascriptInterface`).
  - Also extracts live channel Videos and Playlists tabs directly inside Kotlin/UI modals.

### 7.4 Real-Time Blocking & Reusable UI Components
- **Full-Screen Interception Activity** -> `app/src/main/java/com/example/ui/screens/blocking/AppBlockedActivity.kt`:
  Launched by `FocusSenseAccessibilityService` whenever an instant lock, curfew rule, critical threat, YouTube Shorts, or unapproved YouTube channel is detected. Displays dynamic shields (Indigo for Study Shield, Coral for Safety Threat), quick **"Return Home"**, **"Open Approved Study Channels"** (deep-linking back to `MainActivity` Study Lounge), or PIN-verified Parent Unlock.
- **Floating Banner & Shared Chrome** -> `app/src/main/java/com/example/ui/components/AppRestrictionOverlay.kt` & `CommonComponents.kt`:
  `AppRestrictionOverlay` renders animated emergency threat/restriction banners; `CommonComponents.kt` provides `FocusSenseTopBar`, severity badges, and status chips.
- **Material 3 Design System** -> `app/src/main/java/com/example/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`:
  Custom palette (`IndigoPrimary`, `Navy800`/`Navy900`, `EmeraldSafe`, `CoralDanger`, `AmberWarning`, `PurpleAccent`), light/dark color schemes, and custom typography scale.

### 7.5 ViewModel & Automated Tests
- **ViewModel & State Container** -> `app/src/main/java/com/example/ui/viewmodel/FocusSenseViewModel.kt`:
  Combines Flows from `FocusSenseRepository` with coroutine UI state (`isChildLockActive`, `pomodoroSecondsRemaining`, `isPomodoroRunning`, `youtubeSearchResults`, `selectedChildId`, etc.).
- **Local Unit & Robolectric Tests** -> `app/src/test/java/com/example/ExampleRobolectricTest.kt` & `ExampleUnitTest.kt`:
  Tests Layer 2 classification, educational safe whitelisting, active curfew blocking, instant app locks, YouTube Shorts detection, unapproved channel blocking, ReVanced package matching, and Room entity/DAO flows under Robolectric SDK 36.

---

## Part 8: Testing, Verification & Troubleshooting

### 8.1 Running Automated Tests
```bash
# Compiles debug variant and runs all 17 unit & Robolectric tests on JVM
gradle :app:testDebugUnitTest
```

### 8.2 Common Development & Emulator Notes
1. **Android Studio Project Tree View**: Android Studio defaults to the **Android** tree view, which hides workspace root files such as `server/`, `.env`, `.env.example`, and `README.md`. Switch the Project sidebar dropdown to **Project** to inspect everything.
2. **YouTube Testing on Emulators**: Stock emulators may show a Google Play "Update your app" prompt inside the native Google YouTube app if Play Store isn't signed in or up to date. Use the in-app **FocusYouTube Study Lounge** (embedded WebView with JS distraction blocker) or supported browser/YouTube packages listed in `YouTubeContentGuard.YOUTUBE_PACKAGES` (`com.google.android.youtube`, `com.vanced.android.youtube`, `app.revanced.android.youtube`, `app.rvx.android.youtube`) to test channel & Shorts enforcement reliably.
3. **Child vs Parent Sync Verification**: Both Parent and Child devices must belong to the same `family_id` (paired via Invite Code). Parents pull child activity logs, location fixes, rules, and child installed apps from Supabase (`FocusSenseRepository.refreshChildFromCloud`).

---
*Generated directly from verified FocusSense source files.*
