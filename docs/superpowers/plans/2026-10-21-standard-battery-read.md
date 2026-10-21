# Optional Standard Battery Read Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Execute inline in this session; this directory is not a Git repository, so no commits.

**Goal:** Read and display a validated, optional Bluetooth Battery Service (180F/2A19) percentage without guessing the meaning of DUML `05/06` or sending vendor commands.

**Architecture:** `:protocol` validates the standard one-byte Battery Level value independently of Android. `OmBleManager` discovers the optional characteristic and queues one GATT read only after CCCD confirmation; `BleSession` owns connection-scoped status and invalidates it on link loss or Service Changed. The diagnostic UI displays unknown until a valid read arrives.

**Tech Stack:** Java 17, Android SDK 36/minSdk 31, Nordic Android BLE 2.11.0, JUnit 4.13.2.

**Spec:** [protocol.md](../../protocol.md) §1 (180F/2A19) and §3 (battery caveats), [development-plan.md](../../development-plan.md) §Phase 1b step 3, [hardware-tests.md](../../hardware-tests.md) G01.

## Global Constraints

- Offline and read-only; CCCD configuration and optional 2A19 read are the only ATT operations; no FFF5 writes, motor controls, inferred charging state or DUML-payload battery interpretation.
- Do not require 180F/2A19 for READY; reject missing, malformed and out-of-range values as unknown, never default to zero.
- A previous connection's percentage must not survive disconnect, retry, Service Changed or manual cancellation. Late callbacks must be ignored by session generation.
- Live GATT-read evidence, if available, is separate from JVM test evidence; do not claim a successful read without an observed value.

---

### Task 1: Validate standard Battery Level

**Files:** Create `protocol/src/main/java/org/openom/protocol/BatteryLevel.java`, `protocol/src/test/java/org/openom/protocol/BatteryLevelTest.java`.

**Interfaces:** `public static Integer decode(byte[] value)` returns the unsigned percent 0..100 or `null` if the byte array is null, not exactly one byte or >100. Unlike DUML `05/06`, byte zero is a valid standard 2A19 percentage.

- [x] **Step 1: Write the failing test** covering `new byte[]{0}`, `new byte[]{100}`, `new byte[]{(byte) 101}`, empty/two-byte/null, with literal expected values; wrong unsigned handling or default zero must fail.
- [x] **Step 2: Run the targeted test** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/pascal/Android/Sdk ./gradlew :protocol:test --tests org.openom.protocol.BatteryLevelTest` and observe missing production symbol.
- [x] **Step 3: Implement the minimal decoder** using `value[0] & 0xff` only when `value != null && value.length == 1`.
- [x] **Step 4: Rerun targeted tests** and check all cases pass.

### Task 2: Integrate optional read and connection-scoped display

**Files:** Modify `protocol/src/main/java/org/openom/protocol/OmProfile.java` for standard UUID constants; `ble/src/main/java/org/openom/ble/OmBleManager.java` for optional discovery/read; `ble/src/main/java/org/openom/ble/BleSession.java` for result and invalidation; `app/src/main/java/org/openom/app/MainActivity.java` for a separate battery label. Add targeted pure-JVM state test only if nontrivial session logic can be separated without Android test doubles.

**Interfaces:** `OmBleManager` constructor accepts a `Consumer<byte[]> batteryBytes` and a separate `Consumer<String> batteryInfo`; session handles these guarded by `current(token)`; `BleSession.batteryStatus()` returns a German status string for UI. Use `OmProfile.BATTERY_SERVICE`/`BATTERY_LEVEL` UUIDs and `BluetoothGattCharacteristic.PROPERTY_READ` before queueing `readCharacteristic(battery)`. Wire `.with`, `.fail`, `.invalid`; a failed/unsupported read logs diagnostics and leaves state unknown, without rejecting READY.

- [ ] **Step 1: Review Nordic 2.11.0 read/queue callback semantics** from local library/source before changing the manager.
- [ ] **Step 2: Add a failing behavior test** for any separately testable state/invalidation rule, then verify red before implementation; avoid writing Android-framework mocks that merely mirror callbacks.
- [ ] **Step 3: Implement optional read** after successful CCCD, copy returned bytes before dispatch, and reset the displayed value on release/service invalidation; never use a stale connection result.
- [ ] **Step 4: Build and lint** with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/pascal/Android/Sdk ./gradlew :protocol:test :app:assembleDebug :app:assembleRelease :app:lintDebug :ble:lintDebug`.

### Task 3: Check real GATT behavior and record evidence

**Files:** Update `docs/protocol.md`, `docs/feature-matrix.md`, `docs/test-results.md`, `docs/hardware-tests.md`, `README.md`, `app/build.gradle` and displayed version strings if code version changes.

- [ ] **Step 1: If the gimbal and phone are available**, install the newly built APK, connect via foreground UI, observe actual 2A19 characteristic properties/read/result in a private ADB log; redact MAC/serial in public docs. Keep unsupported/read-error/unknown as an honest result.
- [ ] **Step 2: Record the exact test/build/installation evidence**, note whether measured percent can be independently verified, update version and limitations consistently, and replace artifact hashes only after final builds.
- [ ] **Step 3: Verify final tests, lint, signature, hashes and documentation consistency** after all changes. Leave hardware gate open if not fully completed.

## Self-review

- Coverage: optional discovery/read, validation, unknown/error state, disconnect/reset, observed hardware result and status documentation are assigned above. Charging and DUML parsing are deliberately excluded because they lack independent validation.
- Interface names: `BatteryLevel.decode(byte[])`, `BleSession.batteryStatus()`, `OmProfile.BATTERY_SERVICE` and `BATTERY_LEVEL` are consistent across tasks.
