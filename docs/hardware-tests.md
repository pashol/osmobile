# Hardwaretestplan

Aktuelle Version 0.2.0, aktualisiert 2026-10-21. **Erste reale BLE-Fälle wurden
durchgeführt; die vollständige Hardwareabnahme steht aus.** Beobachtungen
und Grenzen stehen in [test-results.md](test-results.md).
Keine Motorbefehle in dieser APK. Gerät nach DJI-Handbuch montieren/balancieren.

## Protokollkopf je Testlauf

Datum/Tester, APK-SHA256, App-Version, OM170-Firmware (falls bekannt),
Nord-4-Modell/Android-/OxygenOS-Build, Akkuzustand, Montage, konkurrierende
Bluetooth-Hosts, Ergebnis und Logreferenz notieren. Unbekannte Felder als
„unbekannt“ eintragen, nicht schätzen.

## Phase 1a: BLE-Grundlage

| ID | Schritte | Erwartetes Ergebnis / Debugbeleg |
|---|---|---|
| B01 | APK installieren, kalt starten | UI erreichbar, keine Kamera-/Mikrofon-/Standort-Anfrage; keine Crashmeldung |
| B02 | Nearby Devices ablehnen, suchen; anschließend erteilen | Verständliche Meldung, kein Crash; nach Freigabe Suche möglich |
| B03 | Berechtigungen dauerhaft ablehnen; App-Einstellungen öffnen | Freigabe dort möglich, Rückkehr aktualisiert Anzeige |
| B04 | Bluetooth aus, suchen, über Systemdialog einschalten | Kein Hänger; Zustand sichtbar; nach Einschalten erneut suchen |
| B05 | OM2 einschalten, 15-s-Suche | Eigener Kandidat oder umbenanntes BLE-Gerät; Adresse/Name/RSSI; Suche endet automatisch |
| B06 | Während Scan stoppen, nochmals suchen | Keine fortlaufenden Treffer der alten Scaninstanz; neuer Scan begrenzt |
| B07 | Eigenes OM2 auswählen | CONNECTING → DISCOVERING → READY; FFF0, FFF4 NOTIFY, FFF5 WRITE; `CCCD bestätigt` |
| B08 | Nach READY 30 s warten, dann einzeln Hardwaretasten betätigen | RX-Hex notieren, falls vorhanden; keine Interpretation als Foto/Video. Ausbleibendes RX dokumentieren, nicht als Test-Pass ausgeben |
| B09 | Ein anderes eigenes BLE-Gerät ohne Profil auswählen | Verständlicher Profil-/Verbindungsfehler; niemals READY ohne Characteristics/CCCD |
| B10 | Gimbal nach READY ausschalten, nach 5 s einschalten | Verlust sichtbar; höchstens drei Versuche pro Verlustepisode; Rückkehr nur bei erfolgreichem Profil/CCCD |
| B11 | Gimbal nach READY ausgeschaltet lassen | Retry-Budget endet, keine endlose Schleife; manuelle Neuwahl möglich |
| B12 | „Trennen / Abbrechen“ während CONNECTING/RETRY_WAIT drücken | Verbindung/Timer abgebrochen; spätere alte Callbacks setzen nicht erneut READY |
| B13 | READY, Bluetooth am Telefon ausschalten | Keine Crashmeldung; „Bluetooth ausgeschaltet“/getrennt; keine automatische Endlossuche |
| B14 | Gerät außer Reichweite bringen und wieder annähern | Verlust/Timeout/Retry nachvollziehbar; Erfolg nicht aus bloßem GATT-Connect abgeleitet |
| B15 | Während Scan, Connect und READY je dreimal rotieren | UI bedienbar mit Insets; genau eine Sitzung; kein zusätzlicher Connect nur durch Rotation |
| B16 | Home/Display aus während READY oder Retry | Scan/GATT/Retry beendet; Rückkehr bleibt getrennt und erlaubt manuelles Verbinden |
| B17 | In Systemeinstellungen Permission entziehen, zurückkehren | Kein Crash; fehlende Berechtigung sichtbar, keine Fortsetzung alter Sitzung |
| B18 | Flugmodus, Bluetooth gezielt wieder aktivieren; kalt starten und verbinden | Keine Internet-/Kontoabhängigkeit; tatsächliches Resultat notieren |
| B19 | 20 Verbindungs-/Trennzyklen, dann 15 Minuten READY | Keine Ressourcen-/GATT-Client-Erschöpfung; Logs erreichbar; Hintergrundregel bleibt korrekt |
| B20 | Log teilen/leeren, erneut verbinden | Export enthält Zeit/Richtung/UUID; leeren wirkt; keine automatische Übertragung |
| B21 | App-Lizenzen öffnen unter Android 12 und Android 16 | Lizenztexte ohne API-Crash lesbar; UI schließbar |
| B22 | Eigenen GATT-Testperipheral mit FFF0/4/5 und Service Changed verwenden; nach READY Services ändern | READY wird DISCOVERING; erneutes CCCD erforderlich. Bei blockierter Rediscovery nach 20 s Fehler/Retry statt dauerhaft READY |
| B23 | Testperipheral: CCCD-Write ablehnen oder nicht beantworten | Kein READY trotz Nordic-Initialisierungsabschluss; Fehler/Timeout mit Log |

