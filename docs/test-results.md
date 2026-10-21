# Tatsächliche Testergebnisse

Stand 2026-10-21. Aktuelle App 0.2.0 / VersionCode 2; der erste
BLE-Hardwarelauf unten erfolgte mit 0.1.0 / VersionCode 1.

## Softwareprüfung unter Ubuntu

Umgebung: JDK 17 explizit gesetzt, SDK `/home/pascal/Android/Sdk`, Platform 36,
Gradle 8.14.3 (Wrapper mit SHA-256), Android Gradle Plugin 8.13.2.

Ausgeführt:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
ANDROID_HOME=/home/pascal/Android/Sdk \
./gradlew :protocol:test :app:assembleDebug :app:assembleRelease :app:lintDebug :ble:lintDebug
```

| Prüfung | Ergebnis | Umfang |
|---|---|---|
| `:protocol:test` | **18 Tests bestanden**, 0 Fehler, 0 übersprungen | 4 Profilerkennungs-, 2 Retry-, 3 CRC- und 9 Stream-/Frame-Fälle |
| `:app:assembleDebug` | **Bestanden** | Signierte installierbare Debug-APK gebaut |
| `:app:assembleRelease` | **Bestanden** | Unsigned Release-APK gebaut; noch kein Release-Schlüssel |
| `:app:lintDebug` | **No issues found** | Android-Lint App |
| `:ble:lintDebug` | **No issues found** | Android-Lint BLE-Modul |
| `apksigner verify --verbose` Debug | **Verifies**, APK-Signature v2, 1 Signer | Signaturintegrität; Installation separat nachgewiesen |
| `aapt dump badging` Debug | Paket `org.openom.app`, minSdk 31, target/compileSdk 36 | Keine INTERNET/CAMERA/Standort-/Mikrofon-/Speicherberechtigung |
| `adb devices -l` | **CPH2663 über Wireless ADB autorisiert** | Nord 4 / Android 16 (SDK 36), Geräteinstallation erfolgreich |

Testberichte: `protocol/build/test-results/test/` und
`protocol/build/reports/tests/test/index.html`.
Lint-Textberichte unter `app/build/intermediates/lint_intermediate_text_report/`
und entsprechend `ble/build/`.

## Hardwaretest: 0.1.0, Nord 4 / Osmo-Gerät, 2026-10-21

Gerät: OnePlus Nord 4 (`CPH2663`), Android 16 / API 36,
Sicherheitsstand 2026-09-01; Gimbal war eingeschaltet und laut Nutzer beim
ersten Abbruch unverändert in Reichweite. Modell-/Firmware-ID vom Gimbal
nicht abgefragt. Kandidat erschien als `OsmoM…`; Serienkennung und MAC in
diesem Bericht gekürzt. Wireless ADB via Android-TLS-Pairing; die *signierte*
Debug-APK mit dem unten genannten SHA-256 per `adb install -r` installiert.
Vorheriges lokales `adb devices` ohne USB-Verbindung ist durch diesen Lauf
überholt.

| Test | Beobachtung | Bewertung |
|---|---|---|
| B01 Installation/Start | `adb install -r`: Success; Activity startet, Prozess lebt, Log meldet Android 16/IDLE | Bestanden für Install/Start; kein Kaltstart- oder UI-Kompletttest |
| B02 Berechtigung | `BLUETOOTH_SCAN`/`CONNECT` am Gerät als `granted=true` geprüft | Erteilte Rechte nachgewiesen; Ablehnung/Widerruf offen |
| B05 Suche | 20:54:27 Kandidat `OsmoM…` bei −69 dBm; nach Power-Cycle 21:02:54 erneut bei −54 dBm. Zwischenscan über 15 s fand 49 andere Geräte, Osmo zu dem Zeitpunkt nicht sichtbar | Eigener Kandidat bei Werbung gefunden; Nicht-Sichtbarkeit nach Abbruch ungeklärt |
| B07 Profil/CCCD | 20:54:35 und 21:03:04: GATT-Connect, FFF0-Service, FFF4 `NOTIFY` (0x10), FFF5 `WRITE_NO_RESPONSE` (0x04), GATT-CCCD `2902 <- 01 00`, **Bestätigung** und READY | Erstverbindung **bestanden**, langfristige Stabilität nicht abgenommen |
| B08 Empfang/Taste | Fortlaufend zahlreiche `RX FFF4`-Hexdaten; einzelner Auslöserdruck gegen 21:04:48 durchgeführt. Kein einzelnes Tastenevent sicher vom kontinuierlichen Datenstrom abgegrenzt | RX bestanden; Tastensemantik **offen** |
| B10 Power-Cycle/Retry | Zweite Verbindung blieb von 21:03:04 bis zum absichtlichen Ausschalten gegen 21:05:23 READY (≈139 s); nach Wiedereinschalten Reconnect um 21:05:30, CCCD erneut bestätigt und READY 21:05:31; danach wieder RX | **Ein** kontrollierter Power-Cycle mit Reconnect bestanden |
| Manuelles Trennen | 21:06:10 in der App, danach IDLE | Aus READY bestätigt; während CONNECTING/RETRY_WAIT noch offen |

Erster Lauf: Bereits um 20:55:03, etwa **28 Sekunden nach READY**, trennte
die BLE-Verbindung unerwartet bei unverändertem Gimbal/Telefon (Nutzerauskunft).
Die Android-ACL-Historie dokumentiert `CONNECTION_TIMEOUT (0x08)`;
Nordic protokolliert GATT-Status 8. Die App meldete den Verlust und versuchte
1/2/4 Sekunden verzögert erneut; diese Versuche schlugen fehl oder liefen ins
20-Sekunden-Zeitlimit. Ein Folgescan sah den Osmo nicht. Der Bluetooth-Dump
zeigte noch einen LE-Link zu dessen Adresse mit anderen registrierten Clients;
ohne weiteren Nachweis ist **keine** andere App als Ursache identifiziert.
Nach einem vom Nutzer durchgeführten Neustart des Gimbals erschien die
Werbung wieder und die zweite Verbindung blieb bis zum geplanten Abschalten
stabil. RSSI war beim ersten Scan −69 dBm, beim zweiten −54 dBm; Funkqualität
ist eine Hypothese, **keine** nachgewiesene Fehlerursache.

Beispiel aus `RX FFF4`, **GATT-Notification ist nicht zwingend ein komplettes
DUML-Paket** (lange Frames kamen in 20-Byte-Fragmenten):

```text
20:55:03.401  55-35-04-68-09-02-4E-00-00-05-06-64-00-00-00-00-00-00-64-00
20:55:03.404  00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00
20:55:03.405  00-00-00-00-00-00-00-00-00-00-00-96-21
```

Der Header enthält `05/06` (aus OM Research bekannter Akku-Push); `0x64`
wird **nicht** ohne Frame-/Felddekodierung als Prozentangabe gewertet.
Reassembler und CRC-Validierung sind noch nicht implementiert. Der vollständige
temporäre Mitschnitt liegt lokal unter `/tmp/opencode/openom-ble.log` und
enthält fremde BLE-Adressen; er gehört **nicht** zu den öffentlichen Quellen.
Für spätere Tests nach Bedarf erneut aufzeichnen.

## Read-only DUML-RX: 0.2.0-Code, 2026-10-21

TDD: Der CRC-Test scheiterte zunächst an fehlendem `DumlCrc`, der Parser-Test
an fehlendem `DumlStreamDecoder`/`DumlPacket`; nach der Implementierung
bestanden alle vorhandenen und die **12 neuen JVM-Tests**. Die neuen Tests
prüfen echte 14- und 53-Byte-FFF4-Frames mit unabhängig ermittelten
CRC8-/CRC16-Werten, 20+20+13-Fragmentierung, alle Trennstellen eines kleinen
Frames, zwei Frames in einem Chunk, Resynchronisierung nach Müll/CRC-Fehler,
ungültige Header/Protokollversion und Reset bei Verbindungsverlust. Der
Versions-Test schlug beim gezielten Entfernen der Versionsprüfung fehl und
bestand nach Wiederherstellung (Mutationstest). Beide Module linten ohne
Befund, Debug/Release bauen weiterhin; finale Artefakt-Hashes unten.

Ein Zwischenbuild mit identischem Decoder-Code (vor Erhöhung der sichtbaren
Version auf 0.2.0) wurde per Wireless ADB installiert und read-only am
Osmo-Gerät getestet. 21:20:40 Scan-Treffer bei −55 dBm; 21:20:53 GATT/CCCD
erfolgreich, READY. Im lokalen Mitschnitt
`/tmp/opencode/openom-duml-live.log` erschienen genau diese **acht erstmals
gesehenen CRC-geprüften Set/ID-Paare**:

| Set | ID | Erste Sichtung | Aussage |
|---|---|---|---|
| `04` | `1C` | 21:20:53.638 | Payloadsemantik offen |
| `04` | `05` | 21:20:53.669 | Payloadsemantik offen |
| `04` | `57` | 21:20:53.672 | Payloadsemantik offen |
| `02` | `B8` | 21:20:53.763 | Payloadsemantik offen |
| `04` | `24` | 21:20:54.145 | 89-Byte-Payload wurde fragmentiert empfangen |
| `05` | `06` | 21:20:54.176 | Upstream bekannter Akku-Push; Wert noch **nicht** decodiert |
| `00` | `F1` | 21:20:54.556 | Payloadsemantik offen |
| `04` | `27` | 21:20:54.599 | Payloadsemantik offen |

Bis zum manuellen Trennen um 21:21:42 wurde **kein** Parser-Header-/CRC-
Fehler gemeldet. Ein einzelner Fotoauslöserdruck (Nutzerauskunft) lieferte
keinen *neuen* Command-Set/ID-Typ; ohne Vergleich der vollständigen Payloads
und HID-Reports sagt das **nichts** über den Transport des Tastenevents aus.
Es wurden keinerlei DUML-TX-/Motorbefehle gesendet. Die Rohmitschnitte
enthalten fremde Gerätekennungen und verbleiben lokal.

Die finale, neu signierte **0.2.0-Debug-APK** wurde anschließend via
`adb install -r` installiert; `dumpsys package org.openom.app` bestätigt
`versionCode=2`, `versionName=0.2.0` und beide weiterhin erteilten
Nearby-Devices-Rechte. Ein weiterer GATT-Lauf nach dem reinen Versionswechsel
wurde nicht durchgeführt.

## Entwicklungsbefunde

* Test-first: erster Testlauf scheiterte an fehlenden Klassen OmProfile und
  RetryPolicy; nach Implementierung alle sechs Tests erfolgreich.
* Erster umfassender Build überschritt das Tool-Zeitlimit. Wiederholung mit
  längerem Zeitlimit baute beide APKs und zeigte einen echten Lint-Fehler.
* Lint fand `InputStream.readAllBytes()` (erst API 33, minSdk 31). Durch
  kompatiblen gepufferten Leseweg ersetzt; erneute Lint-Prüfung erfolgreich.
* Manifestwarnungen zu App-Icon und Backup-Regeln durch eigenes Vektoricon
  und Android-12-Datentransferregeln behoben.
* Read-only-Code-Review prüfte zusätzlich die Nordic-2.11.0-Implementierung.
  Befund: Service Changed invalidiert Services ohne neuen Connected-Callback.
  Der Prototyp wechselt jetzt dabei in DISCOVERING und startet ein neues
  Initialisierungszeitlimit. Reale Prüfung B22 bleibt offen.
* RSSI wurde zunächst nur beim Erstellen eines Gerätebuttons angezeigt;
  aktualisiert sich nun ohne Austausch des Buttons.
* Compiler meldet die Nutzung älterer Activity-Lifecycle-APIs; Gradle meldet
  deprecated Features mit Blick auf Gradle 9. Der festgelegte Gradle-8-Build
  besteht. Kein Kompatibilitätsversprechen für Gradle 9.

## Testgrenzen / bekannte Einschränkungen

**Ein begrenzter Hardwaretest, kein Emulator-Lauf und keine vollständige
Hardwareabnahme.** Permission-Ablehnung, Edge-to-Edge/Rotation, Hintergrund-
Abbruch, systematischer 20-Zyklen-/Langzeittest und GATT-Negativtests sind
weiter offen. Ein einzelner kontrollierter Reconnect ersetzt keinen
Robustheitstest. Die JVM-Tests testen Profilregeln und Retrybudget, nicht
Androids GATT-Callbacks.

DUML-Framing und Prüfsummen sind implementiert; **keine** Interpretation der
Payload, kein Akku-/Firmwaredecoder und kein TX-Encoder. Keine Motorbefehle,
Kamera, Tracking oder Kreativfunktionen. Service-/Namens-
Übereinstimmung ist keine eindeutige Modellidentifikation. Automatisches
Wiederverbinden erfolgt nur im Vordergrund für eine zuvor bereite Sitzung.

Der In-App-Log ist auf 250 Zeilen begrenzt; längere Aufzeichnungen benötigen
ADB. Ein Service-Changed-/CCCD-Negativtest braucht einen Testperipheral.
Offene Hardwarefälle und Sollresultate: [hardware-tests.md](hardware-tests.md).

## Finale Artefakte

Nach Implementierung des read-only-Decoders und dem Versionswechsel wurden
beide APKs erneut erfolgreich gebaut und die Debug-Signatur erneut geprüft.

| Datei | Größe | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 4.121.764 Bytes | `0ce2c06c7ebf9b5296bed729c2cce04fdccb5c81755b47fdb24be6a56a4b79f5` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3.483.882 Bytes | `0c78aff98a61c422e6916cc75e336284ed6acbaa77f12e4df7a605a9be83cb36` |

Die Debug-APK ist signiert. Die Release-APK ist **nicht signiert** und benötigt
vor Installation den im README dokumentierten eigenen Signierprozess.
