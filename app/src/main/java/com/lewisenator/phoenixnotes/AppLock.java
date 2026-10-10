package com.lewisenator.phoenixnotes;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes sure only one copy of the app uses a data folder: an OS file lock, held while the app runs.
 * The OS releases it if the app dies, so a crash never leaves it stuck.
 */
final class AppLock {

    /**
     * Every held lock's channel. Garbage collection closes unreachable channels, which releases their
     * locks, so held ones are kept here until released, even if nothing else references them.
     */
    private static final Set<FileChannel> HELD = ConcurrentHashMap.newKeySet();

    private final Path file;

    /** Open, and so locked, while held. */
    private Optional<FileChannel> channel = Optional.empty();

    AppLock(Path file) {
        this.file = file;
    }

    /**
     * Takes the lock, waiting up to {@code wait} for whoever holds it to let go. Returns false if they
     * didn't. Taking a lock we already hold does nothing.
     */
    boolean take(Duration wait) throws IOException, InterruptedException {
        if (channel.isPresent()) {
            return true;
        }
        var opened = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var giveUpAt = Instant.now().plus(wait);
        while (!tryLock(opened)) {
            if (Instant.now().isAfter(giveUpAt)) {
                opened.close();
                return false;
            }
            Thread.sleep(Duration.ofMillis(50));
        }
        HELD.add(opened);
        channel = Optional.of(opened);
        return true;
    }

    /** Lets go, so a new version can take over. */
    void release() throws IOException {
        if (channel.isPresent()) {
            HELD.remove(channel.get());
            channel.get().close();
            channel = Optional.empty();
        }
    }

    /** True if we got the lock; false if another process (or another lock in this one) holds it. */
    private static boolean tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock() != null;
        } catch (OverlappingFileLockException e) {
            return false;
        }
    }
}
