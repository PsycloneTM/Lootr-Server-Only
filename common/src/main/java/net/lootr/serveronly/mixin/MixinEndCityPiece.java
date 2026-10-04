package net.lootr.serveronly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCityPieces.EndCityPiece.class)
public class MixinEndCityPiece {
    @WrapOperation(
            method = "handleDataMarker",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean lootr$markElytraItemFrame(ServerLevelAccessor level, Entity entity, Operation<Boolean> original) {
        if (entity instanceof ItemFrame frame) {
            if (ItemFrameMarker.convertElytraToChest(level, frame)) {
                return true;
            }
            ItemFrameMarker.onEndCityElytraFrameSpawned(frame);
        }
        return original.call(level, entity);
    }
}
