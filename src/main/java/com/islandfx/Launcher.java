package com.islandfx;

import javafx.application.Application;

/** Separate launcher also works when JavaFX modules are supplied on the class path. */
public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) { Application.launch(IslandFxApplication.class, args); }
}
