// SPDX-License-Identifier: GPL-3.0-or-later
package org.openom.protocol;

/** Bounded retry budget for one connection-loss episode. */
public final class RetryPolicy {
    private int attempt;

    public long nextDelayMillis() {
        if (attempt >= 3) return -1;
        return 1000L << attempt++;
    }

    public void reset() { attempt = 0; }
}
