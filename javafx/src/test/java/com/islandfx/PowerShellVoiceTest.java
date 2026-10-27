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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PowerShellVoiceTest {
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

    @Test void decodesRecognizedSpeechAndReportsErrors() throws Exception {
        FakeProcess child = new FakeProcess();
        CountDownLatch launched = new CountDownLatch(1), ready = new CountDownLatch(1), phrase = new CountDownLatch(1), error = new CountDownLatch(1), ended = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        try (PowerShellVoice voice = new PowerShellVoice(encoded -> { assertFalse(encoded.isBlank()); launched.countDown(); return child; })) {
            voice.start(text -> { result.set(text); phrase.countDown(); }, (event, detail) -> {
                if (event == DeviceServices.VoiceEvent.LISTENING) ready.countDown();
                if (event == DeviceServices.VoiceEvent.ERROR) error.countDown();
                if (event == DeviceServices.VoiceEvent.ENDED) ended.countDown();
            });
            assertTrue(launched.await(2, TimeUnit.SECONDS));
            child.line("ISLAND_READY");
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            child.line("ISLAND_TEXT:" + Base64.getEncoder().encodeToString("next track".getBytes(StandardCharsets.UTF_8)));
            assertTrue(phrase.await(2, TimeUnit.SECONDS));
            assertEquals("next track", result.get());
            child.line("ISLAND_ERROR");
            assertTrue(error.await(2, TimeUnit.SECONDS));
            child.finish();
            assertFalse(ended.await(200, TimeUnit.MILLISECONDS), "an error should not also report normal completion");
        }
    }

    @Test void stoppingKillsWorkerAndRejectsLateCallbacks() throws Exception {
        FakeProcess child = new FakeProcess();
        CountDownLatch launched = new CountDownLatch(1), ready = new CountDownLatch(1), late = new CountDownLatch(1);
        try (PowerShellVoice voice = new PowerShellVoice(encoded -> { launched.countDown(); return child; })) {
            voice.start(text -> late.countDown(), (event, detail) -> {
                if (event == DeviceServices.VoiceEvent.LISTENING) ready.countDown();
                if (event != DeviceServices.VoiceEvent.LISTENING) late.countDown();
            });
            assertTrue(launched.await(2, TimeUnit.SECONDS));
            child.line("ISLAND_READY");
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            voice.stop();
            assertFalse(child.isAlive());
            child.line("ISLAND_TEXT:bmV4dCB0cmFjaw=="); // Simulate buffered output after stop.
            child.finish();
            assertFalse(late.await(200, TimeUnit.MILLISECONDS));
        }
    }

    @Test void startupFailureIsReported() throws Exception {
        CountDownLatch error = new CountDownLatch(1);
        try (PowerShellVoice voice = new PowerShellVoice(encoded -> { throw new IOException("not installed"); })) {
            voice.start(text -> fail("unexpected phrase"), (event, detail) -> { if (event == DeviceServices.VoiceEvent.ERROR) error.countDown(); });
            assertTrue(error.await(2, TimeUnit.SECONDS));
        }
    }
}
