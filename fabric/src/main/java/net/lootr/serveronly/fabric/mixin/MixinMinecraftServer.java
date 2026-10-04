package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.interaction.Decay;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void lootr$decaySweep(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        Decay.onServerTick((MinecraftServer) (Object) this);
    }
}
