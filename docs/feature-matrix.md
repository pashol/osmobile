# Feature-Matrix

Stand 2026-10-21, nach erstem BLE-Hardwaretest, vor vollständiger Hardwareabnahme. Quellenkürzel stehen in
[architecture.md](architecture.md); Protokollnachweise in [protocol.md](protocol.md).

## Statusregeln

* **Implementiert und getestet**: Testart und Beleg stehen dabei; bei
  Gimbalfunktionen sind reale Hardwaretests notwendig.
* **Implementiert, Hardwaretest ausstehend**: Code/Build vorhanden;
  Hardwareabnahme noch nicht vollständig bestanden (auch bei ersten erfolgreichen
  Teilergebnissen).
* **In Entwicklung**: geplanter/noch nicht abgeschlossener Entwicklungsschritt;
  **kein** Anspruch auf bereits vorhandenen Code.
* **Technisch ungeklärt**: mindestens eine wesentliche Voraussetzung offen.
* **Nicht unterstützt**: ausdrücklich ausgeschlossener Umfang oder belegte Grenze.

Die Prototypfunktionen wurden gebaut und teilweise am Nord 4/Osmo-Gerät
geprüft; siehe [test-results.md](test-results.md). Noch offene Hardwarefälle
stehen in [hardware-tests.md](hardware-tests.md). „In Entwicklung“ bezeichnet bei späteren Phasen den
Roadmap-Status und nicht bereits ausgelieferten Code.

## Bluetooth / Gimbal / Tasten

| Funktion | Projektstatus | Machbarkeit / Evidenz | Nächster Nachweis |
|---|---|---|---|
| Nearby-Devices-Berechtigungen | Implementiert, Hardwaretest ausstehend | Android API 31+, Ziel 36 | Ablehnung, dauerhafte Ablehnung, Widerruf |
| BLE-Scan, Kandidatenliste, Name/RSSI | Implementiert und getestet | Nord 4: Osmo-Kandidat zweimal mit Name/RSSI gefunden, 15-s-Timeout bei Nicht-Sichtbarkeit | Umbenanntes Gerät, Wiederholungs-/Umgebungstest |
| GATT-Verbindung / FFF4-Abonnement | Implementiert, Hardwaretest ausstehend | Nord 4: zweimal FFF0/FFF4/FFF5, CCCD/READY; erster Lauf verlor nach ≈28 s die Verbindung | Ursachenklärung und mehrfacher Stabilitätstest |
| Verbindungsstatus, Abbruch, Fehler | Implementiert, Hardwaretest ausstehend | Nordic-Bibliothek + eigene Sitzung | Ausschalten, Entfernung, BT aus, App-Hintergrund |
| Begrenzte automatische Wiederverbindung | Implementiert, Hardwaretest ausstehend | Ein Power-Cycle: Reconnect/CCCD/READY nach ≈8 s bestanden; nach spontanem Timeout Retries erschöpft | Wiederholungen, manuelles Trennen/Abbruch während Retry |
| Debug-Logging RX / GATT-TX | Implementiert und getestet | Echte FFF4-Fragmente und CCCD-Write per ADB protokolliert; kein DUML-TX | Tastensemantik separat |
| DUML-RX-Framing/CRC | Implementiert und getestet | 12 JVM-Tests mit realen RX-Vektoren; live 8 CRC-geprüfte Set/ID-Typen am Osmo-Gerät | Payload-Semantik/HID und längere Robustheitsläufe offen |
| Eindeutige Modellidentifikation | Technisch ungeklärt | FFF0/Namen sind nicht exklusiv | Modellantwort oder weitere OM2-spezifische Daten |
| Akku / Ladestatus | Technisch ungeklärt | OM `05/06`-Decoder; GATT `2A19` vorhanden | Payload/Skala/Länge real prüfen |
| Gerätename / GATT-Informationen | Implementiert und getestet | Osmo-Name, FFF0/FFF4/FFF5, zusätzliche HID-/Battery-Services im GATT-Dump | Modell eindeutig identifizieren |
| Firmwareversion / Seriennummer | Technisch ungeklärt | GO zeigt diese; kein verifiziertes Requestschema | HCI-Fixture |
| Virtueller Joystick Pan / Tilt / diagonal | Technisch ungeklärt | OM Rotation vorhanden | Einheiten, Grenzen, Release-Stop, Timeout |
| Follow / Free (Lock) | Technisch ungeklärt | DJI-M S. 8, 16 | Moduskommando/Status-Payload |
| Walk / Sport | Technisch ungeklärt | DJI-M S. 16 | OM2-Konfigurationskommando |
| Tilt Axis Lock | Technisch ungeklärt | DJI-M S. 16 | Kommando/Status |
| Hardware-Joystick frei / Achseninvertierung / Tempo | Technisch ungeklärt | DJI-M S. 15, DJI-F | Konfigurationsbytes |
| Gimbal zentrieren | Technisch ungeklärt | Doppeldruck M belegt | App-Recenter nicht mit absolut 0 verwechseln |
| Horizontkorrektur | Technisch ungeklärt | DJI-M S. 15 | Kalibrier-/Offsetkommando |
| Automatische Kalibrierung horizontal | Technisch ungeklärt | DJI-M S. 15 | Verifizierter Ablauf + Resultat |
| Automatische Kalibrierung vertikal | Nicht unterstützt | DJI-M / DJI-F ausdrücklich nein | Keine Option anzeigen |
| Status-/Fehlertelemetrie | Technisch ungeklärt | GO/LED-Status belegt, Decoder offen | Istwerte statt Schätzwerte |
| Hardwaretaste Foto / Video Start / Stop | Technisch ungeklärt | DJI-M S. 10 | FFF4 oder HID, ACK/Deduplizierung |
| Hardware-Zoom | Technisch ungeklärt | DJI-M S. 10 | Richtung, Wiederholung, Loslassen |
| M dreimal: Kamera wechseln | Technisch ungeklärt | DJI-M S. 8 | Event; während Video nicht wechseln |
| Weitere Tastenkombinationen | Technisch ungeklärt | Keine belegte Liste | Nicht als funktionierend anzeigen |

