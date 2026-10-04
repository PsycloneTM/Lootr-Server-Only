package net.lootr.serveronly.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view of the old single-file tracker format (one position set per dimension, no deadlines), kept
 * only so an existing world's {@code lootr_serveronly_decay} / {@code lootr_serveronly_refresh} contents
 * move into the sharded trackers once. {@link PositionTracker} empties it after migrating. Not verified by a
 * build.
 */
public final class LegacyPositions extends SavedData {

    private static final String TAG_DIMENSIONS = "dimensions";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POSITIONS = "positions";

    /** dimension id -> packed positions. */
    public final Map<String, Set<Long>> positions = new HashMap<>();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Always written empty: after migration this file holds nothing, and must not resurrect old entries.
        ListTag dimensions = new ListTag();
        for (Map.Entry<String, Set<Long>> e : positions.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            CompoundTag dimension = new CompoundTag();
            dimension.putString(TAG_DIMENSION, e.getKey());
            long[] packed = e.getValue().stream().mapToLong(Long::longValue).toArray();
            dimension.put(TAG_POSITIONS, new LongArrayTag(packed));
            dimensions.add(dimension);
        }
        tag.put(TAG_DIMENSIONS, dimensions);
        return tag;
    }

    public static LegacyPositions load(CompoundTag tag, HolderLookup.Provider registries) {
        LegacyPositions legacy = new LegacyPositions();
        for (Tag raw : tag.getList(TAG_DIMENSIONS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            Set<Long> set = new HashSet<>();
            for (long packed : entry.getLongArray(TAG_POSITIONS)) {
                set.add(packed);
            }
            if (!set.isEmpty()) {
                legacy.positions.put(entry.getString(TAG_DIMENSION), set);
            }
        }
        return legacy;
    }
}
