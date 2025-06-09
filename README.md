# <img src="https://raw.githubusercontent.com/Julius-Babies/JH_DVBerry/refs/heads/main/app/src/main/res/mipmap-xxhdpi/ic_launcher.webp" width="32px" height="32px" alt=""> DVBerry
Dieses Projekt entstand im Rahmen von [Jugend Hackt 2025 Dresden](https://jugendhackt.org/events/dresden/).

## Projektbeschreibung

DVBerry ist eine Android‑App, die speziell für sehbehinderte und sehbeeinträchtigte Personen in Dresden entwickelt wurde. Nutzer*innen können sich per Sprachausgabe den Abfahrtsplan der jeweils nächsten VVO‑Haltestelle vorlesen lassen.

Die App unterstützt außerdem eine intuitive Steuerung durch Schütteln des Smartphones: Ein einfacher Shake toggelt zwischen Abspielen und Pausieren der Sprachausgabe.

<img src="https://github.com/user-attachments/assets/7c036e0c-7e32-4884-a8cd-c307f71876fb" alt="image" width="300" height="auto">

## Funktionsweise
1. **Anzeige:** Im oberen Bereich der App werden die nächsten Abfahrtszeiten übersichtlich angezeigt.
2. **Sprachausgabe:** Ein großer Button im unteren Bereich liest die angezeigten Abfahrten in optimierter Form vor.
3. **Shake‑Sensor:** Durch Schütteln des Geräts kann die Wiedergabe gestartet oder gestoppt werden, ohne den Bildschirm zu berühren.
Dabei wird auf die Sprachausgabe-API von Android zurückgegriffen.

### Technische Umsetzung
- **Programmiersprache & UI:** Kotlin mit Jetpack Compose
- **Dependency Injection:** Koin
- **HTTP-Client:** Ktor
- **Datenquelle:** VVO‑WebAPI
- **Sensorik:** ShakeSensor (Accelerometer) für Gestensteuerung

### Credits & Attribution
- [VVO-WebAPI Dokumentation (GitHub)](https://github.com/kiliankoe/vvo/blob/main/documentation/webapi.md)
