package com.islandfx;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.Duration;

/** Coordinates UI state without putting background OS reads on the JavaFX thread. */
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
    private final ObjectProperty<MediaInfo> mediaInfo = new SimpleObjectProperty<>(MediaInfo.loading());
    private final MascotMood mascot = new MascotMood();
    private final ObjectProperty<MascotMood.Pose> mascotPose = new SimpleObjectProperty<>(mascot.pose());
    private final ObjectProperty<VoiceState> voice = new SimpleObjectProperty<>(VoiceState.OFF);
    private final StringProperty message = new SimpleStringProperty("Microphone is off");
    private final StringProperty inputSource = new SimpleStringProperty("NO INPUT YET");
    private final StringProperty lastInput = new SimpleStringProperty("Nothing heard or typed yet.");
    private final StringProperty response = new SimpleStringProperty("Try help, time, or battery.");
    private long voiceSession;
    private Timeline idleTicker;

    public AppModel(DeviceServices devices) { this.devices = devices; }
    public BooleanProperty expandedProperty() { return expanded; }
    public StringProperty batteryProperty() { return battery; }
    public ObjectProperty<MediaInfo> mediaInfoProperty() { return mediaInfo; }
    ObjectProperty<MascotMood.Pose> mascotPoseProperty() { return mascotPose; }
    public ObjectProperty<VoiceState> voiceProperty() { return voice; }
    public StringProperty messageProperty() { return message; }
    public StringProperty inputSourceProperty() { return inputSource; }
    public StringProperty lastInputProperty() { return lastInput; }
    public StringProperty responseProperty() { return response; }

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

    public void startMediaMonitoring() {
        try {
            devices.startMedia(info -> {
                if (!closed.get()) Platform.runLater(() -> {
                    if (!closed.get()) mediaInfo.set(info == null ? MediaInfo.unavailable() : info);
                });
            });
        } catch (RuntimeException | LinkageError failure) {
            mediaInfo.set(MediaInfo.unavailable());
        }
    }

    /** Poll the system's idle duration, not the contents or location of input. */
    void startIdleMonitoring() {
        if (closed.get() || idleTicker != null) return;
        mascot.greet(System.nanoTime());
        mascotPose.set(mascot.pose());
        pollIdle();
        idleTicker = new Timeline(new KeyFrame(Duration.seconds(1), event -> pollIdle()));
        idleTicker.setCycleCount(Animation.INDEFINITE);
        idleTicker.play();
    }
    private void pollIdle() {
        OptionalLong reading;
        try { reading = devices.idleMillis(); }
        catch (RuntimeException | LinkageError failure) { reading = OptionalLong.empty(); }
        updateMascot(reading, System.nanoTime());
    }
    // Exposed to package-level tests and preview harnesses for deterministic samples.
    void updateMascot(OptionalLong idleMillis, long nowNanos) {
        mascot.sample(idleMillis, nowNanos);
        mascotPose.set(mascot.pose());
    }
    void noteMascotInteraction() {
        mascot.interact(System.nanoTime());
        mascotPose.set(mascot.pose());
    }

    public void toggleExpanded() { expanded.set(!expanded.get()); }

    public void media(DeviceServices.MediaKey key) {
        boolean sent;
        try { sent = devices.sendMediaKey(key); }
        catch (RuntimeException | LinkageError failure) { sent = false; }
        if (!sent) {
            response.set("Could not send a media key to Windows.");
        } else if (key == DeviceServices.MediaKey.PLAY_PAUSE) {
            response.set("Sent the play/pause toggle to Windows.");
        } else {
            response.set(key == DeviceServices.MediaKey.NEXT ? "Sent next to Windows." : "Sent previous to Windows.");
        }
    }

    public void toggleListening() {
        if (voice.get() == VoiceState.STARTING || voice.get() == VoiceState.LISTENING) {
            stopListening();
            return;
        }
        final long session = ++voiceSession;
        voice.set(VoiceState.STARTING);
        message.set("Starting Windows speech…");
        try {
            devices.startVoice(
                text -> Platform.runLater(() -> {
                    if (!closed.get() && session == voiceSession && voice.get() == VoiceState.LISTENING) {
                        execute(text, "HEARD");
                    }
                }),
                (event, detail) -> Platform.runLater(() -> {
                    if (closed.get() || session != voiceSession) return;
                    if (event == DeviceServices.VoiceEvent.LISTENING) {
                        voice.set(VoiceState.LISTENING);
                        message.set("Listening for speech");
                    } else if (event == DeviceServices.VoiceEvent.PARTIAL) {
                        if (voice.get() == VoiceState.LISTENING && detail != null && !detail.isBlank()) {
                            inputSource.set("HEARING");
                            lastInput.set(tidy(detail));
                            response.set("Listening… waiting for the complete phrase.");
                        }
                    } else if (event == DeviceServices.VoiceEvent.REJECTED) {
                        if (voice.get() == VoiceState.LISTENING) {
                            inputSource.set("NOT RECOGNIZED");
                            lastInput.set(detail == null || detail.isBlank() ? "Could not understand speech." : tidy(detail));
                            response.set("I didn't understand. Try again or type a command.");
                        }
                    } else if (event == DeviceServices.VoiceEvent.ERROR) {
                        voice.set(VoiceState.ERROR);
                        String reason = detail == null || detail.isBlank() ? "Windows speech unavailable" : tidy(detail);
                        message.set(reason);
                        response.set(reason + " You can still type a command.");
                    } else {
                        voice.set(VoiceState.ERROR);
                        message.set("Speech input ended. Click to retry.");
                        response.set("Speech input ended. Retry or type a command.");
                    }
                }));
        } catch (RuntimeException | LinkageError failure) {
            ++voiceSession;
            try { devices.stopVoice(); } catch (RuntimeException | LinkageError ignored) { }
            voice.set(VoiceState.ERROR);
            message.set("Windows speech could not start");
            response.set("Windows speech could not start. You can still type a command.");
        }
    }

    public void stopListening() {
        ++voiceSession;
        devices.stopVoice();
        voice.set(VoiceState.OFF);
        message.set("Microphone is off");
    }

    public void submitCommand(String text) {
        if (text == null || text.isBlank()) {
            response.set("Type a command first.");
            return;
        }
        execute(text, "TYPED");
    }

    private void execute(String text, String source) {
        String cleaned = tidy(text);
        if (cleaned.isBlank()) return;
        inputSource.set(source);
        lastInput.set(cleaned);
        switch (CommandParser.parse(cleaned)) {
            case PLAY_PAUSE -> media(DeviceServices.MediaKey.PLAY_PAUSE);
            case NEXT -> media(DeviceServices.MediaKey.NEXT);
            case PREVIOUS -> media(DeviceServices.MediaKey.PREVIOUS);
            case TIME -> response.set("It’s " + LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
            case BATTERY -> response.set(battery.get().equals("--") ? "No battery reading available." : "Battery: " + battery.get());
            case HELP -> response.set("Try play, pause, next, previous, time, battery, or stop listening.");
            case STOP_LISTENING -> {
                boolean wasOff = voice.get() == VoiceState.OFF;
                stopListening();
                response.set(wasOff ? "The microphone is already off." : "Stopped listening.");
            }
            case UNKNOWN -> response.set("I didn't understand. Type help for commands.");
        }
    }
    private static String tidy(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\p{Cc}", " ").trim();
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500);
    }

    // Only used by the screenshot harness with a fake device; never by the running app.
    void setPreviewSample(int percent, boolean listening) {
        battery.set(percent + "%");
        voice.set(listening ? VoiceState.LISTENING : VoiceState.OFF);
        message.set(listening ? "Listening for speech" : "Microphone is off");
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            ++voiceSession;
            if (idleTicker != null) idleTicker.stop();
            devices.stopVoice();
            batteryWorker.shutdownNow();
            devices.close();
        }
    }
}
