package net.lootr.serveronly.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DueIndexTest {
    private static DueIndex index() {
        return new DueIndex(pos -> pos >> 4);
    }

    @Test
    void takeDueReturnsOnlyDueEntriesAndRemovesThem() {
        DueIndex idx = index();
        idx.put(1, 10);
        idx.put(2, 20);
        idx.put(3, 30);
        List<Long> taken = idx.takeDue(20);
        assertEquals(2, taken.size());
        assertTrue(taken.contains(1L) && taken.contains(2L));
        assertEquals(1, idx.size());
        assertEquals(30L, idx.minDue());
    }

    @Test
    void putReplacesTheDeadlineRatherThanDuplicating() {
        DueIndex idx = index();
        idx.put(1, 10);
        idx.put(1, 50);
        assertEquals(1, idx.size());
        assertTrue(idx.takeDue(10).isEmpty());
        assertEquals(List.of(1L), idx.takeDue(50));
    }

    @Test
    void parkedEntriesAreNotTakenUntilTheirChunkWakes() {
        DueIndex idx = index();
        idx.put(5, DueIndex.PARKED);
        idx.put(100, DueIndex.PARKED);
        assertTrue(idx.takeDue(Long.MAX_VALUE - 1).isEmpty());
        assertEquals(1, idx.wakeChunk(0L, 777));
        assertEquals(List.of(5L), idx.takeDue(777));
        assertTrue(idx.contains(100));
    }

    @Test
    void shiftEarlierMovesDeadlinesAndKeepsSentinels() {
        DueIndex idx = index();
        idx.put(1, 1_000);
        idx.put(2, 0);
        idx.put(3, DueIndex.PARKED);
        idx.shiftEarlier(300);
        assertEquals(0L, idx.minDue());
        assertEquals(List.of(2L), idx.takeDue(0));
        assertTrue(idx.takeDue(699).isEmpty());
        assertEquals(List.of(1L), idx.takeDue(700));
        assertTrue(idx.contains(3));
    }

    @Test
    void shiftEarlierWithNonPositiveDeltaDoesNothing() {
        DueIndex idx = index();
        idx.put(1, 100);
        idx.shiftEarlier(0);
        idx.shiftEarlier(-50);
        assertEquals(100L, idx.minDue());
    }

    @Test
    void removeClearsAllViews() {
        DueIndex idx = index();
        idx.put(1, 10);
        assertTrue(idx.remove(1));
        assertFalse(idx.remove(1));
        assertTrue(idx.isEmpty());
        assertEquals(Long.MAX_VALUE, idx.minDue());
    }

    @Test
    void shiftingInTwoStepsEqualsOneShiftByTheTotal() {
        for (long due : new long[] {0L, 50L, 300L, 301L, 10_000L}) {
            DueIndex stepwise = index();
            DueIndex once = index();
            stepwise.put(1, due);
            once.put(1, due);
            stepwise.shiftEarlier(120);
            stepwise.shiftEarlier(180);
            once.shiftEarlier(300);
            assertEquals(once.minDue(), stepwise.minDue());
        }
    }
}
