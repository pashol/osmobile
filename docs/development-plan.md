# OM2-Alternative Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement
> this plan task-by-task. Ausführung in dieser Sitzung gemäß Auftrag; keine
> automatische Git-Veröffentlichung. Dokumentation und Hardware-Gates einhalten.

**Goal:** Erst eine installierbare BLE-Diagnose-App, danach schrittweise eine
offlinefähige Alternative zu DJI GO für OM170 bereitstellen.

**Architecture:** Java-Module für Protokollwissen und Nordic-BLE-Transport mit
kleinem Diagnosehost. Nach BLE-Hardwareabnahme Open-Camera-Fork mit dünnem
Gimbal-/Kameraadapter; lokale Bildanalyse wird unabhängig vom Transport geplant.

**Tech Stack:** Java 17, Android SDK 36, Gradle 8.14.3, AGP 8.13.2,
Nordic Android BLE Library 2.11.0, JUnit 4.13.2; GPL-3.0-or-later.

**Spec:** [architecture.md](architecture.md), [protocol.md](protocol.md),
[feature-matrix.md](feature-matrix.md); vollständiger Projektauftrag im Gespräch.

## Globale Anforderungen

* Android 16 primär, OnePlus Nord 4 und DJI Osmo Mobile 2 (OM170).
* Offline-Kern, keine Telemetrie/Konten/Cloud, keine proprietären Assets.
* Existierende OSS-Komponenten bevorzugen; keine erfundenen DJI-Kommandos.
* Hardwaretests müssen als solche nachgewiesen sein.
* Erster Prototyp: Berechtigungen, Scan, Verbindung, Status, Logging.
* Keine umfangreiche Kamera-/Trackingimplementierung vor erfolgreichem BLE-Test.
* Motor-/Firmwarewrites sind nicht Teil des ersten Prototyps.

## Implementierungsauftrag jetzt: Phase 1a / BLE-Diagnose

### Aufgabe 1: Testbarer Profilkern und Build

Dateien: `settings.gradle`, `build.gradle`, `gradle.properties`, Wrapper;
`protocol/build.gradle`, `protocol/src/main/java/org/openom/protocol/OmProfile.java`,
`RetryPolicy.java`, zugehörige Tests in `protocol/src/test/java/…`.

Schnittstelle: UUID-Konstanten, Kandidatenhinweis und
`supportsTransport(boolean service, int notifyProperties, int writeProperties)`;
`RetryPolicy.nextDelayMillis()` liefert 1000/2000/4000, anschließend −1,
`reset()` setzt die Episode zurück.

- [x] Tests vor Implementierung: fehlende Characteristics/NOTIFY ablehnen,
  WRITE und WRITE_NO_RESPONSE zulassen, Namen nicht als Identitätsbeweis
  behandeln, Retry-Budget und Reset testen.
- [x] `:protocol:test` ausführen, erwarteten fehlenden Implementierungsfehler prüfen.
- [x] Profilregeln aus OM/gimbal.py umsetzen; keine Adressen fest eintragen.
- [x] `:protocol:test` erneut: alle Fälle erfolgreich.

### Aufgabe 2: Android-BLE-Modul und Diagnosehost

Dateien: `ble/build.gradle`, `ble/src/main/AndroidManifest.xml`,
`ble/src/main/java/org/openom/ble/{OmBleManager,BleSession,SessionLog}.java`;
`app/build.gradle`, `app/src/main/AndroidManifest.xml`,
`app/src/main/java/org/openom/app/MainActivity.java`, Ressourcen unter
`app/src/main/res/values/`, Lizenztexte unter `licenses/`.

Vertrag: Session wird im Hauptthread verwendet, hält Application-Context,
besitzt Scanner/GATT und meldet Status, Geräte und Log an austauschbaren
Listener. UI ruft `startScan()`, `connect(device)`, `disconnect()` auf.
Transport-Callbacks alter Sitzungen werden verworfen. Pro Sitzung höchstens
ein GATT-Manager und ein Scan, Scanlimit 15 Sekunden, Verbindungs-/Initialisierungs-
limit 20 Sekunden, Retry 1/2/4 Sekunden nur bei unerwartetem Verbindungsverlust.

- [x] Nordic `BleManager` für FFF0/FFF4/FFF5, Service-/Property-Logging,
  `enableNotifications` und obligatorische CCCD-Erfolgskontrolle integrieren.