## Kamera (erst nach bestandenem BLE-Gate)

Alle OC-Funktionen unten sind **Upstream-Fähigkeiten**, noch keine Funktionen
der Diagnose-App und keine Zusicherung für die Nord-4-Kamera-HAL.

| Funktion | Projektstatus | Grundlage / Abhängigkeit |
|---|---|---|
| Einzelbild, Front-/Rückkamera | In Entwicklung | Phase 2, OC; Laufzeiterkennung der Kameras |
| Autofokus, Tap-to-Focus, Belichtungsmessung | In Entwicklung | OC + unterstützte AF/AE-Regionen |
| Belichtungskorrektur, AE Lock | In Entwicklung | OC, Kameraeigenschaften prüfen |
| Blitz/Torch, Rasterlinien | In Entwicklung | OC; Blitz nur wenn vorhanden |
| Touch-Zoom | In Entwicklung | OC; verfügbare Zoomspanne/physische Linsen |
| Serienbilder / Burst | Technisch ungeklärt | OC Camera2; GO-Langdruck-Burst ursprünglich nur iOS |
| Intervallfoto | In Entwicklung | OC Repeat; GO 2/5/10 s dokumentiert |
| HDR-Foto | Technisch ungeklärt | OC Belichtungsreihen vs OEM-HDR getrennt prüfen |
| DNG / RAW | Technisch ungeklärt | RAW-Capability und unterstützte Streamkombination |
| Video Start/Stop, lokale Speicherung | In Entwicklung | OC + MediaStore, Hardwaretaste separat blockiert |
| Auflösungen / Frameraten | Technisch ungeklärt | StreamConfigurationMap + Encoderprofile/Codec, keine feste 4K/60-Behauptung |
| Autofokus / EV / AE Lock im Video | Technisch ungeklärt | Verhalten pro Videomodus messen |
| Manueller ISO / Verschlusszeit | Technisch ungeklärt | MANUAL_SENSOR; GO damals iOS-only, mögliche Android-Erweiterung |
| Manueller Fokus | Technisch ungeklärt | LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0; Frontkamera ggf. fix |
| Weißabgleich manuell / Lock | Technisch ungeklärt | verfügbare AWB-Modi / manuelle Farbkontrolle |
| Slow Motion | Technisch ungeklärt | High-Speed-Video + Encoder; GO ursprünglich iOS-only |
| Video-HDR | Technisch ungeklärt | Nicht aus Foto-HDR ableiten; eigener Capability-/Codec-Test |

## Tracking und Kreativfunktionen

