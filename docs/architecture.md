# Architektur und technische Bestandsaufnahme

Stand: 2026-10-20. Ziel: OM170 + OnePlus Nord 4 / Android 16. Diese
Entscheidung wurde **vor Beginn der Prototypimplementierung** getroffen.
Hardwaremessungen liegen noch nicht vor. Quellenstatus und Projektstatus sind
getrennt: Eine upstream getestete Funktion ist kein Hardwaretest dieser App.

## 1. Ergebnis / ADR-001

**Entscheidung: schlanker, Java-basierter Open-Camera-Fork als spätere
Produktanwendung, mit unabhängigem BLE-Modul und reinem Java-Protokollkern.**
Zunächst entsteht eine kleine Diagnose-APK als Host dieser Module. Erst nach
bestandener OM2-Verbindungsprüfung wird Open Camera integriert. Dadurch werden
die umfangreichen Kamerafunktionen wiederverwendet, ohne die erste BLE-Prüfung
an eine Kameraintegration zu koppeln. Die Diagnose-APK ist noch kein
Open-Camera-Fork und enthält noch keinen Kameracode.

Die Anwendung und neue Module werden unter **GPL-3.0-or-later** entwickelt.
DJI SDK, DJI-Konto und DJI-Cloud sind keine Laufzeitabhängigkeiten.

### Vergleich

| Variante | Wiederverwendung | Wartbarkeit / Aufwand | Nachteile | Bewertung |
|---|---|---|---|---|
| A: Open Camera mit schmaler OM2-Erweiterung | Foto, Video, Camera2, manuelle Einstellungen, HDR, RAW, Intervalle, Speicherung, Panorama-Verarbeitung | Wenigste neue Kameraalgorithmen; Java und Views passen zum Bestand; Upstream regelmäßig übernehmen | Große, gekoppelte Klassen; Tracking-Bildpfad muss integriert werden; Forkpflege; GPL | **Gewählt** für funktionale Breite |
| B: Neue CameraX-App + Camera2 Interop | Kamera-Lifecycle, Preview, Aufnahme, ImageAnalysis, Geräte-Workarounds | Gute Grenzen für Tracking und neue UI; langfristig übersichtlich bei kleinerem Funktionsumfang | HDR-Bracketing, RAW-Workflow, manuelle Kontrolle, Panorama, Timelapse-Export und deren Zusammenspiel zusätzlich zu entwickeln; Interop nicht gleich garantierte Kontrolle aller Requests | Rückfalloption, falls Open-Camera-Analysepfad untragbar ist |
| C: Nur Open-Camera-Controller als Bibliothek + neue Oberfläche | CameraController1/2 und Preview | Von Upstream ausdrücklich beschrieben | ApplicationInterface, Speicherpfad, HDR/Panorama, Einstellungen und UI müssen wieder verbunden werden; viel Integrationsarbeit | Spätere Extraktion möglich; kein Vorteil für ersten Vollfunktionsstand |
| D: Osmosis-Fork | Android BLE, DUML-CRC und Medien-UI | Modernes Reverse-Engineering-Beispiel | Externe Kameras über BLE/WLAN, kein Smartphone-Kamerastack und keine OM2-Befehle | Nicht als App-Basis geeignet |

## 2. Untersuchte Primärquellen

Quellen sind am oben genannten Datum abgerufen. Hashes beziehen sich auf die
tatsächlich lokal untersuchten Git-Stände, nicht auf hypothetische Releases.

