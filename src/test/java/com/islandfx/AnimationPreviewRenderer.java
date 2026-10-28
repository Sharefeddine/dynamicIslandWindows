package com.islandfx;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Captures real animated FXML frames with simulated devices, suitable for assembling a GIF. */
public final class AnimationPreviewRenderer {
    private static final class DemoDevices implements DeviceServices {
        public OptionalInt batteryPercent() { return OptionalInt.empty(); }
        public boolean sendMediaKey(MediaKey key) { return true; }
        public void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state) { }
        public void stopVoice() { }
        public void close() { }
    }
    private AnimationPreviewRenderer() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "target/motion-frames" : args[0]);
        Files.createDirectories(directory);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> Platform.runLater(() -> {
            AppModel model = new AppModel(new DemoDevices());
            CompanionModel companion = new CompanionModel(new CompanionStore(null), Clock.systemDefaultZone(), System::nanoTime, (title, message) -> { });
            Stage stage = new Stage();
            try {
                Pane desktop = new Pane(); desktop.setPrefSize(540, 340);
                desktop.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #bcd2ec, #446b9e);");
                Label caption = new Label("JAVAFX MOTION PREVIEW · SIMULATED DATA"); caption.relocate(12, 320);
                caption.setStyle("-fx-text-fill: rgba(240,248,255,0.8); -fx-font-size: 9px; -fx-font-weight: bold;");
                Views.IslandView island = Views.island();
                island.controller().attach(model, companion, () -> { });
                Region notch = (Region) island.root();
                notch.prefWidthProperty().addListener((unused, before, width) -> notch.setLayoutX((540 - width.doubleValue()) / 2));
                notch.relocate((540 - notch.getPrefWidth()) / 2, 0);
                desktop.getChildren().addAll(caption, notch);
                Scene scene = new Scene(desktop, 540, 340); stage.setScene(scene); stage.show();
                model.setPreviewSample(72, false);
                model.mediaInfoProperty().set(MediaInfo.session(MediaInfo.Playback.PLAYING, "A sample YouTube video", "Example channel", "chrome.exe", 193, 256));
                AnimationTimer capture = new AnimationTimer() {
                    long start, previous;
                    int frames;
                    boolean opened, changed, closed;
                    @Override public void handle(long now) {
                        if (start == 0) start = now;
                        long elapsed = now - start;
                        try {
                            if (!opened && elapsed >= 300_000_000L) { opened = true; model.expandedProperty().set(true); }
                            if (!changed && elapsed >= 1_400_000_000L) { changed = true; companion.selectPage(CompanionModel.Page.FOCUS); }
                            if (!closed && elapsed >= 2_500_000_000L) { closed = true; model.expandedProperty().set(false); }
                            if (previous == 0 || now - previous >= 80_000_000L) {
                                previous = now;
                                desktop.applyCss(); desktop.layout();
                                WritableImage image = scene.snapshot(null);
                                int width = (int) image.getWidth(), height = (int) image.getHeight();
                                int[] pixels = new int[width * height];
                                image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                                BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                                output.setRGB(0, 0, width, height, pixels, 0, width);
                                ImageIO.write(output, "png", directory.resolve(String.format("frame-%03d.png", frames++)).toFile());
                            }
                            if (elapsed >= 3_300_000_000L) { stop(); island.controller().close(); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit(); }
                        } catch (Throwable error) {
                            failure.set(error); stop(); island.controller().close(); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit();
                        }
                    }
                };
                capture.start();
            } catch (Throwable error) { failure.set(error); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit(); }
        }));
        finished.await();
        if (failure.get() != null) throw new RuntimeException("Animation preview failed", failure.get());
    }
}
