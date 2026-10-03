package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.Decay;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Drives {@link Decay}'s once-a-second sweep. Fabric API's tick event lives in
 * {@code fabric-lifecycle-events-v1}, which this project avoids depending on, so
 * hook the end of {@code MinecraftServer.tickServer} directly. Not verified by a build.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void lootr$decaySweep(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        Decay.onServerTick((MinecraftServer) (Object) this);
    }
}
