package net.lootr.serveronly.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerClears extends SavedData {
    private static final String NAME = "lootr_serveronly_clears";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_KEY = "Key";
    private static final String TAG_GENERATION = "Generation";

    private static MinecraftServer cachedServer;
    private static PlayerClears cached;

    private final Map<UUID, Integer> generations = new HashMap<>();

    public static PlayerClears get(MinecraftServer server) {
        if (cached == null || cachedServer != server) {
            cached = server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(PlayerClears::new, PlayerClears::load, DataFixTypes.LEVEL), NAME);
            cachedServer = server;
        }
        return cached;
    }

    public static int generation(UUID key) {
        PlayerClears clears = cached;
        return clears == null ? 0 : clears.generations.getOrDefault(key, 0);
    }

    public void clear(UUID key) {
        generations.merge(key, 1, Integer::sum);
        setDirty();
    }

    private static PlayerClears load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerClears clears = new PlayerClears();
        for (Tag raw : tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID(TAG_KEY)) {
                clears.generations.put(entry.getUUID(TAG_KEY), entry.getInt(TAG_GENERATION));
            }
        }
        return clears;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> e : generations.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_KEY, e.getKey());
            entry.putInt(TAG_GENERATION, e.getValue());
            list.add(entry);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }
}
