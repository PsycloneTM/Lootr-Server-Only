package net.lootr.serveronly.registry;

import net.lootr.serveronly.config.LootrConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * How this mod decides "is this particular container instance a Lootr
 * container" - chest, trapped chest, barrel, or shulker box alike.
 * <p>
 * Upstream Lootr answers this question by block IDENTITY - a lootr:chest is
 * always a Lootr chest, full stop, because it's a different block. We can't
 * do that (see ModAttachments doc comment for why), so instead we key off
 * whatever placed the loot: any container whose loot table (vanilla's own
 * {@code setLootTable}/structure-generation mechanism) is assigned MUST be
 * treated as a Lootr container at the moment it is first opened, since
 * ordinary vanilla containers already consume their loot table on first open
 * and go "dumb" - hence Lootr's per-player generation slots naturally into
 * the exact same lifecycle point vanilla uses.
 * <p>
 * {@code getLootTable()} lives on {@link RandomizableContainerBlockEntity},
 * the common vanilla superclass of {@code ChestBlockEntity},
 * {@code BarrelBlockEntity}, and {@code ShulkerBoxBlockEntity} alike (and
 * transitively {@code TrappedChestBlockEntity}, which extends
 * {@code ChestBlockEntity}) - confirmed against upstream Lootr's own
 * {@code LootrBarrelBlockEntity}/{@code LootrShulkerBlockEntity}, which
 * extend this exact vanilla class directly. Generalizing this method's
 * parameter from {@code ChestBlockEntity} to this common supertype is what
 * lets {@link net.lootr.serveronly.interaction.ContainerInteractionHandler}
 * share one code path across all four container kinds instead of
 * duplicating this check per type.
 * <p>
 * This is the cleanest, lowest-mixin-count answer, but it does mean: a
 * player who places a container by hand and drops a loot table onto it (e.g.
 * via NBT edits, worldedit, or a datapack function) also gets "free" Lootr
 * behavior - which upstream Lootr's config toggles (per-block, per-dimension
 * opt-in/opt-out) allow controlling directly instead. A full port needs a
 * config layer mirroring upstream's `LootrConfig`, not implemented in this
 * proof-of-concept - see design doc, open questions.
 */
public final class ModLootTags {

    public static boolean isLootrEnabled(RandomizableContainerBlockEntity entity) {
        return isTableEnabled(getAssignedLootTable(entity));
    }

    /** Single gate for "should this loot table be converted"; see {@link LootrConfig#isLootTableEnabled}. */
    public static boolean isTableEnabled(@Nullable ResourceKey<LootTable> table) {
        return LootrConfig.isLootTableEnabled(table);
    }

    @Nullable
    public static ResourceKey<LootTable> getAssignedLootTable(RandomizableContainerBlockEntity entity) {
        return entity.getLootTable();
    }

    private ModLootTags() {}
}
