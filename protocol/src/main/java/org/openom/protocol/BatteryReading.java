// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

/** Last validated 2A19 reading in one active GATT session. */
public final class BatteryReading {
    private Integer percent;

    public Integer percent() { return percent; }

    public void update(byte[] value) { percent = BatteryLevel.decode(value); }

    public void clear() { percent = null; }
}
