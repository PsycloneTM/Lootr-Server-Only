package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class MixinExplosion {
    @Shadow @Final private Level level;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void lootr$sparseLootContainers(boolean spawnParticles, CallbackInfo ci) {
        Explosion self = (Explosion) (Object) this;
        if (ContainerProtection.blastSpares()) {
            self.getToBlow().removeIf(pos -> ContainerProtection.isManagedAt(this.level, pos));
        }
    }
}
