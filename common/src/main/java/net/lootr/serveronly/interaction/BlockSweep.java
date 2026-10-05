package net.lootr.serveronly.interaction;

import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.data.PositionTracker;
import net.lootr.serveronly.schedule.LootSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.function.Consumer;

final class BlockSweep {
    static final long DONE = Long.MIN_VALUE;

    interface Handler {
        long handle(ServerLevel level, BlockPos pos, RandomizableContainerBlockEntity container, long now);
    }

    static void run(ServerLevel level, PositionTracker tracker, Handler handler, Consumer<RuntimeException> onFailure) {
        long now = level.getGameTime();
        var dimension = level.dimension();
        for (BlockPos pos : tracker.takeDue(dimension, now)) {
            try {
                if (!level.hasChunkAt(pos)) {
                    tracker.park(dimension, pos);
                    continue;
                }
                if (LootrSettings.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(pos)) {
                    tracker.schedule(dimension, pos, now + LootSchedule.DEFER_TICKS);
                    continue;
                }
                if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)
                        || ContainerInteractionHandler.kindOf(container) == null
                        || !LootStateStore.has(container)) {
                    continue;
                }
                long next = handler.handle(level, pos, container, now);
                if (next != DONE) {
                    tracker.schedule(dimension, pos, next);
                }
            } catch (RuntimeException e) {
                tracker.schedule(dimension, pos, now + LootSchedule.DEFER_TICKS);
                onFailure.accept(e);
            }
        }
    }

    private BlockSweep() {}
}
