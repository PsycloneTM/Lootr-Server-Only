package net.lootr.serveronly.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootScheduleStepTest {
    @Test
    void decayWithNoTimerIsStale() {
        assertEquals(LootSchedule.Kind.DROP, LootSchedule.decayStep(-1, 100).kind());
    }

    @Test
    void decayAtZeroActs() {
        assertEquals(LootSchedule.Kind.ACT, LootSchedule.decayStep(0, 100).kind());
    }

    @Test
    void decayWithTimeLeftReschedulesFromNow() {
        LootSchedule.Step step = LootSchedule.decayStep(50, 100);
        assertEquals(LootSchedule.Kind.RESCHEDULE, step.kind());
        assertEquals(150L, step.at());
    }

    @Test
    void refreshWithoutTimerOrDisabledIsStale() {
        assertEquals(LootSchedule.Kind.DROP, LootSchedule.refreshStep(100, -1, 50).kind());
        assertEquals(LootSchedule.Kind.DROP, LootSchedule.refreshStep(100, 10, 0).kind());
        assertEquals(LootSchedule.Kind.DROP, LootSchedule.refreshStep(100, 10, -5).kind());
    }

    @Test
    void refreshNotYetDueReschedulesToItsDeadline() {
        LootSchedule.Step step = LootSchedule.refreshStep(100, 80, 50);
        assertEquals(LootSchedule.Kind.RESCHEDULE, step.kind());
        assertEquals(130L, step.at());
    }

    @Test
    void refreshExactlyAtDeadlineActs() {
        assertEquals(LootSchedule.Kind.ACT, LootSchedule.refreshStep(130, 80, 50).kind());
        assertEquals(LootSchedule.Kind.ACT, LootSchedule.refreshStep(500, 80, 50).kind());
    }

    @Test
    void stepsAgreeWithTheDeadlineArithmetic() {
        for (long first = 0; first < 40; first += 7) {
            for (long ticks = 1; ticks < 40; ticks += 9) {
                for (long now = 0; now < 120; now += 5) {
                    boolean due = LootSchedule.isRefreshDue(now, first, ticks);
                    assertEquals(due, LootSchedule.refreshStep(now, first, ticks).kind() == LootSchedule.Kind.ACT);
                    long left = LootSchedule.decayTicksLeft(first, ticks, now);
                    assertEquals(left == 0, LootSchedule.decayStep(left, now).kind() == LootSchedule.Kind.ACT);
                }
            }
        }
    }
}
