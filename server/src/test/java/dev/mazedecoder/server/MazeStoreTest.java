package dev.mazedecoder.server;

import dev.mazedecoder.core.Grid;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class MazeStoreTest {
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @Test void oldestCreationEvictedAtCountBoundEvenIfRecentlyRead() {
        MutableClock clock = new MutableClock();
        MazeStore store = new MazeStore(2, Duration.ofMinutes(30), clock);
        Grid grid = Grid.builder(1, 1).build();
        String first = store.put(grid);
        String second = store.put(grid);
        assertSame(grid, store.get(first));
        String third = store.put(grid);
        assertEquals(2, store.size());
        assertThrows(MazeStore.MissingMazeException.class, () -> store.get(first));
        assertSame(grid, store.get(second));
        assertSame(grid, store.get(third));
    }
    @Test void expiryAtExactBoundaryAndReadsDoNotExtendLifetime() {
        MutableClock clock = new MutableClock();
        MazeStore store = new MazeStore(2, Duration.ofMinutes(30), clock);
        String id = store.put(Grid.builder(1, 1).build());
        clock.now = clock.now.plusSeconds(1799);
        assertNotNull(store.get(id));
        clock.now = clock.now.plusSeconds(1);
        assertThrows(MazeStore.MissingMazeException.class, () -> store.get(id));
        assertEquals(0, store.size());
    }
    @Test void scheduledCleanupRemovesIdleExpiredMazes() {
        MutableClock clock = new MutableClock();
        MazeStore store = new MazeStore(2, Duration.ofSeconds(1), clock);
        store.put(Grid.builder(1, 1).build());
        clock.now = clock.now.plusSeconds(2);
        store.evictExpired();
        assertEquals(0, store.size());
    }
    @Test void rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new MazeStore(0, Duration.ofMinutes(1), Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new MazeStore(1, Duration.ZERO, Clock.systemUTC()));
    }
}
