package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.BrushableLoot;
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

/**
 * Per-player brushable loot; see {@link BrushableLoot}. Three hooks on
 * vanilla's {@code BrushableBlockEntity}:
 * <ul>
 *   <li>{@code brush}: players who already looted the block make no progress.</li>
 *   <li>{@code unpackLootTable}: cancelled for managed blocks so vanilla never
 *       rolls the shared item nor clears the loot table (it must survive for
 *       the next player).</li>
 *   <li>{@code brushingCompleted}: replaced, so the block is never turned into
 *       sand/gravel and the loot goes to the brusher only.</li>
 * </ul>
 * All three target named methods and private fields; a wrong name fails at
 * startup ({@code defaultRequire = 1}). Not verified by a build.
 */
@Mixin(BrushableBlockEntity.class)
public abstract class MixinBrushableBlockEntity {

    @Shadow @Nullable private ResourceKey<LootTable> lootTable;
    @Shadow private int brushCount;
    @Shadow @Nullable private Direction hitDirection;

    @Inject(method = "brush", at = @At("HEAD"), cancellable = true)
    private void lootr$blockRepeatBrushing(long gameTime, Player player, Direction direction,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (BrushableLoot.alreadyLooted((BrushableBlockEntity) (Object) this, this.lootTable, player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void lootr$keepLootTable(Player player, CallbackInfo ci) {
        if (BrushableLoot.isManaged((BrushableBlockEntity) (Object) this, this.lootTable)) {
            ci.cancel();
        }
    }

    @Inject(method = "brushingCompleted", at = @At("HEAD"), cancellable = true)
    private void lootr$perPlayerCompletion(Player player, CallbackInfo ci) {
        BrushableBlockEntity self = (BrushableBlockEntity) (Object) this;
        if (BrushableLoot.isManaged(self, this.lootTable)) {
            BrushableLoot.complete(self, this.lootTable, player);
            this.brushCount = 0;
            this.hitDirection = null;
            ci.cancel();
        }
    }
}
