package net.lootr.serveronly.fabric.data;

import net.lootr.serveronly.data.PlayerClears;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the "has this player already generated loot here, and what did they
 * get" state for a single Lootr container instance.
 * <p>
 * This is a deliberately simplified reimplementation of upstream Lootr's
 * {@code SimpleLootrInstance}. It intentionally has NO client-sync fields
 * (no clientOpeners set, no clientOpened/clientRefreshing/clientDecaying
 * flags, no visual-openers supplier) because there is no client mod present
 * to sync any of that to - a vanilla client has no idea this data exists.
 * <p>
 * Everything a vanilla client needs to *see* (the chest lid opening, the
 * "someone is looking in here" multi-viewer count, open/close sounds) is
 * handled for free by the vanilla {@link net.minecraft.world.level.block.entity.ChestBlockEntity}
 * / {@link net.minecraft.world.level.block.ChestBlock} machinery this class's
 * owning block entity extends - see LootrChestBlockEntity.
 */
public final class LootrLootState {
    private static final String TAG_CONTAINER_ID = "LootrContainerId";
    private static final String TAG_PLAYER_LOOT = "LootrPlayerLoot";
    private static final String TAG_PLAYER_ID = "Player";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_GENERATION = "Gen";
    private static final String TAG_FIRST_GENERATED_GAME_TIME = "LootrFirstGeneratedGameTime";

    /** Stable identity for this specific block/container instance. */
    private UUID containerId;

    /** Per-player generated contents. Populated lazily, on first open. */
    private final Map<UUID, NonNullList<ItemStack>> perPlayerContents = new HashMap<>();

    /**
     * The {@link PlayerClears} generation each entry above was written under (missing = 0). An
     * entry whose generation is behind its key's current one was cleared with {@code /lootr clear}
     * and counts as if it were not there; it is overwritten the next time that key loots here.
     */
    private final Map<UUID, Integer> entryGeneration = new HashMap<>();

    private final int containerSize;

    /**
     * The level's {@code getGameTime()} at the moment loot was FIRST
     * generated for ANY player against this container instance, or -1 if no
     * one has opened it yet. This is deliberately a single, per-container
     * timestamp rather than one per player - matching upstream Lootr's own
     * refresh model, where "refreshing" re-rolls the whole container (and
     * clears every player's stored contents / opener record) on one shared
     * timer, not a separate timer per player. See {@link #refreshIfDue}.
     * <p>
     * The timer is checked when a player opens the container and by a
     * background sweep (see {@code Refresh}); a container someone has open is
     * never reset under them. Upstream's refresh particles are not reproduced
     * (there is no client mod to draw them).
     */
    private long firstGeneratedGameTime = -1;

    public LootrLootState(int containerSize) {
        this.containerSize = containerSize;
    }

    public UUID getContainerId() {
        if (containerId == null) {
            containerId = UUID.randomUUID();
        }
        return containerId;
    }

    public int getContainerSize() {
        return containerSize;
    }

    /** True if this entry has not been invalidated by {@code /lootr clear}. */
    private boolean isCurrent(UUID playerId) {
        return entryGeneration.getOrDefault(playerId, 0) == PlayerClears.generation(playerId);
    }

    public boolean hasGeneratedFor(UUID playerId) {
        return perPlayerContents.containsKey(playerId) && isCurrent(playerId);
    }

    public NonNullList<ItemStack> getOrCreateContents(UUID playerId, java.util.function.Supplier<NonNullList<ItemStack>> generator) {
        if (!hasGeneratedFor(playerId)) {
            setContents(playerId, generator.get());
        }
        return perPlayerContents.get(playerId);
    }

    public NonNullList<ItemStack> getContentsOrEmpty(UUID playerId) {
        NonNullList<ItemStack> contents = perPlayerContents.get(playerId);
        if (contents == null || !isCurrent(playerId)) {
            return NonNullList.withSize(containerSize, ItemStack.EMPTY);
        }
        return contents;
    }

    public void setContents(UUID playerId, NonNullList<ItemStack> contents) {
        perPlayerContents.put(playerId, contents);
        entryGeneration.put(playerId, PlayerClears.generation(playerId));
    }

    /**
     * Records {@code gameTime} as this container's refresh-timer start point,
     * if (and only if) it doesn't already have one. Call this right after the
     * FIRST successful loot generation for this container (any player) - see
     * {@code ContainerInteractionHandler.generateLootIfNeeded}.
     */
    public void markFirstGeneratedIfAbsent(long gameTime) {
        if (firstGeneratedGameTime < 0) {
            firstGeneratedGameTime = gameTime;
        }
    }

    /** True if a refresh would reset this container right now (without resetting it). */
    public boolean refreshDue(long currentGameTime, long refreshTicks) {
        return refreshTicks > 0 && firstGeneratedGameTime >= 0
                && currentGameTime - firstGeneratedGameTime >= refreshTicks;
    }

