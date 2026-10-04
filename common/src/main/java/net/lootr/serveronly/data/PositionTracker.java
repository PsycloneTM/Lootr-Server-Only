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
        Map<Long, Long> regions = index.regions.get(id(dimension));
        List<BlockPos> out = new ArrayList<>();
        if (regions == null) {
            return out;
        }
        List<Long> dueRegions = new ArrayList<>();
        for (Map.Entry<Long, Long> e : regions.entrySet()) {
            if (e.getValue() <= now) {
                dueRegions.add(e.getKey());
            }
        }
        for (long region : dueRegions) {
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

    public void wakeChunk(ResourceKey<Level> dimension, int chunkX, int chunkZ, long now) {
        long region = regionKey(chunkX >> 5, chunkZ >> 5);
        if (!hasRegion(dimension, region)) {
            return;
        }
        TrackerShard shard = shard(dimension, region);
        if (shard.index.wakeChunk(ChunkPos.asLong(chunkX, chunkZ), now) > 0) {
            shard.setDirty();
            updateIndex(dimension, region, shard);
        }
    }

    public void syncSetting(long setting) {
        if (index.setting == setting) {
            return;
        }
        index.setting = setting;
        index.generation++;
        for (Map<Long, Long> regions : index.regions.values()) {
            regions.replaceAll((k, v) -> 0L);
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
        Map<Long, Long> regions = index.regions.get(id(dimension));
        return regions != null && regions.containsKey(region);
    }

    private TrackerShard shard(ResourceKey<Level> dimension, long region) {
        String name = prefix + "_" + fileSafe(dimension) + "_" + (int) (region >> 32) + "_" + (int) region;
        TrackerShard shard = storage.computeIfAbsent(factory(TrackerShard::new, TrackerShard::load), name);
        if (shard.generation != index.generation) {
            shard.index.wakeAll(0L);
            shard.generation = index.generation;
            shard.setDirty();
        }
        return shard;
    }

    private void updateIndex(ResourceKey<Level> dimension, long region, TrackerShard shard) {
        String dim = id(dimension);
        Map<Long, Long> regions = index.regions.get(dim);
        if (shard.index.isEmpty()) {
            if (regions != null && regions.remove(region) != null) {
                if (regions.isEmpty()) {
                    index.regions.remove(dim);
                }
                index.setDirty();
            }
            return;
        }
        if (regions == null) {
            regions = new java.util.HashMap<>();
            index.regions.put(dim, regions);
        }
        Long old = regions.put(region, shard.index.minDue());
        if (old == null || old != shard.index.minDue()) {
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
