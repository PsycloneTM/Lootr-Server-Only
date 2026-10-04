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

/**
 * Where the decay and refresh sweeps keep the block positions they should look at: the shared implementation
 * behind {@link DecayTracker} and {@link RefreshTracker}.
 * <p>
 * Compared with the old single set per dimension:
 * <ul>
 *     <li><b>Sharded.</b> Positions live in one {@link TrackerShard} file per 32x32-chunk region, so no single
 *     file or in-memory collection holds the whole server's tracked set, and a change only dirties its own
 *     region's file.</li>
 *     <li><b>Due-ordered.</b> Each position carries a "check at game time T" hint kept in a sorted structure
 *     ({@link DueIndex}). A sweep takes only the entries whose hint has arrived; entries far from their
 *     deadline cost nothing per sweep. A small {@link TrackerIndex} records each region's earliest hint, so a
 *     sweep does not even load the shards of regions with nothing due.</li>
 *     <li><b>Timer model untouched.</b> The hint is only a lower bound on when to look. The authoritative
 *     deadline is still the container's own {@code firstGeneratedGameTime} plus the configured ticks, and the
 *     sweep recomputes it from there on every look.</li>
 * </ul>
 * The sweep protocol is: {@link #takeDue} removes the due entries; for each one the sweep either lets it go
 * (nothing left to track) or hands it back with {@link #schedule}.
 * <p>
 * Hints are only ever allowed to be too early, never too late. Two things could make one too late, and both
 * are handled: a timer setting changed ({@link #syncSetting}), and a position that was deferred because its
 * chunk was not loaded ({@link #wakeChunk}, called when that chunk loads).
 * <p>
 * Shards are {@link SavedData}, which vanilla keeps cached once loaded, so a region touched during a session
 * stays in memory until shutdown. What this bounds is the per-sweep work and the size of any one file, not
 * resident memory for regions that were touched. Not verified by a build.
 */
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

    // The one place the loaders differ: plain Mojang-mapped vanilla's Factory takes a third DataFixTypes argument
    // (NeoForge's patched copy has a two-argument constructor). LEVEL is safe: these files are written with the
    // current data version, so the fixer is a no-op, as for the old single-file trackers.
    private static <T extends SavedData> SavedData.Factory<T> factory(Supplier<T> constructor,
                                                                   BiFunction<CompoundTag, HolderLookup.Provider, T> loader) {
        return new SavedData.Factory<>(constructor, loader, DataFixTypes.LEVEL);
    }

    // ---- public API (what the trackers' callers use) --------------------------

    /** Tracks a position, to be looked at on the next sweep. @return true if it was not already tracked. */
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

    /**
     * Removes and returns every tracked position in {@code dimension} whose hint is at or before {@code now}.
     * Whatever the caller still wants tracked must be handed back with {@link #schedule}.
     */
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

    /** Tracks {@code pos} (adding it if it was just taken) and says not to look before game time {@code due}. */
    public void schedule(ResourceKey<Level> dimension, BlockPos pos, long due) {
        long packed = pos.asLong();
        long region = regionOf(packed);
        TrackerShard shard = shard(dimension, region);
        shard.index.put(packed, due);
        shard.setDirty();
        updateIndex(dimension, region, shard);
    }

    /**
     * A chunk just loaded: anything tracked in it that was deferred while it was unloaded becomes due now, so a
     * container that expired meanwhile is handled on the next sweep, as before. Cheap when nothing in the
     * region is tracked (no shard is loaded).
     */
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

    /**
     * Call each sweep with the timer setting the hints depend on ({@code decay_value} / {@code refresh_value}).
     * If it changed since last time, every hint may now be too late, so all are made due. Does not load shards:
     * they resync as they are next used.
     */
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

    // ---- internals -------------------------------------------------------------

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

    /** Loads (or creates) a region's shard, first catching its hints up with any setting change. */
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

    /** Moves an old single-file tracker's positions into shards, once, then leaves that file empty. */
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
                add(dimension, packed, 0L); // no deadline known: look at it on the next sweep, which sets one
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
