package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OverlayLayoutTest {
    @Test void centersIslandAndLeavesSpaceForItsExpandedAnimation() {
        OverlayLayout.Position large = OverlayLayout.position(0, 0, 1440, 900, 310, 487, 306, 310, 490);
        assertEquals(565, large.islandX());
        assertEquals(0, large.islandY());
        assertEquals(981.5, large.coreX());
        assertEquals(92, large.coreY());
        assertTrue(large.coreX() > 720 + 487.0 / 2, "core is beside the widest animated island");
    }

    @Test void smallScreensPlaceCoreBelowAndKeepItInTheWorkArea() {
        OverlayLayout.Position small = OverlayLayout.position(-200, 40, 640, 900, 310, 487, 306, 310, 490);
        assertEquals(-35, small.islandX());
        assertEquals(130, small.coreX());
        assertEquals(364, small.coreY());
        assertTrue(small.coreY() > 40 + 306, "core is below the expanded notch");
        OverlayLayout.Position shortScreen = OverlayLayout.position(-200, 40, 640, 600, 310, 487, 306, 310, 490);
        assertEquals(150, shortScreen.coreY(), "clamp to the work area when there is not enough vertical room");
    }
}
