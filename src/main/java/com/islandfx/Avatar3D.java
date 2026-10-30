package com.islandfx;

import java.io.IOException;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.ParallelCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/** Two actual JavaFX 3D busts, one sculpted from primitives and one using a CC0 anatomical head. */
final class Avatar3D extends SubScene implements AutoCloseable {
    private final Group stylized = new Group(), realistic = new Group();
    private final Group stylizedEyes = new Group(), realisticEyes = new Group();
    private final Group restingArm = new Group(), wavingArm = new Group();
    private final Rotate armRotation = new Rotate(0, 28, 28);
    private final Timeline wave, blink;

    Avatar3D() throws IOException {
        this(new Group());
    }
    private Avatar3D(Group sceneRoot) throws IOException {
        super(sceneRoot, 50, 40, true, SceneAntialiasing.DISABLED);
        setFill(Color.TRANSPARENT); setCamera(new ParallelCamera()); setMouseTransparent(true);
        stylized.setId("avatarStylized3D"); realistic.setId("avatarRealistic3D");
        stylizedEyes.setId("avatarStylizedEyes3D"); realisticEyes.setId("avatarRealisticEyes3D");
        wavingArm.setId("avatarWavingArm3D");
        PhongMaterial skin = material("#f2be9a"), shirt = material("#617eb8"), dark = material("#34354c");
        PhongMaterial white = material("#f6f3ec"), iris = material("#544438");
        Sphere torso = sphere(11.5, 20, 38, 1, shirt, 1.15, 0.80, 0.68);
        Sphere neck = sphere(3.8, 20, 27, 0, skin, 0.8, 1.15, 0.75);
        Cylinder armRest = new Cylinder(2.4, 9); armRest.setMaterial(shirt); armRest.setTranslateX(29); armRest.setTranslateY(31); armRest.setTranslateZ(-1); armRest.setRotate(-53);
        Sphere handRest = sphere(2.5, 33, 33.5, -2, skin, 1, 1, 1);
        restingArm.getChildren().addAll(armRest, handRest);
        Cylinder armWave = new Cylinder(2.4, 13); armWave.setMaterial(shirt); armWave.setTranslateX(32); armWave.setTranslateY(24); armWave.setTranslateZ(-3); armWave.setRotate(42);
        Sphere handWave = sphere(3, 37, 18.5, -4, skin, 1, 1, 1);
        wavingArm.getChildren().addAll(armWave, handWave); wavingArm.getTransforms().add(armRotation);

        Sphere stylizedHead = sphere(10, 20, 15, 0, skin, 0.88, 1.05, 0.82);
        Sphere stylizedHair = sphere(10, 20, 10, 4, dark, 0.97, 0.75, 0.85);
        stylizedEyes.getChildren().addAll(sphere(1.2, 16.6, 15, -8.8, dark, 0.9, 1.1, 0.5),
                sphere(1.2, 23.4, 15, -8.8, dark, 0.9, 1.1, 0.5));
        stylized.getChildren().addAll(stylizedHair, stylizedHead, stylizedEyes);

        AvatarMesh.Data data = AvatarMesh.loadHead();
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(data.points()); mesh.getTexCoords().setAll(0, 0); mesh.getFaces().setAll(data.faces());
        int[] smoothing = new int[data.faces().length / 6]; java.util.Arrays.fill(smoothing, 1);
        mesh.getFaceSmoothingGroups().setAll(smoothing);
        MeshView head = new MeshView(mesh); head.setCullFace(CullFace.NONE); head.setMaterial(skin);
        Sphere hair = sphere(9.4, 20, 4, 4, dark, 0.95, 0.50, 0.93);
        realisticEyes.getChildren().addAll(sphere(1.8, 16.77, 14, 0, white, 1, 0.72, 0.8),
                sphere(1.8, 23.23, 14, 0, white, 1, 0.72, 0.8),
                sphere(0.72, 16.77, 14, -1.42, iris, 1, 1, 0.4),
                sphere(0.72, 23.23, 14, -1.42, iris, 1, 1, 0.4));
        realistic.getChildren().addAll(hair, head, realisticEyes);
        PointLight light = new PointLight(Color.color(0.75, 0.75, 0.75));
        light.setTranslateX(-45); light.setTranslateY(-55); light.setTranslateZ(-85);
        sceneRoot.getChildren().addAll(new AmbientLight(Color.color(0.72, 0.72, 0.72)), light,
                restingArm, torso, neck, stylized, realistic, wavingArm);
        wave = new Timeline(frame(0, armRotation.angleProperty(), 0), frame(180, armRotation.angleProperty(), -25),
                frame(380, armRotation.angleProperty(), 14), frame(580, armRotation.angleProperty(), -23),
                frame(780, armRotation.angleProperty(), 13), frame(1000, armRotation.angleProperty(), 0));
        wave.setCycleCount(2);
        blink = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(stylizedEyes.scaleYProperty(), 1), new KeyValue(realisticEyes.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(3300), new KeyValue(stylizedEyes.scaleYProperty(), 1), new KeyValue(realisticEyes.scaleYProperty(), 1)),
                new KeyFrame(Duration.millis(3380), new KeyValue(stylizedEyes.scaleYProperty(), 0.06), new KeyValue(realisticEyes.scaleYProperty(), 0.06)),
                new KeyFrame(Duration.millis(3500), new KeyValue(stylizedEyes.scaleYProperty(), 1), new KeyValue(realisticEyes.scaleYProperty(), 1)));
        blink.setCycleCount(Animation.INDEFINITE);
        setStyle(CompanionStore.AvatarStyle.STYLIZED);
        showPose(MascotMood.Pose.AWAKE);
    }
    boolean isBlinking() { return blink.getStatus() == Animation.Status.RUNNING; }
    void setStyle(CompanionStore.AvatarStyle style) {
        boolean useReal = style == CompanionStore.AvatarStyle.REALISTIC;
        stylized.setVisible(!useReal); realistic.setVisible(useReal);
    }
    void showPose(MascotMood.Pose pose) {
        wave.stop(); blink.stop(); armRotation.setAngle(0);
        boolean sleeping = pose == MascotMood.Pose.SLEEPING, waving = pose == MascotMood.Pose.WAVING;
        restingArm.setVisible(!waving); wavingArm.setVisible(waving);
        stylizedEyes.setScaleY(sleeping ? 0.06 : 1); realisticEyes.setScaleY(sleeping ? 0.06 : 1);
        if (waving) wave.playFromStart();
        if (!sleeping) blink.playFromStart();
    }
    private static KeyFrame frame(int millis, javafx.beans.value.WritableValue<Number> target, double value) {
        return new KeyFrame(Duration.millis(millis), new KeyValue(target, value, Interpolator.EASE_BOTH));
    }
    private static PhongMaterial material(String color) {
        PhongMaterial material = new PhongMaterial(Color.web(color)); material.setSpecularColor(Color.web("#5b5b5b")); return material;
    }
    private static Sphere sphere(double radius, double x, double y, double z, PhongMaterial material, double sx, double sy, double sz) {
        Sphere sphere = new Sphere(radius, 24); sphere.setTranslateX(x); sphere.setTranslateY(y); sphere.setTranslateZ(z);
        sphere.setScaleX(sx); sphere.setScaleY(sy); sphere.setScaleZ(sz); sphere.setMaterial(material); return sphere;
    }
    @Override public void close() { wave.stop(); blink.stop(); }
}
