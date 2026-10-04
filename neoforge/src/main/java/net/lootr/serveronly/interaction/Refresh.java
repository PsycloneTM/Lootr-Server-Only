package net.lootr.serveronly.interaction;

import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.api.LootListeners;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PositionTracker;
import net.lootr.serveronly.data.RefreshTracker;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.List;

public final class Refresh {

    public static boolean refreshIfDue(ServerLevel level, Object owner, LootrLootState state, long refreshTicks) {
        long now = level.getGameTime();
        if (!state.refreshDue(now, refreshTicks) || Decay.isViewed(level, owner)) {
            return false;
        }
        return state.refreshIfDue(now, refreshTicks);
    }

    public static void notifyRefreshed(ServerLevel level, Object owner) {
        if (owner instanceof BlockEntity blockEntity) {
            LootListeners.refreshed(level, owner, blockEntity.getBlockPos(), lootTableOf(owner));
        } else if (owner instanceof Entity entity) {
            LootListeners.refreshed(level, owner, entity.blockPosition(), lootTableOf(owner));
        }
    }

    @Nullable
    private static ResourceKey<LootTable> lootTableOf(Object owner) {
        if (owner instanceof RandomizableContainerBlockEntity container) {
            return container.getLootTable();
        }
        if (owner instanceof MinecartChest cart) {
            return cart.getLootTable();
        }
        if (owner instanceof DecoratedPotBlockEntity pot) {
            return pot.getLootTable();
        }
        if (owner instanceof BrushableBlockEntity brushable) {
            return ((net.lootr.serveronly.mixin.AccessorBrushableBlockEntity) brushable).lootr$getLootTable();
        }
        return null;
    }

    public static void track(ServerLevel level, RandomizableContainerBlockEntity container) {
        if (LootrConfig.refreshTicksFor(level, container.getBlockPos(), container.getLootTable()) > 0) {
            RefreshTracker.get(level.getServer()).add(level.dimension(), container.getBlockPos());
        }
    }

    static void sweep(MinecraftServer server) {
        if (!(LootrConfig.PERFORM_REFRESH_WHILE_TICKING.get() && LootrConfig.REFRESH_TICKS.get() > 0)) {
            return;
        }
        PositionTracker tracker = RefreshTracker.get(server);
        tracker.syncSetting(LootrConfig.REFRESH_TICKS.get());
        for (ServerLevel level : server.getAllLevels()) {
            if (!LootrConfig.isDimensionEnabled(level.dimension())) {
                continue;
            }
            sweepBlocks(level, tracker);
            sweepCarts(level);
        }
        failureLogged = false;
    }

    private static final long DEFER_TICKS = 200;

    private static void sweepBlocks(ServerLevel level, PositionTracker tracker) {
        long now = level.getGameTime();
        var dimension = level.dimension();
        for (BlockPos pos : tracker.takeDue(dimension, now)) {
            try {
                if (!level.hasChunkAt(pos)) {
                    tracker.schedule(dimension, pos, now + DEFER_TICKS);
                    continue;
                }
                if (LootrConfig.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(pos)) {
                    tracker.schedule(dimension, pos, now + DEFER_TICKS);
                    continue;
                }
                if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)
                        || ContainerInteractionHandler.kindOf(container) == null
                        || !container.hasData(ModAttachments.LOOT_STATE)) {
                    continue;
                }
                LootrLootState state = container.getData(ModAttachments.LOOT_STATE);
                int ticks = LootrConfig.refreshTicksFor(level, pos, container.getLootTable());
                if (state.getFirstGeneratedGameTime() < 0 || ticks <= 0) {
                    continue;
                }
                if (!state.refreshDue(now, ticks)) {
                    tracker.schedule(dimension, pos, state.getFirstGeneratedGameTime() + ticks);
                    continue;
                }
                if (Decay.isViewed(level, container)) {
                    tracker.schedule(dimension, pos, now + 1);
                    continue;
                }
                if (state.refreshIfDue(now, ticks)) {
                    container.setChanged();
                    notifyRefreshed(level, container);
                }
            } catch (RuntimeException e) {
                tracker.schedule(dimension, pos, now + DEFER_TICKS);
                logFailure(e);
            }
        }
    }

    private static void sweepCarts(ServerLevel level) {
        long now = level.getGameTime();
        List<? extends MinecartChest> carts = level.getEntities(EntityType.CHEST_MINECART,
                cart -> cart.hasData(ModAttachments.LOOT_STATE));
        for (MinecartChest cart : carts) {
            try {
                LootrLootState state = cart.getData(ModAttachments.LOOT_STATE);
                int ticks = LootrConfig.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable());
                if (state.refreshDue(now, ticks) && !Decay.isViewed(level, cart)) {
                    if (state.refreshIfDue(now, ticks)) {
                        notifyRefreshed(level, cart);
                    }
                }
            } catch (RuntimeException e) {
                logFailure(e);
            }
        }
    }

    private static boolean failureLogged = false;

    private static void logFailure(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            net.lootr.serveronly.LootrServerOnly.LOGGER.error("Refresh sweep failed on one container; skipping it and carrying on. "
                    + "Repeats of this are not logged until a sweep completes cleanly.", e);
        }
    }

    private Refresh() {}
}
