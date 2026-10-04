package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.OpenTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trapped chests power redstone from {@code ChestBlockEntity.getOpenCount},
 * which reads the real block entity's own opener counter. Loot chests never
 * touch that counter (see {@link OpenTracker}), so report the loot-menu opener
 * count too. Static method, so the handler is static. Not verified by a build.
 */
@Mixin(ChestBlockEntity.class)
public abstract class MixinChestBlockEntity {

    @Inject(method = "getOpenCount", at = @At("RETURN"), cancellable = true)
    private static void lootr$countScopedOpeners(BlockGetter getter, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (getter instanceof Level level) {
            int scoped = OpenTracker.count(level, pos);
            if (scoped > cir.getReturnValue()) {
                cir.setReturnValue(scoped);
            }
        }
    }
}
