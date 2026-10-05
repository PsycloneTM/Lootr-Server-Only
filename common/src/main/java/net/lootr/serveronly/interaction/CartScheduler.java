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

public final class CartScheduler {
    private static final class LevelState {
        final DeadlineQueue<UUID> queue = new DeadlineQueue<>();
        int builtEpoch = -1;
        long decaySetting = Long.MIN_VALUE;
        long refreshSetting = Long.MIN_VALUE;
        boolean failureLogged;
    }

    private static final AtomicInteger EPOCH = new AtomicInteger();

    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();

    private static LevelState stateOf(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new LevelState());
    }

    public static void onEntityTracked(Entity entity) {
        if (entity instanceof MinecartChest cart && cart.level() instanceof ServerLevel level) {
            register(level, cart);
        }
    }

    public static void invalidateAll() {
        EPOCH.incrementAndGet();
    }

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
        int currentEpoch = EPOCH.get();
        state.queue.clear();
        for (MinecartChest cart : level.getEntities(EntityType.CHEST_MINECART, c -> LootStateStore.has(c))) {
            reschedule(level, state, cart, level.getGameTime());
        }
        state.builtEpoch = currentEpoch;
    }

    private static boolean process(ServerLevel level, LevelState state, UUID id, long now) {
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

        state.queue.put(id, Math.max(due, now + LootSchedule.VIEWED_RETRY_TICKS));
    }

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
