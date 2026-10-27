package com.islandfx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** An opt-in Windows System.Speech worker. Stopping the process releases its microphone. */
final class PowerShellVoice implements AutoCloseable {
    private static final String SCRIPT = """
        $ErrorActionPreference = 'Stop'
        $recognizer = $null
        try {
            Add-Type -AssemblyName System.Speech
            $recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine
            $grammar = New-Object System.Speech.Recognition.DictationGrammar
            $recognizer.LoadGrammar($grammar)
            $recognizer.SetInputToDefaultAudioDevice()
            Register-ObjectEvent -InputObject $recognizer -EventName SpeechRecognized -SourceIdentifier island-recognized | Out-Null
            Register-ObjectEvent -InputObject $recognizer -EventName RecognizeCompleted -SourceIdentifier island-ended | Out-Null
            $recognizer.RecognizeAsync([System.Speech.Recognition.RecognizeMode]::Multiple)
            [Console]::WriteLine('ISLAND_READY')
            [Console]::Out.Flush()
            while ($true) {
                $event = Wait-Event -Timeout 2
                if ($null -eq $event) { continue }
                if ($event.SourceIdentifier -eq 'island-recognized') {
                    $text = $event.SourceEventArgs.Result.Text
                    if ($text) {
                        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($text))
                        [Console]::WriteLine('ISLAND_TEXT:' + $encoded)
                        [Console]::Out.Flush()
                    }
                } elseif ($event.SourceIdentifier -eq 'island-ended') {
                    throw 'Speech input ended'
                }
                Remove-Event -EventIdentifier $event.EventIdentifier
            }
        } catch {
            [Console]::WriteLine('ISLAND_ERROR')
            [Console]::Out.Flush()
        } finally {
            if ($recognizer) { $recognizer.Dispose() }
        }
        """;
    @FunctionalInterface interface ProcessStarter { Process start(String encodedScript) throws IOException; }
    private final Object lock = new Object();
    private final ProcessStarter starter;
    private final ExecutorService worker = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "island-voice");
        thread.setDaemon(true);
        return thread;
    });
    private long generation;
    private Process process;

    PowerShellVoice() {
        this(encoded -> new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded).redirectErrorStream(true).start());
    }
    PowerShellVoice(ProcessStarter starter) { this.starter = starter; }

    void start(Consumer<String> phrase, BiConsumer<DeviceServices.VoiceEvent, String> state) {
        final long id;
        synchronized (lock) {
            stopLocked();
            id = ++generation;
        }
        worker.execute(() -> run(id, phrase, state));
    }

    private void run(long id, Consumer<String> phrase, BiConsumer<DeviceServices.VoiceEvent, String> state) {
        boolean reportedError = false;
        Process child = null;
        try {
            String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            child = starter.start(encoded);
            synchronized (lock) {
                if (id != generation) { child.destroyForcibly(); return; }
                process = child;
            }
            try (BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = output.readLine()) != null) {
                    if (line.equals("ISLAND_READY")) {
                        emit(id, () -> state.accept(DeviceServices.VoiceEvent.LISTENING, ""));
                    } else if (line.startsWith("ISLAND_TEXT:")) {
                        try {
                            String text = new String(Base64.getDecoder().decode(line.substring(12)), StandardCharsets.UTF_8);
                            emit(id, () -> phrase.accept(text));
                        } catch (IllegalArgumentException ignored) { /* Ignore malformed output. */ }
                    } else if (line.equals("ISLAND_ERROR")) {
                        reportedError = true;
                        emit(id, () -> state.accept(DeviceServices.VoiceEvent.ERROR, "Check microphone and Windows speech"));
                    }
                }
            }
            if (!reportedError) emit(id, () -> state.accept(DeviceServices.VoiceEvent.ENDED, ""));
        } catch (IOException failure) {
            emit(id, () -> state.accept(DeviceServices.VoiceEvent.ERROR, "Windows speech could not start"));
        } finally {
            synchronized (lock) { if (process == child) process = null; }
            if (child != null && child.isAlive()) child.destroyForcibly();
        }
    }

    private void emit(long id, Runnable callback) {
        synchronized (lock) { if (id == generation) callback.run(); }
    }
    void stop() { synchronized (lock) { stopLocked(); } }
    private void stopLocked() {
        ++generation;
        if (process != null) { process.destroyForcibly(); process = null; }
    }
    @Override public void close() { stop(); worker.shutdownNow(); }
}
