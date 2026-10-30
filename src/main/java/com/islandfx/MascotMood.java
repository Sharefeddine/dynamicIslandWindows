package com.islandfx;

import java.util.OptionalLong;

/** Small deterministic state machine; it never inspects what the user typed or clicked. */
final class MascotMood {
    enum Pose { AWAKE, SLEEPING, WAVING }
    static final long SLEEP_AFTER_MILLIS = 2 * 60_000L;
    static final long WAVE_NANOS = 2_400_000_000L;
    private Pose pose = Pose.AWAKE;
    private long waveStarted;

    Pose pose() { return pose; }

    void greet(long nowNanos) {
        pose = Pose.WAVING;
        waveStarted = nowNanos;
    }

    /** An interaction with the island wakes the character immediately. */
    void interact(long nowNanos) {
        if (pose == Pose.SLEEPING) greet(nowNanos);
    }

    void sample(OptionalLong idleMillis, long nowNanos) {
        boolean known = idleMillis != null && idleMillis.isPresent() && idleMillis.getAsLong() >= 0;
        if (known && idleMillis.getAsLong() >= SLEEP_AFTER_MILLIS) {
            pose = Pose.SLEEPING;
        } else if (pose == Pose.SLEEPING) {
            if (known) greet(nowNanos);
            else pose = Pose.AWAKE; // Do not claim the user is idle when the API is unavailable.
        } else if (pose == Pose.WAVING && nowNanos - waveStarted >= WAVE_NANOS) {
            pose = Pose.AWAKE;
        }
    }
}
