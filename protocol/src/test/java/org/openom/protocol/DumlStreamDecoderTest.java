// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Captured FFF4 bytes, not packets manufactured by the decoder itself. */
public class DumlStreamDecoderTest {
    private static final byte[] SHORT = hex("55 0E 04 66 04 02 00 00 20 04 1C 25 4D D6");
    private static final byte[] BATTERY = hex(
            "55 35 04 68 09 02 4E 00 00 05 06 64 00 00 00 00 00 00 64 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 96 21");

    @Test public void capturedFrameExposesOnlyVerifiedFieldsAndDefensivePayload() {
        DumlPacket packet = new DumlStreamDecoder().feed(SHORT).get(0);
        assertEquals(14, packet.length());
        assertEquals(0x04, packet.sender());
        assertEquals(0x02, packet.receiver());
        assertEquals(0, packet.sequence());
        assertEquals(0x20, packet.flags());
        assertEquals(0x04, packet.commandSet());
        assertEquals(0x1c, packet.commandId());
        assertArrayEquals(new byte[]{0x25}, packet.payload());
        packet.payload()[0] = 0;
        assertArrayEquals(new byte[]{0x25}, packet.payload());
    }

    @Test public void capturedBatteryPushReassemblesTwentyTwentyThirteen() {
        DumlStreamDecoder decoder = new DumlStreamDecoder();
        assertTrue(decoder.feed(Arrays.copyOfRange(BATTERY, 0, 20)).isEmpty());
        assertTrue(decoder.feed(Arrays.copyOfRange(BATTERY, 20, 40)).isEmpty());
        List<DumlPacket> found = decoder.feed(Arrays.copyOfRange(BATTERY, 40, 53));
        assertEquals(1, found.size());
        assertEquals(53, found.get(0).length());
        assertEquals(0x05, found.get(0).commandSet());
        assertEquals(0x06, found.get(0).commandId());
        assertEquals(0x4e, found.get(0).sequence());
        assertEquals(40, found.get(0).payload().length);
        assertEquals(0x64, found.get(0).payload()[0] & 0xff);
        assertEquals(0, decoder.rejectedFrames());
    }

    @Test public void everySplitOffsetCompletesExactlyOnce() {
        for (int split = 1; split < SHORT.length; split++) {
            DumlStreamDecoder decoder = new DumlStreamDecoder();
            assertTrue("split " + split, decoder.feed(Arrays.copyOfRange(SHORT, 0, split)).isEmpty());
            assertEquals("split " + split, 1,
                    decoder.feed(Arrays.copyOfRange(SHORT, split, SHORT.length)).size());
        }
    }

    @Test public void consecutivePacketsAndLeadingJunkDoNotConsumeEachOther() {
        byte[] stream = new byte[3 + SHORT.length + BATTERY.length];
        System.arraycopy(hex("00 77 13"), 0, stream, 0, 3);
        System.arraycopy(SHORT, 0, stream, 3, SHORT.length);
        System.arraycopy(BATTERY, 0, stream, 3 + SHORT.length, BATTERY.length);
        List<DumlPacket> packets = new DumlStreamDecoder().feed(stream);
        assertEquals(2, packets.size());
        assertEquals(0x1c, packets.get(0).commandId());
        assertEquals(0x06, packets.get(1).commandId());
    }

    @Test public void corruptPayloadCrcIsRejectedAndNextFrameIsRecovered() {
        byte[] bad = SHORT.clone();
        bad[11] ^= 1;
        byte[] combined = new byte[bad.length + SHORT.length];
        System.arraycopy(bad, 0, combined, 0, bad.length);
        System.arraycopy(SHORT, 0, combined, bad.length, SHORT.length);
        DumlStreamDecoder decoder = new DumlStreamDecoder();
        assertEquals(1, decoder.feed(combined).size());
        assertEquals(1, decoder.rejectedFrames());
    }

    @Test public void invalidHeaderLengthCrcOrVersionResyncToNextFrame() {
        byte[] invalid = hex("55 00 04 00 55 0E 08 00 55 FF 07 00");
        byte[] combined = new byte[invalid.length + SHORT.length];
        System.arraycopy(invalid, 0, combined, 0, invalid.length);
        System.arraycopy(SHORT, 0, combined, invalid.length, SHORT.length);
        assertEquals(1, new DumlStreamDecoder().feed(combined).size());
    }

    @Test public void validCrcsDoNotMakeAnUnsupportedProtocolVersionAcceptable() {
        byte[] wrongVersion = SHORT.clone();
        wrongVersion[2] = 0x08; // Version 2 with otherwise identical frame.
        wrongVersion[3] = (byte) DumlCrc.crc8(wrongVersion, 0, 3);
        int crc = DumlCrc.crc16(wrongVersion, 0, wrongVersion.length - 2);
        wrongVersion[12] = (byte) crc;
        wrongVersion[13] = (byte) (crc >>> 8);
        DumlStreamDecoder decoder = new DumlStreamDecoder();
        assertTrue(decoder.feed(wrongVersion).isEmpty());
        assertEquals(1, decoder.rejectedFrames());
        assertEquals(1, decoder.feed(SHORT).size());
    }

    @Test public void resetDiscardsIncompletePacketAndResetsRejectionCount() {
        DumlStreamDecoder decoder = new DumlStreamDecoder();
        byte[] bad = SHORT.clone();
        bad[11] ^= 1;
        assertTrue(decoder.feed(bad).isEmpty());
        assertEquals(1, decoder.rejectedFrames());
        assertTrue(decoder.feed(Arrays.copyOfRange(BATTERY, 0, 20)).isEmpty());
        decoder.reset();
        assertEquals(0, decoder.rejectedFrames());
        assertTrue(decoder.feed(Arrays.copyOfRange(BATTERY, 20, 53)).isEmpty());
        assertEquals(1, decoder.feed(SHORT).size());
    }

    @Test public void nullNotificationIsRejected() {
        assertThrows(NullPointerException.class, () -> new DumlStreamDecoder().feed(null));
    }

    private static byte[] hex(String text) { return java.util.HexFormat.of().parseHex(text.replace(" ", "")); }
}
