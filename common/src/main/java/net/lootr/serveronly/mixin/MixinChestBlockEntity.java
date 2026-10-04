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
