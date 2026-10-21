// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import androidx.annotation.NonNull;
import no.nordicsemi.android.ble.ConnectRequest;
import no.nordicsemi.android.ble.observer.ConnectionObserver;
import org.openom.protocol.OmProfile;
import org.openom.protocol.RetryPolicy;
import org.openom.protocol.DumlPacket;
import org.openom.protocol.DumlStreamDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** UI-independent foreground session. Public methods and listener run on the main thread. */
@SuppressLint("MissingPermission") // All entry points check permissions; revocation is also caught.
public final class BleSession {
    public interface Listener { void onChanged(); }
    public enum State { IDLE, SCANNING, CONNECTING, DISCOVERING, READY, RETRY_WAIT, ERROR }

    public static final class Device {
        public final BluetoothDevice bluetoothDevice;
        public final String name;
        public final String address;
        public final int rssi;
        public final boolean candidate;

        Device(ScanResult result) {
            bluetoothDevice = result.getDevice();
            address = bluetoothDevice.getAddress();
            ScanRecord record = result.getScanRecord();
            String advertised = record == null ? null : record.getDeviceName();
            name = advertised == null ? bluetoothDevice.getName() : advertised;
            rssi = result.getRssi();
            boolean service = record != null && record.getServiceUuids() != null
                    && record.getServiceUuids().contains(new ParcelUuid(OmProfile.SERVICE));
            candidate = OmProfile.isCandidate(name, service);
        }

        @Override public String toString() {
            return (candidate ? "Kandidat · " : "BLE · ") + (name == null ? "Ohne Namen" : name)
                    + "\n" + address + " · " + rssi + " dBm";
        }
    }

    private final Context context;
    private final BluetoothAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionLog log = new SessionLog();
    private final LinkedHashMap<String, Device> devices = new LinkedHashMap<>();
    private final RetryPolicy retries = new RetryPolicy();
    private Listener listener;
    private State state = State.IDLE;
    private String status = "Bereit für Gerätesuche";
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private OmBleManager manager;
    private ConnectRequest connection;
    private BluetoothDevice selected;
    private boolean wanted;
    private boolean everReady;
    private boolean closed;
    private long generation;
    private final Runnable scanTimeout = this::stopScan;
    private Runnable connectTimeout;
    private Runnable retryTask;
    private boolean updatePending;
    private final Runnable notifyUi = () -> {
        updatePending = false;
        if (listener != null && !closed) listener.onChanged();
    };

