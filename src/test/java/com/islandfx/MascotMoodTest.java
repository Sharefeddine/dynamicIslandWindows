package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MascotMoodTest {
    @Test void greetsBrieflyThenReturnsToAwake() {
        MascotMood mood = new MascotMood();
        mood.greet(100);
        assertEquals(MascotMood.Pose.WAVING, mood.pose());
        mood.sample(OptionalLong.of(0), 100 + MascotMood.WAVE_NANOS - 1);
        assertEquals(MascotMood.Pose.WAVING, mood.pose());
        mood.sample(OptionalLong.of(0), 100 + MascotMood.WAVE_NANOS);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose());
    }

    @Test void sleepsAfterInactivityAndWavesOnceOnReturn() {
        MascotMood mood = new MascotMood();
        mood.sample(OptionalLong.of(MascotMood.SLEEP_AFTER_MILLIS - 1), 0);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose());
        mood.sample(OptionalLong.of(MascotMood.SLEEP_AFTER_MILLIS), 1);
        assertEquals(MascotMood.Pose.SLEEPING, mood.pose());
        mood.sample(OptionalLong.of(300), 10);
        assertEquals(MascotMood.Pose.WAVING, mood.pose());
        mood.sample(OptionalLong.of(200), 10 + MascotMood.WAVE_NANOS);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose());
        mood.sample(OptionalLong.of(0), 20 + MascotMood.WAVE_NANOS);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose(), "continued activity must not restart the wave");
    }

    @Test void islandInteractionWakesImmediatelyWithoutRestartingAnExistingWave() {
        MascotMood mood = new MascotMood();
        mood.sample(OptionalLong.of(MascotMood.SLEEP_AFTER_MILLIS), 0);
        mood.interact(10);
        assertEquals(MascotMood.Pose.WAVING, mood.pose());
        mood.interact(100);
        mood.sample(OptionalLong.of(0), 10 + MascotMood.WAVE_NANOS);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose());
    }

    @Test void unavailableIdleDataDoesNotPretendTheUserIsAsleep() {
        MascotMood mood = new MascotMood();
        mood.sample(OptionalLong.empty(), 0);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose());
        mood.sample(OptionalLong.of(MascotMood.SLEEP_AFTER_MILLIS), 1);
        mood.sample(OptionalLong.empty(), 2);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose());
        mood.sample(OptionalLong.of(-1), 3);
        assertEquals(MascotMood.Pose.AWAKE, mood.pose());
    }

    @Test void windowsTickArithmeticHandlesThirtyTwoBitWrap() {
        assertEquals(1000, WindowsDeviceServices.elapsedTicks(5000, 4000));
        assertEquals(32, WindowsDeviceServices.elapsedTicks(16, 0xfffffff0));
    }
}
