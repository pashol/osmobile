// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.openom.ble.BleSession;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Small native diagnostic host. Camera UI is introduced only after BLE hardware acceptance. */
public final class MainActivity extends Activity {
    private BleSession session;
    private TextView permissions;
    private TextView status;
    private TextView log;
    private LinearLayout deviceList;
    private Button scan;
    private String displayedDevices = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Object retained = getLastNonConfigurationInstance();
        session = retained instanceof BleSession ? (BleSession) retained : new BleSession(this);
        createUi();
        session.setListener(this::render);
        render();
    }

    private void createUi() {
        getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(16));
        scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(scroll);
        text(body, getString(R.string.app_name), 26);
        text(body, getString(R.string.intro), 16);
        permissions = text(body, "", 14);
        button(body, R.string.permissions, this::requestBluetoothPermissions);
        button(body, R.string.bluetooth_on, () -> {
            if (!session.hasPermissions()) { requestBluetoothPermissions(); return; }
            try { startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); }
            catch (SecurityException | ActivityNotFoundException e) { toast("Bluetooth nicht verfügbar"); }
        });
        button(body, R.string.settings, () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        status = text(body, "", 19);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        scan = button(body, R.string.scan, () -> {
            if (!session.hasPermissions()) { requestBluetoothPermissions(); return; }
            if (session.state() == BleSession.State.SCANNING) session.stopScan();
            else session.startScan();
        });
        button(body, R.string.disconnect, session::disconnect);
        text(body, getString(R.string.profile_note), 14);
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        body.addView(deviceList);
        button(body, R.string.share_log, this::shareLog);
        button(body, R.string.clear_log, session::clearLog);
        button(body, R.string.licenses, this::showLicenses);
        log = text(body, "", 12);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
    }

    private void render() {
        permissions.setText(session.hasPermissions() ? R.string.permission_ok : R.string.permission_missing);
        status.setText(session.status());
        scan.setText(session.state() == BleSession.State.SCANNING ? R.string.scan_stop : R.string.scan);
        scan.setEnabled(session.hasBluetooth() && switch (session.state()) {
            case IDLE, SCANNING, ERROR -> true;
            default -> false;
        });
        List<BleSession.Device> devices = session.devices();
        // Do not replace touched buttons on every RSSI update; stable address order during a scan.
        String key = devices.stream().map(d -> d.address + ":" + d.name + ":" + d.candidate)
                .collect(java.util.stream.Collectors.joining("|"));
        boolean selectable = session.state() == BleSession.State.IDLE
                || session.state() == BleSession.State.SCANNING || session.state() == BleSession.State.ERROR;
        key += selectable;
        if (!key.equals(displayedDevices)) {
            displayedDevices = key;
            deviceList.removeAllViews();
            if (devices.isEmpty()) text(deviceList, getString(R.string.no_devices), 15);
            for (BleSession.Device device : devices) {
                Button choose = new Button(this);
                choose.setText(device.toString());
                choose.setAllCaps(false);
                choose.setMinHeight(dp(56));
                choose.setEnabled(selectable);
                choose.setOnClickListener(v -> session.connect(device));
                deviceList.addView(choose);
            }
        }
        for (int i = 0; i < devices.size(); i++) {
            ((Button) deviceList.getChildAt(i)).setText(devices.get(i).toString());
        }
        log.setText(session.logText());
    }

    private void requestBluetoothPermissions() {
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT}, 1);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] names, int[] grants) {
        super.onRequestPermissionsResult(requestCode, names, grants);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    @Override protected void onStop() {
        if (!isChangingConfigurations()) session.disconnect();
        super.onStop();
    }

    @Override public Object onRetainNonConfigurationInstance() { return session; }

    @Override protected void onDestroy() {
        session.setListener(null);
        if (!isChangingConfigurations()) session.close();
        super.onDestroy();
    }

    private void shareLog() {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "OpenOM BLE-Diagnose 0.2.0")
                .putExtra(Intent.EXTRA_TEXT, session.logText());
        try { startActivity(Intent.createChooser(intent, "Diagnose-Log teilen")); }
        catch (ActivityNotFoundException e) { toast("Keine App zum Teilen vorhanden"); }
    }

    private void showLicenses() {
        StringBuilder text = new StringBuilder("OpenOM · GPL-3.0-or-later\n\n");
        try {
            String[] files = getAssets().list("");
            if (files != null) for (String file : files) {
                if (!file.endsWith(".txt")) continue;
                try (var stream = getAssets().open(file)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    text.append(file).append("\n").append(new String(bytes.toByteArray(), StandardCharsets.UTF_8))
                            .append("\n\n");
                }
            }
        } catch (IOException e) { text.append("Lizenzdatei nicht lesbar: ").append(e.getMessage()); }
        new AlertDialog.Builder(this).setTitle(R.string.licenses).setMessage(text)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private TextView text(LinearLayout parent, String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view);
        return view;
    }

    private Button button(LinearLayout parent, int resource, Runnable action) {
        Button view = new Button(this);
        view.setText(resource);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setOnClickListener(v -> action.run());
        parent.addView(view);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
