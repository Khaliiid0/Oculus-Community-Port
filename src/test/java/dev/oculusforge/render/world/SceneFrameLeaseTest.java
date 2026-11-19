package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SceneFrameLeaseTest {
    @Test void normalFrameReleaseDoesNotCloseTheStillActivePack() {
        var lease=new SceneFrameLease<>(new Object());assertFalse(lease.release());
        assertThrows(IllegalStateException.class,lease::release);
    }
    @Test void repeatedOwnerFailuresDeferOneCloseUntilScopeRelease() {
        var owner=new Object();var lease=new SceneFrameLease<>(owner);
        assertTrue(lease.deferClose(owner));assertTrue(lease.deferClose(owner));assertTrue(lease.release());
        assertFalse(lease.deferClose(owner));assertThrows(IllegalStateException.class,lease::release);
    }
    @Test void unrelatedOwnerCannotAcquireTheInFlightFramebuffer() {
        var owner=new Object();var lease=new SceneFrameLease<>(owner);
        assertFalse(lease.deferClose(new Object()));assertFalse(lease.deferClose(null));assertFalse(lease.release());
        assertThrows(NullPointerException.class,()->new SceneFrameLease<>(null));
    }
}
