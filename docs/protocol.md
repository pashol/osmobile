# OM170: BLE- und DUML-Evidenzregister

Stand 2026-10-21. Quellstände: [architecture.md](architecture.md), IDs OM,
OS, DJI-M und DJI-F. **Begrenzte lokale BLE-/DUML-RX-Tests durchgeführt**;
siehe [test-results.md](test-results.md).

## Evidenzstufen

* **Implementiert und getestet (upstream)**: Quelle behauptet explizit einen
  OM2-Test. Ohne einzelne Capture-/Testfälle ist nur der genannte Umfang belegt.
* **Implementiert, aber nicht getestet**: Code vorhanden; kein separater
  OM2-Testnachweis für genau diese Funktion. Bedeutet nicht, dass nie getestet wurde.
* **Dokumentiert**: öffentlich beschrieben, noch keine belastbare lokale Umsetzung.
* **Vermutet**: Hypothese aus ähnlichen Geräten/Beobachtungen; keine Freigabe.
* **Unbekannt**: keine ausreichend spezifische Information gefunden.

Diese Stufen sind **nicht** die Projektstatuswerte der Feature-Matrix.

## 1. BLE-Transport

Alle Kurz-UUIDs unten verwenden die Basis
`0000xxxx-0000-1000-8000-00805f9b34fb`.

| Funktion | UUID | Evidenz / Grenze |
|---|---|---|
| Vendor-Service | FFF0 | OM/gimbal.py:10; misc/attributes.md:41ff |
| DUML Notify (RX) | FFF4 | OM/gimbal.py:11, 28; CCCD 2902 im GATT-Dump |
| DUML Write (TX) | FFF5 | OM/gimbal.py:12, 54; Write-Eigenschaften zur Laufzeit prüfen |
| GAP / Name | 1800 / 2A00 | OM-GATT-Dump; Name änderbar laut DJI-F |
| Device Information / PnP | 180A / 2A50 | OM-GATT-Dump; keine Firmware-Characteristic 2A26 im gezeigten Dump |
| Battery | 180F / 2A19 | **Im OM2-zuordenbaren GATT-Dump vorhanden**; Werte/Read/Notify nicht separat getestet |
| HID / Reports | 1812 / 2A4D | Im Dump vorhanden; Zuordnung zu einzelnen Hardwaretasten **unbekannt** |

Der Dump enthält dieselbe Adresse, die `rotate.py` als `OM2_ADDRESS` benennt.
Damit ist er dem OM2 deutlich besser zuordenbar als ein generischer DJI-Dump.
Die automatisch angezeigten Namen „FiRa“, „Car Connectivity“ usw. bei FFFx
sind keine sachliche Beschreibung des DJI-Protokolls.

### Verbindung im Prototyp

1. BLUETOOTH_SCAN und BLUETOOTH_CONNECT ab API 31 anfragen.
2. Zeitbegrenzter Scan ohne verpflichtenden Servicefilter: Service-UUIDs können
   in Advertisements fehlen und Gerätenamen geändert sein.
3. Namen, Adresse, RSSI und FFF0-Hinweis anzeigen. OSMO/OM170-Namen sind nur
   Kandidatenhinweise. Keine automatische Verbindung zu beliebigen Treffern.
4. Nutzer wählt Gerät; GATT verbinden, Services entdecken.
5. FFF0, FFF4 mit NOTIFY und FFF5 mit WRITE/WRITE_NO_RESPONSE prüfen.
6. FFF4-Notification lokal aktivieren und CCCD 2902 mit `01 00` setzen.
7. Erst nach bestätigter Initialisierung „GATT bereit“ melden.
8. Empfangene Bytes mit Richtung/UUID/Zeit loggen. Keine Motor-/Firmware-
   Kommandos senden. CCCD-Write ist BLE-Konfiguration, kein DUML-Kommando.

OM Research verbindet direkt mit Bleak und aktiviert Notifications, ohne
eigenen Konto-/Cloud-Handshake. Das ist positive Evidenz für Offlinebetrieb an
bereits verwendeten Geräten, kein Nachweis zur Erstaktivierung neuer Geräte.
Android-Bonding nicht vorsorglich erzwingen. Falls das Gerät Authentifizierung
verlangt, Fehlercode dokumentieren und erst dann den benötigten Ablauf klären.

