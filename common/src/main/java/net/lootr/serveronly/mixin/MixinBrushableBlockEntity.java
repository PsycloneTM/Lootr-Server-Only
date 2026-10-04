package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrushableBlockEntity.class)
public abstract class MixinBrushableBlockEntity {

    @Shadow @Nullable private ResourceKey<LootTable> lootTable;
    @Shadow private int brushCount;
    @Shadow @Nullable private Direction hitDirection;

    @Inject(method = "brush", at = @At("HEAD"), cancellable = true)
    private void lootr$blockRepeatBrushing(long gameTime, Player player, Direction direction,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (LootrHooks.brushableAlreadyLooted((BrushableBlockEntity) (Object) this, this.lootTable, player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void lootr$keepLootTable(Player player, CallbackInfo ci) {
        if (LootrHooks.brushableIsManaged((BrushableBlockEntity) (Object) this, this.lootTable)) {
            ci.cancel();
        }
    }

    @Inject(method = "brushingCompleted", at = @At("HEAD"), cancellable = true)
    private void lootr$perPlayerCompletion(Player player, CallbackInfo ci) {
        BrushableBlockEntity self = (BrushableBlockEntity) (Object) this;
        if (LootrHooks.brushableIsManaged(self, this.lootTable)) {
            LootrHooks.brushableComplete(self, this.lootTable, player);
            this.brushCount = 0;
            this.hitDirection = null;
            ci.cancel();
        }
    }
}
