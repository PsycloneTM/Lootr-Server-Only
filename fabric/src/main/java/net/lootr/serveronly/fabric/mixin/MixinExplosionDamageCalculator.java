package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * {@code blast_resistant}, matching upstream Lootr: while the option is on, a managed loot container
 * counts as having a blast resistance of 16 for the explosion's own ray calculation.
 * <p>
 * Upstream gets this by overriding {@code Block.getExplosionResistance} on its custom blocks. This mod keeps
 * vanilla's blocks, so the same number is substituted where vanilla asks for it: the per-block resistance
 * lookup that every ray goes through. Rays therefore lose strength against the container exactly as they
 * would upstream (it also shields what is behind it), and it is destroyed only by a ray strong enough to beat
 * resistance 16, instead of by a flat explosion-power cutoff.
 * <p>
 * Overrides that do not call super (the wind charge calculator, which destroys no blocks) are unaffected.
 * Not verified by a build.
 */
@Mixin(ExplosionDamageCalculator.class)
public abstract class MixinExplosionDamageCalculator {
    @Inject(method = "getBlockExplosionResistance", at = @At("RETURN"), cancellable = true)
    private void lootr$blastResistant(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state,
                                      FluidState fluid, CallbackInfoReturnable<Optional<Float>> cir) {
        Optional<Float> raised = ContainerProtection.blastResistantResistance(cir.getReturnValue(), reader, pos, state);
        if (raised != cir.getReturnValue()) {
            cir.setReturnValue(raised);
        }
    }
}