    /**
     * If this container has been generated for at least {@code refreshTicks}
     * game-time ticks, clears all stored per-player loot and openers and
     * resets the refresh timer, so the very next open (by anyone) rolls
     * completely fresh loot - matching upstream's
     * {@code ILootrInfoProvider#performRefresh}, minus the client-facing
     * decay/refresh particle bookkeeping upstream also does there (no client
     * mod exists to show those to). A {@code refreshTicks <= 0} disables
     * refreshing entirely for this container (the config's default), and a
     * container nobody has opened yet ({@code firstGeneratedGameTime < 0})
     * is never "due" - there is nothing to refresh.
     *
     * @return true if this call actually reset the container (caller should
     * treat it as never-opened for every player from this point on).
     */
    public boolean refreshIfDue(long currentGameTime, long refreshTicks) {
        if (refreshTicks <= 0 || firstGeneratedGameTime < 0) {
            return false;
        }
        if (currentGameTime - firstGeneratedGameTime < refreshTicks) {
            return false;
        }
        perPlayerContents.clear();
        entryGeneration.clear();
        firstGeneratedGameTime = -1;
        return true;
    }

    /**
     * How many players (or teams) have a loot entry here, including "already took it" markers.
     * Entries wiped by {@code /lootr clear} are not counted.
     */
    public int lootedCount() {
        int count = 0;
        for (UUID id : perPlayerContents.keySet()) {
            if (isCurrent(id)) {
                count++;
            }
        }
        return count;
    }

    /** Game time of the first generation, or -1 if nothing has been generated (or it was reset). */
    /**
     * Reads just the first-looted timestamp out of a saved tag, -1 if there is none. The decay sweep
     * calls this once a second per tracked container, so it must not deserialize every player's items.
     */
    public static long peekFirstGeneratedGameTime(CompoundTag tag) {
        return tag.contains(TAG_FIRST_GENERATED_GAME_TIME) ? tag.getLong(TAG_FIRST_GENERATED_GAME_TIME) : -1;
    }

    public long getFirstGeneratedGameTime() {
        return firstGeneratedGameTime;
    }

    /** Moves the shared refresh/decay timer. Intended for the administrative commands only. */
    public void setFirstGeneratedGameTime(long gameTime) {
        firstGeneratedGameTime = gameTime;
    }

    /**
     * Forgets every player's loot and the refresh timer, so the very next
     * open by anyone rolls fresh loot. Used by the admin reset command; the
     * refresh timer's own reset in {@link #refreshIfDue} does the same thing.
     *
     * @return true if there was anything to forget.
     */
    public boolean resetAll() {
        boolean hadAnything = !perPlayerContents.isEmpty() || firstGeneratedGameTime >= 0;
        perPlayerContents.clear();
        entryGeneration.clear();
        firstGeneratedGameTime = -1;
        return hadAnything;
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        perPlayerContents.clear();
        entryGeneration.clear();
        firstGeneratedGameTime = tag.contains(TAG_FIRST_GENERATED_GAME_TIME)
                ? tag.getLong(TAG_FIRST_GENERATED_GAME_TIME)
                : -1;
        if (tag.hasUUID(TAG_CONTAINER_ID)) {
            containerId = tag.getUUID(TAG_CONTAINER_ID);
        }
        if (tag.contains(TAG_PLAYER_LOOT)) {
            for (net.minecraft.nbt.Tag entryTag : tag.getList(TAG_PLAYER_LOOT, CompoundTag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) entryTag;
                UUID playerId = entry.getUUID(TAG_PLAYER_ID);
                NonNullList<ItemStack> items = NonNullList.withSize(containerSize, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(entry.getCompound(TAG_ITEMS), items, provider);
                perPlayerContents.put(playerId, items);
                if (entry.contains(TAG_GENERATION)) {
                    entryGeneration.put(playerId, entry.getInt(TAG_GENERATION));
                }
            }
        }
    }

    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putUUID(TAG_CONTAINER_ID, getContainerId());
        if (firstGeneratedGameTime >= 0) {
            tag.putLong(TAG_FIRST_GENERATED_GAME_TIME, firstGeneratedGameTime);
        }
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Map.Entry<UUID, NonNullList<ItemStack>> entry : perPlayerContents.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TAG_PLAYER_ID, entry.getKey());
            CompoundTag itemsTag = new CompoundTag();
            ContainerHelper.saveAllItems(itemsTag, entry.getValue(), provider);
            entryTag.put(TAG_ITEMS, itemsTag);
            entryTag.putInt(TAG_GENERATION, entryGeneration.getOrDefault(entry.getKey(), 0));
            list.add(entryTag);
        }
        tag.put(TAG_PLAYER_LOOT, list);
    }
}
