package net.lootr.serveronly.mixin;

import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.interaction.LootrHooks;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class MixinItemFrame {

    @Inject(method = "survives", at = @At("HEAD"), cancellable = true)
    private void lootr$selfSupport(CallbackInfoReturnable<Boolean> cir) {
        if (LootrSettings.itemFramesSelfSupport() && LootrHooks.isManagedFrame((ItemFrame) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
