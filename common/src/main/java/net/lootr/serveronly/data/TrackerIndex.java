package net.lootr.serveronly.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;

public final class TrackerIndex extends SavedData {
    private static final String TAG_DIMENSIONS = "dimensions";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_REGIONS = "regions";
    private static final String TAG_MIN_DUES = "min_dues";
    private static final String TAG_GENERATION = "generation";
    private static final String TAG_SETTING = "setting";
    private static final String TAG_SHIFT_TOTAL = "shift_total";

    public final RegionQueue queue = new RegionQueue();

    public int generation;

    public long shiftTotal;
    public long setting = Long.MIN_VALUE;

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dimensions = new ListTag();
        for (String dim : queue.dimensions()) {
            CompoundTag dimension = new CompoundTag();
            dimension.putString(TAG_DIMENSION, dim);
            Map<Long, Long> entries = queue.entries(dim);
            long[] keys = new long[entries.size()];
            long[] mins = new long[keys.length];
            int i = 0;
            for (Map.Entry<Long, Long> r : entries.entrySet()) {
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
        tag.putLong(TAG_SHIFT_TOTAL, shiftTotal);
        return tag;
    }

    public static TrackerIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackerIndex index = new TrackerIndex();
        for (Tag raw : tag.getList(TAG_DIMENSIONS, Tag.TAG_COMPOUND)) {
            CompoundTag dimension = (CompoundTag) raw;
            long[] keys = dimension.getLongArray(TAG_REGIONS);
            long[] mins = dimension.getLongArray(TAG_MIN_DUES);
            String dim = dimension.getString(TAG_DIMENSION);
            for (int i = 0; i < keys.length; i++) {
                index.queue.put(dim, keys[i], i < mins.length ? mins[i] : 0L);
            }
        }
        index.generation = tag.getInt(TAG_GENERATION);
        index.shiftTotal = tag.getLong(TAG_SHIFT_TOTAL);
        if (tag.contains(TAG_SETTING)) {
            index.setting = tag.getLong(TAG_SETTING);
        }
        return index;
    }
}
