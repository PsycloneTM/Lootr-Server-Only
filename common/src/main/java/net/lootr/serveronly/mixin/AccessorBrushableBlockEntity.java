package net.lootr.serveronly.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrushableBlockEntity.class)
public interface AccessorBrushableBlockEntity {
    @Nullable
    @Accessor("lootTable")
    ResourceKey<LootTable> lootr$getLootTable();
}
