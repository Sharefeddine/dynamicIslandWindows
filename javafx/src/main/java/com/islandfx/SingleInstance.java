package com.islandfx;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A per-user file lock, released by the OS even if the process exits unexpectedly. */
final class SingleInstance implements AutoCloseable {
    private FileChannel channel;
    private FileLock lock;
    boolean acquire() throws IOException {
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "island-javafx.lock");
        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try { lock = channel.tryLock(); }
        catch (OverlappingFileLockException inThisProcess) { lock = null; }
        if (lock == null) { channel.close(); channel = null; return false; }
        return true;
    }
    @Override public void close() {
        try { if (lock != null) lock.release(); } catch (IOException ignored) { }
        try { if (channel != null) channel.close(); } catch (IOException ignored) { }
        lock = null;
        channel = null;
    }
}
