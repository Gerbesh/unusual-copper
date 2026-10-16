package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.compat.quark.TroughFeedingPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TroughFeedingPolicyTest {
    @Test void feedingStopsAtTheLimitRatherThanSpendingFoodAboveIt() {
        assertTrue(TroughFeedingPolicy.permits(31,32,0,true));
        assertFalse(TroughFeedingPolicy.permits(32,32,0,true));
        assertFalse(TroughFeedingPolicy.permits(33,32,0,true));
        assertTrue(TroughFeedingPolicy.permits(33,64,0,true));
    }
    @Test void babiesParentsOnCooldownAndAlreadyLovingAnimalsDoNotConsumeFood() {
        assertFalse(TroughFeedingPolicy.permits(2,32,-24000,true));
        assertFalse(TroughFeedingPolicy.permits(2,32,6000,true));
        assertFalse(TroughFeedingPolicy.permits(2,32,0,false));
        assertTrue(TroughFeedingPolicy.permits(2,32,0,true));
    }
    @Test void zeroOrNegativeLimitsNeverAllowFeeding() {
        assertFalse(TroughFeedingPolicy.permits(2,0,0,true));
        assertFalse(TroughFeedingPolicy.permits(2,-1,0,true));
        assertFalse(TroughFeedingPolicy.permits(-1,32,0,true));
    }
}
