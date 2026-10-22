# FocusSense: Real-Time Context-Aware Parental Control Platform
## Architecture Blueprint & 7-Day Implementation Plan

---

## 1. Executive Summary & Core Concept

**FocusSense** is a real-time, context-aware parental control and child safety platform. 

The system operates using a **single Android codebase (single APK)** that can be installed on both the **Parent's phone** and the **Child's phone**. The device role is selected upon initial launch. The two devices communicate securely through a centralized cloud backend (**FastAPI on Render** + **PostgreSQL on Supabase**), with heavy AI vulnerability analysis offloaded to a dedicated self-hosted open-source **DeepSeek LLM inference server**.

```
                           ┌────────────────────────┐
                           │   Single Android APK   │
                           │      (FocusSense)      │
                           └───────────┬────────────┘
                                       │ First Launch
                         ┌─────────────┴─────────────┐
                         ▼                           ▼
                 [ PARENT DEVICE ]           [ CHILD DEVICE ]
                 • Account Signup/Login      • Authenticates with Parent credentials
                 • Child selection           • Registers Child Name / Device ID
                 • Real-time map & location  • Grants Accessibility & Location perms
                 • Instant threat alert feed • Background Accessibility text scraper
                 • Schedule & app rules      • Smart local noise filter & debouncer
                         │                           │
                         │    HTTPS REST / WSS       │
                         └─────────────┬─────────────┘
                                       ▼
                 ┌───────────────────────────────────────────┐
                 │          Central API (Render)             │
                 │              FastAPI Server               │
                 │   • Device Pairing & Auth                 │
                 │   • Location & Activity Sync              │
                 │   • Request Routing & Load Balancing      │
                 └─────────────┬─────────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
 ┌──────────────────────┐              ┌──────────────────────┐
 │ Supabase PostgreSQL  │              │ DeepSeek AI Server   │
 │ • Users & Devices    │              │ (vLLM / Ollama API)  │
 │ • Schedules & Rules  │              │ • Zero per-token fee │
 │ • Flagged Alerts     │              │ • Total data privacy │
 │ • Location History   │              │ • Structured JSON    │
 └──────────────────────┘              └──────────────────────┘
```

---

## 2. System Architecture & Components

### A. Mobile Application (Android / Jetpack Compose)
1. **Initial Role Selection & Onboarding Flow**:
   - Clean slate (no mock or hardcoded seed data).
   - Splash screen asks: **"I am a Parent"** vs **"This is my Child's Device"**.
   - **Parent Flow**: Register / Login -> Dashboard -> Sees list of paired children or a "Pair New Child" prompt.
   - **Child Flow**: Enter Parent Email/Password -> Specify Child Name/Device -> Pairs with Supabase database -> Guides user through granting Android Permissions (Accessibility, Location, Usage Stats) -> Enters persistent monitoring service.

2. **Accessibility Monitoring Engine (`FocusSenseAccessibilityService`)**:
   - Listens to active window `AccessibilityEvent`s across target applications (social media, browsers, chats).
   - Scrapes text nodes from the screen.
   - **Smart On-Device Filtering**:
     - Ignores keyboards, status bar, clocks, system UI, and known safe educational apps.
     - Debounces rapid scrolling to preserve battery and CPU.
     - Performs fast keyword/pattern check for critical signals before forwarding.

3. **Background Location Service (`LocationTrackingService`)**:
   - Uses `FusedLocationProviderClient` to stream GPS updates periodically.
   - Stores location locally if offline; syncs to backend when connected.

4. **Parent Console UI (`ParentDashboardScreen`)**:
   - Live location map / status of each child.
   - Real-time alert feed with threat severity badges (e.g., *Stranger Risk*, *Cyberbullying*, *Explicit Content*, *Self-Harm*).
   - App schedule management (bedtime curfew, study hours).

---

### B. Backend & Cloud Infrastructure

1. **Central API Server (`FastAPI` on Render)**:
   - **Authentication**: JWT-based login/registration for parents.
   - **Device Pairing**: Associates child device tokens to the parent account.
   - **Ingestion Pipeline**: Receives scraped snippets from child devices (`POST /api/child/inspect-content`).
   - **Load Balancing & Orchestration**: Forwards content to the DeepSeek server and saves results to database.
   - **Telemetry & Rules**: Serves live location traces and synchronized schedule rules.

