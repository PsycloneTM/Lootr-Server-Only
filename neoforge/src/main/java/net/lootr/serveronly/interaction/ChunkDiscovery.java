package net.lootr.serveronly.interaction;

import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.data.DecayTracker;
import net.lootr.serveronly.data.RefreshTracker;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;

@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class ChunkDiscovery {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            discover(level, chunk);
        }
    }

    private static void discover(ServerLevel level, LevelChunk chunk) {
        wakeTracked(level, chunk);
        boolean decay = LootrConfig.decayTicks() > 0 && LootrConfig.START_DECAY_WHILE_TICKING.get();
        boolean refresh = LootrConfig.REFRESH_TICKS.get() > 0 && LootrConfig.START_REFRESH_WHILE_TICKING.get();
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
            if (LootrConfig.REFRESH_TICKS.get() > 0) {
                RefreshTracker.get(server).wakeChunk(level.dimension(), x, z, now);
            }
        } catch (RuntimeException e) {
            LootrServerOnly.LOGGER.error("Could not wake tracked containers for a loaded chunk; the sweep will still reach them.", e);
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
        return LootrConfig.REFRESH_TICKS.get() > 0;
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
                if (be instanceof RandomizableContainerBlockEntity container
                        && ContainerInteractionHandler.kindOf(container) != null
                        && container.hasData(ModAttachments.LOOT_STATE)
                        && container.getData(ModAttachments.LOOT_STATE).getFirstGeneratedGameTime() >= 0) {
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
            LootrServerOnly.LOGGER.error("Could not scan a chunk for loot containers; skipping it.", e);
        }
        return new Scan(looted, decayAdded, refreshAdded);
    }

    private ChunkDiscovery() {}
}
