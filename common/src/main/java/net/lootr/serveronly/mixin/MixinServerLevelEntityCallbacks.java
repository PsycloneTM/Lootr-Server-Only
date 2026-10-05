package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.CartScheduler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers chest minecarts with {@link CartScheduler} as they enter a level. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class MixinServerLevelEntityCallbacks {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void lootr$scheduleCart(Entity entity, CallbackInfo ci) {
        CartScheduler.onEntityTracked(entity);
    }
}
