package net.lootr.serveronly.interaction;

import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.schedule.DeadlineQueue;
import net.lootr.serveronly.schedule.LootSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.WeakHashMap;

/**
 * Decay and refresh scheduling for loaded chest minecarts.
 *
 * <p>Carts are only actionable while loaded, so unlike containers their deadlines are kept in
 * memory and not saved. A cart is registered when it enters the world ({@code MixinServerLevelEntityCallbacks})
 * and whenever its timer changes through a player interaction. The sweep then looks only at the
 * earliest deadline and touches nothing unless a cart is actually due. The previous sweep
 * enumerated every loaded chest minecart on every pass.
 *
 * <p>The queue is rebuilt from the loaded carts once on the first sweep, and again if the decay or
 * refresh setting changes or the configuration is reloaded. This also covers carts that entered
 * the world before the hook was active.
 */
public final class CartScheduler {
    private static final class LevelState {
        final DeadlineQueue<UUID> queue = new DeadlineQueue<>();
        int builtEpoch = -1;
        long decaySetting = Long.MIN_VALUE;
        long refreshSetting = Long.MIN_VALUE;
        boolean failureLogged;
    }

    /** Bumped by {@link #invalidateAll}; safe to call from any thread (config reloads may not run on the server thread). */
    private static final AtomicInteger EPOCH = new AtomicInteger();

    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();

    private static LevelState stateOf(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new LevelState());
    }

    /** Called by the entity-tracking hook for every entity that enters a level. */
    public static void onEntityTracked(Entity entity) {
        if (entity instanceof MinecartChest cart && cart.level() instanceof ServerLevel level) {
            register(level, cart);
        }
    }

    /** Forces a rebuild from the loaded carts on the next sweep, e.g. after a configuration reload. Thread-safe. */
    public static void invalidateAll() {
        EPOCH.incrementAndGet();
    }

    /** Schedules (or reschedules) a cart from its current timer. A cart with nothing to do is dropped. */
    public static void register(ServerLevel level, MinecartChest cart) {
        if (!LootrSettings.isDimensionEnabled(level.dimension()) || !LootStateStore.has(cart)) {
            return;
        }
        reschedule(level, stateOf(level), cart, level.getGameTime());
    }

    public static void sweep(ServerLevel level) {
        boolean decayOn = decayOn();
        boolean refreshOn = refreshOn();
        if (!decayOn && !refreshOn) {
            return;
        }
        LevelState state = stateOf(level);
        long decaySetting = LootrSettings.decayTicks();
        long refreshSetting = LootrSettings.refreshTicks();
        if (state.builtEpoch != EPOCH.get() || state.decaySetting != decaySetting || state.refreshSetting != refreshSetting) {
            rebuild(level, state);
            state.decaySetting = decaySetting;
            state.refreshSetting = refreshSetting;
        }
        long now = level.getGameTime();
        if (state.queue.peekDue() > now) {
            return;
        }
        boolean failed = false;
        for (UUID id : state.queue.pollDue(now)) {
            failed |= !process(level, state, id, now);
        }
        if (!failed) {
            state.failureLogged = false;
        }
    }

    private static void rebuild(ServerLevel level, LevelState state) {
        // Read before scanning: an invalidation that arrives mid-rebuild must still trigger another one.
        int currentEpoch = EPOCH.get();
        state.queue.clear();
        for (MinecartChest cart : level.getEntities(EntityType.CHEST_MINECART, c -> LootStateStore.has(c))) {
            reschedule(level, state, cart, level.getGameTime());
        }
        state.builtEpoch = currentEpoch;
    }

    /** Returns false if the cart failed and was deferred. */
    private static boolean process(ServerLevel level, LevelState state, UUID id, long now) {
        // Gone, or unloaded since it was queued: it is registered again when it next enters the world.
        if (!(level.getEntity(id) instanceof MinecartChest cart) || cart.isRemoved()) {
            return true;
        }
        try {
            if (!LootStateStore.has(cart)) {
                return true;
            }
            long first = LootStateStore.firstGenerated(cart);
            ResourceKey<LootTable> table = cart.getLootTable();
            BlockPos pos = cart.blockPosition();

            // Decay first: if both are due, the cart decays and refresh never runs.
            if (decayOn() && Decay.ticksLeft(level, pos, first, table, now) == 0) {
                if (!Decay.decayIfDue(level, cart, LootStateStore.getOrCreate(cart, level))) {
                    state.queue.put(id, now + LootSchedule.VIEWED_RETRY_TICKS);
                }
                return true;
            }
            if (refreshOn()) {
                int ticks = LootrSettings.refreshTicksFor(level, pos, table);
                if (LootSchedule.isRefreshDue(now, first, ticks)) {
                    if (Decay.isViewed(level, cart)) {
                        state.queue.put(id, now + LootSchedule.VIEWED_RETRY_TICKS);
                        return true;
                    }
                    LootrLootState lootState = LootStateStore.getOrCreate(cart, level);
                    if (lootState.refreshIfDue(now, ticks)) {
                        LootStateStore.save(cart, lootState, level);
                        Refresh.notifyRefreshed(level, cart);
                    }
                }
            }
            reschedule(level, state, cart, now);
            return true;
        } catch (RuntimeException e) {
            state.queue.put(id, now + LootSchedule.DEFER_TICKS);
            if (!state.failureLogged) {
                state.failureLogged = true;
                LootrServerOnlyConstants.LOGGER.error("Cart decay/refresh failed on one chest minecart; skipping it and carrying on. "
                        + "Repeats of this are not logged until a sweep completes cleanly.", e);
            }
            return false;
        }
    }

    private static void reschedule(ServerLevel level, LevelState state, MinecartChest cart, long now) {
        long due = nextDue(level, cart);
        UUID id = cart.getUUID();
        if (due == LootSchedule.NO_DEADLINE) {
            state.queue.remove(id);
            return;
        }
        // Never schedule at or before now: that would re-fire on the very next sweep.
        state.queue.put(id, Math.max(due, now + LootSchedule.VIEWED_RETRY_TICKS));
    }

    /** The earlier of the cart's decay and refresh deadlines, or NO_DEADLINE if neither applies. */
    private static long nextDue(ServerLevel level, MinecartChest cart) {
        long first = LootStateStore.firstGenerated(cart);
        if (first < 0) {
            return LootSchedule.NO_DEADLINE;
        }
        ResourceKey<LootTable> table = cart.getLootTable();
        BlockPos pos = cart.blockPosition();
        long due = LootSchedule.NO_DEADLINE;
        if (decayOn() && Decay.covers(level, pos, table)) {
            due = Math.min(due, LootSchedule.decayDueAt(first, LootrSettings.decayTicks()));
        }
        if (refreshOn()) {
            due = Math.min(due, LootSchedule.refreshDueAt(first, LootrSettings.refreshTicksFor(level, pos, table)));
        }
        return due;
    }

    private static boolean decayOn() {
        return LootrSettings.decayTicks() > 0 && LootrSettings.performDecayWhileTicking();
    }

    private static boolean refreshOn() {
        return LootrSettings.refreshTicks() > 0 && LootrSettings.performRefreshWhileTicking();
    }

    private CartScheduler() {}
}
