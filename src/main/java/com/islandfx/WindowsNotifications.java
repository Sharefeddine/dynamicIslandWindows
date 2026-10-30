package com.islandfx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Best-effort Windows toast. Uses PowerShell's registered identity; falls back to the tray on failure. */
final class WindowsNotifications implements AutoCloseable {
    @FunctionalInterface interface Sender { boolean send(String title, String message) throws Exception; }
    // Only fixed code goes into the command. User text travels as environment data, then through the XML DOM.
    static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            [void][Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime]
            [void][Windows.UI.Notifications.ToastTemplateType, Windows.UI.Notifications, ContentType=WindowsRuntime]
            [void][Windows.UI.Notifications.ToastNotification, Windows.UI.Notifications, ContentType=WindowsRuntime]
            $xml = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
            $nodes = $xml.GetElementsByTagName('text')
            [void]$nodes.Item(0).AppendChild($xml.CreateTextNode($env:ISLAND_NOTICE_TITLE))
            [void]$nodes.Item(1).AppendChild($xml.CreateTextNode($env:ISLAND_NOTICE_BODY))
            $toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
            $id = '{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\WindowsPowerShell\\v1.0\\powershell.exe'
            $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($id)
            if ($notifier.Setting.ToString() -ne 'Enabled') { exit 2 }
            $notifier.Show($toast)
            """;
    private final Sender sender;
    private final ExecutorService worker;
    private final AtomicBoolean closed = new AtomicBoolean();

    WindowsNotifications() { this(WindowsNotifications::sendWithPowerShell); }
    WindowsNotifications(Sender sender) {
        this.sender = sender;
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "island-windows-notifications"); thread.setDaemon(true); return thread;
        });
    }
    static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"); }
    void send(String title, String message, Runnable fallback) {
        if (closed.get()) return;
        try {
            worker.execute(() -> {
                boolean ok;
                try { ok = sender.send(title, message); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                catch (Exception failure) { ok = false; }
                if (!ok && !closed.get()) fallback.run();
            });
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            if (!closed.get()) fallback.run();
        }
    }
    static ProcessBuilder command(String title, String message) {
        String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
        builder.environment().put("ISLAND_NOTICE_TITLE", "Island · " + title);
        builder.environment().put("ISLAND_NOTICE_BODY", message);
        return builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
    }
    static boolean sendWithPowerShell(String title, String message) throws IOException, InterruptedException {
        Process process = command(title, message).start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) return false;
            return process.exitValue() == 0;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
    @Override public void close() { closed.set(true); worker.shutdownNow(); }
}
