# Pubblicazione su Google Play

## Configurazione già pronta
- `applicationId`: **it.filippocasini.gpsphoto** (versionCode 3, versionName 1.1.0)
- Firma release letta da `keystore.properties` (vedi `keystore.properties.template`)
- Minify + shrinkResources attivi con regole ProGuard per GeoPackage/CameraX/osmdroid
- Target SDK 35 (requisito Play 2025+)

## Passi per pubblicare
1. Genera il keystore (una sola volta, conservalo al sicuro):
   ```
   keytool -genkeypair -v -keystore release.jks -alias gpsphoto -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copia `keystore.properties.template` in `keystore.properties` e compila password e alias.
3. Genera l'App Bundle firmato:
   ```
   ./gradlew bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/`
4. In Play Console: crea l'app, carica l'AAB in una traccia (internal testing prima).

## Richiesto dalla Play Console (non nel codice)
- **Privacy policy** (URL pubblico): obbligatoria perché l'app usa fotocamera e posizione.
- **Data safety form**: dichiarare che foto e posizione restano sul dispositivo (nessun upload).
- Schede store: icona 512px, feature graphic 1024×500, screenshot.
- Se il versionCode 3 è già stato usato in un upload precedente, incrementalo.

## Nota importante
La app usa un **foreground service** con tipo `camera|location`: nella Play Console
va compilata la dichiarazione d'uso dei foreground service (motivazione: registrazione
fotografica georeferenziata continua avviata dall'utente).
