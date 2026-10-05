package net.lootr.serveronly.schedule;

/**
 * The single definition of how Lootr refresh and decay deadlines are calculated.
 *
 * <p>The timer origin is {@code LootrLootState.firstGeneratedGameTime}. A value below zero means
 * no timer is running. Every other class that needs a deadline or a "due" check must call into
 * this class rather than doing its own arithmetic.
 *
 * <p>This class has no Minecraft dependencies so it can be unit-tested directly.
 */
public final class LootSchedule {
    /** Returned by the deadline methods when no timer is active. */
    public static final long NO_DEADLINE = Long.MAX_VALUE;

    /** Retry delay when a due container or cart is currently open in someone's menu. */
    public static final long VIEWED_RETRY_TICKS = 1L;

    /** Retry delay after a failure, or while the target is outside the world border. */
    public static final long DEFER_TICKS = 200L;

    /** Stored due time meaning "evaluate at the next sweep". Never shifted. */
    public static final long DUE_NOW = 0L;

    /** Stored due time meaning "waiting for its chunk to load". Never shifted. */
    public static final long PARKED = Long.MAX_VALUE;

    private LootSchedule() {}

    /**
     * Moves a stored due time {@code delta} ticks earlier, for when a duration setting gets shorter.
     * "Due now" and "parked" are sentinels and are returned unchanged; everything else is clamped at
     * zero. Stored deadlines are only ever pulled earlier, never later. An entry that fires early is
     * re-evaluated and rescheduled by the sweep, but one that fires late would be missed.
     */
    public static long shiftEarlier(long due, long delta) {
        if (delta <= 0 || due == DUE_NOW || due == PARKED) {
            return due;
        }
        return Math.max(DUE_NOW, due - delta);
    }

    /** True when a refresh timer is active and {@code now} has reached its deadline. */
    public static boolean isRefreshDue(long now, long firstGenerated, long refreshTicks) {
        return refreshTicks > 0 && firstGenerated >= 0 && now - firstGenerated >= refreshTicks;
    }

    /** Game time at which the refresh timer expires, or {@link #NO_DEADLINE} if none is active. */
    public static long refreshDueAt(long firstGenerated, long refreshTicks) {
        if (refreshTicks <= 0 || firstGenerated < 0) {
            return NO_DEADLINE;
        }
        return firstGenerated + refreshTicks;
    }

    /**
     * Ticks remaining until decay, clamped at zero. Returns -1 when no timer is active.
     * The caller is responsible for checking that decay applies to this container.
     */
    public static long decayTicksLeft(long firstGenerated, long decayTicks, long now) {
        if (firstGenerated < 0) {
            return -1;
        }
        return Math.max(0, firstGenerated + decayTicks - now);
    }

    /** Game time at which decay becomes due, or {@link #NO_DEADLINE} if no timer is active. */
    public static long decayDueAt(long firstGenerated, long decayTicks) {
        if (firstGenerated < 0) {
            return NO_DEADLINE;
        }
        return firstGenerated + decayTicks;
    }

    /** What a background sweep should do with an entry that came due. */
    public enum Kind {
        /** Stale: nothing to do and no reason to keep tracking it. */
        DROP,
        /** Not actually due yet (or its deadline moved): look again at {@link Step#at()}. */
        RESCHEDULE,
        /** Due now: perform the action (after the caller's own viewer check). */
        ACT
    }

    public record Step(Kind kind, long at) {
        public static final Step DROP = new Step(Kind.DROP, 0L);
        public static final Step ACT = new Step(Kind.ACT, 0L);

        public static Step reschedule(long at) {
            return new Step(Kind.RESCHEDULE, at);
        }
    }

    /**
     * Decay rule for a due entry. {@code ticksLeft} is {@link #decayTicksLeft} after the caller has
     * checked that decay still covers the target: below zero means no timer is running (stale),
     * zero means due, above zero means the deadline moved later (e.g. the setting was lengthened).
     */
    public static Step decayStep(long ticksLeft, long now) {
        if (ticksLeft < 0) {
            return Step.DROP;
        }
        return ticksLeft == 0 ? Step.ACT : Step.reschedule(now + ticksLeft);
    }

    /**
     * Refresh rule for a due entry. No timer or refresh disabled for it means stale; a deadline still in
     * the future (setting lengthened, or the timer restarted) is rescheduled to that deadline.
     */
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
