// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import no.nordicsemi.android.ble.BleManager;
import org.openom.protocol.OmProfile;
import java.util.function.Consumer;

/** Transport only: there is deliberately no DUML/motor write API in this prototype. */
final class OmBleManager extends BleManager {
    private final Consumer<String> output;
    private final Consumer<byte[]> rxBytes;
    private final Consumer<String> initializationFailure;
    private final Runnable servicesInvalidated;
    private BluetoothGattCharacteristic rx;
    private volatile boolean subscribed;

    OmBleManager(Context context, Consumer<String> output, Consumer<byte[]> rxBytes,
                  Consumer<String> initializationFailure,
                  Runnable servicesInvalidated) {
        super(context);
        this.output = output;
        this.rxBytes = rxBytes;
        this.initializationFailure = initializationFailure;
        this.servicesInvalidated = servicesInvalidated;
    }

    @Override protected boolean isRequiredServiceSupported(@NonNull BluetoothGatt gatt) {
        for (BluetoothGattService service : gatt.getServices()) {
            output.accept("SERVICE " + service.getUuid());
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                output.accept("  CHAR " + c.getUuid() + " properties=0x"
                        + Integer.toHexString(c.getProperties()));
            }
        }
        BluetoothGattService service = gatt.getService(OmProfile.SERVICE);
        rx = service == null ? null : service.getCharacteristic(OmProfile.NOTIFY);
        BluetoothGattCharacteristic tx = service == null ? null : service.getCharacteristic(OmProfile.WRITE);
        boolean supported = OmProfile.supportsTransport(service != null,
                rx == null ? 0 : rx.getProperties(), tx == null ? 0 : tx.getProperties());
        return supported && rx.getDescriptor(OmProfile.CCCD) != null;
    }

    @Override protected void initialize() {
        subscribed = false;
        setNotificationCallback(rx).with((device, data) -> {
            output.accept("RX FFF4 " + data);
            byte[] value = data.getValue();
            if (value != null) rxBytes.accept(value.clone());
        });
        enableNotifications(rx)
                .before(device -> output.accept("TX GATT CCCD 2902 <- 01 00 (Notify abonnieren)"))
                .done(device -> {
                    subscribed = true;
                    output.accept("CCCD bestätigt; FFF4-Notifications aktiviert");
                })
                .fail((device, status) -> initializationFailure.accept("CCCD fehlgeschlagen: " + status))
                .invalid(() -> initializationFailure.accept("CCCD-Anfrage ungültig"))
                .enqueue();
    }

    boolean isSubscribed() { return subscribed; }

    @Override protected void onServicesInvalidated() {
        rx = null;
        subscribed = false;
        output.accept("GATT-Services ungültig / Verbindung beendet");
        servicesInvalidated.run();
    }

    @Override public int getMinLogPriority() { return Log.DEBUG; }

    @Override public void log(int priority, @NonNull String message) {
        // BleManager may log during its superclass construction.
        if (output != null) output.accept("GATT " + message);
    }
}
