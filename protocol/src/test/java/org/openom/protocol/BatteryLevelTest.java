// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

/** Bluetooth SIG Battery Level (2A19), not a DJI DUML payload. */
public class BatteryLevelTest {
    @Test public void acceptsUnsignedSingleByteBoundaryValues() {
        assertEquals(Integer.valueOf(0), BatteryLevel.decode(new byte[]{0}));
        assertEquals(Integer.valueOf(100), BatteryLevel.decode(new byte[]{100}));
    }

    @Test public void rejectsOutOfRangeRatherThanWrappingOrClamping() {
        assertNull(BatteryLevel.decode(new byte[]{(byte) 101}));
        assertNull(BatteryLevel.decode(new byte[]{(byte) 255}));
    }

    @Test public void rejectsMissingAndMalformedValuesRatherThanShowingZero() {
        assertNull(BatteryLevel.decode(null));
        assertNull(BatteryLevel.decode(new byte[0]));
        assertNull(BatteryLevel.decode(new byte[]{42, 0}));
    }
}
