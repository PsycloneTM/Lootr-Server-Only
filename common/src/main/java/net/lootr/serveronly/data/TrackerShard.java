package net.lootr.serveronly.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public final class TrackerShard extends SavedData {
    private static final String TAG_POSITIONS = "positions";
    private static final String TAG_DUES = "dues";
    private static final String TAG_GENERATION = "generation";
    private static final String TAG_APPLIED_SHIFT = "applied_shift";

    public final DueIndex index = new DueIndex(TrackerShard::chunkKey);
    public int generation;
    /** How much of {@code TrackerIndex.shiftTotal} this shard's stored deadlines already include. */
    public long appliedShift;

    public static long chunkKey(long packedPos) {
        return ChunkPos.asLong(BlockPos.getX(packedPos) >> 4, BlockPos.getZ(packedPos) >> 4);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[][] snap = index.snapshot();
        tag.put(TAG_POSITIONS, new LongArrayTag(snap[0]));
        tag.put(TAG_DUES, new LongArrayTag(snap[1]));
        tag.putInt(TAG_GENERATION, generation);
        tag.putLong(TAG_APPLIED_SHIFT, appliedShift);
        return tag;
    }

    public static TrackerShard load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackerShard shard = new TrackerShard();
        long[] positions = tag.getLongArray(TAG_POSITIONS);
        long[] dues = tag.getLongArray(TAG_DUES);
        for (int i = 0; i < positions.length; i++) {
            shard.index.put(positions[i], i < dues.length ? dues[i] : 0L);
        }
        shard.generation = tag.getInt(TAG_GENERATION);
        shard.appliedShift = tag.getLong(TAG_APPLIED_SHIFT);
        return shard;
    }
}
