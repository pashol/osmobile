// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

/** A connection-bound snapshot must not leak into the next connection. */
public class BatteryReadingTest {
    @Test public void clearsLastReadingWhenConnectionEnds() {
        BatteryReading reading = new BatteryReading();
        assertNull(reading.percent());
        reading.update(new byte[]{73});
        assertEquals(Integer.valueOf(73), reading.percent());
        reading.clear();
        assertNull(reading.percent());
    }

    @Test public void invalidFreshValueCannotLeaveAnOldPercentageVisible() {
        BatteryReading reading = new BatteryReading();
        reading.update(new byte[]{73});
        reading.update(new byte[]{(byte) 255});
        assertNull(reading.percent());
    }
}
