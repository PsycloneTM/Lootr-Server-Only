package net.lootr.serveronly.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * One saved file holding the tracked positions of one 32x32-chunk region of one dimension, for one tracker
 * (decay or refresh). See {@link PositionTracker}. Not verified by a build.
 */
public final class TrackerShard extends SavedData {

    private static final String TAG_POSITIONS = "positions";
    private static final String TAG_DUES = "dues";
    private static final String TAG_GENERATION = "generation";

    public final DueIndex index = new DueIndex(TrackerShard::chunkKey);
    /** The {@link TrackerIndex#generation} this shard's hints were last brought up to date with. */
    public int generation;

    public static long chunkKey(long packedPos) {
        return ChunkPos.asLong(BlockPos.getX(packedPos) >> 4, BlockPos.getZ(packedPos) >> 4);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[][] snap = index.snapshot();
        tag.put(TAG_POSITIONS, new LongArrayTag(snap[0]));
        tag.put(TAG_DUES, new LongArrayTag(snap[1]));
        tag.putInt(TAG_GENERATION, generation);
        return tag;
    }

    public static TrackerShard load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackerShard shard = new TrackerShard();
        long[] positions = tag.getLongArray(TAG_POSITIONS);
        long[] dues = tag.getLongArray(TAG_DUES);
        for (int i = 0; i < positions.length; i++) {
            // A missing/short dues array just means "check now", which is always safe.
            shard.index.put(positions[i], i < dues.length ? dues[i] : 0L);
        }
        shard.generation = tag.getInt(TAG_GENERATION);
        return shard;
    }
}
