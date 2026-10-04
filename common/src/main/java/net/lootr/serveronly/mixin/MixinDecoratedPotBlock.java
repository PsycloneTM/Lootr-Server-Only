package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
import net.lootr.serveronly.config.LootrSettings;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DecoratedPotBlock.class)
public abstract class MixinDecoratedPotBlock {

    @Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
    private void lootr$keepLootPots(Level level, BlockState state, BlockHitResult hit,
                                    Projectile projectile, CallbackInfo ci) {
        if (LootrSettings.protectContainers() && LootrHooks.isManagedAt(level, hit.getBlockPos())) {
            ci.cancel();
        }
    }
}