| Funktion | Projektstatus | Machbarkeit / offene Punkte |
|---|---|---|
| Lokale Gesichtserkennung | In Entwicklung | Phase 3, MediaPipe, gebündeltes Modell |
| Lokale Personenerkennung | In Entwicklung | Phase 3, Personklasse, Modell-/Lizenzwahl |
| Motivauswahl / ID / Wiedererkennung | In Entwicklung | Bounding-Box-Auswahl, Assoziation; keine biometrische Identifizierung |
| Automatische Gimbal-Nachführung | Technisch ungeklärt | OM2-Speed/Stop-Nachweis und gemessene Latenz |
| Tracking während Video | Technisch ungeklärt | Gleichzeitiger YUV-/Video-Stream + thermischer Test |
| Verlustanzeige / manuell beenden | In Entwicklung | Zustandsautomat; Verlust stoppt Motorsteuerung |
| Andere Objektklassen | Nicht unterstützt | Nicht Teil erster Version laut Auftrag |
| Panorama allgemein | In Entwicklung | DJI-M/DJI-F; OC Stitching als Startpunkt |
| Automatisches 180°-Panorama | Technisch ungeklärt | Gimbalbewegung/Überlappung; exakter GO-Untermodus in geprüften Quellen nicht spezifiziert |
| 3×3-Panorama | Technisch ungeklärt | Mehrzeilen-Stitching und Bahn; exakte Original-Untermodusspezifikation noch offen |
| Panorama speichern / Fortschritt / Einzelbilder | In Entwicklung | OC erweitern; Originalbilder speichern laut DJI-F möglich |
| Timelapse: Intervall / Dauer / erwartete Videolänge | In Entwicklung | OC Aufnahme, eigener Jobplan; Länge = Bildzahl / Ausgabe-FPS |
| Timelapse: Encoder / Auflösung / Speicher / Fortschritt / Abbruch | In Entwicklung | MediaCodec + MediaMuxer; OC Video-Speed teilweise wiederverwendbar |
| Konstante AE/AF/WB bei Timelapse | Technisch ungeklärt | Capability und Sensorverhalten; kein universelles Lock versprechen |
| Motion Timelapse, bis fünf Punkte | Technisch ungeklärt | DJI-M S. 14/DJI-F; absolute Kommandos vorhanden, Reproduzierbarkeit offen |
| Position speichern / Vorschau / weiche Übergänge | Technisch ungeklärt | Istwinkel, Referenzrahmen, Zeitbyte/Interpolation fehlen |
| Hyperlapse mit Gimbalstabilisierung | In Entwicklung | DJI-M S. 14; Timelapse-Pipeline plus Follow-Stabilisierung |
| Zusätzliche elektronische Videostabilisierung | Technisch ungeklärt | Camera2 EIS/OIS-Modi; Crop/FPS, nicht mit Gimbalstabilisierung gleichsetzen |

## Medien und weitere Funktionen

| Funktion | Projektstatus | Grundlage / Grenze |
|---|---|---|
| Eigene Fotos/Videos anzeigen und abspielen | In Entwicklung | Phase 5; OC externe Galerie ist noch keine interne Galerie |
| Löschen / Teilen / Export | In Entwicklung | MediaStore-URIs, Intent-Grants, Android-Consent für fremde Medien |
| Einfache Bearbeitung | In Entwicklung | Zuschnitt/Rotation, Video-Trim als definierter erster Umfang |
| Livestreaming | In Entwicklung | GO belegt S. 15/FAQ; optional spätere Netzwerkfunktion, nicht Offline-Kern |
| Beauty | Technisch ungeklärt | GO Foto und Video bis 720p laut FAQ; offene, lizenzierte Implementierung wählen |
| Firmware-Updates | Nicht unterstützt | Kein sicheres dokumentiertes geprüftes OM2-Verfahren vorhanden |
| DJI-Konto / SkyPixel | Nicht unterstützt | Bewusst keine Betriebsabhängigkeit oder Integration |
| Offlinebetrieb / keine Telemetrie | Implementiert, Hardwaretest ausstehend | Kein INTERNET-Permission im gebauten APK; Offline-Hardwaretest ausstehend |
| Android-16-Debug-/Release-Build | Implementiert und getestet | target 36; Gradle-Build und Debug-Signaturprüfung, Release unsigned; keine Geräteabnahme |
| Profilregeln / begrenztes Retrybudget | Implementiert und getestet | 6 JVM-Tests, nicht gleich Android-GATT-/Lifecycle-Test |

## Testnachweise

Konkrete Ergebnisse stehen in [test-results.md](test-results.md), reproduzierbare
Fälle in [hardware-tests.md](hardware-tests.md). Ein erfolgreicher Gradle-Build
setzt keine der Gimbalzeilen auf „Implementiert und getestet“.
