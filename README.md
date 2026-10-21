# OpenOM - Osmo Mobile 2 on Android

Open, native Android application for the DJI Osmo Mobile 2 (OM170).
Reference device: OnePlus Nord 4 running Android 16.

**Current status: Phase 0 research and BLE diagnostic prototype 0.3.0.**
The APK includes a permission dialog, BLE scanning, manual device selection,
GATT profile inspection, FFF4 notifications, connection status, limited
reconnection, local debug logs, read-only DUML reassembly with CRC validation,
and an optional one-time read of the standard GATT battery level (2A19).
On the Nord 4 running Android 16, scanning, GATT profile inspection, CCCD
confirmation, FFF4 reception, and a power-cycle reconnect have been verified
with the Osmo device. An unexpected controller timeout during the first run
is still unexplained; full hardware acceptance is pending. Camera control,
motor control, **DUML battery decoding**, and tracking are not implemented.
The Osmo device returned a value through 2A19 (80%); independent percentage
and charging-status validation is still pending.
Details: [test report](docs/test-results.md).

## Documentation

* [Architecture, source comparison, and license review](docs/architecture.md)
* [Feature matrix and feasibility](docs/feature-matrix.md)
* [BLE/DUML protocol and open commands](docs/protocol.md)
* [Development plan and hardware gates](docs/development-plan.md)
* [Reproducible hardware tests](docs/hardware-tests.md)
* [Observed test results](docs/test-results.md)

Architectural decision: **Open Camera as the future camera base**, with an
own modular OM2 driver; currently this is a small diagnostic host. The Nordic
Android BLE Library is reused for GATT. Protocol knowledge comes primarily
from OM Research. There is no DJI SDK dependency.

## Available APK

```text
app/build/outputs/apk/debug/app-debug.apk
```

This debug APK is signed and intended for installation. The also-built
`app/build/outputs/apk/release/app-release-unsigned.apk` requires a separate
release signature before installation. Builds and hashes: [test report](docs/test-results.md).

## Ubuntu: Development Environment

Required: **JDK 17**, Android SDK Platform **36**, Build Tools **35.0.0**,
Platform Tools (ADB), and Gradle **8.14.3** through the included wrapper.
The Android Gradle Plugin is pinned to **8.13.2**. Android Studio is optional.

For example, install the JDK on Ubuntu:

```bash
sudo apt install openjdk-17-jdk unzip
```

