package net.lootr.serveronly.fabric.registry;

import net.lootr.serveronly.fabric.config.LootrConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

public final class ModLootTags {

    public static boolean isLootrEnabled(RandomizableContainerBlockEntity entity) {
        return isTableEnabled(getAssignedLootTable(entity));
    }

    public static boolean isTableEnabled(@Nullable ResourceKey<LootTable> table) {
        return LootrConfig.isLootTableEnabled(table);
    }

    @Nullable
    public static ResourceKey<LootTable> getAssignedLootTable(RandomizableContainerBlockEntity entity) {
        return entity.getLootTable();
    }

    private ModLootTags() {}
}
