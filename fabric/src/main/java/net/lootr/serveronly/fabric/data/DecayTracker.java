package net.lootr.serveronly.fabric.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saved set of block positions the decay sweep should look at, per dimension.
 * Vanilla block entities have no per-tick hook we can use, and scanning every
 * loaded chunk would be wasteful, so a position is added when a decay-covered
 * container is looted or re-opened and removed when the sweep finds nothing left
 * to do there. Stored in the overworld's data storage, one file for the whole
 * server. Not verified by a build.
 */
public final class DecayTracker extends SavedData {

    private static final String NAME = "lootr_serveronly_decay";
    private static final String TAG_DIMENSIONS = "dimensions";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POSITIONS = "positions";

    private final Map<ResourceKey<Level>, Set<Long>> positions = new HashMap<>();

    public static DecayTracker get(MinecraftServer server) {
        // Plain Mojang-mapped vanilla's Factory record takes THREE arguments;
        // the two-argument constructor only exists in NeoForge's patched copy,
        // which is why the NeoForge module compiles without the third. The
        // third is a DataFixTypes. NeoForge documents null as allowed, but I
        // could not read unpatched vanilla to confirm it null-checks that
        // argument, and a missing check would crash on the SECOND server start
        // (when the file exists and is read back). A real value is safe either
        // way: our file is saved with the current data version, so the fixer
        // is a no-op until a later Minecraft upgrade, where it only touches
        // fields it knows about and ours are not among them.
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DecayTracker::new, DecayTracker::load, DataFixTypes.LEVEL), NAME);
    }

    /** @return true if the position was not already tracked (so something was actually added). */
    public boolean add(ResourceKey<Level> dimension, BlockPos pos) {
        if (positions.computeIfAbsent(dimension, k -> new HashSet<>()).add(pos.asLong())) {
            setDirty();
            return true;
        }
        return false;
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        Set<Long> set = positions.get(dimension);
        if (set != null && set.remove(pos.asLong())) {
            if (set.isEmpty()) {
                positions.remove(dimension);
            }
            setDirty();
        }
    }

    /** A copy, so the sweep can add/remove while iterating. */
    public List<BlockPos> snapshot(ResourceKey<Level> dimension) {
        Set<Long> set = positions.get(dimension);
        List<BlockPos> out = new ArrayList<>();
        if (set != null) {
            for (long packed : set) {
                out.add(BlockPos.of(packed));
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dimensions = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : positions.entrySet()) {
            CompoundTag dimension = new CompoundTag();
            dimension.putString(TAG_DIMENSION, e.getKey().location().toString());
            long[] packed = new long[e.getValue().size()];
            int i = 0;
            for (long p : e.getValue()) {
                packed[i++] = p;
            }
            dimension.put(TAG_POSITIONS, new LongArrayTag(packed));
            dimensions.add(dimension);
        }
        tag.put(TAG_DIMENSIONS, dimensions);
        return tag;
    }

    private static DecayTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        DecayTracker tracker = new DecayTracker();
        for (Tag raw : tag.getList(TAG_DIMENSIONS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(TAG_DIMENSION));
            if (id == null) {
                continue;
            }
            Set<Long> set = new HashSet<>();
            for (long packed : entry.getLongArray(TAG_POSITIONS)) {
                set.add(packed);
            }
            if (!set.isEmpty()) {
                tracker.positions.put(ResourceKey.create(Registries.DIMENSION, id), set);
            }
        }
        return tracker;
    }
}
