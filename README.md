# Повітряна тривога (`com.ukrainealarm`) for Wear OS + Spring Boot Webhook Backend

A multi-module solution that replicates the official **Air Alert! (`com.ukrainealarm` / «Повітряна тривога»)** design and region hierarchy on Wear OS smartwatches (API 30+), powered by the official **Ukraine Alarm API (`https://api.ukrainealarm.com/swagger/index.html`)**, a **Java Spring Boot 3 Webhook Backend**, and **Firebase Cloud Messaging (FCM)**.

---

## Architecture & Features

- 🏢 **Java Spring Boot 3 Backend (`:backend`)**:
  - Integrates with `https://api.ukrainealarm.com/swagger/index.html` (`/api/v3/regions`, `/api/v3/alerts/{regionId}`, `/api/v3/webhook`).
  - Exposes `POST /api/v1/webhook/ukrainealarm` to receive real-time `AlertRegionModel` webhooks when an alert starts or ends.
  - Caches and indexes the 3-level Ukrainian administrative hierarchy (**Область → Район → Територіальна громада**) so alerts triggered at an Oblast or District level automatically notify devices subscribed to child communities.
  - Sends high-priority **Firebase Cloud Messaging (FCM)** data pushes (`AndroidConfig.Priority.HIGH`) to subscribed topics (`region_{regionId}`) and registered device tokens.
- 📱 **Android Phone Companion (`:app`)**:
  - Official `com.ukrainealarm` dark charcoal (`#18191B`) & crimson (`#C81E1E`) interface.
  - **3-Step Region Picker**: Select your **Область → Район → Громада** (matching `com.ukrainealarm`).
  - **Silent FCM Bridge**: Receives high-priority FCM data messages in the background (`AirAlertFirebaseMessagingService`) without making sound on the phone, filters by **Active Working Hours**, and triggers the watch via the Wearable Data Layer API.
  - **Instant Stop & Simulator Controls**: Includes a prominent **"ЗУПИНИТИ ТРИВОГУ НА ГОДИННИКУ"** button and simulator triggers for Red (Air Raid), Yellow (Chemical/Hazard), and Green (All Clear) states.
- ⌚ **Wear OS Smartwatch App (`:wear`)**:
  - Official `com.ukrainealarm` visual design adapted for round Wear OS displays:
    - Concentric circles warning emblem, threat title (`ПОВІТРЯНА ТРИВОГА!`, `ЗАГРОЗА АРТОБСТРІЛУ!`, `ХІМІЧНА НЕБЕЗПЕКА!`), region header, live elapsed timer (`00:03:15`), and pill **СТОП** button.
    - Calming emerald green **ВІДБІЙ ТРИВОГИ** screen on All Clear.
  - **Hard Vibration Only (No Sound)**:
    - Uses maximum-amplitude (`255`) repeating haptic waveforms with `VibrationAttributes.USAGE_ALARM` to bypass Do Not Disturb (DND) and wake the screen (`SCREEN_BRIGHT_WAKE_LOCK`) without playing audible sound.

---

## Project Structure

```
air-alert/
├── backend/              # Java 17 + Spring Boot 3 Webhook & FCM Push Server
│   ├── client/           # UkraineAlarmApiClient (/api/v3/regions, /alerts, /webhook)
│   ├── controller/       # WebhookController & MobileApiController
│   ├── model/            # AlertRegionModel, AlertDto, RegionsViewModel, WebHookModel
│   └── service/          # RegionHierarchyService, DeviceSubscriptionService, FcmPushService
├── app/                  # Android Phone Companion App
│   ├── network/          # BackendClient (Regions sync & FCM topic subscription)
│   ├── schedule/         # ScheduleManager (Active working hours filter)
│   ├── service/          # AirAlertFirebaseMessagingService & PhoneWearMessageSender
│   └── ui/               # DashboardScreen, RegionPickerDialog, ScheduleDialog
├── wear/                 # Wear OS Smartwatch App (API 30+)
│   ├── audio/            # AlertVibrator (Maximum-intensity alarm haptics, soundless)
│   ├── service/          # AlertAlarmService & WearAlertListenerService
│   ├── ui/               # FullScreenAlertActivity, AlertScreen, AllClearScreen, SettingsActivity
│   └── data/             # AlertHistoryManager
└── shared/               # Common models (AlertEvent, ThreatType, RegionNode, ScheduleConfig)
```

---

## Building & Running

### 1. Run All Tests & Build APKs + Backend JAR
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew test assembleDebug :backend:assemble
```

### 2. Run the Java Spring Boot Backend
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:UKRAINE_ALARM_API_TOKEN = "your_ukrainealarm_api_token"
$env:FIREBASE_CREDENTIALS_PATH = "C:\path\to\firebase-service-account.json"
.\gradlew :backend:bootRun
```

#### Backend REST Endpoints
- `POST /api/v1/webhook/ukrainealarm` — Webhook callback endpoint for `api.ukrainealarm.com`
- `POST /api/v1/webhook/register` — Registers your public webhook URL with `https://api.ukrainealarm.com/api/v3/webhook` (`{"webHookUrl": "https://your-domain.com/api/v1/webhook/ukrainealarm"}`)
- `GET /api/v1/regions` — Returns the 3-level Ukrainian regions hierarchy (`Область → Район → Громада`)
- `GET /api/v1/alerts/{regionId}` — Returns active alert status for a region
- `POST /api/v1/devices/register` — Registers a phone's FCM token and selected `regionId`
- `POST /api/v1/test/webhook` — Simulates an incoming webhook event for testing:
  ```bash
  curl -X POST http://localhost:8080/api/v1/test/webhook \
    -H "Content-Type: application/json" \
    -d '{"regionId":"31","regionName":"м. Київ","alertType":"AIR","active":true}'
  ```
