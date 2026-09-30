package net.lootr.serveronly.fabric.registry;

import net.lootr.serveronly.fabric.config.LootrConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric-side copy of the NeoForge side's
 * {@code net.lootr.serveronly.registry.ModLootTags}. This class has no
 * NeoForge or Fabric API imports at all - loader-agnostic vanilla logic -
 * so, like {@link net.lootr.serveronly.fabric.data.LootrLootState} and
 * {@link net.lootr.serveronly.fabric.data.PlayerScopedContainer}, it is
 * copied verbatim rather than reimplemented. See the NeoForge side's
 * javadoc for the full rationale behind the "any container with an assigned
 * loot table is a Lootr container" detection strategy and its known
 * limitation (no config layer yet to opt individual containers out).
 */
public final class ModLootTags {

    public static boolean isLootrEnabled(RandomizableContainerBlockEntity entity) {
        return isTableEnabled(getAssignedLootTable(entity));
    }

    /** Single gate for "should this loot table be converted"; see LootrConfig#isLootTableEnabled. */
    public static boolean isTableEnabled(@Nullable ResourceKey<LootTable> table) {
        return LootrConfig.isLootTableEnabled(table);
    }

    @Nullable
    public static ResourceKey<LootTable> getAssignedLootTable(RandomizableContainerBlockEntity entity) {
        return entity.getLootTable();
    }

    private ModLootTags() {}
}