    private final BroadcastReceiver bluetoothState = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            int value = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (value == BluetoothAdapter.STATE_OFF || value == BluetoothAdapter.STATE_TURNING_OFF) {
                disconnect();
                setState(State.IDLE, "Bluetooth ausgeschaltet");
            }
        }
    };

    public BleSession(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager service = this.context.getSystemService(BluetoothManager.class);
        adapter = service == null ? null : service.getAdapter();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        // Bluetooth broadcasts may originate in the privileged Bluetooth package, not system UID.
        if (Build.VERSION.SDK_INT >= 33) this.context.registerReceiver(bluetoothState, filter, Context.RECEIVER_EXPORTED);
        else this.context.registerReceiver(bluetoothState, filter);
        log.add("OpenOM 0.2.0 · Android " + Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT);
        log.add("Nur BLE-Diagnose. Keine DUML-TX-Kommandos; Modell/Hardwaretest noch unbestätigt.");
    }

    public void setListener(Listener value) { listener = value; changed(); }
    public State state() { return state; }
    public String status() { return status; }
    public String logText() { return log.text(); }
    public boolean hasBluetooth() { return adapter != null; }
    public List<Device> devices() {
        List<Device> list = new ArrayList<>(devices.values());
        list.sort((a, b) -> {
            int candidateOrder = Boolean.compare(b.candidate, a.candidate);
            return candidateOrder != 0 ? candidateOrder : a.address.compareTo(b.address);
        });
        return list;
    }
    public void clearLog() { log.clear(); changed(); }

    public boolean hasPermissions() {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isBluetoothEnabled() {
        try { return adapter != null && hasPermissions() && adapter.isEnabled(); }
        catch (SecurityException e) { return false; }
    }

    private boolean available() {
        if (closed) return false;
        if (!hasPermissions()) { setState(State.ERROR, "Bluetooth-Berechtigungen fehlen"); return false; }
        if (!isBluetoothEnabled()) { setState(State.ERROR, "Bluetooth nicht verfügbar oder ausgeschaltet"); return false; }
        return true;
    }

    public void startScan() {
        if (!available()) return;
        if (manager != null || wanted) { log("Vor einer neuen Suche bitte trennen."); return; }
        stopScan();
        devices.clear();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { setState(State.ERROR, "Kein BLE-Scanner verfügbar"); return; }
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                main.post(() -> {
                    if (scanCallback != this || closed) return;
                    try {
                        Device device = new Device(result);
                        if (!devices.containsKey(device.address)) {
                            if (devices.size() >= 100) return;
                            log("SCAN " + device.toString().replace('\n', ' '));
                        }
                        devices.put(device.address, device);
                        changed();
                    } catch (SecurityException e) { permissionLost(); }
                });
            }
            @Override public void onScanFailed(int errorCode) {
                main.post(() -> {
                    if (scanCallback != this) return;
                    stopScan();
                    setState(State.ERROR, "BLE-Suche fehlgeschlagen: " + errorCode);
                });
            }
        };
        try {
            scanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            setState(State.SCANNING, "Suche läuft (15 s) · eigenes Gerät auswählen");
            main.postDelayed(scanTimeout, 15_000);
        } catch (SecurityException e) { permissionLost(); }
        catch (IllegalStateException e) { stopScan(); setState(State.ERROR, "BLE-Suche: " + e.getMessage()); }
    }

    public void stopScan() {
        main.removeCallbacks(scanTimeout);
        ScanCallback old = scanCallback;
        scanCallback = null; // Invalidate queued scan callbacks before stopping Android's scanner.
        try { if (scanner != null && old != null) scanner.stopScan(old); }
        catch (SecurityException | IllegalStateException e) { log("Scan beendet: " + e.getClass().getSimpleName()); }
        scanner = null;
        if (state == State.SCANNING) setState(State.IDLE, "Suche beendet · " + devices.size() + " Geräte");
    }

    public void connect(Device device) {
        if (!available()) return;
        disconnect();
        selected = device.bluetoothDevice;
        wanted = true;
        everReady = false;
        retries.reset();
        attemptConnection();
    }

    private void attemptConnection() {
        if (!wanted || !available()) { wanted = false; return; }
        final long token = ++generation;
        final DumlStreamDecoder decoder = new DumlStreamDecoder();
        final Set<Integer> observedCommands = new HashSet<>();
        OmBleManager current = new OmBleManager(context,
                text -> main.post(() -> { if (current(token)) log(text); }),
                bytes -> main.post(() -> {
                    if (!current(token)) return;
                    long before = decoder.rejectedFrames();
                    for (DumlPacket packet : decoder.feed(bytes)) {
                        int key = (packet.commandSet() << 8) | packet.commandId();
                        if (observedCommands.add(key)) log(String.format(Locale.ROOT,
                                "DUML RX set=0x%02X cmd=0x%02X seq=%d payload=%d B (erstmals)",
                                packet.commandSet(), packet.commandId(), packet.sequence(),
                                packet.payload().length));
                    }
                    if (before == 0 && decoder.rejectedFrames() > 0)
                        log("DUML RX: ungültiger Header/CRC verworfen; Rohdaten im ADB-Log");
                }),
                error -> main.post(() -> { if (current(token)) failed(error, false); }),
                () -> main.post(() -> {
                    if (current(token)) {
                        decoder.reset();
                        observedCommands.clear();
                        setState(State.DISCOVERING, "Services ungültig · prüfe Profil/Abonnement erneut");
                        armConnectTimeout(token);
                    }
                }));
        manager = current;
        current.setConnectionObserver(new ConnectionObserver() {
            private void deliver(Runnable event) { main.post(() -> { if (current(token)) event.run(); }); }
            @Override public void onDeviceConnecting(@NonNull BluetoothDevice device) {
                deliver(() -> setState(State.CONNECTING, "Verbinde mit " + device.getAddress()));
            }
            @Override public void onDeviceConnected(@NonNull BluetoothDevice device) {
                deliver(() -> setState(State.DISCOVERING, "GATT verbunden · prüfe Services und Notifications"));
            }
            @Override public void onDeviceReady(@NonNull BluetoothDevice device) {
                deliver(() -> {
                    // Nordic calls ready even if an initialization request failed. Check CCCD explicitly.
                    if (!current.isSubscribed()) { failed("Notification-Abonnement nicht bestätigt", false); return; }
                    clearConnectTimeout();
                    everReady = true;
                    retries.reset();
                    setState(State.READY, "GATT bereit · " + device.getAddress() + " · FFF4 abonniert (Modell unbestätigt)");
                });
            }
            @Override public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
                deliver(() -> failed("Verbindung fehlgeschlagen, Grund " + reason,
                        reason != REASON_NOT_SUPPORTED && reason != REASON_CANCELLED));
            }
            @Override public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
                deliver(() -> log("GATT trennt"));
            }
            @Override public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
                deliver(() -> failed("Verbindung beendet, Grund " + reason, reason != REASON_NOT_SUPPORTED));
            }
        });
        setState(State.CONNECTING, "Verbinde · Zeitlimit 20 s");
        armConnectTimeout(token);
        try {
            connection = current.connect(selected).useAutoConnect(false).timeout(20_000)
                    .fail((device, code) -> main.post(() -> {
                        if (current(token)) failed("GATT-Anfrage fehlgeschlagen: " + code, true);
                    }));
            connection.enqueue();
        } catch (SecurityException e) { permissionLost(); }
    }

    private boolean current(long token) { return !closed && wanted && token == generation; }

    private void failed(String message, boolean retryable) {
        releaseManager();
        log(message);
        // Initial connect errors are visible immediately. Only reconnect a previously ready session.
        long delay = wanted && everReady && retryable && isBluetoothEnabled() ? retries.nextDelayMillis() : -1;
        if (delay < 0) {
            wanted = false;
            setState(State.ERROR, message + " · bitte erneut suchen/verbinden");
            return;
        }
        setState(State.RETRY_WAIT, message + " · Wiederverbinden in " + delay / 1000 + " s");
        final long token = generation;
        retryTask = () -> { if (current(token)) attemptConnection(); };
        main.postDelayed(retryTask, delay);
    }

    private void clearConnectTimeout() {
        if (connectTimeout != null) main.removeCallbacks(connectTimeout);
        connectTimeout = null;
    }

    private void armConnectTimeout(long token) {
        clearConnectTimeout();
        connectTimeout = () -> { if (current(token)) failed("Verbindung/Initialisierung: Zeitlimit 20 s", true); };
        main.postDelayed(connectTimeout, 20_000);
    }

    private void releaseManager() {
        ++generation;
        clearConnectTimeout();
        if (retryTask != null) main.removeCallbacks(retryTask);
        retryTask = null;
        OmBleManager old = manager;
        manager = null;
        try {
            if (connection != null) connection.cancelPendingConnection();
            if (old != null) old.close();
        } catch (SecurityException e) { log("GATT schließen nach Berechtigungswiderruf"); }
        connection = null;
    }

    public void disconnect() {
        wanted = false;
        everReady = false;
        stopScan();
        releaseManager();
        selected = null;
        setState(State.IDLE, "Getrennt · bereit für Gerätesuche");
    }

    private void permissionLost() {
        disconnect();
        setState(State.ERROR, "Bluetooth-Berechtigung entzogen · bitte erneut freigeben");
    }

    public void close() {
        if (closed) return;
        disconnect();
        closed = true;
        listener = null;
        context.unregisterReceiver(bluetoothState);
        main.removeCallbacksAndMessages(null);
    }

    private void setState(State value, String text) {
        state = value;
        status = text;
        log(value + " · " + text);
    }

    private void log(String text) { log.add(text); changed(); }

    private void changed() {
        // Coalesce notifications/advertisements; never rebuild UI for every BLE packet.
        if (!updatePending && !closed) {
            updatePending = true;
            main.postDelayed(notifyUi, 200);
        }
    }
}
