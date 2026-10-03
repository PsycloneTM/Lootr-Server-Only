package net.lootr.serveronly.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code bypass_spawn_protection}: lets non-operators USE a loot container inside the
 * server's spawn-protection area, as upstream Lootr does. Vanilla's
 * {@code handleUseItemOn} asks {@code ServerLevel.mayInteract} before doing anything, which
 * would otherwise stop the right-click before this mod's handler ever sees it.
 * <p>
 * Narrow on purpose: only a managed loot block, only when the click is a use (a sneaking
 * player holding something is placing a block against it, which stays blocked), and the
 * world border is still respected. Breaking is a different packet and is not affected.
 * Same target upstream wraps. Not verified by a build.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean lootr$bypassSpawnProtection(ServerLevel level, Player player, BlockPos pos, Operation<Boolean> original) {
        boolean allowed = original.call(level, player, pos);
        if (allowed || !LootrConfig.bypassSpawnProtection()) {
            return allowed;
        }
        if (player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
            return false;
        }
        return level.getWorldBorder().isWithinBounds(pos) && ContainerProtection.isManagedAt(level, pos);
    }
}
