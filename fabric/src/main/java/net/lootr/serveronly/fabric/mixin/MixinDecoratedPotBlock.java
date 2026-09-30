package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla shatters a decorated pot when any projectile (arrow, trident,
 * snowball...) hits it: {@code onProjectileHit} sets the block cracked and
 * destroys it. For a loot pot that would remove it for every player. This
 * skips the vanilla body for pots this mod manages; the projectile itself
 * behaves normally (it sticks in or bounces off the pot).
 * <p>
 * Gated on {@code protect_containers}, like the rest of the protection code.
 * The method is declared exactly once in {@code DecoratedPotBlock}
 * (verified against the 1.21.1 sources). Not verified by a build.
 */
@Mixin(DecoratedPotBlock.class)
public abstract class MixinDecoratedPotBlock {

    @Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
    private void lootr$keepLootPots(Level level, BlockState state, BlockHitResult hit,
                                    Projectile projectile, CallbackInfo ci) {
        if (LootrConfig.protectContainers() && ContainerProtection.isManagedAt(level, hit.getBlockPos())) {
            ci.cancel();
        }
    }
}