B22/B23 benötigen einen kontrollierten BLE-Testperipheral, nicht willkürliche
Manipulation des OM2. Falls nicht vorhanden, offen lassen. Das ist derzeit
kein automatisierter Android-Integrationstest.

### Gate für Kameraentwicklung

B01–B07, B10–B18 und wiederholbare Verbindungen müssen am Nord 4/OM170
bestanden sein. B08 muss entweder RX-Fixtures liefern oder als offener
Initialisierungsbedarf untersucht werden. Bei unbekanntem Profil/fehlendem
CCCD zunächst Protokoll klären, nicht Kamera/Tracking darauf aufbauen.

### ADB-Mitschnitt

```bash
adb devices -l
adb logcat -v threadtime -s OpenOM:D '*:S' > openom-ble.log
```

Vor Testbeginn Logaufzeichnung starten, Aktionen mit Uhrzeit notieren.
Die App selbst hält nur 250 Zeilen. Für neue Kommandos siehe protocol.md.

## Phase 1b: Steuerung nach separater Freigabe

* G01: Akkuwerte über Lade-/Entladezustände mit GO/LEDs vergleichen; ungültige
  Payload/fehlende Telemetrie = unbekannt, nicht 0 Prozent.
* G02: Einzelachse im Zentrum ±5°, erst nach Einheitenbestätigung langsame
  Bewegung; Sollrichtung/Istbewegung/Stopzeit messen. Dann diagonal.
* G03: Loslassen, Cancel, Hintergrund, Verbindungsverlust und Watchdog;
  keine fortgesetzten alten Bewegungsbefehle nach Wiederverbindung.
* G04: Follow/Free, Walk/Sport, Tilt Lock jeweils einzeln setzen/lesen;
  Recenter gesondert statt absolut 0 voraussetzen.
* G05: Wiederholbare absolute Positionen aus beiden Richtungen, nach Neustart
  und mit anderer Griffausrichtung; Referenzrahmen und Fehler messen.

Erwartete Bytes kommen aus bestätigten Fixtures, nicht aus erfundenen Tests.

## Phase 2: Kamera

C01 Capability-Dump jeder Kamera; nur unterstützte Optionen anzeigen.
C02 Foto/Burst/RAW/HDR je verfügbarer Kombination, Medien nach App-Neustart lesbar.
C03 Video je angebotener Auflösung/FPS, Ton optional, Start/Stop und Hardwareevent
genau einmal; Front-/Rückwechsel nicht während Video.
C04 Zoom/AF/AE/WB/ISO einschließlich unsupported-Fällen.
C05 voller Speicher, entzogenes Mikrofon/Kamera, MediaStore-Fehler und Abbruch.
Logs: Kamera-ID, Capability, Request/Result, Dateiformat, tatsächliche Dauer/FPS,
Fehler; keine Bilddaten im Diagnoselog.

## Phase 3: Tracking

T01 ein Gesicht/eine Person, Antippen, kein ungewollter Zielwechsel.
T02 mehrere Personen, Verdeckung, Verlassen des Bilds, Wiederkehr; Verlust stoppt.
T03 alle Displayrotationen, Frontkameraspiegelung und Zoom/Crop.
T04 30 Minuten Video+Tracking; p50/p95 Inferenz, Framealter, Reglerausgang,
BLE-Latenz, Dropped Frames, Temperatur und Akku notieren.
T05 manuelles Stoppen und Gimbal-Disconnect jederzeit wirksam.
T06 Erststart offline mit gebündelten Modellen; keine Modelldownload-Abhängigkeit.

## Phase 4: Kreativfunktionen

K01 Intervall/Dauer/Ausgabe-FPS: Bildanzahl und Videolänge mit Soll vergleichen.
K02 Timelapse AE/AF/WB-Konsistenz und voller Speicher/Abbruch.
K03 180°-Einzelreihe und 3×3: Überlappung, Reihenfolge, Stitching, Anschlagabstand.
K04 fünf Motionlapse-Punkte, Vorschau und echte Aufnahme wiederholen;
Positionsabweichung/Übergänge messen, kein Drift als Präzision ausgeben.
K05 Hyperlapse beim Gehen, Gimbal- und elektronische Stabilisierung getrennt bewerten.

## Phase 5: Release

R01 Galerie/Teilen/Löschen/Export samt URI-Rechten und Fremdmedien-Consent.
R02 Bildzuschnitt/Rotation und Videotrim, Originale nachvollziehbar erhalten.
R03 Kalibrierung nur horizontal und mit bestätigtem Verfahren.
R04 1-Stunden-Aufnahmeläufe, App-Kill, Neustart, Speicherknappheit.
R05 signiertes Upgrade mit gleichem Schlüssel; Quellen/Lizenzen passend zur APK.

## Ergebnisvorlage

```text
Test-ID:
Datum / Tester:
APK-Version / SHA256:
Telefon / Android-Build:
Gimbal-Modell / Firmware:
Voraussetzungen und Schritte:
Erwartet:
Beobachtet:
Log-/Fixture-Referenz:
Ergebnis: bestanden / fehlgeschlagen / nicht durchgeführt
Bekannte Einschränkung / Folgeaufgabe:
```
