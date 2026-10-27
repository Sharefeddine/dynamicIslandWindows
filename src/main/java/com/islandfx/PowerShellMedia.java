package com.islandfx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Streams local Windows media-session snapshots without blocking the JavaFX thread. */
final class PowerShellMedia implements AutoCloseable {
    @FunctionalInterface interface ProcessStarter { Process start(String encodedScript) throws IOException; }
    private final ProcessStarter starter;
    private final long retryMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lock = new Object();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "island-windows-media");
        thread.setDaemon(true);
        return thread;
    });
    private boolean started;
    private Process process;

    PowerShellMedia() {
        this(encoded -> new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded).redirectErrorStream(true).start(), 15000);
    }
    PowerShellMedia(ProcessStarter starter, long retryMillis) {
        this.starter = starter;
        this.retryMillis = retryMillis;
    }

    void start(Consumer<MediaInfo> callback) {
        synchronized (lock) {
            if (closed.get() || started) return;
            started = true;
            worker.execute(() -> run(callback));
        }
    }

    private void run(Consumer<MediaInfo> callback) {
        while (!closed.get()) {
            Process child = null;
            try {
                String script = "$islandParentPid = " + ProcessHandle.current().pid() + "\n" + readScript();
                String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
                child = starter.start(encoded);
                synchronized (lock) {
                    if (closed.get()) { child.destroyForcibly(); return; }
                    process = child;
                }
                MediaInfo last = null;
                try (BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!closed.get() && (line = output.readLine()) != null) {
                        Optional<MediaInfo> frame = parseFrame(line);
                        if (frame.isPresent() && !closed.get()) {
                            last = frame.get();
                            callback.accept(last);
                        }
                    }
                }
                if (!closed.get() && (last == null || last.availability() != MediaInfo.Availability.UNAVAILABLE)) {
                    callback.accept(MediaInfo.unavailable("Media helper stopped; retrying."));
                }
            } catch (IOException | RuntimeException failure) {
                if (!closed.get()) callback.accept(MediaInfo.unavailable("Could not start the Windows media helper."));
            } finally {
                synchronized (lock) { if (process == child) process = null; }
                if (child != null && child.isAlive()) child.destroyForcibly();
            }
            if (!closed.get()) {
                try { Thread.sleep(retryMillis); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private static String readScript() throws IOException {
        try (InputStream input = PowerShellMedia.class.getResourceAsStream("media-session.ps1")) {
            if (input == null) throw new IOException("Windows media helper is missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Optional<MediaInfo> parseFrame(String line) {
        if (line == null || line.length() > 16384) return Optional.empty();
        String[] fields = line.split("\\|", -1);
        if (fields.length == 2 && fields[0].equals("ISLAND_MEDIA")) {
            if (fields[1].equals("NONE")) return Optional.of(MediaInfo.noSession());
            if (fields[1].equals("UNAVAILABLE")) return Optional.of(MediaInfo.unavailable());
        }
        if (fields.length == 3 && fields[0].equals("ISLAND_MEDIA") && fields[1].equals("UNAVAILABLE")) {
            try { return Optional.of(MediaInfo.unavailable(decode(fields[2]))); }
            catch (IllegalArgumentException invalid) { return Optional.empty(); }
        }
        if (fields.length != 6 || !fields[0].equals("ISLAND_MEDIA") || !fields[1].equals("SESSION")) return Optional.empty();
        try {
            return Optional.of(MediaInfo.session(MediaInfo.playbackFromWindows(fields[2]),
                    decode(fields[3]), decode(fields[4]), decode(fields[5])));
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    @Override public void close() {
        closed.set(true);
        synchronized (lock) {
            if (process != null) { process.destroyForcibly(); process = null; }
        }
        worker.shutdownNow();
    }
}
