# Read-only DUML RX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In this session execute inline; no Git repository is present, so no commits.

**Goal:** Validate and reassemble real OM2 FFF4 notifications into complete DUML packets and report new command kinds without transmitting any DUML bytes.

**Architecture:** A pure Java streaming parser in `:protocol` owns a bounded byte buffer and emits immutable validated packets. The Android `:ble` module feeds copies of notifications into one decoder per connection, logs only the first occurrence of each command set/ID and anomalies; the existing raw RX log remains available for correlation. Reset parser state on disconnect and service invalidation.

**Tech Stack:** Java 17, JUnit 4.13.2, Android SDK 36/minSdk 31, Nordic BLE 2.11.0, Gradle 8.14.3.

**Spec:** `docs/protocol.md` §2 and `docs/test-results.md` (observed FFF4 fragment fixture dated 2026-10-21).

## Global Constraints

- No new permission, account, Internet dependency, or DJI SDK.
- No DUML TX or motor commands; CCCD notification configuration remains the only GATT write.
- Preserve raw source address/flags without inventing device semantics; parse only verified framing fields.
- Protect against corrupt length/CRC and reset incomplete data after disconnect.
- A real GATT session needs the user-unlocked Nord 4 and powered OM2; JVM tests must run without hardware.

## File map

- `protocol/src/main/java/org/openom/protocol/DumlCrc.java`: CRC8/CRC16 operations with reflected constants, no Android API.
- `protocol/src/main/java/org/openom/protocol/DumlPacket.java`: immutable validated frame fields + payload copy.
- `protocol/src/main/java/org/openom/protocol/DumlStreamDecoder.java`: bounded parser, CRC verification, resynchronization, reset, counters.
- `protocol/src/test/java/org/openom/protocol/DumlCrcTest.java` and `DumlStreamDecoderTest.java`: real observed byte vectors and deliberately corrupted/split input.
- `ble/src/main/java/org/openom/ble/OmBleManager.java`: forward copied RX bytes in addition to existing raw logger.
- `ble/src/main/java/org/openom/ble/BleSession.java`: one decoder per connection, decoded command-kind log, decoder reset on service change.
- `docs/protocol.md`, `docs/test-results.md`, `docs/feature-matrix.md`, `README.md`: provenance and exact verified state.

---

### Task 1: CRC primitives from captured frames

**Files:** Create `protocol/src/main/java/org/openom/protocol/DumlCrc.java`; test `protocol/src/test/java/org/openom/protocol/DumlCrcTest.java`.

**Interfaces:** `public static int crc8(byte[] bytes, int offset, int length)` and `crc16(byte[] bytes, int offset, int length)`; validate slice bounds. CRC8 init `0x77` poly `0x8c`, CRC16 init `0x3692` poly `0x8408` (both reflected; no final XOR).

- [x] **Step 1: Write failing tests** with literal observed frame `55 0E 04 66 04 02 00 00 20 04 1C 25 4D D6`, independently calculated header CRC `0x66` and full-frame CRC `0xD64D`, plus the 53-byte battery push in test-results.md with expected `0x2196`.
  ```java
  assertEquals(0x66, DumlCrc.crc8(frame, 0, 3));
  assertEquals(0xD64D, DumlCrc.crc16(frame, 0, frame.length - 2));
  ```
