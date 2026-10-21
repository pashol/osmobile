// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

/** Validates a standard Bluetooth Battery Level (2A19) characteristic value. */
public final class BatteryLevel {
    private BatteryLevel() { }

    /** Returns the unsigned percentage, or null if the characteristic value is not valid. */
    public static Integer decode(byte[] value) {
        if (value == null || value.length != 1) return null;
        int percent = value[0] & 0xff;
        return percent <= 100 ? percent : null;
    }
}
