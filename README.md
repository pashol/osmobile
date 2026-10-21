# OpenOM — Osmo Mobile 2 unter Android

Offene, native Android-Anwendung für den DJI Osmo Mobile 2 (OM170).
Referenzgerät: OnePlus Nord 4 mit Android 16.

**Aktueller Stand: Phase-0-Recherche und BLE-Diagnoseprototyp 0.2.0.**
Die APK enthält Berechtigungsdialog, BLE-Suche, manuelle Geräteauswahl,
GATT-Profilprüfung, FFF4-Notifications, Verbindungsstatus, begrenzte
Wiederverbindung, lokale Debug-Logs und read-only DUML-Reassembly mit CRC-Prüfung.
Auf dem Nord 4 mit Android 16 wurden
Suche, GATT-Profil, CCCD-Bestätigung, FFF4-Empfang und ein Power-Cycle-Reconnect
am Osmo-Gerät praktisch nachgewiesen. Ein unerwarteter Controller-Timeout im
ersten Durchlauf ist noch ungeklärt; die vollständige Hardwareabnahme steht
aus. Kamera, Motorsteuerung, Akku-Decoder und Tracking sind noch nicht
implementiert. Einzelheiten: [Testbericht](docs/test-results.md).

## Dokumentation

* [Architektur, Quellenvergleich und Lizenzprüfung](docs/architecture.md)
* [Feature-Matrix und Machbarkeit](docs/feature-matrix.md)
* [BLE-/DUML-Protokoll und offene Kommandos](docs/protocol.md)
* [Entwicklungsplan und Hardware-Gates](docs/development-plan.md)
* [Reproduzierbare Hardwaretests](docs/hardware-tests.md)
* [Tatsächliche Testergebnisse](docs/test-results.md)

Architekturentscheidung: **Open Camera als spätere Kamerabasis**, eigener
modularer OM2-Treiber; aktuell ein kleiner Diagnosehost. Für GATT wird Nordic
Android BLE Library wiederverwendet. Protokollwissen stammt vor allem aus
OM Research. Keine DJI-SDK-Abhängigkeit.

## Vorhandene APK

```text
app/build/outputs/apk/debug/app-debug.apk
```

Diese Debug-APK ist signiert und zur Installation vorgesehen. Die ebenfalls
gebaute `app/build/outputs/apk/release/app-release-unsigned.apk` benötigt vor
Installation eine eigene Release-Signatur. Builds/Hashes: [Testbericht](docs/test-results.md).

## Ubuntu: Entwicklungsumgebung

Benötigt werden **JDK 17**, Android SDK Platform **36**, Build Tools **35.0.0**,
Platform Tools (ADB) und Gradle **8.14.3** über den mitgelieferten Wrapper.
Android Gradle Plugin ist auf **8.13.2** festgelegt. Android Studio ist optional.

JDK beispielsweise über Ubuntu installieren:

```bash
sudo apt install openjdk-17-jdk unzip
```

