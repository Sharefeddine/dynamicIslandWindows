package com.islandfx;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/** Original, lightweight vector companion used inside the JavaFX/FXML island header. */
public final class MascotView extends Pane implements AutoCloseable {
    private final Group awakeEyes = new Group();
    private final Group sleepyEyes = new Group();
    private final Group restingArm = new Group();
    private final Group wavingArm = new Group();
    private final Group sleepMarks = new Group();
    private final SVGPath body = path("M 9 37 C 9 29 13 26 18 25 L 23 25 C 29 26 32 30 32 37 Z", "#6079b7", null, 0);
    private final Rotate armRotation = new Rotate(0, 28, 28);
    private final Timeline wave;
    private final Timeline sleep;

    public MascotView() {
        setMinSize(50, 40); setPrefSize(50, 40); setMaxSize(50, 40);
        setMouseTransparent(true);
        setAccessibleText("Island companion");
        setId("mascotView");
        sleepMarks.setId("mascotSleepMarks");
        wavingArm.setId("mascotWavingArm");
        sleepyEyes.setId("mascotSleepyEyes");
        awakeEyes.setId("mascotAwakeEyes");

        Line rest = line(28, 28, 34, 32, "#7791cd", 4);
        Circle restHand = circle(34.5, 32, 2.7, "#ffd3ad");
        restingArm.getChildren().addAll(rest, restHand);
        Line arm = line(28, 28, 37, 18, "#7791cd", 4);
        Circle hand = circle(38, 16.5, 3.1, "#ffd3ad");
        wavingArm.getChildren().addAll(arm, hand);
        wavingArm.getTransforms().add(armRotation);

        SVGPath neck = path("M 17 22 H 23 V 28 H 17 Z", "#f5bd94", null, 0);
        Circle face = circle(20, 15, 10, "#ffd3ad");
        SVGPath hair = path("M 10 14 C 8 6 13 3 20 3 C 27 3 31 7 30 14 L 28 15 C 28 12 27 10 26 9 C 22 11 16 11 11 10 Z", "#303b57", null, 0);
        awakeEyes.getChildren().addAll(circle(16.5, 15, 1.1, "#3a3141"), circle(23.5, 15, 1.1, "#3a3141"));
        sleepyEyes.getChildren().add(path("M 14.5 15 Q 16.5 17 18.5 15 M 21.5 15 Q 23.5 17 25.5 15", "transparent", "#684f55", 1.1));
        SVGPath smile = path("M 17.5 20 Q 20 22 22.5 20", "transparent", "#9a625e", 1);

        Text smallZ = new Text(34, 19, "z"); smallZ.setFont(Font.font("System", FontWeight.BOLD, 7)); smallZ.setFill(Color.web("#aabfe7"));
        Text middleZ = new Text(38, 13, "z"); middleZ.setFont(Font.font("System", FontWeight.BOLD, 8)); middleZ.setFill(Color.web("#c3d7ff"));
        Text largeZ = new Text(43, 9, "Z"); largeZ.setFont(Font.font("System", FontWeight.BOLD, 9)); largeZ.setFill(Color.web("#dce7ff"));
        sleepMarks.getChildren().addAll(smallZ, middleZ, largeZ);
        getChildren().addAll(restingArm, body, neck, face, hair, awakeEyes, sleepyEyes, smile, wavingArm, sleepMarks);

        wave = new Timeline(
                frame(0, armRotation.angleProperty(), 0), frame(180, armRotation.angleProperty(), -25),
                frame(380, armRotation.angleProperty(), 14), frame(580, armRotation.angleProperty(), -23),
                frame(780, armRotation.angleProperty(), 13), frame(1000, armRotation.angleProperty(), 0));
        wave.setCycleCount(2);
        sleep = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(sleepMarks.opacityProperty(), 0.15),
                        new KeyValue(sleepMarks.translateYProperty(), 2), new KeyValue(body.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(650), new KeyValue(sleepMarks.opacityProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(body.scaleYProperty(), 1.06, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1900), new KeyValue(sleepMarks.opacityProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(sleepMarks.translateYProperty(), -5, Interpolator.EASE_BOTH),
                        new KeyValue(body.scaleYProperty(), 1, Interpolator.EASE_BOTH)));
        sleep.setCycleCount(Animation.INDEFINITE);
        showPose(MascotMood.Pose.AWAKE);
    }

    void showPose(MascotMood.Pose pose) {
        wave.stop(); sleep.stop();
        armRotation.setAngle(0); body.setScaleY(1);
        boolean sleeping = pose == MascotMood.Pose.SLEEPING;
        boolean waving = pose == MascotMood.Pose.WAVING;
        awakeEyes.setVisible(!sleeping); sleepyEyes.setVisible(sleeping);
        restingArm.setVisible(!waving); wavingArm.setVisible(waving);
        sleepMarks.setVisible(sleeping);
        setAccessibleText(sleeping ? "Island companion sleeping" : waving ? "Island companion waving hello" : "Island companion awake");
        if (sleeping) sleep.playFromStart();
        else if (waving) wave.playFromStart();
    }

    private static KeyFrame frame(int millis, javafx.beans.value.WritableValue<Number> target, double value) {
        return new KeyFrame(Duration.millis(millis), new KeyValue(target, value, Interpolator.EASE_BOTH));
    }
    private static Circle circle(double x, double y, double radius, String fill) {
        return new Circle(x, y, radius, Color.web(fill));
    }
    private static Line line(double x1, double y1, double x2, double y2, String stroke, double width) {
        Line line = new Line(x1, y1, x2, y2); line.setStroke(Color.web(stroke)); line.setStrokeWidth(width); line.setStrokeLineCap(StrokeLineCap.ROUND); return line;
    }
    private static SVGPath path(String data, String fill, String stroke, double width) {
        SVGPath shape = new SVGPath(); shape.setContent(data); shape.setFill(Color.web(fill));
        if (stroke != null) { shape.setStroke(Color.web(stroke)); shape.setStrokeWidth(width); shape.setStrokeLineCap(StrokeLineCap.ROUND); }
        return shape;
    }
    @Override public void close() { wave.stop(); sleep.stop(); }
}