Der Prototyp belässt die Standard-MTU. Ein späterer 21-Byte-Rotationsframe
braucht bei MTU 23 zwei Writes (20+1); OM Python nutzt `mtu_size - 3`, Web-JS
teilt in 20 Bytes. MTU darf nicht mit DUML-Paketlänge verwechselt werden.

## 2. DUML-Framing (read-only Decoder implementiert)

| Offset | Inhalt |
|---|---|
| 0 | SOF `55` |
| 1–2 | Little-endian: Länge in unteren 10 Bits, Version in oberen 6 Bits |
| 3 | CRC8 über Bytes 0–2 |
| 4 | Senderadresse, generisches DUML: Gerätetyp untere 5 Bits, Index obere 3 |
| 5 | Empfängeradresse, gleiches Layout |
| 6–7 | Sequenz, in OM-Parser little-endian |
| 8 | Flags, ACK-/Verschlüsselungs-/Request-Felder; nicht für alle OM2-Flows validiert |
| 9 | Command Set |
| 10 | Command ID |
| 11 .. Länge−3 | Payload |
| Länge−2 .. Länge−1 | CRC16 little-endian |

Mindestlänge 13, höchstens 1023 aus dem Längenfeld. RX ist ein Bytestrom:
Fragmentierung und mehrere Pakete je Notification unterstützen. Vor Decoding
Header-CRC, Länge, Version und Paket-CRC prüfen, Puffer begrenzen und nach
Müll/CRC-Fehlern resynchronisieren. Bei Disconnect Puffer verwerfen.

CRC8: reflektierte Rechenform Start `0x77`, Polynom `0x8c`.
CRC16: allgemeiner Full-frame-Start `0x3692`, Polynom `0x8408` (siehe
OS/DjiCrc.kt, Herkunft dji-remote). OM/gimbal.py rechnet für seinen festen
Rotationsheader nur über Body+Payload mit fortgesetztem Start `0xdf0c`.
**Diesen Body-Start nicht für beliebige vollständige Frames verwenden.**

`:protocol` enthält ab 0.2.0 `DumlCrc`, `DumlPacket` und `DumlStreamDecoder`.
Längen-/Versionsgrenzen, beide CRCs, Fragmentierung (u.a. 20+20+13 Bytes),
Resync und Reset nach Trennung sind durch JVM-Tests mit echten RX-Fixtures
geprüft. `:ble` loggt die erste Sichtung jedes CRC-validierten
Command-Set/ID-Paars pro Verbindung; die Rohdaten bleiben separat im ADB-Log.
Der Live-Test fand acht Typen, darunter `05/06`, ohne gemeldeten CRC-Fehler.
**Noch kein** Decoder für Payload-Semantik, Hardwaretasten oder TX-Kommandos.

### Qualitätsbefunde zur direkten Portierung

* `duml.py` prüft weder CRCs noch sinnvolle Mindestlänge. Ein Längenfeld 0 oder
  zu kurzer Frame kann zu Fehlern führen. Nicht unverändert übernehmen.
* Die Bezeichnungen/Bitaufteilung `sender_idx = byte & 7`, `sender = byte >> 3`
  in `duml.py` passen nicht zum generischen DUML-Adressschema. Bis zur Prüfung
  rohe Adressbytes erhalten; `0x02 -> 0x04` nicht nach diesem Parser umbenennen.
* `gimbal.py` nutzt feste Sequenz 1 und festen 21-Byte-Header; das reicht für
  einen PoC, nicht für korrelierte Requests, ACKs und parallele Telemetrie.
* Batteriedecoder greift ohne Längen-/Werteprüfung auf erstes/letztes Byte zu.
* `rotate.py` enthält zwar OM2-Adresse, führt aktuell aber **OM4_ADDRESS** aus
  und sendet ±1500 in Speed mode. Das Beispiel ist keine sichere OM2-Testbahn.
* JS zeigt die gleichen Rotationstypen und Skala ×10; `handleNotify` decodiert
  nichts. Der Web-PoC hat keinen gesonderten OM2-Testbericht.
* OS/DjiMessage.kt behandelt Länge nur als ein Byte und erwartet Byte 2 = 04.
  Außerdem beschreibt es die Sequenzinterpretation für andere Kameras anders.
  Kein unkritischer Ersatz für einen allgemeinen OM2-Streamingparser.