- [x] Scanner ohne verpflichtenden Namens-/UUID-Filter; begrenzte Trefferliste,
  Nutzerwahl, sichtbare Kandidatenhinweise implementieren.
- [x] Fehler/Disconnect/Timeout/BT-aus/Berechtigungsfehler anzeigen, manuelles
  Trennen beendet Retries, Schließen beendet Scan/Callbacks/Ressourcen.
- [x] UI: Permissions, Systemdialog BT einschalten, App-Einstellungen bei
  Ablehnung, Scan/Stop/Trennen, Status, Geräteliste, lokale Logansicht/-teilen.
- [x] Rotation erhält Session, Hintergrund beendet sie; Edge-to-edge-Insets implementiert (Hardwaretest offen).
- [x] `:app:assembleDebug :app:assembleRelease :app:lintDebug` ausführen.

### Aufgabe 3: Nachweise und Übergabe

Dateien: `README.md`, `docs/hardware-tests.md`, `docs/test-results.md`,
`.gitignore`, Lizenz-/Abhängigkeitshinweise und aktualisierte Feature-Matrix.

- [x] Debug-APK-Signatur und Paketmanifest prüfen. Release zunächst unsigned;
  reproduzierbaren eigenen Signierweg dokumentieren, Schlüssel nicht ablegen.
- [x] Ubuntu-/ADB-/Logging-Anleitung und Testfälle mit Sollresultaten schreiben.
- [x] Erste reale BLE-Tests über Wireless ADB: Scan, FFF0/FFF4/FFF5, CCCD,
  RX und ein kontrollierter Power-Cycle-Reconnect; siehe test-results.md.
- [ ] BLE-Hardware-Gate abschließen: unerwarteten Timeout untersuchen,
  Berechtigungs-/Lifecycle- und Langzeittests aus hardware-tests.md durchführen.
- [x] Matrix nur anhand tatsächlich vorhandener Implementierung/Belege aktualisieren.

## Danach: Phasen und Abnahme-Gates

Schätzungen für eine erfahrene entwickelnde Person mit regelmäßigem
Hardwarezugriff, in **Arbeitswochen**, keine Termine oder Garantie. Reverse
Engineering kann einzelne Funktionen länger blockieren oder ausschließen.

| Phase | Lieferumfang | Größenordnung | Abnahme / Blocker |
|---|---|---|---|
| 0 | Quellen, Architektur, Lizenzprüfung, Matrix | 2–4 Arbeitstage inklusive vertiefter Rückfragen | Quellen/Evidenz nachvollziehbar; Entscheidung dokumentiert |
| 1a | Diagnose-APK | 2–5 Arbeitstage inkl. Gerätefeedback | Installieren, offline scannen, FFF0/4/5, CCCD, RX, Lifecycle |
| 1b | robuster DUML-Codec, Akku, Joystick, Center, belegte Modi | 2–6 Wochen | HCI-Fixtures, CRC/Reassembly-Tests; realer Stop/Timeout; unbekannte Modi bleiben verborgen |
| 2 | Open-Camera-Fork, Foto/Video, Tasten, Zoom, Einstellungen | 3–6 Wochen | Wiederholte Aufnahmen und MediaStore; Capability-Dump; keine Kameraregression |
| 3 | Gesichter/Personen, Auswahl, Regler, Video + Tracking | 4–8 Wochen | Lokale Modelle/Lizenzen, Video ohne Stalls, Verlust stoppt Bewegung, Latenz-/Thermiktest |
| 4 | Timelapse, Panorama, Motionlapse, Hyperlapse | 5–10 Wochen | Bahngenauigkeit, Abbruch, Bildausrichtung, Encoder; absolute Positionen nachgewiesen |
| 5 | Kalibrierung soweit belegt, Galerie, Bearbeitung, Stabilisierung | 4–8 Wochen | Langzeittests, Speicher-/Permissionfehler, Signierung, Quellrelease |

Vollständiger Funktionsumfang ist ein mehrmonatiges Projekt. Eine genaue
Planung der späteren Codeänderungen erfolgt pro Phase nach dem vorherigen
Hardware-Gate; die folgende Roadmap spezifiziert deren Prüfkriterien.

### Phase 1b: Protokoll und Gimbal

1. Echte Notifications und jeweils eine GO-Aktion aufzeichnen; Fixtures mit
   Firmware/Modell/erwarteter Semantik erstellen.
