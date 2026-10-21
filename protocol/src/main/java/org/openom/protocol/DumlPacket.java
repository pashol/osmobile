// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import java.util.Arrays;

/** Validated received frame. Raw addresses and flags deliberately have no inferred semantics. */
public final class DumlPacket {
    private final int length;
    private final int sender;
    private final int receiver;
    private final int sequence;
    private final int flags;
    private final int commandSet;
    private final int commandId;
    private final byte[] payload;

    DumlPacket(byte[] frame) {
        length = frame.length;
        sender = frame[4] & 0xff;
        receiver = frame[5] & 0xff;
        sequence = (frame[6] & 0xff) | ((frame[7] & 0xff) << 8);
        flags = frame[8] & 0xff;
        commandSet = frame[9] & 0xff;
        commandId = frame[10] & 0xff;
        payload = Arrays.copyOfRange(frame, 11, frame.length - 2);
    }

    public int length() { return length; }
    public int sender() { return sender; }
    public int receiver() { return receiver; }
    public int sequence() { return sequence; }
    public int flags() { return flags; }
    public int commandSet() { return commandSet; }
    public int commandId() { return commandId; }
    public byte[] payload() { return payload.clone(); }
}
