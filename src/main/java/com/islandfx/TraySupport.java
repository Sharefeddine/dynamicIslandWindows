package com.islandfx;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.util.Objects;
import javax.imageio.ImageIO;
import javafx.application.Platform;

/** Tray support is optional; every action also exists in the island's context menu. */
final class TraySupport implements AutoCloseable {
    private TrayIcon icon;
    void install(Runnable toggleCore, Runnable toggleVoice, Runnable quit) {
        try {
            if (!SystemTray.isSupported()) return;
            PopupMenu menu = new PopupMenu();
            MenuItem core = new MenuItem("Show / hide JARVIS");
            core.addActionListener(event -> Platform.runLater(toggleCore));
            MenuItem voice = new MenuItem("Start / stop listening");
            voice.addActionListener(event -> Platform.runLater(toggleVoice));
            MenuItem exit = new MenuItem("Exit Island");
            exit.addActionListener(event -> Platform.runLater(quit));
            menu.add(core); menu.add(voice); menu.addSeparator(); menu.add(exit);
            icon = new TrayIcon(ImageIO.read(Objects.requireNonNull(TraySupport.class.getResource("tray.png"))), "Island for Windows", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(event -> Platform.runLater(toggleCore));
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException | IOException | RuntimeException ignored) {
            icon = null;
        }
    }
    boolean notify(String title, String message) {
        TrayIcon current = icon;
        if (current == null) return false;
        java.awt.EventQueue.invokeLater(() -> current.displayMessage(title, message, TrayIcon.MessageType.INFO));
        return true;
    }
    @Override public void close() {
        if (icon != null && SystemTray.isSupported()) SystemTray.getSystemTray().remove(icon);
        icon = null;
    }
}
