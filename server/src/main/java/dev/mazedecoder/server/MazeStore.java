package dev.mazedecoder.server;

import dev.mazedecoder.core.Grid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Synchronized insertion order makes the count bound and FIFO eviction atomic. */
@Component
public class MazeStore {
    private record Entry(Grid grid, Instant expiresAt) { }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final int maxCount;
    private final Duration ttl;
    private final Clock clock;

    public MazeStore(@Value("${maze.store.max-count:100}") int maxCount,
                     @Value("${maze.store.ttl:PT30M}") Duration ttl, Clock clock) {
        if (maxCount < 1 || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Invalid store limits");
        this.maxCount = maxCount;
        this.ttl = ttl;
        this.clock = clock;
    }
    public synchronized String put(Grid grid) {
        evictExpired();
        while (entries.size() >= maxCount) entries.remove(entries.keySet().iterator().next());
        String id = UUID.randomUUID().toString();
        entries.put(id, new Entry(grid, clock.instant().plus(ttl)));
        return id;
    }
    public synchronized Grid get(String id) {
        evictExpired();
        Entry entry = entries.get(id);
        if (entry == null) throw new MissingMazeException();
        return entry.grid();
    }
    @Scheduled(fixedDelayString = "${maze.store.cleanup-ms:60000}")
    public synchronized void evictExpired() {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
    }
    synchronized int size() { return entries.size(); }

    public static final class MissingMazeException extends RuntimeException {
        MissingMazeException() { super("Maze not found or expired; generate a new maze"); }
    }
}
