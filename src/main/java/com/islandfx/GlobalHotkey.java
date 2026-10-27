package com.islandfx;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.MSG;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional Ctrl+Alt+J hotkey, registered on a dedicated Windows message-loop thread. */
final class GlobalHotkey implements AutoCloseable {
    private static final int HOTKEY_ID = 1;
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile int threadId;

    void start(Runnable onHotkey) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return;
        Thread thread = new Thread(() -> {
            try {
                threadId = Kernel32.INSTANCE.GetCurrentThreadId();
                if (!User32.INSTANCE.RegisterHotKey(null, HOTKEY_ID, 0x0002 | 0x0001 | 0x4000, 'J')) return;
                try {
                    MSG message = new MSG();
                    while (!closed.get() && User32.INSTANCE.GetMessage(message, null, 0, 0) > 0) {
                        if (message.message == WM_HOTKEY && message.wParam.intValue() == HOTKEY_ID) onHotkey.run();
                    }
                } finally { User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID); }
            } catch (RuntimeException | LinkageError ignored) {
                // The shortcut is optional; tray and mouse controls still work.
            }
        }, "island-hotkey");
        thread.setDaemon(true);
        thread.start();
    }
    @Override public void close() {
        closed.set(true);
        int id = threadId;
        if (id != 0) {
            try { User32.INSTANCE.PostThreadMessage(id, WM_QUIT, new WPARAM(0), new LPARAM(0)); }
            catch (RuntimeException | LinkageError ignored) { }
        }
    }
}
