package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PowerShellMediaTest {
    private static String encode(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String frame(String state, String title, String artist, String source) {
        return "ISLAND_MEDIA|SESSION|" + state + "|" + encode(title) + "|" + encode(artist) + "|" + encode(source);
    }

    @Test void parsesWindowsSessionAndUnicodeWithoutGuessingPlayback() {
        MediaInfo media = PowerShellMedia.parseFrame(frame("Playing", "YouTube: رحلة | music", "Example channel", "chrome.exe")).orElseThrow();
        assertEquals(MediaInfo.Availability.AVAILABLE, media.availability());
        assertEquals(MediaInfo.Playback.PLAYING, media.playback());
        assertEquals("YouTube: رحلة | music", media.title());
        assertEquals("Example channel", media.artist());
        assertEquals("Google Chrome", media.sourceLabel());
        assertEquals(MediaInfo.Playback.PAUSED, PowerShellMedia.parseFrame(frame("Paused", "A", "", "msedge.exe")).orElseThrow().playback());
        assertEquals(MediaInfo.Playback.UNKNOWN, PowerShellMedia.parseFrame(frame("Changing", "A", "", "other.exe")).orElseThrow().playback());
        assertEquals(MediaInfo.Availability.NO_SESSION, PowerShellMedia.parseFrame("ISLAND_MEDIA|NONE").orElseThrow().availability());
        assertEquals(MediaInfo.Availability.UNAVAILABLE, PowerShellMedia.parseFrame("ISLAND_MEDIA|UNAVAILABLE").orElseThrow().availability());
        assertEquals("Windows media access denied", PowerShellMedia.parseFrame("ISLAND_MEDIA|UNAVAILABLE|" + encode("Windows media access denied")).orElseThrow().detail());
    }

    @Test void acceptsAnOptionalWindowsTimelineWithoutInventingOne() {
        String base = frame("Playing", "Video", "Channel", "chrome.exe");
        MediaInfo noTimeline = PowerShellMedia.parseFrame(base).orElseThrow();
        assertFalse(noTimeline.hasTimeline());
        MediaInfo timeline = PowerShellMedia.parseFrame(base + "|75|200").orElseThrow();
        assertTrue(timeline.hasTimeline());
        assertEquals(75, timeline.positionSeconds());
        assertEquals(200, timeline.durationSeconds());
        assertFalse(PowerShellMedia.parseFrame(base + "||").orElseThrow().hasTimeline());
        assertFalse(PowerShellMedia.parseFrame(base + "|bad|200").orElseThrow().hasTimeline());
        assertEquals(200, PowerShellMedia.parseFrame(base + "|500|200").orElseThrow().positionSeconds());
    }

    @Test void ignoresMalformedAndOverlongFrames() {
        assertTrue(PowerShellMedia.parseFrame(null).isEmpty());
        assertTrue(PowerShellMedia.parseFrame("powershell diagnostic output").isEmpty());
        assertTrue(PowerShellMedia.parseFrame("ISLAND_MEDIA|SESSION|Playing|***|YQ==|Yg==").isEmpty());
        assertTrue(PowerShellMedia.parseFrame("ISLAND_MEDIA|SESSION|Playing|" + "a".repeat(17000) + "||").isEmpty());
    }

    private static final class FakeProcess extends Process {
        private final PipedInputStream stdout = new PipedInputStream();
        private final PipedOutputStream writer;
        final AtomicBoolean alive = new AtomicBoolean(true);
        FakeProcess() throws IOException { writer = new PipedOutputStream(stdout); }
        void line(String value) throws IOException { writer.write((value + "\n").getBytes(StandardCharsets.UTF_8)); writer.flush(); }
        void finish() throws IOException { alive.set(false); writer.close(); }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { if (alive.get()) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { alive.set(false); }
        @Override public Process destroyForcibly() { alive.set(false); return this; }
        @Override public boolean isAlive() { return alive.get(); }
    }

    @Test void streamsUpdatesAndStopsTheWorker() throws Exception {
        FakeProcess child = new FakeProcess();
        CountDownLatch launched = new CountDownLatch(1), update = new CountDownLatch(1), late = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<MediaInfo> received = new AtomicReference<>();
        try (PowerShellMedia media = new PowerShellMedia(encoded -> {
            String script = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_16LE);
            assertTrue(script.contains("GlobalSystemMediaTransportControlsSessionManager"));
            assertTrue(script.contains("$islandParentPid = "));
            launched.countDown();
            return child;
        }, 50)) {
            media.start(value -> {
                if (callbacks.incrementAndGet() == 1) { received.set(value); update.countDown(); }
                else late.countDown();
            });
            assertTrue(launched.await(2, TimeUnit.SECONDS));
            child.line(frame("Playing", "Video title", "Channel", "chrome.exe"));
            assertTrue(update.await(2, TimeUnit.SECONDS));
            assertEquals("Video title", received.get().title());
            media.close();
            assertFalse(child.isAlive());
            try { child.line("ISLAND_MEDIA|NONE"); } catch (IOException closedPipe) { /* Reader may already be closed. */ }
            child.finish();
            assertFalse(late.await(200, TimeUnit.MILLISECONDS));
        }
    }

    @Test void preservesTheHelperErrorWhenTheProcessExits() throws Exception {
        FakeProcess child = new FakeProcess();
        CountDownLatch launched = new CountDownLatch(1), error = new CountDownLatch(1), extra = new CountDownLatch(1);
        AtomicReference<String> detail = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        try (PowerShellMedia media = new PowerShellMedia(encoded -> { launched.countDown(); return child; }, 5000)) {
            media.start(value -> {
                if (calls.incrementAndGet() == 1) { detail.set(value.detail()); error.countDown(); }
                else extra.countDown();
            });
            assertTrue(launched.await(2, TimeUnit.SECONDS));
            child.line("ISLAND_MEDIA|UNAVAILABLE|" + encode("Access denied"));
            assertTrue(error.await(2, TimeUnit.SECONDS));
            child.finish();
            assertEquals("Access denied", detail.get());
            assertFalse(extra.await(200, TimeUnit.MILLISECONDS), "the detailed error must not be replaced on EOF");
        }
    }

    @Test void reportsFailureWhenTheWorkerCannotStart() throws Exception {
        CountDownLatch unavailable = new CountDownLatch(1);
        try (PowerShellMedia media = new PowerShellMedia(encoded -> { throw new IOException("not installed"); }, 5000)) {
            media.start(value -> { if (value.availability() == MediaInfo.Availability.UNAVAILABLE) unavailable.countDown(); });
            assertTrue(unavailable.await(2, TimeUnit.SECONDS));
        }
    }

    @Test void retriesAfterAWorkerStartupFailure() throws Exception {
        FakeProcess child = new FakeProcess();
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch unavailable = new CountDownLatch(1), retried = new CountDownLatch(1), update = new CountDownLatch(1);
        try (PowerShellMedia media = new PowerShellMedia(encoded -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("temporary failure");
            retried.countDown();
            return child;
        }, 30)) {
            media.start(value -> {
                if (value.availability() == MediaInfo.Availability.UNAVAILABLE) unavailable.countDown();
                if (value.availability() == MediaInfo.Availability.AVAILABLE) update.countDown();
            });
            assertTrue(unavailable.await(2, TimeUnit.SECONDS));
            assertTrue(retried.await(2, TimeUnit.SECONDS));
            child.line(frame("Playing", "Video after retry", "", "chrome.exe"));
            assertTrue(update.await(2, TimeUnit.SECONDS));
            media.close();
            child.finish();
        }
    }
}
