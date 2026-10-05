package net.lootr.serveronly.interaction;

import net.lootr.serveronly.schedule.LootSchedule;
import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.config.LootrSettings;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

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
        if (LootrSettings.refreshTicksFor(level, container.getBlockPos(), container.getLootTable()) > 0) {
            RefreshTracker.get(level.getServer()).add(level.dimension(), container.getBlockPos());
        }
    }

    static boolean backgroundEnabled() {
        return LootrSettings.performRefreshWhileTicking() && LootrSettings.refreshTicks() > 0;
    }

    /** Decides what to do with a due container: refresh it, wait for viewers, or look again later. */
    static long decideBlock(ServerLevel level, BlockPos pos, RandomizableContainerBlockEntity container, long now) {
        long first = LootStateStore.firstGenerated(container);
        int ticks = LootrSettings.refreshTicksFor(level, pos, container.getLootTable());
        LootSchedule.Step step = LootSchedule.refreshStep(now, first, ticks);
        if (step.kind() == LootSchedule.Kind.DROP) {
            return BlockSweep.DONE;
        }
        if (step.kind() == LootSchedule.Kind.RESCHEDULE) {
            return step.at();
        }
        if (Decay.isViewed(level, container)) {
            return now + LootSchedule.VIEWED_RETRY_TICKS;
        }
        LootrLootState state = LootStateStore.getOrCreate(container, level);
        if (state.refreshIfDue(now, ticks)) {
            LootStateStore.save(container, state, level);
            notifyRefreshed(level, container);
        }
        return BlockSweep.DONE;
    }

    private Refresh() {}
}
