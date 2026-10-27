package com.islandfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class OverlayLayoutTest {
    @Test void centersAndClampsOnPrimaryWorkArea() {
        OverlayLayout.Position large = OverlayLayout.position(0, 0, 1440, 390, 310);
        assertEquals(525, large.islandX());
        assertEquals(8, large.islandY());
        assertEquals(933, large.coreX());
        assertEquals(92, large.coreY());
        OverlayLayout.Position small = OverlayLayout.position(-200, 40, 640, 390, 310);
        assertEquals(-75, small.islandX());
        assertEquals(130, small.coreX());
        assertEquals(132, small.coreY());
    }
}
