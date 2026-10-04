package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.LootrServerOnlyFabric;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.data.DecayTracker;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.data.RefreshTracker;
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

public final class ChunkDiscovery {

    public static void onChunkLoaded(ServerLevel level, LevelChunk chunk) {
        wakeTracked(level, chunk);
        boolean decay = LootrConfig.decayTicks() > 0 && LootrConfig.startDecayWhileTicking();
        boolean refresh = LootrConfig.refreshTicks() > 0 && LootrConfig.startRefreshWhileTicking();
        if ((!decay && !refresh) || !LootrConfig.isDimensionEnabled(level.dimension())) {
            return;
        }
        scan(level, chunk, decay, refresh);
    }

    private static void wakeTracked(ServerLevel level, LevelChunk chunk) {
        try {
            MinecraftServer server = level.getServer();
            long now = level.getGameTime();
            int x = chunk.getPos().x;
            int z = chunk.getPos().z;
            if (LootrConfig.decayTicks() > 0) {
                DecayTracker.get(server).wakeChunk(level.dimension(), x, z, now);
            }
            if (LootrConfig.refreshTicks() > 0) {
                RefreshTracker.get(server).wakeChunk(level.dimension(), x, z, now);
            }
        } catch (RuntimeException e) {
            LootrServerOnlyFabric.LOGGER.error("Could not wake tracked containers for a loaded chunk; the sweep will still reach them.", e);
        }
    }

    public record Scan(int looted, int decayAdded, int refreshAdded) {
        public static final Scan NONE = new Scan(0, 0, 0);

        public Scan plus(Scan other) {
            return new Scan(looted + other.looted, decayAdded + other.decayAdded, refreshAdded + other.refreshAdded);
        }
    }

    public static boolean decayEnabled() {
        return LootrConfig.decayTicks() > 0;
    }

    public static boolean refreshEnabled() {
        return LootrConfig.refreshTicks() > 0;
    }

    public static Scan scan(ServerLevel level, LevelChunk chunk, boolean decay, boolean refresh) {
        if (LootrConfig.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(chunk.getPos())) {
            return Scan.NONE;
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
            LootrServerOnlyFabric.LOGGER.error("Could not scan a chunk for loot containers; skipping it.", e);
        }
        return new Scan(looted, decayAdded, refreshAdded);
    }

    private ChunkDiscovery() {}
}
