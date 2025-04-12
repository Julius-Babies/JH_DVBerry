# JH_Wegweiser
Dieses Projekt ist im Rahmen von [Jugend Hackt 2025 Dresden](https://jugendhackt.org/events/dresden/) entstanden.

## Das Projekt
Dies ist eine Android App, die für seheingeschränkte Personen in Dresden entwickelt wurde. Nutzer*innen können sich hier per Sprachausgabe den Abfahrtsplan der nächsten VVO-Haltestelle ausgeben lassen.

![Screenshot_20250412_230916_Wegweiser](https://github.com/user-attachments/assets/4b1b8520-273e-41f2-9fb7-7b31d815fbbf)

Im oberen Teil der App werden die Abfahrtszeiten ausgegeben. Das große Button in der unteren Hälfte ließt diese in optimierter Form vor. Dabei wird auf die Sprachausgabe-API von Android zurückgegriffen.

### Tech
Verwendet wird Kotlin mit Jetpack Compose, sowie Koin für Dependency Injection und KTor als HTTP Client. Die Datenquelle ist hierbei die VVO-API.

### Credits & Attribution
- [VVO-WebAPI Dokumentation (GitHub)](https://github.com/kiliankoe/vvo/blob/main/documentation/webapi.md)
