package org.openom.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class OmProfileTest {
    @Test public void rejectsMissingOrWrongDirectionCharacteristics() {
        assertFalse(OmProfile.supportsTransport(false, 0x10, 0x08));
        assertFalse(OmProfile.supportsTransport(true, 0, 0x08));
        assertFalse(OmProfile.supportsTransport(true, 0x02, 0x08));
        assertFalse(OmProfile.supportsTransport(true, 0x10, 0));
        assertFalse(OmProfile.supportsTransport(true, 0x10, 0x02));
    }

    @Test public void acceptsEitherGattWriteModeAndAdditionalProperties() {
        assertTrue(OmProfile.supportsTransport(true, 0x10, 0x08));
        assertTrue(OmProfile.supportsTransport(true, 0x10, 0x04));
        assertTrue(OmProfile.supportsTransport(true, 0x12, 0x0c));
    }

    @Test public void unnamedAndRenamedDevicesRemainPossibleViaAdvertisement() {
        assertTrue(OmProfile.isCandidate(null, true));
        assertTrue(OmProfile.isCandidate("Mein Gimbal", true));
        assertFalse(OmProfile.isCandidate(null, false));
        assertFalse(OmProfile.isCandidate("Thermometer", false));
    }

    @Test public void namesAreOnlyCaseInsensitiveHints() {
        assertTrue(OmProfile.isCandidate("Osmo Mobile 2", false));
        assertTrue(OmProfile.isCandidate("OM170-123", false));
        assertFalse(OmProfile.isCandidate("Wohnzimmer", false));
    }
}
