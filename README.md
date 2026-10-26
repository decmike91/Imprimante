# PrintMonitor

Application Android (Kotlin, Jetpack Compose) de surveillance locale :
Elegoo Centauri Carbon (SDCP), Creality K1 (WebSocket 9999), prises Tapo (KLAP), caméras MJPEG.

- Ouvrir le dossier dans Android Studio, laisser Gradle synchroniser, puis Run.
- minSdk 26 (Android 8), targetSdk 35.
- Configuration réseau, Livebox 5 et accès à distance : voir **GUIDE.md**.

## Structure

```
app/src/main/java/fr/printmonitor/
├── App.kt                 Application + service premier plan (alertes écran éteint)
├── MainActivity.kt        Point d'entrée Compose
├── MonitorHub.kt          Connexions, sondage des prises, alertes, extinction auto
├── Notifier.kt            Canaux et notifications
├── data/Models.kt         États, configuration (SharedPreferences)
├── printers/
│   ├── WsPrinterClient.kt     WebSocket avec reconnexion automatique
│   ├── ElegooSdcpClient.kt    Centauri Carbon — SDCP v3 (3030 / caméra 3031)
│   └── CrealityK1Client.kt    K1 — WebSocket 9999 (caméra 8080)
├── tapo/TapoKlapClient.kt     Prises Tapo — handshake KLAP + AES-CBC
└── ui/
    ├── Dashboard.kt       Cartes imprimantes, commandes, prises
    ├── MjpegView.kt       Lecteur MJPEG
    └── SettingsScreen.kt  Réglages
```