2. CRC8/CRC16, Längen-/Versionsprüfung, Fragment-Reassembly und Resync testen:
   20+1-Split, jedes mögliche Splitoffset, mehrere Frames, Müll, ungültige
   Länge/CRC, Reset nach Disconnect. Erst dann Codec portieren.
3. Standardakku-Read bzw. DUML-Push offline decodieren, Wertevalidierung und
   „unbekannt/veraltet“ statt Default-0 testen.
4. GimbalController mit begrenzten Kommandos, sequenzieller TX-Queue,
   Stop-Priorität, Watchdog und kontrollierter Rate; keine öffentlichen
   „send arbitrary hex“-Buttons.
5. Niedrige Einzelachsenbewegungen, Loslassen, Abbruch, Funkverlust, ACK vs.
   tatsächliche Bewegung prüfen. Center und Modi erst mit eigenen Nachweisen.

### Phase 2: Kamera

1. OC-Stable-Stand mit Provenienz und GPL-Notices importieren, Paket/Name ändern,
   dünnen Adapter an Aufnahme und Zoom setzen, Upstream-Tests erhalten.
2. Camera2-Capabilities je Kamera/Modus exportieren; Liste auswählbarer
   Auflösungen/FPS mit Encoderfähigkeiten schneiden. Nicht unterstützte
   manuellen Optionen ausblenden.
3. Nachgewiesene Hardwareevents deduplizieren, Aufnahme nur einmal pro Aktion,
   Kamerawechsel während Video verhindern, Zoom-Loslassen testen.
4. MediaStore scoped storage; eigene Medien ohne pauschale Leseberechtigung,
   Mikrofon nur bei Video mit Ton, fremde Medien über Photo Picker/Consent.

### Phase 3: Tracking

1. Gesicht/Person auf aufgenommenen Fixtures ohne Motor testen, Gewichte
   mit Lizenz/Hash bündeln, Flugmodus und erster Offline-Start prüfen.
2. Frame -> Display -> Motorachsen-Transformation, Spiegelung, Crop und
   Geräteausrichtung mit synthetischen Eckpunkten testen.
3. Targetassoziation und Regler mit simuliertem dt/Jitter/Verlust testen;
   Anti-Windup, Limits, alte Frames, manueller Stop.
4. Zuerst Diagnoseoverlay, dann langsame reale Nachführung, anschließend
   parallel Video. Benchmark und 30-Minuten-Thermiklauf dokumentieren.

### Phase 4: Kreativfunktionen

1. Persistenter Aufnahmejob mit Intervall, Dauer, Bildzahl, Video-FPS,
   Ausgabeauflösung; erwartete Länge N/FPS. Abbruch und voller Speicher.
2. AE/AF/WB-Lock je Kamera testen; MediaCodec/Muxer statt unnötiger FFmpeg-
   Integration. Nicht abgeschlossene Ausgaben sauber als unvollständig behandeln.
3. Panoramaplan anhand FOV/Überlappung und kontrollierbaren Grenzen; erst eine
   Reihe, dann 3×3. Einzelbilder erhalten, Bewegung beruhigen vor Auslösung.
4. Fünf Motionlapse-Punkte nur mit reproduzierbarer Positionierung; Soll/Ist,
   Vorschau und sanfte Segmente messen. Zeitintegration allein begründet keine
   Positionsgarantie. Hyperlapse nutzt gemeinsamen Zeitrafferpfad.

### Phase 5: Stabilisierung und Release

1. Galerie für eigene Medien, Wiedergabe/Teilen/Export/Löschen;
   erster Editorumfang Foto-Rotation/Zuschnitt und Video-Trim.
2. Kalibrierung nur mit belegtem Kommando, erlaubter Montage und auswertbarem
   Resultat. Firmware-Update bleibt ohne geprüftes Verfahren ausgeschlossen.
3. Beauty/Livestream separat bewerten; Netzwerk optional und vom Offline-Kern
   trennen. DJI-Konto/SkyPixel werden nicht integriert.
4. Release-Signierung, zugehöriger Quelltext, Lizenzen, Upgrade/Backup,
   Android-16-Langzeittests und nachvollziehbare bekannte Einschränkungen.

Für jede Phase: Debug-Ausgaben und Testprotokolle mit erwarteten/realen
Resultaten; automatisierte Tests ersetzen keine Geräteabnahme.
