// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Bounded, single-thread-owned decoder of FFF4 notification bytes (not notification-sized frames). */
public final class DumlStreamDecoder {
    private static final int MIN_LENGTH = 13;
    private static final int MAX_LENGTH = 1023;
    private final byte[] buffer = new byte[MAX_LENGTH];
    private int used;
    private long rejected;

    public List<DumlPacket> feed(byte[] chunk) {
        Objects.requireNonNull(chunk, "chunk");
        List<DumlPacket> packets = new ArrayList<>();
        for (byte value : chunk) {
            if (used == MAX_LENGTH) {
                rejected++;
                discard(1);
            }
            buffer[used++] = value;
            process(packets);
        }
        return packets;
    }

    public long rejectedFrames() { return rejected; }

    /** Incomplete data cannot be continued across a connection or service change. */
    public void reset() { used = 0; rejected = 0; }

    private void process(List<DumlPacket> packets) {
        while (used > 0) {
            if ((buffer[0] & 0xff) != 0x55) { discard(1); continue; }
            if (used < 4) return;
            int field = (buffer[1] & 0xff) | ((buffer[2] & 0xff) << 8);
            int length = field & 0x3ff;
            int version = field >>> 10;
            if (length < MIN_LENGTH || version != 1 || DumlCrc.crc8(buffer, 0, 3) != (buffer[3] & 0xff)) {
                rejected++;
                discard(1);
                continue;
            }
            if (used < length) return;
            int expectedCrc = (buffer[length - 2] & 0xff) | ((buffer[length - 1] & 0xff) << 8);
            if (DumlCrc.crc16(buffer, 0, length - 2) != expectedCrc) {
                rejected++;
                discard(1);
                continue;
            }
            packets.add(new DumlPacket(Arrays.copyOf(buffer, length)));
            discard(length);
        }
    }

    private void discard(int count) {
        used -= count;
        System.arraycopy(buffer, count, buffer, 0, used);
    }
}
