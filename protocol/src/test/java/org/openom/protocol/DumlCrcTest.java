// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** Literal OM2 FFF4 bytes captured on 2026-10-21; expectations checked independently. */
public class DumlCrcTest {
    private static final byte[] SHORT = hex("55 0E 04 66 04 02 00 00 20 04 1C 25 4D D6");
    private static final byte[] BATTERY = hex(
            "55 35 04 68 09 02 4E 00 00 05 06 64 00 00 00 00 00 00 64 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 96 21");

    @Test public void headerCrcMatchesTwoCapturedOm2Frames() {
        assertEquals(0x66, DumlCrc.crc8(SHORT, 0, 3));
        assertEquals(0x68, DumlCrc.crc8(BATTERY, 0, 3));
    }

    @Test public void fullFrameCrcMatchesTwoCapturedOm2Frames() {
        assertEquals(0xD64D, DumlCrc.crc16(SHORT, 0, SHORT.length - 2));
        assertEquals(0x2196, DumlCrc.crc16(BATTERY, 0, BATTERY.length - 2));
    }

    @Test public void sliceOffsetsWorkAndInvalidRangesAreRejected() {
        byte[] wrapped = hex("00 55 0E 04 66");
        assertEquals(0x66, DumlCrc.crc8(wrapped, 1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> DumlCrc.crc16(SHORT, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> DumlCrc.crc8(SHORT, 1, 14));
    }

    private static byte[] hex(String text) { return java.util.HexFormat.of().parseHex(text.replace(" ", "")); }
}
