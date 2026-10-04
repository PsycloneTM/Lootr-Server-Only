package net.lootr.serveronly.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;

public interface LootListener {

    default int priority() {
        return 0;
    }

    default String name() {
        return getClass().getName();
    }

    default void onLooted(ServerLevel level, Object holder, BlockPos pos, ServerPlayer looter, ResourceKey<LootTable> table) {
    }

    default void onDecaying(ServerLevel level, Object holder, BlockPos pos, ResourceKey<LootTable> table) {
    }

    default void onRefreshed(ServerLevel level, Object holder, BlockPos pos, ResourceKey<LootTable> table) {
    }
}
