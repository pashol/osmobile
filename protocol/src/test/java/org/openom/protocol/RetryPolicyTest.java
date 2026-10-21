package org.openom.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class RetryPolicyTest {
    @Test public void repeatedFailureEventuallyStopsInsteadOfLoopingForever() {
        RetryPolicy policy = new RetryPolicy();
        assertEquals(1000, policy.nextDelayMillis());
        assertEquals(2000, policy.nextDelayMillis());
        assertEquals(4000, policy.nextDelayMillis());
        assertEquals(-1, policy.nextDelayMillis());
        assertEquals(-1, policy.nextDelayMillis());
    }

    @Test public void newEpisodeGetsFreshRetryBudget() {
        RetryPolicy policy = new RetryPolicy();
        policy.nextDelayMillis();
        policy.nextDelayMillis();
        policy.reset();
        assertEquals(1000, policy.nextDelayMillis());
    }
}