2. **Database (`PostgreSQL` on Supabase)**:
   - `users`: Parent accounts and child profiles.
   - `devices`: Registered devices, FCM push tokens, online status, battery percentage.
   - `activity_logs`: Flagged threat incidents, confidence score, raw snippet, and AI explanation.
   - `location_history`: Timestamped latitude/longitude traces.
   - `schedule_rules`: Time-based application restriction windows.

3. **Dedicated Open-Source LLM Server (DeepSeek)**:
   - Runs an open-source model (e.g., DeepSeek-R1-Distill-7B/8B or DeepSeek-V3 via vLLM or Ollama).
   - Accessible internally or via secure API token from the FastAPI backend.
   - Evaluates context for child safety threats with zero commercial per-token costs.
   - Emits structured JSON:
     ```json
     {
       "threat_detected": true,
       "threat_category": "Stranger Risk",
       "severity": "HIGH",
       "summary_for_parent": "Contact asking child to meet outside without parental knowledge."
     }
     ```

---

## 3. Data Processing & Threat Classification Funnel

To ensure high performance, zero lag, minimal battery consumption, and rock-solid privacy:

| Tier | Layer | Where It Runs | Purpose |
| :--- | :--- | :--- | :--- |
| **Tier 1** | **Noise Elimination** | Child Android Device | Filters out system UI, navigation bars, keyboard letters, and redundant scroll events. |
| **Tier 2** | **Heuristic Trigger** | Child Android Device | Fast regex / intent dictionary matches suspicious keywords or predatory sentence structures. Discards 95%+ of safe browsing. |
| **Tier 3** | **DeepSeek Inference** | GPU / Server Instance | Evaluates nuance, intent, and subtle context. Returns structured risk score and explanation for parents. |
| **Tier 4** | **Parent Alert** | Parent Android Device | Real-time push notification and dashboard update when severe threats occur. |

---

## 4. 7-Day Implementation Roadmap

| Day | Focus Area | Deliverables & Tasks |
| :--- | :--- | :--- |
| **Day 1** | **Role Selection & Clean Auth Architecture** | • Remove all hardcoded mock/seed profiles from Room DB.<br>• Build first-launch Role Selection Screen ("Parent" vs "Child").<br>• Implement Parent Sign Up & Sign In UI.<br>• Implement Child Device Setup (authenticate with parent creds + child name).<br>• Store user role and auth token in persistent preferences. |
| **Day 2** | **Backend Setup & Device Pairing** | • Configure FastAPI endpoints for Parent Auth (`/api/auth/register`, `/api/auth/login`).<br>• Implement Child Device Registration & Pairing endpoint (`/api/devices/pair`).<br>• Connect Supabase PostgreSQL schema with migration tables.<br>• Validate two devices can pair via the backend. |
| **Day 3** | **Child Onboarding & Permission Wizard** | • Build step-by-step permission setup flow for the child's device:<br>  1. Accessibility Service activation guide.<br>  2. Background Location access.<br>  3. Usage Access (app time tracking).<br>• Add battery optimization exemption prompt for uninterrupted background service. |
| **Day 4** | **Accessibility Text Scraper & Local Filter** | • Implement active window text extraction in `FocusSenseAccessibilityService`.<br>• Build Tier 1 & Tier 2 on-device filter (noise removal, debouncing, package whitelisting).<br>• Extract target browser URLs and chat strings.<br>• Buffer and send candidate text chunks to backend. |
| **Day 5** | **DeepSeek AI Server Integration & Threat Pipeline** | • Build the FastAPI evaluation endpoint (`/api/evaluate`) calling DeepSeek (vLLM/Ollama).<br>• Formulate system prompts for child vulnerability classification (grooming, bullying, explicit, self-harm).<br>• Return structured JSON analysis and save flagged alerts in Supabase. |
| **Day 6** | **Live GPS Tracking & Geofencing** | • Implement continuous/periodic location updates in `LocationTrackingService`.<br>• Post coordinates to `/api/location/update`.<br>• Build Parent Live Location screen with map view, status, and battery percentage. |
| **Day 7** | **Parent Dashboard, Alerts Feed & End-to-End Testing** | • Finalize Parent Dashboard: live child switcher, real-time alert feed, and app schedule controls.<br>• Test complete end-to-end loop: Type simulated message on Child phone -> Scraped -> Sent to DeepSeek -> Flagged -> Displayed immediately on Parent phone.<br>• Security audit, error handling, and release readiness. |

---

## 5. Next Immediate Action
Start with **Day 1: Role Selection & Clean Auth Architecture** by replacing hardcoded seed data with the clean onboarding and authentication flow.
