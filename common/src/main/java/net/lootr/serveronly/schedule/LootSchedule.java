package net.lootr.serveronly.schedule;

public final class LootSchedule {
    public static final long NO_DEADLINE = Long.MAX_VALUE;

    public static final long VIEWED_RETRY_TICKS = 1L;

    public static final long DEFER_TICKS = 200L;

    public static final long DUE_NOW = 0L;

    public static final long PARKED = Long.MAX_VALUE;

    private LootSchedule() {}

    public static long shiftEarlier(long due, long delta) {
        if (delta <= 0 || due == DUE_NOW || due == PARKED) {
            return due;
        }
        return Math.max(DUE_NOW, due - delta);
    }

    public static boolean isRefreshDue(long now, long firstGenerated, long refreshTicks) {
        return refreshTicks > 0 && firstGenerated >= 0 && now - firstGenerated >= refreshTicks;
    }

    public static long refreshDueAt(long firstGenerated, long refreshTicks) {
        if (refreshTicks <= 0 || firstGenerated < 0) {
            return NO_DEADLINE;
        }
        return firstGenerated + refreshTicks;
    }

    public static long decayTicksLeft(long firstGenerated, long decayTicks, long now) {
        if (firstGenerated < 0) {
            return -1;
        }
        return Math.max(0, firstGenerated + decayTicks - now);
    }

    public static long decayDueAt(long firstGenerated, long decayTicks) {
        if (firstGenerated < 0) {
            return NO_DEADLINE;
        }
        return firstGenerated + decayTicks;
    }

    public enum Kind {
        DROP,

        RESCHEDULE,

        ACT
    }

    public record Step(Kind kind, long at) {
        public static final Step DROP = new Step(Kind.DROP, 0L);
        public static final Step ACT = new Step(Kind.ACT, 0L);

        public static Step reschedule(long at) {
            return new Step(Kind.RESCHEDULE, at);
        }
    }

    public static Step decayStep(long ticksLeft, long now) {
        if (ticksLeft < 0) {
            return Step.DROP;
        }
        return ticksLeft == 0 ? Step.ACT : Step.reschedule(now + ticksLeft);
    }

    public static Step refreshStep(long now, long firstGenerated, long refreshTicks) {
        if (firstGenerated < 0 || refreshTicks <= 0) {
            return Step.DROP;
        }
        if (!isRefreshDue(now, firstGenerated, refreshTicks)) {
            return Step.reschedule(refreshDueAt(firstGenerated, refreshTicks));
        }
        return Step.ACT;
    }
}
