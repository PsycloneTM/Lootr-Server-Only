package net.lootr.serveronly.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code brushables_self_support}: a loot suspicious block skips its "is there air below me"
 * falling check, so removing the block under it no longer drops it. Wraps the single
 * {@code FallingBlock.isFree} call in {@code tick} (the other call, in {@code animateTick},
 * is client-side particles and is not touched). Same target upstream uses.
 * Not verified by a build.
 */
@Mixin(BrushableBlock.class)
public abstract class MixinBrushableBlock {

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/FallingBlock;isFree(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean lootr$selfSupport(BlockState below, Operation<Boolean> original,
                                      @Local(argsOnly = true) ServerLevel level,
                                      @Local(argsOnly = true) BlockPos pos) {
        if (LootrConfig.brushablesSelfSupport() && ContainerProtection.isManagedAt(level, pos)) {
            return false;
        }
        return original.call(below);
    }
}
