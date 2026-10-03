package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.LootrServerOnlyFabric;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.data.DecayTracker;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.data.RefreshTracker;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;

/**
 * {@code start_decay_while_ticking} / {@code start_refresh_while_ticking}: when a chunk loads, containers in it
 * that were already looted (they have a first-looted time) but are not yet in the decay / refresh tracker are
 * added, so they are handled without anyone opening them again. Called from {@code MixinLevelChunk} (Fabric
 * API's chunk-load event lives in {@code fabric-lifecycle-events-v1}, which this project avoids). It only
 * looks at the one chunk that just loaded and does nothing unless a start toggle is on and its feature is
 * enabled. Not verified by a build.
 */
public final class ChunkDiscovery {

    public static void onChunkLoaded(ServerLevel level, LevelChunk chunk) {
        boolean decay = LootrConfig.decayTicks() > 0 && LootrConfig.startDecayWhileTicking();
        boolean refresh = LootrConfig.refreshTicks() > 0 && LootrConfig.startRefreshWhileTicking();
        if ((!decay && !refresh) || !LootrConfig.isDimensionEnabled(level.dimension())) {
            return;
        }
        scan(level, chunk, decay, refresh);
    }

    /** Outcome of one scan: containers found that were already looted, and how many were newly tracked. */
    public record Scan(int looted, int decayAdded, int refreshAdded) {
        public static final Scan NONE = new Scan(0, 0, 0);

        public Scan plus(Scan other) {
            return new Scan(looted + other.looted, decayAdded + other.decayAdded, refreshAdded + other.refreshAdded);
        }
    }

    /** Decay is on at all ({@code decay_value} above 0). The {@code force_*} commands ignore the start toggle. */
    public static boolean decayEnabled() {
        return LootrConfig.decayTicks() > 0;
    }

    /** Refresh is on at all ({@code refresh_value} above 0). The {@code force_*} commands ignore the start toggle. */
    public static boolean refreshEnabled() {
        return LootrConfig.refreshTicks() > 0;
    }

    /**
     * Adds the already-looted containers of one chunk to the decay and/or refresh tracker. Shared by the
     * chunk-load hook above (which only calls it when a start toggle is on) and the {@code /lootr force_*}
     * commands (which call it regardless of the toggles). Only looks at the chunk it is given; never loads one.
     * Trackers are sets, so scanning the same chunk twice is harmless; only positions that were not already
     * tracked are counted as added.
     */
    public static Scan scan(ServerLevel level, LevelChunk chunk, boolean decay, boolean refresh) {
        if (LootrConfig.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(chunk.getPos())) {
            return Scan.NONE; // check_world_border
        }
        int looted = 0;
        int decayAdded = 0;
        int refreshAdded = 0;
        try {
            MinecraftServer server = level.getServer();
            for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                if (!(be instanceof RandomizableContainerBlockEntity container)
                        || ContainerInteractionHandler.kindOf(container) == null) {
                    continue;
                }
                CompoundTag saved = container.getAttached(ModAttachments.LOOT_STATE);
                if (saved != null && LootrLootState.peekFirstGeneratedGameTime(saved) >= 0) {
                    looted++;
                    BlockPos pos = container.getBlockPos();
                    ResourceKey<LootTable> table = container.getLootTable();
                    // Same gates as Decay.track / Refresh.track, repeated here only so additions can be counted.
                    if (decay && Decay.covers(level, pos, table)
                            && DecayTracker.get(server).add(level.dimension(), pos)) {
                        decayAdded++;
                    }
                    if (refresh && LootrConfig.refreshTicksFor(level, pos, table) > 0
                            && RefreshTracker.get(server).add(level.dimension(), pos)) {
                        refreshAdded++;
                    }
                }
            }
        } catch (RuntimeException e) {
            // An exception escaping chunk loading (or a command) would be far worse than a missed container.
            LootrServerOnlyFabric.LOGGER.error("Could not scan a chunk for loot containers; skipping it.", e);
        }
        return new Scan(looted, decayAdded, refreshAdded);
    }

    private ChunkDiscovery() {}
}