Install the Android SDK through Android Studio or the official
[command-line tools](https://developer.android.com/studio#command-tools).
Review and accept SDK licenses in the SDK Manager or interactively with
`sdkmanager`. After installation:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
java -version
adb version
```

On the Ubuntu system used here, the SDK is located at
`/home/pascal/Android/Sdk`. The default Java/JAVA_HOME is not reliably JDK 17;
set it explicitly as shown above for builds.

### Build and Check

From the project directory:

```bash
./gradlew --version
./gradlew :protocol:test :app:assembleDebug :app:assembleRelease :app:lintDebug :ble:lintDebug
```

The wrapper verifies the Gradle download with SHA-256. Direct dependencies
are versioned; resolve the runtime dependencies with:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

The first build requires Internet access for Gradle/Maven artifacts. After
the cache is complete, `./gradlew --offline :app:assembleDebug` is possible.
**The app itself does not require Internet access.** Here, "reproducible"
means that tools, sources, and build commands are fixed and repeatable; byte-
identical signed APKs on different machines have not been established. In
particular, Gradle creates a machine-dependent debug key.

### Installation and Start

Enable Developer Options and USB debugging on the Nord 4, connect it by USB,
and confirm the RSA dialog:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.openom.app/.MainActivity
```

`unauthorized`: unlock the phone and confirm USB debugging.
An empty device list: check the cable, USB mode, and Ubuntu udev access.

**Wireless ADB (Android 11+):** Put both devices on the same Wi-Fi network.
On the phone, enable Developer Options -> Wireless debugging and open
"Pair with pairing code". Use the *pairing address* and temporary code shown
there once:

```bash
adb mdns services                 # _adb-tls-pairing and _adb-tls-connect
adb pair PHONE-IP:PAIRING-PORT
# Enter the 6-digit code interactively
adb connect PHONE-IP:CONNECTION-PORT
adb devices -l
```

The pairing and connection ports are different; the connection port can
change after a reboot. If several ADB transports refer to the same phone,
use `adb -s PHONE-IP:CONNECTION-PORT` for all subsequent commands.
On the Ubuntu system used here, `adb` is at
`/home/pascal/Android/Sdk/platform-tools/adb` if `PATH` is not set yet.
When the phone is locked, the debug UI test cannot tap through ADB; keep the
phone unlocked and the app in the foreground.

For installation directly from a file manager, transfer the APK to the phone
and allow that file manager to install unknown apps. No DJI GO installation
is required.

## First Connection Test

1. Equip and balance the gimbal according to its manual, then turn it on.
2. Disconnect DJI GO and other gimbal/BLE connections. Test the OM2 with one host.
3. Start OpenOM -> **Bluetooth permissions** -> allow nearby devices.
4. If necessary, **turn on Bluetooth**, then scan for 15 seconds.
5. Select your device by name/address. A renamed device may appear without a
   "candidate" hint; it is still selectable.
6. Expected sequence: Connect -> profile check -> **GATT ready / FFF4 subscribed**.
7. Check the log for `SERVICE`, `CHAR`, `CCCD confirmed`, and, if applicable,
   `RX FFF4`. "GATT ready" does not uniquely identify the model. If the
   device exposes the standard Battery Level characteristic (180F/2A19) with
   READ support, it is read once; missing or invalid responses remain
   "unknown".

FFF0/FFF4/FFF5 are inspected. The app only writes the notification
configuration (CCCD), **not motor, calibration, or firmware commands**.
Whether push notifications arrive without additional initialization must be
checked on the available device. An empty RX log alone does not prove a
connection failure.

If a previously ready connection is lost unexpectedly, the app attempts to
reconnect three times (waits of 1/2/4 seconds, up to 20 seconds per attempt).
Initial connection failures require a manual retry. **Disconnect / Cancel**
ends the attempts. The session disconnects in the background; reconnect
intentionally after returning. Screen rotation preserves the session.

## Debugging

```bash
adb logcat -v threadtime -s OpenOM:D '*:S'
```

For persistent local capture in a second shell:

```bash
adb logcat -v threadtime -s OpenOM:D '*:S' > openom-ble.log
```

The app displays the last **250 log lines** in RAM; **Share logs** exports
this excerpt through the Android share dialog. Create longer captures with
ADB. Logs contain device addresses/names and RX data; remove personal
identifiers before publishing a bug report. Opening other apps through
sharing or settings may disconnect the session according to the foreground
rule.

* Scan error 2/6 or Android scan limit: stop scanning, wait briefly, and try
  again; avoid rapid retry loops.
* Missing profile / CCCD: inspect the complete GATT dump; do not guess UUIDs.
* GATT error/timeout: disconnect competing hosts, check Bluetooth state and
  distance, and reconnect once. Native GATT status codes and Nordic "reason"
  codes use different number spaces.
* Permission error: open App Settings -> Nearby devices, then scan again.
* On Service Changed, inspect the services again and start a new timeout.
* No battery value: 2A19 is optional and is shown only after a successful
  GATT read; DUML `05/06` is not interpreted as a percentage. The firmware
  version is still not read.

For DJI GO HCI captures and controlled reverse engineering, see
[protocol.md](docs/protocol.md#6-reproduzierbarer-reverse-engineering-ablauf).

## Release Signing

Use your own long-term key pair. Enter passwords interactively; store keys
outside the source directory. Example after creating a private key directory:

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

No release key pair was created here. Debug and release signatures differ;
switching may require uninstalling the debug app. Updates must be signed with
the same release key.

## Structure, Privacy, and License

```text
app/        Native diagnostic UI, permissions, lifecycle
ble/        Android scanner, Nordic transport, device session, logs
protocol/   Android-free profile/DUML/2A19 knowledge with JVM tests
docs/       Research, architecture, protocol, plan, test evidence
licenses/   Complete license texts, visible in the APK under "Licenses"
```

Android 12+ (minSdk 31), Android 16 as the primary target. At runtime, only
BLUETOOTH_SCAN/CONNECT are used; there is no location, Internet, camera,
microphone, or storage access in the prototype. Libraries add a legacy
Bluetooth permission limited to API 30 and an app-internal signature
permission. There are no accounts, analytics, automatic uploads, or stored
media.

GPL-3.0-or-later; see [LICENSE](LICENSE) and [third-party notices](licenses/NOTICE.txt).
When distributing the APK, make the corresponding complete source code and
build files available. The supplied DJI GO APK is not included.
The next development step is full real-world BLE acceptance, including
repeated 2A19 comparison measurements and investigation of the unexpected
timeout. Motor control requires additional verified fixtures.
