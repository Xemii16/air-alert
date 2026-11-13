# Air Alert for Wear OS

Real-time Ukrainian air raid and hazard alert system for Wear OS smartwatches, powered by the Ukraine Alarm API, a Spring Boot webhook backend, and Firebase Cloud Messaging.

It wakes the watch screen over the lockscreen and triggers repeating alarm-grade vibration (bypassing Do Not Disturb without audible sound), filtered by configurable active hours on the companion phone app.

## Modules

* **`:wear`** — Wear OS smartwatch application (API 30+, Material 3 Expressive). Handles full-screen wakeups, `USAGE_ALARM` haptics, and two-way dismissal sync.
* **`:app`** — Android phone companion (API 26+, Material 3 Expressive). Manages region selection (`State -> District -> Community`), working hours schedules, silent FCM reception, and Wearable Data Layer forwarding.
* **`:backend`** — Java 17 and Spring Boot 3 server. Ingests API webhooks, resolves administrative region hierarchies, and dispatches high-priority FCM data pushes.
* **`:shared`** — Shared data models, threat mappings, and Wearable protocol constants.

## Building

Air Alert uses Gradle to handle dependencies and building.

#### Requirements
* JDK 17 or newer
* Android SDK (`compileSdk = 35`)
* Git

#### Compiling from source
```sh
git clone https://github.com/Xemii16/air-alert.git
cd air-alert/
./gradlew build
```

Output binaries are written to:
* `app/build/outputs/apk/debug/app-debug.apk`
* `wear/build/outputs/apk/debug/wear-debug.apk`
* `backend/build/libs/backend-1.0.0.jar`

## Configuration & Deployment

1. **Firebase (`:app`)**: Place your `google-services.json` (`package_name`: `com.balamut.airalert`) in `app/google-services.json` for local builds, or set the `GOOGLE_SERVICES_JSON` repository secret for GitHub Actions.
2. **Backend (`:backend`)**: Configure `UKRAINE_ALARM_API_TOKEN`, `WEBHOOK_PUBLIC_URL`, and `FIREBASE_CREDENTIALS_PATH`.
3. **Cloud & CI/CD**: See [`deploy/README.md`](deploy/README.md) for Google Cloud Compute Engine, Caddy HTTPS, and GitHub Actions deployment setup.
