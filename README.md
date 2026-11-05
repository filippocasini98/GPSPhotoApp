# GPSPhotoApp (Android)

App Android per la registrazione fotografica georeferenziata: scatta foto in automatico o manualmente durante una sessione di registrazione e le associa alle coordinate GPS, visualizzandole su mappa.

## Caratteristiche

- Registrazione di sessioni foto con scatto automatico (interval configurable) o manuale
- Geolocalizzazione di ogni foto via GPS
- Mappa con i punti scattati (osmdroid)
- Galleria delle foto della sessione
- Esportazione dei dati (GeoPackage / GPX)
- Tema chiaro/scuro, localizzazione IT/EN
- Nessun dato inviato online: foto e posizione restano sul dispositivo

## Tecnologia

- Kotlin + Jetpack Compose
- CameraX, osmdroid, Foreground Service (camera|location)
- Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`)

## Requisiti

- Android 7.0+ (minSdk 24), targetSdk 35
- Android Studio (Ladybug o più recente consigliato)

## Build

```bash
./gradlew assembleDebug
```

APK di debug: `app/build/outputs/apk/debug/`

(per la versione firmata e la pubblicazione su Play Store vedi `RELEASE.md`)

## Struttura

```
app/src/main/java/com/example/gpsphotapp/
├── MainActivity.kt        # entry point, permessi
├── RecordingService.kt    # foreground service (camera|location)
├── CameraManager.kt       # scatto foto (CameraX)
├── LocationHelper.kt      # GPS
├── DataManager.kt / ExportManager.kt  # persistenza ed esportazione
├── AppViewModel.kt        # logica applicativa
└── ui/                    # schermate Compose
```

## Licenza

Copyright (c) 2025 Filippo Casini. Tutti i diritti riservati.
