package net.lootr.serveronly.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.lootr.serveronly.fabric.registry.ItemFrameMarker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Marks the End City ship's Elytra frame. Identical in intent and injection
 * target to the NeoForge side's mixin of the same name - only the package
 * differs. See {@link ItemFrameMarker}'s javadoc for the full rationale.
 * <p>
 * Vanilla builds that frame in code inside {@code handleDataMarker} (the
 * "Elytra" data marker) rather than storing it in the template, so
 * {@link MixinStructureTemplate} never sees it. {@code handleDataMarker} also
 * spawns the ship's Shulker through the same call, so the hook only reacts to
 * an {@link ItemFrame} and lets everything else through untouched.
 * <p>
 * Unlike upstream, this does not cancel vanilla and spawn a replacement
 * entity: it lets vanilla spawn its own frame and tags it. Not verified by a
 * build here.
 */
@Mixin(EndCityPieces.EndCityPiece.class)
public class MixinEndCityPiece {

    @WrapOperation(
            method = "handleDataMarker",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean lootr$markElytraItemFrame(ServerLevelAccessor level, Entity entity, Operation<Boolean> original) {
        if (entity instanceof ItemFrame frame) {
            if (ItemFrameMarker.convertElytraToChest(level, frame)) {
                return true; // a chest replaced the frame; vanilla's frame is never added
            }
            ItemFrameMarker.onEndCityElytraFrameSpawned(frame);
        }
        return original.call(level, entity);
    }
}
