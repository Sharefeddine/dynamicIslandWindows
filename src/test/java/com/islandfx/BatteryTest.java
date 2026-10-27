package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BatteryTest {
    @Test void reportsRealPercentOrUnavailable() {
        assertEquals(72, WindowsDeviceServices.decodeBattery(1, 72).orElseThrow());
        assertEquals(0, WindowsDeviceServices.decodeBattery(2, 0).orElseThrow());
        assertTrue(WindowsDeviceServices.decodeBattery(128, 72).isEmpty());
        assertTrue(WindowsDeviceServices.decodeBattery(255, 72).isEmpty());
        assertTrue(WindowsDeviceServices.decodeBattery(1, 255).isEmpty());
        assertTrue(WindowsDeviceServices.decodeBattery(1, -1).isEmpty());
    }
}
