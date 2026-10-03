package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code item_frames_self_support}: a marked loot item frame reports that it always survives,
 * so removing the block it hangs on no longer pops it off the wall. {@code ItemFrame.survives}
 * is public and is what vanilla's periodic attachment check calls. Not verified by a build.
 */
@Mixin(ItemFrame.class)
public abstract class MixinItemFrame {

    @Inject(method = "survives", at = @At("HEAD"), cancellable = true)
    private void lootr$selfSupport(CallbackInfoReturnable<Boolean> cir) {
        if (LootrConfig.itemFramesSelfSupport() && ContainerProtection.isManagedFrame((ItemFrame) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