Android SDK über Android Studio oder die offiziellen
[Command-line Tools](https://developer.android.com/studio#command-tools)
installieren. SDK-Lizenzen im SDK Manager bzw. interaktiv mit `sdkmanager`
lesen und akzeptieren. Nach der Installation:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
java -version
adb version
```

Im hier verwendeten Ubuntu-System liegt das SDK bereits unter
`/home/pascal/Android/Sdk`. Das voreingestellte Java/JAVA_HOME ist nicht
verlässlich JDK 17; deshalb für Builds ausdrücklich wie oben setzen.

### Bauen und prüfen

Im Projektverzeichnis:

```bash
./gradlew --version
./gradlew :protocol:test :app:assembleDebug :app:assembleRelease :app:lintDebug :ble:lintDebug
```

Der Wrapper überprüft den Gradle-Download per SHA-256. Die direkten
Abhängigkeiten sind versioniert; Auflösung der Laufzeitabhängigkeiten:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

Der erste Build benötigt Internet für Gradle/Maven-Artefakte. Nach vollständigem
Caching ist `./gradlew --offline :app:assembleDebug` möglich. **Die App selbst
benötigt kein Internet.** „Reproduzierbar“ bezeichnet hier festgelegte
Werkzeuge/Quellen und wiederholbare Buildbefehle; byte-identische signierte
APKs auf unterschiedlichen Rechnern sind nicht nachgewiesen. Insbesondere
erzeugt Gradle einen rechnerabhängigen Debug-Schlüssel.

### Installation und Start

Am Nord 4 Entwickleroptionen und USB-Debugging einschalten, per USB verbinden
und den RSA-Dialog bestätigen:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.openom.app/.MainActivity
```

`unauthorized`: Telefon entsperren und USB-Debugging bestätigen.
Leere Geräteliste: Kabel/USB-Modus/Ubuntu-udev-Zugriff prüfen.

**Wireless ADB (Android 11+):** Beide Geräte ins gleiche WLAN bringen. Am
Telefon Entwickleroptionen → Kabelloses Debugging aktivieren und „Mit
Kopplungscode koppeln“ öffnen. Die dort angezeigte *Kopplungsadresse* und
den kurzlebigen Code einmalig verwenden:

```bash
adb mdns services                 # _adb-tls-pairing und _adb-tls-connect
adb pair TELEFON-IP:KOPPLUNGSPORT
# 6-stelligen Code interaktiv eingeben
adb connect TELEFON-IP:VERBINDUNGSPORT
adb devices -l
```

Kopplungs- und Verbindungsport sind verschieden; der Verbindungsport kann
sich nach Neustart ändern. Bei mehreren ADB-Transporten desselben Telefons
alle nachfolgenden Befehle mit `adb -s TELEFON-IP:VERBINDUNGSPORT` ausführen.
Auf diesem Ubuntu-Rechner liegt `adb` unter
`/home/pascal/Android/Sdk/platform-tools/adb`, falls `PATH` noch nicht gesetzt
ist. Beim Sperren kann der Debug-UI-Test nicht per ADB getippt werden;
Telefon dafür entsperrt und App im Vordergrund lassen.

Für Installation direkt aus einem Dateimanager die APK auf das Telefon
übertragen und für diesen Dateimanager „Unbekannte Apps installieren“ erlauben.
Keine DJI-GO-Installation ist erforderlich.

## Erster Verbindungstest

1. Gimbal nach Handbuch bestücken und ausbalancieren, einschalten.
2. DJI GO und andere Gimbal/BLE-Verbindungen trennen. OM2 mit einem Host testen.
3. OpenOM starten → **Bluetooth-Berechtigungen** → Geräte in der Nähe erlauben.
4. Bei Bedarf **Bluetooth einschalten**, dann **15 s suchen**.
5. Eigenes Gerät anhand Name/Adresse auswählen. Ein umbenanntes Gerät kann
   ohne „Kandidat“-Hinweis erscheinen; auch dann ist es auswählbar.
6. Erwarteter Verlauf: Verbinden → Profilprüfung → **GATT bereit / FFF4 abonniert**.
7. Im Log `SERVICE`, `CHAR`, `CCCD bestätigt` und gegebenenfalls `RX FFF4`
   prüfen. „GATT bereit“ bedeutet noch keine eindeutige Modellidentifikation.

FFF0/FFF4/FFF5 werden geprüft. Die App schreibt lediglich die
Notification-Konfiguration (CCCD), **keine Motor-, Kalibrier- oder
Firmwarekommandos**. Ob ohne weitere Initialisierung bereits Pushnachrichten
eintreffen, ist am vorhandenen Gerät zu prüfen. Ein leeres RX-Log allein
beweist noch keinen Verbindungsfehler.

Bei unerwartetem Verlust einer zuvor bereiten Verbindung versucht die App
dreimal neu zu verbinden (Wartezeiten 1/2/4 Sekunden, je Versuch maximal
20 Sekunden). Initiale Verbindungsfehler verlangen manuelle Wiederholung.
**Trennen / Abbrechen** beendet die Versuche. Im Hintergrund wird getrennt;
bei Rückkehr bewusst neu verbinden. Bildschirmrotation erhält die Sitzung.

## Debugging

```bash
adb logcat -v threadtime -s OpenOM:D '*:S'
```

Für dauerhafte lokale Aufzeichnung in einer zweiten Shell:

```bash
adb logcat -v threadtime -s OpenOM:D '*:S' > openom-ble.log
```

Die App zeigt die letzten **250 Logzeilen** im RAM; **Log teilen** exportiert
diesen Ausschnitt über den Android-Teilen-Dialog. Längere Mitschnitte über
ADB erstellen. Logs enthalten Geräteadressen/Namen und RX-Daten; vor einer
öffentlichen Fehlerbeschreibung persönliche Kennungen entfernen.
Öffnen anderer Apps über „Teilen“/Einstellungen kann die Sitzung entsprechend
der Vordergrundregel trennen.

* Scanfehler 2/6 oder Android-Scanlimit: Suche stoppen, kurz warten, erneut
  versuchen; keine schnellen Wiederholungsschleifen.
* Fehlendes Profil / CCCD: vollständigen GATT-Dump prüfen; keine UUIDs raten.
* GATT-Fehler/Timeout: konkurrierenden Host trennen, Bluetoothzustand und
  Entfernung prüfen, einmal neu verbinden. Native GATT-Status und
  Nordic-„Grund“-Codes sind unterschiedliche Zahlenräume.
* Permissionfehler: App-Einstellungen → Geräte in der Nähe; danach neu suchen.
* Bei „Service Changed“ wird erneut geprüft und ein neues Zeitlimit gestartet.
* Kein Akku/Firmwarewert: derzeit absichtlich noch kein Decoder, kein Defekt
  der Kamera oder des Gimbals daraus ableitbar.

Für DJI-GO-HCI-Aufzeichnungen und kontrolliertes Reverse Engineering siehe
[protocol.md](docs/protocol.md#6-reproduzierbarer-reverse-engineering-ablauf).

## Release-Signierung

Ein eigenes, dauerhaft aufbewahrtes Schlüsselpaar verwenden. Passwörter
interaktiv eingeben; Schlüssel außerhalb des Quellverzeichnisses speichern.
Beispiel nach Anlegen eines privaten Schlüsselverzeichnisses:

```bash
keytool -genkeypair -keystore "$HOME/openom-keys/release.jks" \
  -alias openom -keyalg RSA -keysize 3072 -validity 10000

"$ANDROID_HOME/build-tools/35.0.0/zipalign" -f -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  app/build/outputs/apk/release/app-release-aligned.apk

"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks "$HOME/openom-keys/release.jks" --ks-key-alias openom \
  --out app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/apk/release/app-release-aligned.apk

"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose \
  app/build/outputs/apk/release/app-release.apk
```

Ein eigenes Release-Schlüsselpaar wurde hier nicht erzeugt. Debug- und
Release-Signaturen sind verschieden; Wechsel kann eine Deinstallation der
Debug-App erfordern. Updates müssen mit demselben Release-Schlüssel signiert sein.

## Struktur, Datenschutz und Lizenz

```text
app/        Native Diagnose-UI, Permissions, Lifecycle
ble/        Android-Scanner, Nordic-Transport, Gerätesitzung, Logs
protocol/   Android-freies Profilwissen und Retry-Regeln mit JVM-Tests
docs/       Recherche, Architektur, Protokoll, Plan, Testnachweise
licenses/   Vollständige Lizenztexte, in der APK über „Lizenzen“ sichtbar
```

Android 12+ (minSdk 31), Android 16 als primäres Ziel. Zur Laufzeit nur
BLUETOOTH_SCAN/CONNECT; kein Standort, Internet, Kamera, Mikrofon oder
Speicherzugriff im Prototyp. Bibliotheken ergänzen eine bis API 30 begrenzte
Legacy-Bluetooth-Berechtigung und eine appinterne Signature-Berechtigung.
Keine Accounts, Analytics, automatischen Uploads oder gespeicherten Medien.

GPL-3.0-or-later; siehe [LICENSE](LICENSE) und [Drittanbieterhinweise](licenses/NOTICE.txt).
Bei Weitergabe der APK den zugehörigen vollständigen Quelltext und die
Builddateien zugänglich machen. Die vorhandene DJI-GO-APK gehört nicht dazu.
Der nächste Entwicklungsschritt ist die dokumentierte reale BLE-Abnahme,
anschließend ein fixture-getesteter DUML-Codec und begrenzte Gimbalsteuerung.
