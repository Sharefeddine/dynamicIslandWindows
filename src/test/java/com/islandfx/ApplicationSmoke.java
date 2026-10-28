package com.islandfx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Launches the actual two-stage app, verifies placement and animation, then exits. */
public final class ApplicationSmoke {
    private static Stage primary, core;
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
            core = (Stage) Window.getWindows().stream()
                    .filter(window -> window instanceof Stage s && "JARVIS".equals(s.getTitle()) && s.isShowing())
                    .findFirst().orElseThrow(() -> new AssertionError("separate JARVIS window appears"));
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            check(Math.abs(primary.getX() + primary.getWidth() / 2 - (screen.getMinX() + screen.getWidth() / 2)) < 2,
                    "island centered in primary work area");
            Event.fireEvent(primary.getScene().getRoot(), mouse(MouseEvent.MOUSE_ENTERED, MouseButton.NONE));
            check(primary.getScene().getRoot().lookup("#details").isVisible(), "hover opens the island");
        });
        Thread.sleep(180);
        runOnFx(() -> {
            double width = primary.getScene().getRoot().prefWidth(-1);
            check(width > 310 && width < 490, "expansion animates between compact and open widths");
            var page = primary.getScene().getRoot().lookup("#musicPage");
            var nav = primary.getScene().getRoot().lookup("#navigation");
            check(page.localToScene(page.getBoundsInLocal()).getMaxY() <= nav.localToScene(nav.getBoundsInLocal()).getMinY() + 1,
                    "page content does not overlap navigation during the morph");
        });
        Thread.sleep(600);
        runOnFx(() -> {
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            check(Math.abs(primary.getScene().getRoot().prefWidth(-1) - 480) < 1, "island finishes opening");
            check(Math.abs(primary.getX() + primary.getWidth() / 2 - (screen.getMinX() + screen.getWidth() / 2)) < 3, "animated island remains centered");
            check(Math.abs(primary.getY() - screen.getMinY()) < 1, "notch sits at the top edge");
            check(core.getX() >= primary.getX() + primary.getWidth() || core.getY() >= primary.getY() + primary.getHeight(),
                    "initial JARVIS placement does not cover the expanded island when there is room");
            Event.fireEvent(primary.getScene().getRoot(), mouse(MouseEvent.MOUSE_EXITED, MouseButton.NONE));
        });
        Thread.sleep(1100);
        runOnFx(() -> {
            check(Math.abs(primary.getScene().getRoot().prefWidth(-1) - 310) < 1, "island closes after pointer leaves");
            Event.fireEvent(primary.getScene().getRoot().lookup("#header"), mouse(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY));
            Event.fireEvent(primary.getScene().getRoot(), mouse(MouseEvent.MOUSE_EXITED, MouseButton.NONE));
        });
        Thread.sleep(1100);
        runOnFx(() -> {
            check(Math.abs(primary.getScene().getRoot().prefWidth(-1) - 480) < 1, "click pins the island open when the pointer leaves");
            Event.fireEvent(primary.getScene().getRoot().lookup("#header"), mouse(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY));
        });
        Thread.sleep(650);
        runOnFx(() -> {
            check(Math.abs(primary.getScene().getRoot().prefWidth(-1) - 310) < 1, "clicking the pinned header closes the island");
            Event.fireEvent(primary, new WindowEvent(primary, WindowEvent.WINDOW_CLOSE_REQUEST));
        });
        System.out.println("Both JavaFX windows, hover/pin animation, placement, and cleanup passed.");
    }
    private static MouseEvent mouse(EventType<MouseEvent> type, MouseButton button) {
        return new MouseEvent(type, 12, 12, 12, 12, button, 1,
                false, false, false, false, button == MouseButton.PRIMARY, false, false, true, false, false, null);
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
