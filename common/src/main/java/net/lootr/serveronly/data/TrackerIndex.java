package net.lootr.serveronly.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Small saved file listing, per dimension, which regions have a {@link TrackerShard} with entries and the
 * earliest hint in each. It is what lets a sweep skip every region with nothing due without loading that
 * region's shard. Its size grows with the number of regions, not the number of containers.
 * <p>
 * {@code generation} / {@code setting} exist so a changed timer setting (decay_value / refresh_value) can make
 * every hint "check now" without loading every shard: bump the generation, zero these minimums, and each
 * shard resyncs itself the first time it is loaded. Not verified by a build.
 */
public final class TrackerIndex extends SavedData {

    private static final String TAG_DIMENSIONS = "dimensions";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_REGIONS = "regions";
    private static final String TAG_MIN_DUES = "min_dues";
    private static final String TAG_GENERATION = "generation";
    private static final String TAG_SETTING = "setting";

    /** dimension id -> (region key -> earliest hint in that region's shard). */
    public final Map<String, Map<Long, Long>> regions = new HashMap<>();
    public int generation;
    public long setting = Long.MIN_VALUE;

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dimensions = new ListTag();
        for (Map.Entry<String, Map<Long, Long>> e : regions.entrySet()) {
            CompoundTag dimension = new CompoundTag();
            dimension.putString(TAG_DIMENSION, e.getKey());
            long[] keys = new long[e.getValue().size()];
            long[] mins = new long[keys.length];
            int i = 0;
            for (Map.Entry<Long, Long> r : e.getValue().entrySet()) {
                keys[i] = r.getKey();
                mins[i++] = r.getValue();
            }
            dimension.put(TAG_REGIONS, new LongArrayTag(keys));
            dimension.put(TAG_MIN_DUES, new LongArrayTag(mins));
            dimensions.add(dimension);
        }
        tag.put(TAG_DIMENSIONS, dimensions);
        tag.putInt(TAG_GENERATION, generation);
        tag.putLong(TAG_SETTING, setting);
        return tag;
    }

    public static TrackerIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackerIndex index = new TrackerIndex();
        for (Tag raw : tag.getList(TAG_DIMENSIONS, Tag.TAG_COMPOUND)) {
            CompoundTag dimension = (CompoundTag) raw;
            long[] keys = dimension.getLongArray(TAG_REGIONS);
            long[] mins = dimension.getLongArray(TAG_MIN_DUES);
            Map<Long, Long> map = new HashMap<>();
            for (int i = 0; i < keys.length; i++) {
                map.put(keys[i], i < mins.length ? mins[i] : 0L);
            }
            index.regions.put(dimension.getString(TAG_DIMENSION), map);
        }
        index.generation = tag.getInt(TAG_GENERATION);
        if (tag.contains(TAG_SETTING)) {
            index.setting = tag.getLong(TAG_SETTING);
        }
        return index;
    }
}
