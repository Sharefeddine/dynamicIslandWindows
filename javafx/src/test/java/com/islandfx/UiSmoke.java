package com.islandfx;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Standalone headless-capable integration test for actual FXML views and controller state. */
public final class UiSmoke {
    private static final class FakeDevices implements DeviceServices {
        final List<MediaKey> keys = new ArrayList<>();
        Consumer<String> onPhrase;
        int stopCalls;
        public OptionalInt batteryPercent() { return OptionalInt.empty(); }
        public boolean sendMediaKey(MediaKey key) { keys.add(key); return true; }
        public void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state) {
            onPhrase = phrase; state.accept(VoiceEvent.LISTENING, "");
        }
        public void stopVoice() { stopCalls++; }
        public void close() { }
    }
    private static FakeDevices devices;
    private static AppModel model;
    private static Views.IslandView island;
    private static Views.JarvisView core;
    private static Stage stage;
    private static boolean hidden;
    private UiSmoke() { }

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX did not start");
        try {
            runOnFx(() -> {
                devices = new FakeDevices(); model = new AppModel(devices);
                try { island = Views.island(); core = Views.jarvis(); }
                catch (Exception error) { throw new RuntimeException(error); }
                island.controller().attach(model, () -> { });
                core.controller().attach(model, () -> { hidden = true; model.stopListening(); });
                Pane pane = new Pane(island.root(), core.root());
                stage = new Stage(); stage.setScene(new Scene(pane, 900, 600)); stage.show();
                pane.applyCss(); pane.layout();
                check(label(island.root(), "#batteryLabel").getText().equals("--"), "battery must start unavailable");
                check(label(core.root(), "#voiceLabel").getText().equals("Ready to listen"), "mic must start off");
                VBox details = (VBox) island.root().lookup("#details");
                check(!details.isVisible() && !details.isManaged(), "island starts compact");
                model.toggleExpanded();
                check(details.isVisible() && details.isManaged(), "island expands");
                for (Node node : island.root().lookupAll(".transport")) {
                    Button button = (Button) node;
                    if ("Play or pause".equals(button.getAccessibleText())) button.fire();
                }
                check(devices.keys.equals(List.of(DeviceServices.MediaKey.PLAY_PAUSE)), "media button uses Windows toggle");
                model.toggleListening();
                check(model.voiceProperty().get() == AppModel.VoiceState.STARTING, "voice starts asynchronously");
            });
            runOnFx(() -> {
                check(model.voiceProperty().get() == AppModel.VoiceState.LISTENING, "ready event enters listening state");
                check(label(core.root(), "#voiceLabel").getText().equals("Listening..."), "listening is indicated");
                devices.onPhrase.accept("next track");
            });
            runOnFx(() -> {
                check(devices.keys.equals(List.of(DeviceServices.MediaKey.PLAY_PAUSE, DeviceServices.MediaKey.NEXT)), "voice command sent next");
                model.stopListening();
                devices.onPhrase.accept("next track"); // An event from the old session must be ignored.
            });
            runOnFx(() -> {
                check(devices.keys.size() == 2, "stale recognition ignored after stopping");
                check(label(core.root(), "#voiceLabel").getText().equals("Ready to listen"), "mic off state displayed");
                model.toggleListening();
            });
            runOnFx(() -> {
                ((Button) core.root().lookup("#closeButton")).fire();
                check(hidden && model.voiceProperty().get() == AppModel.VoiceState.OFF, "hiding core stops mic");
                check(devices.stopCalls >= 2, "voice worker is stopped when requested and when hidden");
                model.close(); stage.hide();
            });
            System.out.println("FXML views, media, voice states, stale callbacks, and hide cleanup passed.");
        } finally { Platform.exit(); }
    }
    private static Label label(Node root, String selector) { return (Label) root.lookup(selector); }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
    private static void runOnFx(Runnable task) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try { task.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); }
        });
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX operation timed out");
        if (error.get() != null) throw new AssertionError("JavaFX smoke test failed", error.get());
    }
}