Wiederverwendet werden zunächst Transportbibliothek, UUID-/Profilwissen und
später CRC-/Framingwissen mit Testvektoren. Python/Bleak und Browser-Web-Bluetooth
selbst werden nicht in Android eingebettet.

## 3. Bekannte und fehlende Kommandos

| Funktion | Set / ID / Payload aus Quelle | Evidenz für OM2 | Freigabe dieser App |
|---|---|---|---|
| Verbindung + Rotation als PoC | FFF0/FFF4/FFF5 | **Implementiert und getestet (upstream)**: README ausdrücklich „Tested on: OM2, OM3, OM4“; keine Firmware-/Einzeltestliste | Nur Transport/RX lokal getestet; Rotation nicht gesendet |
| Relative Rotation | `04/14`; yaw, roll, pitch je int16 LE, dann `04`, Zeitbyte | **Implementiert, aber nicht separat OM2-getestet belegt** | Nicht senden |
| Absolute Rotation | `04/14`; gleiche Achsenfolge, Modus `05` | **Implementiert, aber nicht separat OM2-getestet belegt** | Bezugssystem, Präzision und Grenzen offen |
| Speed Rotation | `04/0c`; gleiche Achsenfolge, Modus `80` | **Implementiert**, allgemeine PoC-Testaussage; aktuelles Beispiel auf OM4 | Einheit, Timeout/Stop auf OM2 messen |
| Akku-Push | `05/06`; erstes Byte Prozent, letztes `1` = lädt laut Python | **Implementiert, aber nicht einzeln getestet belegt** | Rohdaten zuerst; keine erfundene 0%-Anzeige |
| Standardakku | `180F/2A19` | **Dokumentiert** im GATT-Dump | Lesbarkeit und Skala prüfen; später optional bevorzugen |
| Follow / Free (Lock) | OM2-Bedienung DJI-M S. 8, 16 | Funktion **dokumentiert**, DUML-Set/ID/Payload **unbekannt** | Erst HCI-Vergleich |
| Walk / Sport; Tilt Lock | DJI-M S. 16 / DJI-F | Funktion **dokumentiert**, Kommando **unbekannt** | Erst HCI-Vergleich |
| Recenter | Doppeldruck M, DJI-M S. 8 | Funktion **dokumentiert**, App-Kommando **unbekannt** | Absolut (0,0,0) nicht ungeprüft mit Recenter gleichsetzen |
| Joystick frei/beide Achsen, Invertierung, Geschwindigkeit | DJI-M S. 15 / DJI-F | Funktion **dokumentiert**, Konfigurationsbytes **unbekannt** | Virtueller Joystick später lokal; Hardwarekonfiguration separat |
| Horizontkorrektur / Auto-Kalibrierung | DJI-M S. 15 | Funktion **dokumentiert**, Protokoll **unbekannt** | Keine Drohnen-Kalibrierbefehle übernehmen |
| Istwinkel / Gimbalstatus / Fehlerbits | Ähnliche DUML-Geräte haben Telemetrie | **Vermutet**; kein verifizierter OM2-Decoder | Keine Positionsregelung darauf behaupten |
| Firmwareversion / Modell / Seriennummer | Anzeige in DJI GO, S. 15 | Funktion **dokumentiert**, spezifischer Request/Reply **unbekannt** | PnP allein ist keine Firmwareversion |
| Aufnahme-/Zoom-/Kamerawechsel-Events | FFF4 oder HID mögliche Wege | **Unbekannt**: Kanäle, IDs, ACK, Wiederholungen | Nur passiv erfassen |
| Firmware schreiben | Offizieller GO-Ablauf S. 17 | Kein überprüfbares freies OM2-Verfahren gefunden | Nicht unterstützt |

Nicht implementiert bedeutet nicht hardwareseitig unmöglich. Die dokumentierten
Modi Walk/Sport/Tilt Lock gehören tatsächlich zum OM2; sie wurden nicht von
neueren DJI-Produkten übernommen.

## 4. Hardwaretasten: belegte Semantik, offener Transport

