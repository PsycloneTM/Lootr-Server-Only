package net.lootr.serveronly.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootScheduleTest {

    // --- refresh ---

    @Test
    void refreshNotDueWithoutTimer() {
        assertFalse(LootSchedule.isRefreshDue(1_000_000L, -1L, 100L));
    }

    @Test
    void refreshNotDueWhenTicksDisabled() {
        assertFalse(LootSchedule.isRefreshDue(1_000_000L, 0L, 0L));
        assertFalse(LootSchedule.isRefreshDue(1_000_000L, 0L, -5L));
    }

    @Test
    void refreshDoesNotFireEarly() {
        assertFalse(LootSchedule.isRefreshDue(99L, 0L, 100L));
    }

    @Test
    void refreshFiresExactlyAtDeadline() {
        assertTrue(LootSchedule.isRefreshDue(100L, 0L, 100L));
    }

    @Test
    void refreshDueAtMatchesIsRefreshDue() {
        long first = 500L;
        long ticks = 200L;
        long due = LootSchedule.refreshDueAt(first, ticks);
        assertEquals(700L, due);
        assertFalse(LootSchedule.isRefreshDue(due - 1, first, ticks));
        assertTrue(LootSchedule.isRefreshDue(due, first, ticks));
    }

    @Test
    void refreshDueAtReportsNoDeadlineWhenInactive() {
        assertEquals(LootSchedule.NO_DEADLINE, LootSchedule.refreshDueAt(-1L, 100L));
        assertEquals(LootSchedule.NO_DEADLINE, LootSchedule.refreshDueAt(0L, 0L));
    }

    // --- decay ---

    @Test
    void decayTicksLeftReportsInactiveTimer() {
        assertEquals(-1L, LootSchedule.decayTicksLeft(-1L, 100L, 50L));
    }

    @Test
    void decayTicksLeftCountsDown() {
        assertEquals(40L, LootSchedule.decayTicksLeft(100L, 100L, 160L));
        assertEquals(100L, LootSchedule.decayTicksLeft(100L, 100L, 100L));
    }

    @Test
    void decayTicksLeftClampsAtZeroPastDeadline() {
        assertEquals(0L, LootSchedule.decayTicksLeft(100L, 100L, 300L));
    }

    @Test
    void decayDueAtReportsNoDeadlineWhenInactive() {
        assertEquals(LootSchedule.NO_DEADLINE, LootSchedule.decayDueAt(-1L, 100L));
        assertEquals(300L, LootSchedule.decayDueAt(200L, 100L));
    }

    // --- equivalence with the pre-refactor arithmetic ---

    /**
     * The old code computed refresh as {@code now - first >= ticks} and decay as
     * {@code max(0, first + decay - now)}. The new helpers must agree with both over a range of inputs.
     */
    @Test
    void matchesLegacyArithmetic() {
        long[] firsts = {0L, 1L, 100L, 12_345L};
        long[] ticks = {1L, 20L, 600L, 24_000L};
        for (long first : firsts) {
            for (long t : ticks) {
                for (long now = first - 5; now <= first + t + 5; now++) {
                    boolean legacyRefresh = now - first >= t;
                    assertEquals(legacyRefresh, LootSchedule.isRefreshDue(now, first, t),
                            "refresh first=" + first + " ticks=" + t + " now=" + now);
                    long legacyLeft = Math.max(0, first + t - now);
                    assertEquals(legacyLeft, LootSchedule.decayTicksLeft(first, t, now),
                            "decay first=" + first + " decay=" + t + " now=" + now);
                }
            }
        }
    }

    @Test
    void shiftEarlierMovesOrdinaryDeadlines() {
        assertEquals(700L, LootSchedule.shiftEarlier(1_000L, 300L));
    }

    @Test
    void shiftEarlierClampsAtZeroAndKeepsSentinels() {
        assertEquals(0L, LootSchedule.shiftEarlier(100L, 5_000L));
        assertEquals(LootSchedule.DUE_NOW, LootSchedule.shiftEarlier(LootSchedule.DUE_NOW, 300L));
        assertEquals(LootSchedule.PARKED, LootSchedule.shiftEarlier(LootSchedule.PARKED, 300L));
    }

    @Test
    void shiftEarlierIgnoresNonPositiveDelta() {
        assertEquals(1_000L, LootSchedule.shiftEarlier(1_000L, 0L));
        assertEquals(1_000L, LootSchedule.shiftEarlier(1_000L, -10L));
    }

    @Test
    void retryConstantsAreTheDocumentedValues() {
        assertEquals(1L, LootSchedule.VIEWED_RETRY_TICKS);
        assertEquals(200L, LootSchedule.DEFER_TICKS);
    }
}