| ID | Quelle / Stand | Konkret untersucht |
|---|---|---|
| OM | [alkersan/om-research](https://github.com/alkersan/om-research/tree/b27c9230166346300af24cda95e487de69fddf5a), `b27c9230166346300af24cda95e487de69fddf5a`, 2026-02-04 | README, LICENSE, gimbal.py, duml.py, rotate.py, misc/attributes.md, web-bluetooth/app.js; SDK-App als Referenzzweck |
| OC | [Open Camera](https://sourceforge.net/p/opencamera/code/ci/0dd4cbe78872df2c6e4eb6cee3fb0d5637b0f52e/tree/), `0dd4cbe78872df2c6e4eb6cee3fb0d5637b0f52e`, v1.56.2, 2026-05-27 | Gradle, opencamera_source.txt, remotecontrol/BluetoothRemoteControl.java, Controller-/Preview-Struktur und [Funktionshilfe](https://opencamera.org.uk/help.html) |
| OS | [KonradIT/osmosis](https://github.com/KonradIT/osmosis/tree/6992036abc29a01126a728443f1d29a83a7ef467), `6992036abc29a01126a728443f1d29a83a7ef467`, 2026-09-17 | README, LICENSE.txt, duml/DjiCrc.kt, DjiMessage.kt; Herkunftshinweise zu dji-remote |
| DJI-M | [Originalhandbuch v1.2, 2018.11](https://dl.djicdn.com/downloads/Osmo%20Mobile%202/20181107/Osmo_Mobile_2_User_Manual_EN.pdf) | S. 8–18: Tasten, Modi, Kamera, Kreativfunktionen, Grenzen, Updates |
| DJI-F | [Offizielle OM2-Supportseite / FAQ](https://www.dji.com/support/product/osmo-mobile-2) | Spezifikationen, fünf Motionlapse-Punkte, Joystick-Geschwindigkeit, Beauty, Livestream, Bluetooth-Gerätenamen |

Die allgemeine DJI-Produktseite lieferte beim Abruf keine verwertbaren
Funktionsdetails; Handbuch und produktspezifische FAQ sind die Referenzen.
Die FAQ nennt stellenweise einen „Trigger“; für den OM2 gilt die im Handbuch
gezeichnete Power/Mode-Taste. Hochkantmontage des Telefons ist außerdem nicht
identisch mit dem durch Griffdrehung erreichten „Portrait Mode“ auf S. 12.

OMs `sdk-app` verwendet DJI Mobile SDK **4.18**, registriert die App über
`DJISDKManager.registerApp`, sucht über den DJI-Bluetooth-Connector und sendet
absolute SDK-Rotationen. Das ist eine Quelle für kontrollierte Referenzmitschnitte,
keine freie Transportimplementierung ohne DJI-Abhängigkeit. Deshalb wird der
Python-/Web-Protokollansatz statt dieser SDK-App portiert.

### Open Camera im Detail

* Java, Android Views/AppCompat, `CameraController` abstrahiert Camera1/Camera2.
  `Preview` enthält auch Aufnahmeabläufe. `ApplicationInterface` verbindet
  Kamera und Anwendung; `MyApplicationInterface` realisiert die App-Anbindung.
  `ImageSaver`, HDR-/Panorama-Verarbeitung und Speicherverwaltung gehören zum
  Gesamtpaket, nicht zu einer leicht austauschbaren CameraX-Komponente.
* Geprüfter Build: compileSdk/targetSdk **36**, minSdk **23**, Java 17,
  AGP 8.13.2. Das ist belegte Android-16-Ausrichtung, kein Nord-4-Hardwaretest.
* Foto, Video, Front/Rückkamera, Touch-Fokus/-Messung, AE-Lock, Zoom, Flash,
  Raster und unterstützte Auflösungen sind vorhanden.
* Camera2: ISO, Belichtungszeit, manueller Fokus, Weißabgleich, DNG/RAW,
  Bracketing/Burst abhängig von Kameraeigenschaften. Software-HDR durch
  Belichtungsreihen ist von OEM-HDR/Ultra-HDR und Video-HDR zu unterscheiden.
* Intervalle/Repeat und beschleunigte Videoaufnahme sind vorhanden. Ein
  wiederaufnehmbarer Einzelbild-zu-Video-Workflow mit frei wählbarer Dauer ist
  zusätzliche Integrationsarbeit; kein automatischer Gleichstand mit DJI GO.
* Panorama ist vorhanden, aber als nutzergeführte Bildfolge. Eine motorisierte
  180°- oder mehrzeilige 3×3-Aufnahme ist damit noch nicht implementiert.
* Slow Motion hängt von Camera2 und High-Speed-Konfigurationen ab.
* `remotecontrol` unterstützt Kraken-Smart-Housing und zeigt geeignete
  Einhängepunkte: `triggerRemoteControlAction()`, Kamera-/Videomodus und Zoom.
  Es ist **kein DJI-Treiber**. Bestehende Remote-Ereignisse dürfen erst nach
  OM2-Ereignisnachweis angebunden werden.
* Galerie-Schaltfläche öffnet üblicherweise eine externe Galerie. Eine eigene
  vollständige Galerie und Medienbearbeitung werden dadurch nicht geliefert.
* Wartungsstrategie: Upstream unverändert importieren und Herkunft festhalten;
  OM2-Code in eigenen Modulen, dünner Adapter an Aufnahme/Zoom und Overlay.
  Kein großflächiger Umbau von MainActivity/Preview. Upstream-Tests erhalten.
* Risiko: paralleler YUV-Analyseausgang während Video muss auf dem Nord 4 mit
  Camera2-Streamkombinationen geprüft werden. Kein zweiter Kamerabesitzer neben
  Open Camera; kein parallel geöffnetes CameraX für dieselbe Kamera.

### Weitere technisch nutzbare Projekte

| Projekt | Nutzbarer Baustein | Grenzen / Entscheidung |
|---|---|---|
| [Nordic Android BLE Library](https://github.com/NordicSemiconductor/Android-BLE-Library), 2.11.0 | Java `BleManager`, GATT-Queue, CCCD, Service-Erkennung, Verbindungs-Timeout, Fragmentierung | **Im Prototyp verwenden**. Kein Scanner enthalten; Android BluetoothLeScanner genügt ab API 31. Neue Kotlin-BLE-2.0-Linie laut README noch früh, hier nicht gewählt. |
| [dimadesu/dji-remote](https://github.com/dimadesu/dji-remote) | DUML-CRC, Android-BLE-Beispiele | Action/Pocket-Streaming; kein OM2-Nachweis. Osmosis übernimmt CRC-Code von hier. Kein Auth-/Pairing-Flow dieser Kameras auf OM2 übertragen. |
| [o-gs/dji-firmware-tools](https://github.com/o-gs/dji-firmware-tools) | `comm_dissector`, `comm_dat2pcap`, allgemeiner DUML-Aufbau | Analysewerkzeuge, keine nachgewiesene OM2-Kommandoliste. Keine Firmware-/Kalibrierskripte für Drohnen ausführen. Dateilizenzen vor Codeübernahme prüfen. |
| [GrapheneOS Camera](https://github.com/GrapheneOS/Camera) | CameraX-basierte OSS-Kamera, MIT | Alternative zu Neuentwicklung für Basiskamera; keine hier nachgewiesene gleichwertige manuelle/Kreativ-Pipeline wie Open Camera. |
| [MediaPipe](https://github.com/google-ai-edge/mediapipe), [Android-Objektdetektor-Beispiel](https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/object_detection/android) | Lokale Detektion, Face Detector, EfficientDet-Lite/MobileNet mit Person-Klasse | Für Phase 3 bevorzugt; Runtime und Modell jeweils separat lizenzieren, Gewichte mit Hash bündeln. Beispiel lädt beim Build, nicht erst zur Laufzeit. |
| [ML Kit Face Detection](https://developers.google.com/ml-kit/vision/face-detection/android) | Einfacher schneller Gesichtsdetektor, Tracking-IDs | Gebündelte Variante offline, Play-Services-Variante benötigt ggf. Download. SDK nicht als vollständig OSS voraussetzen, [Terms](https://developers.google.com/ml-kit/terms) prüfen. Wegen OSS-/Datenschutzanforderung nicht Standard. |
| [LiteRT](https://github.com/google-ai-edge/LiteRT) / TensorFlow Lite | Lokale Inferenz und Delegates | Runtime allein ist kein Detektor/Tracker; zusätzlicher Modell-/Preprocessing-Aufwand. Alternative unter Detector-Schnittstelle. |
| [OpenCV 4.x](https://github.com/opencv/opencv) | Stitching/Features/Warping, optischer Fluss | Apache-2.0 im geprüften 4.x-LICENSE; nur falls Open-Camera-Panorama für Mehrzeilen nicht genügt. Native Builds/16-KB-Seiten auf Android 16 prüfen. |
| [CyrilSiman/OsmoSphere](https://github.com/CyrilSiman/OsmoSphere) | Historischer Ansatz für Panorama-Bahnsteuerung | Archiviert, README nennt Galaxy S6/Marshmallow und Osmo Mobile, kein OM2-Test und kein verwendbarer lizenzierter Treiber im untersuchten Material. Nicht übernehmen. |

Die GitHub-Repositoriesuche `osmo mobile android` und die Referenzketten von OM
Research/Osmosis ergaben keinen nachgewiesen vollständigen, wartbaren freien
OM2-DJI-GO-Ersatz. Das ist ein Rechercheergebnis, kein Beweis der Nichtexistenz.

## 3. Modulgrenzen

```text
Diagnose-UI (Phase 0/1a)          Open-Camera-Fork (ab Phase 2)
             \                  / Kameraadapter + Overlay
              Gerätesitzung / Capability- und Zustandsmodell
                    |                          |
            BLE-Transport                  GimbalController (später)
       Nordic + Android-Scanner                  |
                    |                       DUML-Codec
                    +------ Raw RX ------> Protokollkern

Kamera-Frame -> Detector -> Zielassoziation -> Regler -> GimbalController
                      (separate begrenzte Queues / Zeitstempel)
```

Aktueller Dateiplan: `app/` Diagnoseoberfläche und Permission-Flow;
`ble/` Scanner, Nordic-Adapter und Sitzungssteuerung; `protocol/` Android-freie
UUID-/Profilregeln und später DUML-Codec; `docs/` Evidenz und Testprotokolle.
Keine Dummy-Steuerbefehle oder simulierten Akkuwerte in der UI.

* Nur die Transportebene kennt BluetoothGatt. UUIDs kommen aus OM Research.
* Erst nach Profilprüfung **und erfolgreichem CCCD-Abonnement** „bereit“ melden.
  Der Text muss erklären, dass ein kompatibles GATT-Profil keine eindeutige
  OM2-Modellidentifikation ist. Gerätename ist veränderbar (DJI-F).
* Reines Protokollmodul soll CRC, Streaming-Reassembly, Typen und Decoder
  testbar machen. Im ersten Prototyp nur Profilerkennung und unverändertes
  RX-Logging; vollständiger Codec erst mit Fixtures im nächsten Schritt.
* Die UI ist Beobachter, keine GATT-Callback-Sammlung. Lebenszyklus: Scan
  zeitlich begrenzt; im Hintergrund explizit trennen; Rotation soll die Sitzung
  erhalten. Spätere lange Aufnahmen brauchen eigene Service-Entscheidung.
* Android 16: target 36, Insets/Edge-to-edge beachten, keine unnötige
  Orientierungsfixierung. Für den Diagnoseprototyp minSdk 31: nur moderne
  Nearby-Devices-Berechtigungen; keine Legacy-Standortabhängigkeit.
* Keine INTERNET-, Kamera-, Mikrofon- oder Speicherberechtigung im BLE-Prototyp.
  Logdaten sind lokal; Export nur durch Nutzeraktion.

## 4. Tracking-Entscheidung (Plan, noch nicht implementiert)

MediaPipe Face Detector + kleiner Personendetektor werden hinter austauschbarer
`Detector`-Grenze bevorzugt: lokaler Betrieb, offene Runtime, vorhandene
Android-Beispiele. Keine Aussage zur besten Genauigkeit ohne Vergleichsdaten.
ML Kit bietet geringeren Integrationsaufwand, aber zusätzliche SDK-Bedingungen;
LiteRT direkten Zugriff auf Modelle bei höherem Integrationsaufwand.

Abnahmebenchmark auf Nord 4: gleiche Clips/Lichtbedingungen für Gesicht und
Person, p50/p95-Inferenzzeit, False Positives, Zielverlust, thermisches Verhalten,
Video-Drops und Energieverbrauch messen. Initial 640×480 / höchstens 15 Analysen
pro Sekunde als **Messstart**, keine zugesicherte Leistung. Ältere Frames
verwerfen, Kamera-Images immer schließen, BLE nie auf den Inferenzthread legen.
Sichtfeld-, Crop-, Displayrotations- und Frontkamera-Spiegeltransformation testen.

Regler zunächst P/PD, PID nur bei Bedarf mit Anti-Windup; Totzone,
Geschwindigkeits-/Beschleunigungsbegrenzung, Zeitstempel und Watchdog.
Zielverlust -> Bewegung stoppen, Wiedererkennung auf begrenzten Bereich/Zeit;
kein automatischer Sprung auf eine andere Person. Manuelles Stoppen hat Vorrang.
Geschwindigkeitsunits und motorseitiger Timeout müssen vorher geklärt sein.

## 5. Lizenzprüfung / ADR-002

| Komponente | Lizenz / Befund | Verträglichkeit und Pflichten |
|---|---|---|
| Neue App und Module | GPL-3.0-or-later | Vollständiger zugehöriger Quelltext inkl. Buildskripten bei Weitergabe bereitstellen |
| Open Camera | GPL v3 **oder später**, ausdrücklich in opencamera_source.txt §Licence | Mit gewählter Projektlizenz kompatibel; Copyright, GPL und Änderungen dokumentieren; eigener Name/Assets/Datenschutztext |
| OM Research | MIT, Copyright 2025 Dmytro Aleksandrov | Kompatibel; bei Portierung MIT-Hinweis erhalten; Fakten/UUIDs jetzt als Referenz |
| Osmosis | MIT, Copyright 2026 Konrad Iturbe | Kompatibel; vendored-Code hat zusätzliche Herkunft dji-remote, diese Hinweise mitführen; noch nicht eingebunden |
| Nordic BLE 2.x | BSD-3-Clause, Copyright Nordic Semiconductor | Kompatibel; Lizenztext auch mit Binärdistribution; keine Namenswerbung |
| AndroidX / CameraX | Apache-2.0 | Mit GPLv3 kompatibel; Lizenz-/NOTICE-Dateien behalten |
| MediaPipe, OpenCV 4.x | Apache-2.0 (geprüfte Haupt-LICENSE-Dateien) | GPLv3-kompatible Runtimes; Modellgewichte, Drittbibliotheken und konkrete Artefakte zusätzlich prüfen |
| GrapheneOS Camera | MIT | Kompatibel, aber hier keine Integration |
| ML Kit | SDK-Bedingungen; Samples separat Apache-2.0 | Sample-Lizenz ist nicht SDK-Lizenz; keine pauschale GPL-/OSS-Freigabe |
| DJI GO APK / DJI SDK / Handbuch | Proprietäre Referenzen, keine Übernahmelizenz festgestellt | Keine Einbettung in App/Quellrelease; Fakten und eigene Mitschnitte für Interoperabilität dokumentieren |

Eine reine GPLv2-only-Auswahl wäre für die vorgesehene Apache-2.0-Kombination
ungeeignet. Konkrete Modellgewichte sind noch nicht ausgewählt, daher besteht
noch **keine** Lizenzfreigabe für ein späteres Tracking-APK. Für den BLE-Prototyp
werden nur die tatsächlich verwendeten Abhängigkeiten mitgeliefert dokumentiert.

## 6. Lokale DJI-GO-APK

Datei: `1763955921590-DJI-GO3_251028-11-20963_go3_official_sec.apk`

* SHA-256: `880b67803c60ea35b488882727936b4fa885f37b014aa9495411298224513109`
* `aapt dump badging`: Paket `dji.pilot`, Version **3.1.83**, Code **36020963**,
  minSdk 16, targetSdk 26, compileSdk 28, ARMv7/ARM64-Nativeanteile.
* Metadaten lokal untersucht. Weder ausgeführt noch Bluetooth-Verkehr damit
  erfasst; Android-16-Lauffähigkeit dieser APK ist unbekannt. Der Dateiname ist
  kein Beweis für Herkunft oder Authentizität.
* APK bleibt lokale Referenz, wird vom neuen Build nicht gelesen und von
  Versionskontrolle/Quellpaketen ausgeschlossen. Keine proprietären Assets
  oder dekompilierten Implementierungen übernommen.

## 7. Wesentliche Risiken und Entscheidungspunkte

1. OM2-Protokollabdeckung ist erheblich kleiner als die dokumentierte DJI-GO-UI.
   Modus-/Tasten-/Kalibrierbefehle brauchen eigene Evidenz (siehe protocol.md).
2. Aktivierung eines fabrikneuen Gimbals ohne DJI ist nicht nachgewiesen. OM
   Research belegt keine Erstaktivierung; vorhandenes Gerät offline prüfen.
3. FFF0 ist nicht exklusiv für DJI/OM2; Profil und menschliche Geräteauswahl
   dürfen nicht als kryptografische Modellidentifikation ausgegeben werden.
4. Nord-4-Kameraeigenschaften, Streamkombinationen, FPS, RAW und Stabilisierung
   sind noch ungemessen. Capability-Abfrage pro Kamera und Modus ist Pflicht.
5. Mechanik: 240 g, 58,6–85 mm Breite, ≤8,9 mm Dicke; Hülle/Anbauteile und
   Balance real prüfen. Kontrollierbarer Bereich ist kleiner als der Anschlag.
6. Positionskommandos existieren upstream, aber Präzision, Koordinatenbezug und
   Istwinkel-Telemetrie sind offen. Motionlapse ist deshalb kein sicherer Quick Win.
7. Open Camera reduziert Implementierungsaufwand, bringt aber Forkpflege und
   gekoppelte Aufnahmeabläufe. Gate vor Phase 3: Video + Analyse-Stream messen.
8. Android BLE/OEM-Energiesparen, Abbrüche und verspätete Callbacks verlangen
   Tests auf dem Nord 4. Emulatoren ersetzen den GATT-/Motorentest nicht.

Hardwaregrenzen und Protokollfreigaben werden vor jeder Bewegung aus dem
Evidenzregister übernommen; Firmware-Schreibfunktionen bleiben ausgeschlossen,
solange das vom Auftrag verlangte überprüfbare Verfahren fehlt.
