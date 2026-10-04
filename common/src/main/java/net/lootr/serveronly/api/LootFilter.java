package net.lootr.serveronly.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface LootFilter {
    int priority();

    String name();

    boolean mutate(List<ItemStack> items, Context context);

    record Context(ServerLevel level, @Nullable Entity looter, LootTable table, RandomSource random) {}
}
