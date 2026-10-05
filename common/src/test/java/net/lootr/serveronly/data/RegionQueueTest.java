package net.lootr.serveronly.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionQueueTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void emptyDimensionTakesNothing() {
        RegionQueue q = new RegionQueue();
        assertTrue(q.takeDue(OVERWORLD, 1_000L).isEmpty());
    }

    @Test
    void takeDueReturnsOnlyRegionsAtOrBeforeNow() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, 100L);
        q.put(OVERWORLD, 2L, 200L);
        q.put(OVERWORLD, 3L, 300L);
        assertEquals(List.of(1L, 2L), q.takeDue(OVERWORLD, 200L));
        assertFalse(q.contains(OVERWORLD, 1L));
        assertFalse(q.contains(OVERWORLD, 2L));
        assertTrue(q.contains(OVERWORLD, 3L));
    }

    @Test
    void takeDueIsInDueTimeOrder() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 9L, 50L);
        q.put(OVERWORLD, 4L, 10L);
        q.put(OVERWORLD, 7L, 30L);
        assertEquals(List.of(4L, 7L, 9L), q.takeDue(OVERWORLD, 100L));
    }

    @Test
    void putReplacesRatherThanDuplicates() {
        RegionQueue q = new RegionQueue();
        assertNull(q.put(OVERWORLD, 1L, 500L));
        assertEquals(500L, (long) q.put(OVERWORLD, 1L, 100L));
        assertEquals(100L, (long) q.get(OVERWORLD, 1L));
        // Only one entry, and it is due at 100 (not also at 500).
        assertTrue(q.takeDue(OVERWORLD, 499L).equals(List.of(1L)));
        assertTrue(q.takeDue(OVERWORLD, 10_000L).isEmpty());
    }

    @Test
    void removeDropsTheRegion() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, 100L);
        assertTrue(q.remove(OVERWORLD, 1L));
        assertFalse(q.remove(OVERWORLD, 1L));
        assertTrue(q.takeDue(OVERWORLD, 1_000L).isEmpty());
    }

    @Test
    void dimensionsAreIsolated() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, 100L);
        q.put(NETHER, 1L, 50L);
        assertEquals(List.of(1L), q.takeDue(NETHER, 60L));
        assertTrue(q.contains(OVERWORLD, 1L));
        assertFalse(q.contains(NETHER, 1L));
    }

    @Test
    void parkedSentinelIsNeverTaken() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, Long.MAX_VALUE);
        assertTrue(q.takeDue(OVERWORLD, Long.MAX_VALUE - 1).isEmpty());
    }

    @Test
    void shiftEarlierPullsDeadlinesForwardWithoutMakingEverythingDue() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, 1_000L);
        q.put(OVERWORLD, 2L, 5_000L);
        q.put(NETHER, 3L, 9_000L);
        q.shiftEarlier(400L);
        // Regression guard for the old syncSetting: nothing may become due just because a setting changed.
        assertTrue(q.takeDue(OVERWORLD, 599L).isEmpty());
        assertEquals(Long.valueOf(600L), q.get(OVERWORLD, 1L));
        assertEquals(Long.valueOf(4_600L), q.get(OVERWORLD, 2L));
        assertEquals(Long.valueOf(8_600L), q.get(NETHER, 3L));
        assertEquals(List.of(1L), q.takeDue(OVERWORLD, 600L));
    }

    @Test
    void shiftEarlierLeavesSentinelsAndClampsAtZero() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, 0L);
        q.put(OVERWORLD, 2L, DueIndex.PARKED);
        q.put(OVERWORLD, 3L, 50L);
        q.shiftEarlier(1_000L);
        assertEquals(Long.valueOf(0L), q.get(OVERWORLD, 1L));
        assertEquals(Long.valueOf(DueIndex.PARKED), q.get(OVERWORLD, 2L));
        assertEquals(Long.valueOf(0L), q.get(OVERWORLD, 3L));
    }

    @Test
    void shiftEarlierKeepsBothViewsInSync() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, 100L);
        q.put(OVERWORLD, 2L, 100L);
        q.put(OVERWORLD, 3L, 700L);
        q.shiftEarlier(50L);                 // 1 and 2 -> 50, 3 -> 650
        q.put(OVERWORLD, 3L, 640L);          // replace after a shift: must not leave a stale 650 behind
        assertTrue(q.takeDue(OVERWORLD, 49L).isEmpty());
        assertEquals(2, q.takeDue(OVERWORLD, 50L).size());
        assertTrue(q.takeDue(OVERWORLD, 639L).isEmpty());
        assertEquals(List.of(3L), q.takeDue(OVERWORLD, 650L));
        assertTrue(q.entries(OVERWORLD).isEmpty());
    }

    @Test
    void entriesReflectCurrentState() {
        RegionQueue q = new RegionQueue();
        q.put(OVERWORLD, 1L, 100L);
        q.put(OVERWORLD, 2L, 200L);
        Map<Long, Long> expected = new HashMap<>();
        expected.put(1L, 100L);
        expected.put(2L, 200L);
        assertTrue(expected.equals(q.entries(OVERWORLD)));
        assertTrue(q.entries(NETHER).isEmpty());
    }

    /**
     * Model-based check: applies random operations to the queue and to a naive map that scans every
     * entry (the old behaviour), and requires the same results for every query.
     */
    @Test
    void matchesNaiveModelUnderRandomOperations() {
        Random rng = new Random(20241008L);
        RegionQueue q = new RegionQueue();
        Map<Long, Long> model = new HashMap<>();
        String dim = OVERWORLD;
        long now = 0L;
        for (int step = 0; step < 20_000; step++) {
            int op = rng.nextInt(10);
            long region = rng.nextInt(64);
            if (op < 4) {
                long due = rng.nextInt(4) == 0 ? Long.MAX_VALUE : now + rng.nextInt(500);
                assertTrue(Objects.equals(model.put(region, due), q.put(dim, region, due)), "put step " + step);
            } else if (op < 6) {
                assertTrue((model.remove(region) != null) == q.remove(dim, region), "remove step " + step);
            } else if (op < 9) {
                now += rng.nextInt(50);
                List<Long> expected = new ArrayList<>();
                for (Map.Entry<Long, Long> e : model.entrySet()) {
                    if (e.getValue() <= now) {
                        expected.add(e.getKey());
                    }
                }
                Collections.sort(expected);
                List<Long> actual = new ArrayList<>(q.takeDue(dim, now));
                Collections.sort(actual);
                assertTrue(expected.equals(actual), "takeDue step " + step);
                for (long r : expected) {
                    model.remove(r);
                }
            } else {
                assertTrue(Objects.equals(model.get(region), q.get(dim, region)), "get step " + step);
            }
            assertTrue(model.equals(q.entries(dim)), "state step " + step);
        }
    }
}
