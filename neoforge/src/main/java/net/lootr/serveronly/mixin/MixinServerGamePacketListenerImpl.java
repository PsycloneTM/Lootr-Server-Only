package net.lootr.serveronly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.interaction.ContainerProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @WrapOperation(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean lootr$bypassSpawnProtection(ServerLevel level, Player player, BlockPos pos, Operation<Boolean> original) {
        boolean allowed = original.call(level, player, pos);
        if (allowed || !LootrConfig.BYPASS_SPAWN_PROTECTION.get()) {
            return allowed;
        }
        if (player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
            return false;
        }
        return level.getWorldBorder().isWithinBounds(pos) && ContainerProtection.isManagedAt(level, pos);
    }
}
