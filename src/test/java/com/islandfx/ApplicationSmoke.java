package com.islandfx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Launches the actual two-stage app, verifies placement and expansion, then exits. */
public final class ApplicationSmoke {
    private static Stage primary;
    private ApplicationSmoke() { }
    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timed out");
        runOnFx(() -> {
            primary = new Stage();
            try { new IslandFxApplication().start(primary); }
            catch (Exception failure) { throw new RuntimeException(failure); }
            check(primary.isShowing(), "island window appears");
            check(primary.isAlwaysOnTop(), "island remains on top");
            check(Window.getWindows().stream().anyMatch(window -> window instanceof Stage s && "JARVIS".equals(s.getTitle()) && s.isShowing()),
                    "separate JARVIS window appears");
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            check(Math.abs(primary.getX() + primary.getWidth() / 2 - (screen.getMinX() + screen.getWidth() / 2)) < 2,
                    "island centered in primary work area");
            var header = primary.getScene().getRoot().lookup("#header");
            Event.fireEvent(header, new MouseEvent(MouseEvent.MOUSE_CLICKED, 12, 12, 12, 12, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null));
            check(primary.getScene().getRoot().lookup("#details").isVisible(), "island expands on click");
        });
        runOnFx(() -> Event.fireEvent(primary, new WindowEvent(primary, WindowEvent.WINDOW_CLOSE_REQUEST)));
        System.out.println("Actual JavaFX application opened both windows, centered, expanded, and exited.");
    }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX operation timed out");
        if (failure.get() != null) throw new AssertionError("Application smoke test failed", failure.get());
    }
}
