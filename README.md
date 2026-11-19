# GPSPhotoApp (Android)

Android app for georeferenced photo logging: automatically or manually takes photos during a recording session, label them easily and tag each one with GPS coordinates, displaying them on a map.

## Features

- Photo session recording with automatic (configurable interval) or manual shooting
- GPS geotagging of every photo
- Map view of captured points (osmdroid)
- Session photo gallery
- Data export (GeoPackage / GPX)
- Light/dark theme, IT/EN localization
- No data sent online: photos and location stay on the device

## Technology

- Kotlin + Jetpack Compose
- CameraX, osmdroid, Foreground Service (camera|location)
- Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`)

## Requirements

- Android 7.0+ (minSdk 24), targetSdk 35
- Android Studio (Ladybug or newer recommended)

## Build

```bash
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/`

(for the signed release build and Play Store publishing see `RELEASE.md`)

## Project structure

```
app/src/main/java/com/example/gpsphotapp/
├── MainActivity.kt        # entry point, permissions
├── RecordingService.kt    # foreground service (camera|location)
├── CameraManager.kt       # photo capture (CameraX)
├── LocationHelper.kt      # GPS
├── DataManager.kt / ExportManager.kt  # persistence and export
├── AppViewModel.kt        # app logic
└── ui/                    # Compose screens
```

## License

Copyright (c) 2025 Filippo Casini. All rights reserved.
