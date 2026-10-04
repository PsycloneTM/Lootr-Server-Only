package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.lootr.serveronly.fabric.interaction.ItemFrameVisualSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void lootr$protectLootEntities(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!source.isCreativePlayer() && ContainerProtection.isManagedEntity((Entity) (Object) this, source)) {
            cir.setReturnValue(true);
        }
    }
    @Inject(method = "startSeenByPlayer", at = @At("TAIL"))
    private void lootr$syncItemFrameVisualState(ServerPlayer player, CallbackInfo ci) {
        if ((Object) this instanceof ItemFrame frame) {
            ItemFrameVisualSync.hideIfLooted(player, frame);
        }
    }

}
