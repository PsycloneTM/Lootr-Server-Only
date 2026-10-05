package net.lootr.serveronly.interaction;

import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.data.PositionTracker;
import net.lootr.serveronly.schedule.LootSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.function.Consumer;

/**
 * The part of a block sweep that decay and refresh share: take what is due, skip what cannot be
 * acted on yet (unloaded chunk, outside the world border, no longer a Lootr container), and
 * contain failures. What to do with a container that is really there is left to a {@link Handler}.
 */
final class BlockSweep {
    /** Returned by a handler that has finished with the position and wants it dropped from the tracker. */
    static final long DONE = Long.MIN_VALUE;

    interface Handler {
        /** Returns {@link #DONE}, or the game time at which this position should be looked at again. */
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
