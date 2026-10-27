package com.islandfx;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.OptionalInt;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Coordinates UI state without putting OS work on the JavaFX application thread. */
public final class AppModel implements AutoCloseable {
    public enum VoiceState { OFF, STARTING, LISTENING, ERROR }

    private final DeviceServices devices;
    private final ScheduledExecutorService batteryWorker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "island-battery");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BooleanProperty expanded = new SimpleBooleanProperty(false);
    private final StringProperty battery = new SimpleStringProperty("--");
    private final ObjectProperty<VoiceState> voice = new SimpleObjectProperty<>(VoiceState.OFF);
    private final StringProperty message = new SimpleStringProperty("Microphone is off");
    private long voiceSession;

    public AppModel(DeviceServices devices) { this.devices = devices; }
    public BooleanProperty expandedProperty() { return expanded; }
    public StringProperty batteryProperty() { return battery; }
    public ObjectProperty<VoiceState> voiceProperty() { return voice; }
    public StringProperty messageProperty() { return message; }

    public void startBatteryPolling() {
        batteryWorker.scheduleWithFixedDelay(this::pollBattery, 0, 30, TimeUnit.SECONDS);
    }

    private void pollBattery() {
        OptionalInt reading;
        try { reading = devices.batteryPercent(); }
        catch (RuntimeException | LinkageError failure) { reading = OptionalInt.empty(); }
        final String display = reading.isPresent() ? reading.getAsInt() + "%" : "--";
        if (!closed.get()) Platform.runLater(() -> { if (!closed.get()) battery.set(display); });
    }

    public void toggleExpanded() { expanded.set(!expanded.get()); }

    public void media(DeviceServices.MediaKey key) {
        boolean sent;
        try { sent = devices.sendMediaKey(key); }
        catch (RuntimeException | LinkageError failure) { sent = false; }
        message.set(sent ? "Media key sent to Windows" : "Could not send a media key");
    }

    public void toggleListening() {
        if (voice.get() == VoiceState.STARTING || voice.get() == VoiceState.LISTENING) {
            stopListening();
            return;
        }
        final long session = ++voiceSession;
        voice.set(VoiceState.STARTING);
        message.set("Starting Windows speech…");
        devices.startVoice(
                text -> Platform.runLater(() -> {
                    if (!closed.get() && session == voiceSession && voice.get() == VoiceState.LISTENING) handlePhrase(text);
                }),
                (event, detail) -> Platform.runLater(() -> {
                    if (closed.get() || session != voiceSession) return;
                    if (event == DeviceServices.VoiceEvent.LISTENING) {
                        voice.set(VoiceState.LISTENING);
                        message.set("Try music, time, or battery");
                    } else if (event == DeviceServices.VoiceEvent.ERROR) {
                        voice.set(VoiceState.ERROR);
                        message.set(detail == null || detail.isBlank() ? "Windows speech unavailable" : detail);
                    } else {
                        voice.set(VoiceState.ERROR);
                        message.set("Speech input ended. Click to retry.");
                    }
                }));
    }

    public void stopListening() {
        ++voiceSession;
        devices.stopVoice();
        voice.set(VoiceState.OFF);
        message.set("Microphone is off");
    }

    void handlePhrase(String text) {
        switch (CommandParser.parse(text)) {
            case PLAY_PAUSE -> media(DeviceServices.MediaKey.PLAY_PAUSE);
            case NEXT -> media(DeviceServices.MediaKey.NEXT);
            case PREVIOUS -> media(DeviceServices.MediaKey.PREVIOUS);
            case TIME -> message.set("It’s " + LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
            case BATTERY -> message.set(battery.get().equals("--") ? "No battery reading available" : "Battery: " + battery.get());
            case HELP -> message.set("Try music, next, time, or battery");
            case STOP_LISTENING -> stopListening();
            case UNKNOWN -> message.set("Try music, next, time, or battery");
        }
    }

    // Only used by the screenshot harness with a fake device; never by the running app.
    void setPreviewSample(int percent, boolean listening) {
        battery.set(percent + "%");
        voice.set(listening ? VoiceState.LISTENING : VoiceState.OFF);
        message.set(listening ? "Try music, time, or battery" : "Microphone is off");
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            ++voiceSession;
            devices.stopVoice();
            batteryWorker.shutdownNow();
            devices.close();
        }
    }
}
