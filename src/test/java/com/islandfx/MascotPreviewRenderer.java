package com.islandfx;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.OptionalInt;
import java.util.OptionalLong;
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

/** Captures the actual mascot animation with simulated idle samples. */
public final class MascotPreviewRenderer {
    private static final class DemoDevices implements DeviceServices {
        public OptionalInt batteryPercent() { return OptionalInt.empty(); }
        public boolean sendMediaKey(MediaKey key) { return true; }
        public void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state) { }
        public void stopVoice() { }
        public void close() { }
    }
    private MascotPreviewRenderer() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "target/mascot-frames" : args[0]);
        Files.createDirectories(directory);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> Platform.runLater(() -> {
            AppModel model = new AppModel(new DemoDevices());
            CompanionModel companion = new CompanionModel(new CompanionStore(null), Clock.systemDefaultZone(), System::nanoTime, (title, message) -> { });
            Stage stage = new Stage();
            try {
                Pane desktop = new Pane(); desktop.setPrefSize(540, 90);
                desktop.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #bdd3ed, #426895);");
                Label caption = new Label("SIMULATED: IDLE FOR TWO MINUTES"); caption.relocate(115, 65);
                caption.setStyle("-fx-text-fill: rgba(245,249,255,0.9); -fx-font-size: 9px; -fx-font-weight: bold;");
                Views.IslandView island = Views.island();
                island.controller().attach(model, companion, () -> { });
                Region notch = (Region) island.root(); notch.relocate(115, 0);
                desktop.getChildren().addAll(caption, notch);
                Scene scene = new Scene(desktop, 540, 90); stage.setScene(scene); stage.show();
                model.setPreviewSample(72, false);
                model.mediaInfoProperty().set(MediaInfo.session(MediaInfo.Playback.PLAYING, "A sample YouTube video", "Example channel", "chrome.exe"));
                model.updateMascot(OptionalLong.of(MascotMood.SLEEP_AFTER_MILLIS), System.nanoTime());
                AnimationTimer capture = new AnimationTimer() {
                    long start, previous;
                    int frames;
                    boolean returned, finishedWave;
                    @Override public void handle(long now) {
                        if (start == 0) start = now;
                        long elapsed = now - start;
                        try {
                            if (!returned && elapsed >= 2_700_000_000L) {
                                returned = true; model.updateMascot(OptionalLong.of(0), now);
                                caption.setText("SIMULATED: USER RETURNS · WAVING HELLO");
                            }
                            if (!finishedWave && elapsed >= 5_300_000_000L) {
                                finishedWave = true; model.updateMascot(OptionalLong.of(0), now);
                                caption.setText("SIMULATED: AWAKE AGAIN");
                            }
                            if (previous == 0 || now - previous >= 100_000_000L) {
                                previous = now;
                                desktop.applyCss(); desktop.layout();
                                WritableImage image = scene.snapshot(null);
                                int width = (int) image.getWidth(), height = (int) image.getHeight();
                                int[] pixels = new int[width * height];
                                image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                                BufferedImage original = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                                original.setRGB(0, 0, width, height, pixels, 0, width);
                                BufferedImage zoom = new BufferedImage(660, 180, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D graphics = zoom.createGraphics();
                                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                                graphics.drawImage(original, 0, 0, 660, 180, 105, 0, 435, 90, null); graphics.dispose();
                                ImageIO.write(zoom, "png", directory.resolve(String.format("frame-%03d.png", frames++)).toFile());
                            }
                            if (elapsed >= 6_100_000_000L) { stop(); island.controller().close(); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit(); }
                        } catch (Throwable error) {
                            failure.set(error); stop(); island.controller().close(); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit();
                        }
                    }
                };
                capture.start();
            } catch (Throwable error) { failure.set(error); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit(); }
        }));
        finished.await();
        if (failure.get() != null) throw new RuntimeException("Mascot preview failed", failure.get());
    }
}