- [x] **Step 2: Verify RED:** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :protocol:test --tests org.openom.protocol.DumlCrcTest` fails because `DumlCrc` does not exist.
- [x] **Step 3: Implement:** for each byte `crc ^= bytes[offset+i] & 0xff;` followed by eight iterations of `crc = (crc >>> 1) ^ ((crc & 1) != 0 ? polynomial : 0);`; mask result (`0xff`/`0xffff`). CRC8 output for first three bytes of the fixture must be `0x66`, CRC16 for all but final two `0xD64D`.
- [x] **Step 4: Verify GREEN** with the same test command; then run `:protocol:test`.

### Task 2: Bounded streaming packet parser

**Files:** Create `protocol/src/main/java/org/openom/protocol/DumlPacket.java`, `DumlStreamDecoder.java`; test `protocol/src/test/java/org/openom/protocol/DumlStreamDecoderTest.java`.

**Interfaces:** `new DumlStreamDecoder().feed(byte[] chunk): List<DumlPacket>`; `reset(): void`; `rejectedFrames(): long`. `DumlPacket` exposes `length()`, `sender()`, `receiver()`, `sequence()`, `flags()`, `commandSet()`, `commandId()`, `payload()` (defensive copy). Length = low 10 bits of LE 16-bit field, version = high 6 bits and must equal observed `1`; size 13..1023. CRC8 at index 3 over 0..2; CRC16 LE in final two bytes over all preceding bytes.

- [x] **Step 1: Write failing tests** using literal observed 14-byte frame (`set=04`, `cmd=1c`, `sequence=0`, 1-byte payload `25`), 53-byte `05/06` frame as three 20/20/13-byte chunks, every possible split of the 14-byte frame, back-to-back frames, leading junk/invalid header/invalid CRC16, zero/short/unsupported-version lengths, and `reset()` after a partial frame; assert emitted fields and rejection counts.
  ```java
  assertTrue(decoder.feed(Arrays.copyOfRange(battery, 0, 20)).isEmpty());
  assertTrue(decoder.feed(Arrays.copyOfRange(battery, 20, 40)).isEmpty());
  assertEquals(0x06, decoder.feed(Arrays.copyOfRange(battery, 40, 53)).get(0).commandId());
  ```
- [x] **Step 2: Verify RED:** `./gradlew :protocol:test --tests org.openom.protocol.DumlStreamDecoderTest` fails because parser/packet classes do not exist.
- [x] **Step 3: Implement:** append until header present, discard one byte on invalid SOF/header CRC/length/version, wait for complete declared frame, reject bad CRC16 by discarding one byte and retrying, emit validated packet and remove exactly its length. Keep a fixed maximum 1023-byte buffer, return a fresh list, throw `NullPointerException` on null input; `reset()` clears incomplete data and counters as specified by the test.
- [x] **Step 4: Verify GREEN** with the targeted test command and `:protocol:test`.

### Task 3: Read-only transport integration and evidence

**Files:** Modify `ble/src/main/java/org/openom/ble/OmBleManager.java`, `BleSession.java`; update `docs/protocol.md`, `docs/test-results.md`, `docs/feature-matrix.md`, `README.md`.

**Interfaces:** add `Consumer<byte[]> rxBytes` to `OmBleManager` constructor. In notification callback, keep existing `output.accept("RX FFF4 " + data)`, then `rxBytes.accept(data.getValue().clone())` if non-null. In `BleSession.attemptConnection()`, keep decoder and seen `(set << 8) | cmd` keys local to the connection token; route copied bytes to the main thread, ignore stale generation tokens. Log one `DUML RX set=0x05 cmd=0x06 seq=... payload=...B` per newly observed command kind; never treat payload bytes as percent or button events. On invalid services, clear parser/seen state before status change.

- [x] **Step 1: Verify baseline** `:protocol:test :app:assembleDebug :ble:lintDebug` before changes (prior report provides baseline; rerun if needed).
- [x] **Step 2: Wire the read-only callback** and compile; compare with `OmBleManager` Nordic callback signature. Preserve CCCD-only TX invariant by checking no new `writeCharacteristic`/DUML TX call exists.
- [x] **Step 3: Run software checks** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/pascal/Android/Sdk ./gradlew :protocol:test :app:assembleDebug :app:assembleRelease :app:lintDebug :ble:lintDebug` and check the debug APK signature.
- [x] **Step 4: If the device is available**, install the debug APK via `adb -s <mDNS-or-IP:port> install -r ...`, enable permission without revocation, scan/select the identified Osmo once, assert `CCCD bestätigt` followed by a valid `DUML RX set=0x05 cmd=0x06` summary, and disconnect. Record actual observation (or reason test was not performed). Do not run motor commands.
- [x] **Step 5: Update documentation** with concrete results, unchanged limitations, dated evidence and APK hash. Preserve raw log locally, redact addresses/serial in public examples.

## Self-review gate

- [x] Compare parser/CRC rules and 20+20+13 fixture against `docs/protocol.md` §2 and the log excerpt in `docs/test-results.md`; do not infer button semantics.
- [x] Scan for placeholders, broken API names, or hidden write commands. End with the exact commands and results observed rather than assumptions.
