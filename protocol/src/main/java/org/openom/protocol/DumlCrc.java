// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import java.util.Objects;

/** Reflected DUML CRCs: full-frame initial values, not OM's special-case body seed. */
public final class DumlCrc {
    private DumlCrc() { }

    public static int crc8(byte[] bytes, int offset, int length) {
        range(bytes, offset, length);
        int crc = 0x77;
        for (int i = offset; i < offset + length; i++) {
            crc ^= bytes[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) != 0 ? 0x8c : 0);
        }
        return crc & 0xff;
    }

    public static int crc16(byte[] bytes, int offset, int length) {
        range(bytes, offset, length);
        int crc = 0x3692;
        for (int i = offset; i < offset + length; i++) {
            crc ^= bytes[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) != 0 ? 0x8408 : 0);
        }
        return crc & 0xffff;
    }

    private static void range(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes");
        if (offset < 0 || length < 0 || offset > bytes.length || length > bytes.length - offset)
            throw new IndexOutOfBoundsException("Invalid CRC slice");
    }
}
