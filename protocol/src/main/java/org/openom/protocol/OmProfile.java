// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

import java.util.Locale;
import java.util.UUID;

/** Profile facts from alkersan/om-research; not an OM2 identity guarantee. */
public final class OmProfile {
    public static final UUID SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    public static final UUID NOTIFY = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private OmProfile() { }

    public static boolean supportsTransport(boolean service, int notifyProperties, int writeProperties) {
        // Bluetooth SIG characteristic property bits, independent of Android classes.
        return service && (notifyProperties & 0x10) != 0 && (writeProperties & 0x0c) != 0;
    }

    public static boolean isCandidate(String name, boolean advertisesService) {
        String normalized = name == null ? "" : name.toUpperCase(Locale.ROOT);
        return advertisesService || normalized.contains("OSMO") || normalized.contains("OM170");
    }
}
