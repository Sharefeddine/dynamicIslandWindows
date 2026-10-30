package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WindowsNotificationsTest {
    @Test void textIsDataNotPowerShellOrXmlCode() {
        String attack = "'); Remove-Item -Recurse $env:USERPROFILE; <xml>& مرحبا";
        ProcessBuilder builder = WindowsNotifications.command("Task reminder", attack);
        assertEquals(attack, builder.environment().get("ISLAND_NOTICE_BODY"));
        assertTrue(builder.environment().get("ISLAND_NOTICE_TITLE").contains("Task reminder"));
        var args = builder.command();
        String script = new String(Base64.getDecoder().decode(args.get(args.indexOf("-EncodedCommand") + 1)), StandardCharsets.UTF_16LE);
        assertFalse(script.contains(attack));
        assertFalse(script.contains("Remove-Item"));
        assertTrue(script.contains("CreateTextNode($env:ISLAND_NOTICE_BODY)"));
        assertTrue(script.contains("ToastNotificationManager"));
    }
    @Test void failedToastUsesFallbackButSuccessfulToastDoesNot() throws Exception {
        CountDownLatch fallback = new CountDownLatch(1);
        try (WindowsNotifications notifications = new WindowsNotifications((title, body) -> false)) {
            notifications.send("Task", "Test", fallback::countDown);
            assertTrue(fallback.await(2, TimeUnit.SECONDS));
        }
        CountDownLatch sent = new CountDownLatch(1), unexpectedFallback = new CountDownLatch(1);
        try (WindowsNotifications notifications = new WindowsNotifications((title, body) -> { sent.countDown(); return true; })) {
            notifications.send("Task", "Test", unexpectedFallback::countDown);
            assertTrue(sent.await(2, TimeUnit.SECONDS));
            assertFalse(unexpectedFallback.await(100, TimeUnit.MILLISECONDS));
        }
    }
}
