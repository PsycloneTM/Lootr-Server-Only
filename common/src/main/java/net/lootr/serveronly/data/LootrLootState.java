package net.lootr.serveronly.data;

import net.lootr.serveronly.schedule.LootSchedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LootrLootState {
    private static final String TAG_CONTAINER_ID = "LootrContainerId";
    private static final String TAG_PLAYER_LOOT = "LootrPlayerLoot";
    private static final String TAG_PLAYER_ID = "Player";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SCORED = "LootrScored";
    private static final String TAG_GENERATION = "Gen";
    private static final String TAG_FIRST_GENERATED_GAME_TIME = "LootrFirstGeneratedGameTime";

    private UUID containerId;

    private final Map<UUID, NonNullList<ItemStack>> perPlayerContents = new HashMap<>();

    private final java.util.Set<UUID> scored = new java.util.LinkedHashSet<>();

    public java.util.Set<UUID> getScored() {
        return java.util.Collections.unmodifiableSet(scored);
    }

    private final Map<UUID, Integer> entryGeneration = new HashMap<>();

    private final int containerSize;

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
        scored.add(playerId);
        entryGeneration.put(playerId, PlayerClears.generation(playerId));
    }

    public void markFirstGeneratedIfAbsent(long gameTime) {
        if (firstGeneratedGameTime < 0) {
            firstGeneratedGameTime = gameTime;
        }
    }

    public boolean refreshDue(long currentGameTime, long refreshTicks) {
        return LootSchedule.isRefreshDue(currentGameTime, firstGeneratedGameTime, refreshTicks);
    }

    public boolean refreshIfDue(long currentGameTime, long refreshTicks) {
        if (!LootSchedule.isRefreshDue(currentGameTime, firstGeneratedGameTime, refreshTicks)) {
            return false;
        }
        perPlayerContents.clear();
        entryGeneration.clear();
        firstGeneratedGameTime = -1;
        return true;
    }

    public int lootedCount() {
        int count = 0;
        for (UUID id : perPlayerContents.keySet()) {
            if (isCurrent(id)) {
                count++;
            }
        }
        return count;
    }

    public long getFirstGeneratedGameTime() {
        return firstGeneratedGameTime;
    }

    public void setFirstGeneratedGameTime(long gameTime) {
        firstGeneratedGameTime = gameTime;
    }

    public boolean resetAll() {
        boolean hadAnything = !perPlayerContents.isEmpty() || firstGeneratedGameTime >= 0;
        perPlayerContents.clear();
        entryGeneration.clear();
        firstGeneratedGameTime = -1;
        return hadAnything;
    }

    public static long peekFirstGeneratedGameTime(CompoundTag tag) {
        return tag.contains(TAG_FIRST_GENERATED_GAME_TIME) ? tag.getLong(TAG_FIRST_GENERATED_GAME_TIME) : -1;
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        perPlayerContents.clear();
        scored.clear();
        entryGeneration.clear();
        if (tag.contains(TAG_SCORED)) {
            for (net.minecraft.nbt.Tag t : tag.getList(TAG_SCORED, CompoundTag.TAG_INT_ARRAY)) {
                scored.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
            }
        }
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
                scored.add(playerId);
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
        net.minecraft.nbt.ListTag scoredList = new net.minecraft.nbt.ListTag();
        for (UUID id : scored) {
            scoredList.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        }
        tag.put(TAG_SCORED, scoredList);
    }
}
