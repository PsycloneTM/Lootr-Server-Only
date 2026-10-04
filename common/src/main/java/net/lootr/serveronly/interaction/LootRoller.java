package net.lootr.serveronly.interaction;

import net.lootr.serveronly.api.LootFilters;

import net.lootr.serveronly.config.LootrSettings;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

public final class LootRoller {

    public static long effectiveSeed(long containerSeed) {
        return LootrSettings.randomiseSeed() ? 0L : containerSeed;
    }

    public static NonNullList<ItemStack> rollInto(LootTable table, LootParams params, long containerSeed, int size) {
        SimpleContainer scratch = new SimpleContainer(size);
        table.fill(scratch, params, effectiveSeed(containerSeed));
        NonNullList<ItemStack> out = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size; i++) {
            out.set(i, scratch.getItem(i));
        }
        return LootFilters.isEmpty() ? out : LootFilters.applyToInventory(out, table, params);
    }

    public static List<ItemStack> roll(LootTable table, LootParams params, long containerSeed) {
        List<ItemStack> items = table.getRandomItems(params, effectiveSeed(containerSeed));
        if (!LootFilters.isEmpty()) {
            LootFilters.apply(items, table, params);
        }
        return items;
    }

    public static void triggerGenerateLoot(ServerPlayer player, ResourceKey<LootTable> lootTable) {
        CriteriaTriggers.GENERATE_LOOT.trigger(player, lootTable);
    }

    private LootRoller() {}
}
