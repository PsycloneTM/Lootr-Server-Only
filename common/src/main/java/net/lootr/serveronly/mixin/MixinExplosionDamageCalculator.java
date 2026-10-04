package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
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

@Mixin(ExplosionDamageCalculator.class)
public abstract class MixinExplosionDamageCalculator {
    @Inject(method = "getBlockExplosionResistance", at = @At("RETURN"), cancellable = true)
    private void lootr$blastResistant(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state,
                                      FluidState fluid, CallbackInfoReturnable<Optional<Float>> cir) {
        Optional<Float> raised = LootrHooks.blastResistantResistance(cir.getReturnValue(), reader, pos, state);
        if (raised != cir.getReturnValue()) {
            cir.setReturnValue(raised);
        }
    }
}
