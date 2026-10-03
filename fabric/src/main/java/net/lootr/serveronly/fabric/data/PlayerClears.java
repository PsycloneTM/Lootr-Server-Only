package net.lootr.serveronly.fabric.data;

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

/**
 * Backs {@code /lootr clear <players>}: "forget everything this player has looted".
 * <p>
 * Loot lives in each container's own attachment, and containers cannot be listed from
 * outside (there is no index of every chest in the world), so a clear cannot walk them
 * and delete one entry each. Instead it is lazy. Every loot key (a player's UUID, or a
 * team's when team loot is on) has a <i>generation</i> number, 0 until it is cleared.
 * Each per-player entry in a container remembers the generation it was written under
 * ({@link LootrLootState}), and counts as "not looted yet" the moment the key's
 * generation moves past it. Clearing is therefore one increment, takes effect for every
 * container, pot, suspicious block and item frame at once, and a container's old entry
 * is simply overwritten the next time that player loots it.
 * <p>
 * The numbers are read from {@link LootrLootState}, which has no server in hand, so the
 * loaded instance is cached in a static. {@link #get} refreshes that cache, and the
 * once-a-second tick calls it (see {@code Decay.onServerTick}), so it is in place long
 * before any player can interact. Saved as {@code data/lootr_serveronly_clears.dat}.
 */
public final class PlayerClears extends SavedData {
    private static final String NAME = "lootr_serveronly_clears";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_KEY = "Key";
    private static final String TAG_GENERATION = "Generation";

    private static MinecraftServer cachedServer;
    private static PlayerClears cached;

    private final Map<UUID, Integer> generations = new HashMap<>();

    /** The world's clears; also (re)points the static cache used by {@link #generation}. */
    public static PlayerClears get(MinecraftServer server) {
        if (cached == null || cachedServer != server) {
            // Vanilla's Factory takes three arguments; DataFixTypes.LEVEL rather than null (see DecayTracker).
            cached = server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(PlayerClears::new, PlayerClears::load, DataFixTypes.LEVEL), NAME);
            cachedServer = server;
        }
        return cached;
    }

    /** A loot key's current generation: 0 if never cleared (or if the world's data is not loaded yet). */
    public static int generation(UUID key) {
        PlayerClears clears = cached;
        return clears == null ? 0 : clears.generations.getOrDefault(key, 0);
    }

    /** Makes every entry this key has written so far count as "not looted yet". */
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
