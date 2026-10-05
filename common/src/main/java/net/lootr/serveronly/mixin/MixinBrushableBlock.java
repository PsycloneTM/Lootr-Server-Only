package net.lootr.serveronly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.interaction.ContainerProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BrushableBlock.class)
public abstract class MixinBrushableBlock {
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/FallingBlock;isFree(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean lootr$selfSupport(BlockState below, Operation<Boolean> original,
                                      @Local(argsOnly = true) ServerLevel level,
                                      @Local(argsOnly = true) BlockPos pos) {
        if (LootrSettings.brushablesSelfSupport() && ContainerProtection.isManagedAt(level, pos)) {
            return false;
        }
        return original.call(below);
    }
}
