package net.lootr.serveronly.fabric.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads {@code BrushableBlockEntity#lootTable}, which vanilla keeps private with no getter. */
@Mixin(BrushableBlockEntity.class)
public interface AccessorBrushableBlockEntity {
    @Nullable
    @Accessor("lootTable")
    ResourceKey<LootTable> lootr$getLootTable();
}
