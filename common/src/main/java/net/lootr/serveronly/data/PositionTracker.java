package net.lootr.serveronly.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class PositionTracker {
    private final DimensionDataStorage storage;
    private final String prefix;
    private final TrackerIndex index;

    PositionTracker(MinecraftServer server, String prefix, String legacyName) {
        this.storage = server.overworld().getDataStorage();
        this.prefix = prefix;
        this.index = storage.computeIfAbsent(factory(TrackerIndex::new, TrackerIndex::load), prefix + "_index");
        migrateLegacy(legacyName);
    }

    private static <T extends SavedData> SavedData.Factory<T> factory(Supplier<T> constructor,
                                                                   BiFunction<CompoundTag, HolderLookup.Provider, T> loader) {
        return new SavedData.Factory<>(constructor, loader, DataFixTypes.LEVEL);
    }

    public boolean add(ResourceKey<Level> dimension, BlockPos pos) {
        return add(dimension, pos.asLong(), 0L);
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        long packed = pos.asLong();
        long region = regionOf(packed);
        if (!hasRegion(dimension, region)) {
            return;
        }
        TrackerShard shard = shard(dimension, region);
        if (shard.index.remove(packed)) {
            shard.setDirty();
            updateIndex(dimension, region, shard);
        }
    }

    public List<BlockPos> takeDue(ResourceKey<Level> dimension, long now) {
        // Only regions whose earliest deadline has passed are visited; the rest are not scanned.
        List<BlockPos> out = new ArrayList<>();
        for (long region : index.queue.takeDue(id(dimension), now)) {
            TrackerShard shard = shard(dimension, region);
            List<Long> taken = shard.index.takeDue(now);
            if (!taken.isEmpty()) {
                shard.setDirty();
                for (long packed : taken) {
                    out.add(BlockPos.of(packed));
                }
            }
            updateIndex(dimension, region, shard);
        }
        return out;
    }

    public void schedule(ResourceKey<Level> dimension, BlockPos pos, long due) {
        long packed = pos.asLong();
        long region = regionOf(packed);
        TrackerShard shard = shard(dimension, region);
        shard.index.put(packed, due);
        shard.setDirty();
        updateIndex(dimension, region, shard);
    }

    public void park(ResourceKey<Level> dimension, BlockPos pos) {
        schedule(dimension, pos, DueIndex.PARKED);
    }

    public int wakeChunk(ResourceKey<Level> dimension, int chunkX, int chunkZ, long now) {
        long region = regionKey(chunkX >> 5, chunkZ >> 5);
        if (!hasRegion(dimension, region)) {
            return 0;
        }
        TrackerShard shard = shard(dimension, region);
        int woken = shard.index.wakeChunk(ChunkPos.asLong(chunkX, chunkZ), now);
        if (woken > 0) {
            shard.setDirty();
            updateIndex(dimension, region, shard);
        }
        return woken;
    }

    public void syncSetting(long setting) {
        if (index.setting == setting) {
            return;
        }
        long previous = index.setting;
        index.setting = setting;
        if (previous != Long.MIN_VALUE && setting < previous) {
            // A shorter duration can make stored deadlines too late, so pull them earlier. A longer one
            // only makes them early, and the sweep reschedules an early entry when it fires.
            long delta = previous - setting;
            index.shiftTotal += delta;
            index.queue.shiftEarlier(delta);
        }
        index.setDirty();
    }

    private boolean add(ResourceKey<Level> dimension, long packed, long due) {
        long region = regionOf(packed);
        TrackerShard shard = shard(dimension, region);
        if (!shard.index.add(packed, due)) {
            return false;
        }
        shard.setDirty();
        updateIndex(dimension, region, shard);
        return true;
    }

    private boolean hasRegion(ResourceKey<Level> dimension, long region) {
        return index.queue.contains(id(dimension), region);
    }

    private TrackerShard shard(ResourceKey<Level> dimension, long region) {
        String name = prefix + "_" + fileSafe(dimension) + "_" + (int) (region >> 32) + "_" + (int) region;
        TrackerShard shard = storage.computeIfAbsent(factory(TrackerShard::new, TrackerShard::load), name);
        if (shard.generation != index.generation) {
            // Save data written before deadline shifting existed: wake it once, as the old code did.
            shard.index.wakeAll(0L);
            shard.generation = index.generation;
            shard.setDirty();
        }
        if (shard.appliedShift != index.shiftTotal) {
            shard.index.shiftEarlier(index.shiftTotal - shard.appliedShift);
            shard.appliedShift = index.shiftTotal;
            shard.setDirty();
        }
        return shard;
    }

    private void updateIndex(ResourceKey<Level> dimension, long region, TrackerShard shard) {
        String dim = id(dimension);
        if (shard.index.isEmpty()) {
            if (index.queue.remove(dim, region)) {
                index.setDirty();
            }
            return;
        }
        long min = shard.index.minDue();
        Long old = index.queue.put(dim, region, min);
        if (old == null || old != min) {
            index.setDirty();
        }
    }

    private void migrateLegacy(String legacyName) {
        LegacyPositions legacy = storage.computeIfAbsent(factory(LegacyPositions::new, LegacyPositions::load), legacyName);
        if (legacy.positions.isEmpty()) {
            return;
        }
        for (Map.Entry<String, java.util.Set<Long>> e : legacy.positions.entrySet()) {
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(e.getKey());
            if (loc == null) {
                continue;
            }
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
            for (long packed : e.getValue()) {
                add(dimension, packed, 0L);
            }
        }
        legacy.positions.clear();
        legacy.setDirty();
    }

    private static String id(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    private static String fileSafe(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace() + "__" + dimension.location().getPath().replace('/', '_');
    }

    private static long regionOf(long packedPos) {
        return regionKey(BlockPos.getX(packedPos) >> 9, BlockPos.getZ(packedPos) >> 9);
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
