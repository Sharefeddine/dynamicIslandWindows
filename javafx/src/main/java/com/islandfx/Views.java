package com.islandfx;

import java.io.IOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public final class Views {
    public record IslandView(Parent root, IslandController controller) { }
    public record JarvisView(Parent root, JarvisController controller) { }
    private Views() { }

    public static IslandView island() throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(Views.class.getResource("island.fxml")));
        Parent root = loader.load();
        return new IslandView(root, loader.getController());
    }
    public static JarvisView jarvis() throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(Views.class.getResource("jarvis.fxml")));
        Parent root = loader.load();
        return new JarvisView(root, loader.getController());
    }
}
