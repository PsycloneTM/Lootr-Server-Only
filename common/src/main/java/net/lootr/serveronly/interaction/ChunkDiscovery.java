package net.lootr.serveronly.interaction;

import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.data.DecayTracker;
import net.lootr.serveronly.data.RefreshTracker;
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
        boolean decay = LootrSettings.decayTicks() > 0 && LootrSettings.startDecayWhileTicking();
        boolean refresh = LootrSettings.refreshTicks() > 0 && LootrSettings.startRefreshWhileTicking();
        if ((!decay && !refresh) || !LootrSettings.isDimensionEnabled(level.dimension())) {
            return;
        }
        scan(level, chunk, decay, refresh);
    }

    private static void wakeTracked(ServerLevel level, LevelChunk chunk) {
        try {
            wake(level, chunk, true, true);
        } catch (RuntimeException e) {
            LootrServerOnlyConstants.LOGGER.error("Could not wake tracked containers for a loaded chunk; "
                    + "/lootr force_chunk will re-queue them.", e);
        }
    }

    private static int[] wake(ServerLevel level, LevelChunk chunk, boolean decay, boolean refresh) {
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        int x = chunk.getPos().x;
        int z = chunk.getPos().z;
        int decayWoken = decay ? DecayTracker.get(server).wakeChunk(level.dimension(), x, z, now) : 0;
        int refreshWoken = refresh ? RefreshTracker.get(server).wakeChunk(level.dimension(), x, z, now) : 0;
        return new int[] {decayWoken, refreshWoken};
    }

    public record Scan(int looted, int decayAdded, int refreshAdded, int decayWoken, int refreshWoken) {
        public static final Scan NONE = new Scan(0, 0, 0, 0, 0);

        public Scan plus(Scan other) {
            return new Scan(looted + other.looted, decayAdded + other.decayAdded, refreshAdded + other.refreshAdded,
                    decayWoken + other.decayWoken, refreshWoken + other.refreshWoken);
        }
    }

    public static boolean decayEnabled() {
        return LootrSettings.decayTicks() > 0;
    }

    public static boolean refreshEnabled() {
        return LootrSettings.refreshTicks() > 0;
    }

    public static Scan scan(ServerLevel level, LevelChunk chunk, boolean decay, boolean refresh) {
        if (LootrSettings.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(chunk.getPos())) {
            return Scan.NONE;
        }
        int looted = 0;
        int decayAdded = 0;
        int refreshAdded = 0;
        int decayWoken = 0;
        int refreshWoken = 0;
        try {
            int[] woken = wake(level, chunk, decay, refresh);
            decayWoken = woken[0];
            refreshWoken = woken[1];
            MinecraftServer server = level.getServer();
            for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                if (!(be instanceof RandomizableContainerBlockEntity container)
                        || ContainerInteractionHandler.kindOf(container) == null) {
                    continue;
                }
                if (LootStateStore.firstGenerated(container) >= 0) {
                    looted++;
                    BlockPos pos = container.getBlockPos();
                    ResourceKey<LootTable> table = container.getLootTable();
                    if (decay && Decay.covers(level, pos, table)
                            && DecayTracker.get(server).add(level.dimension(), pos)) {
                        decayAdded++;
                    }
                    if (refresh && LootrSettings.refreshTicksFor(level, pos, table) > 0
                            && RefreshTracker.get(server).add(level.dimension(), pos)) {
                        refreshAdded++;
                    }
                }
            }
        } catch (RuntimeException e) {
            LootrServerOnlyConstants.LOGGER.error("Could not scan a chunk for loot containers; skipping it.", e);
        }
        return new Scan(looted, decayAdded, refreshAdded, decayWoken, refreshWoken);
    }

    private ChunkDiscovery() {}
}
