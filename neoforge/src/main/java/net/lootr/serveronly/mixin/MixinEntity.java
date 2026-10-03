package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.ItemFrameVisualSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(method = "startSeenByPlayer", at = @At("TAIL"))
    private void lootr$syncItemFrameVisualState(ServerPlayer player, CallbackInfo ci) {
        if ((Object) this instanceof ItemFrame frame) {
            ItemFrameVisualSync.hideIfLooted(player, frame);
        }
    }
}