| Bedienung | Originalfunktion | Einschränkung / Test |
|---|---|---|
| Joystick horizontal/vertikal | Pan/Tilt, im „Free“-Joystick-Setting beide Achsen | Gimbal verarbeitet Bewegung selbst; App-Ereignis nicht bewiesen |
| M einmal | Follow ↔ Free | Nicht zugleich als Foto/Video-Umschalter behandeln |
| M zweimal | Zentrieren | Kann gimbalintern erfolgen; keine App-Nachricht voraussetzen |
| M dreimal | Front/Rückkamera | Laut Handbuch nicht während Video; beendet ActiveTrack |
| Aufnahme einmal | Foto oder Video Start/Stop je App-Modus | Ereignis-/Deduplizierungslogik erfassen |
| Aufnahme gehalten | Burst | Original laut Handbuch **nur iOS**; Android-Erweiterung nur nach Eventnachweis |
| Zoomschieber | Zoom +/− | Eventfrequenz, Richtung, Loslassen und Stop erfassen |
| M 1,5 s gehalten | Ein/Aus | Geräteeigene Funktion |

Weitere Mehrfachklickkombinationen sind nicht nachgewiesen. Im Prototyp wird
keine Kombination als Kameraaktion interpretiert.

## 5. Mechanik und Motionlapse

DJI-M S. 18: steuerbarer Pan ±160°, Tilt ±155° in horizontaler Montage;
mechanisch Pan/Tilt ±170°, Roll −90° bis +70°, max. steuerbar 120°/s.
**120°/s ist eine Obergrenze, kein empfohlener erster Testwert.**
Geplante Ersttests: einzeln ±5° im mittleren Bereich, höchstens 5°/s nach
bestätigter Einheit, Roll unverändert, kurze Laufzeit und geprüfter Stop.
Bei vertikaler Montage kann Zentrieren im Flashlight Mode kollidieren;
Autokalibrierung ist vertikal nicht unterstützt (S. 12, 15).

Für Motionlapse gibt es upstream absolute Kommandos: „nur relative Steuerung“
ist daher keine belastbare Schlussfolgerung. Noch fehlen OM2-Referenzrahmen,
reproduzierbare Zielerreichung, Positionsrückmeldung und sanfte Übergänge.
Geschwindigkeit über Zeit zu integrieren driftet bei BLE-Jitter und Last;
Telefon-IMU misst zudem Bewegung des gesamten Systems. Ohne Istwinkel oder
nachgewiesen verlässliche absolute Positionen keine präzise Fünfpunkt-Bahn
versprechen. Dauerbyte-Sättigung und Wiederholungsrate ebenfalls messen.

## 6. Reproduzierbarer Reverse-Engineering-Ablauf

1. Diagnose-APK verbinden; vollständige Services/Eigenschaften und FFF4-RX
   protokollieren. Hardware/Firmware/Telefon/Android-Build notieren.
2. DJI GO auf unterstütztem Testgerät starten, falls vorhandene APK nicht auf
   Android 16 läuft. Android-Entwickleroption „Bluetooth HCI snoop log“ einschalten,
   ggf. Bluetooth neu starten. Gimbal jeweils nur mit einem Host verbinden.
3. Pro Mitschnitt genau **eine** Aktion: 10 s Ruhe, Aktion mit Zeitmarke,
   10 s Ruhe; mindestens drei Wiederholungen, Gegenaktion/Negativkontrolle.
4. `adb bugreport` sichern. Lage/Verfügbarkeit des btsnoop-Logs ist OEM-abhängig;
   nicht pauschal einen lesbaren `/sdcard/btsnoop_hci.log` voraussetzen.
5. Wireshark: ATT Write/Notification, zugehörige Handles und CCCD ermitteln;
   Nutzdaten reassemblieren, CRC prüfen, Änderungen gegenüber Ruhephase
   vergleichen. HID-Berichte getrennt von DUML untersuchen.
6. Eigene kleine Hex-Fixtures mit Quelle, Richtung, Modell, Firmware,
   Semantik/Unsicherheit und erwartetem Ergebnis speichern. Persönliche
   Gerätekennungen vor Veröffentlichung entfernen.
7. Erst Decoder offline testen. Dann bekannter, begrenzter Befehl mit Stop-
   und Abbruchtest; keine Kommando-ID-Suche durch Ausprobieren am Motor.
8. Register/Feature-Matrix aktualisieren, inklusive negativem Ergebnis.

Für jede neue Freigabe: Datum, Tester, OM170-Firmware, Android-Build,
App-Version/Hash, TX/RX-Fixture, erwartete und beobachtete Bewegung,
Wiederholungen, Abweichungen, Stop-/Disconnect-Verhalten. ACK allein beweist
keine ausgeführte Bewegung. Firmware- und Kalibrierwrites separat behandeln.
