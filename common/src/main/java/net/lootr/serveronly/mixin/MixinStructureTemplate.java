package net.lootr.serveronly.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.HolderLookup;
import net.lootr.serveronly.data.LootStateStripper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(StructureTemplate.class)
public class MixinStructureTemplate {
    @WrapOperation(
            method = {"addEntitiesToWorld", "placeEntities"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;createEntityIgnoreException(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;"))
    private Optional<Entity> lootr$markStructureItemFrames(ServerLevelAccessor level, CompoundTag tag, Operation<Optional<Entity>> original) {
        Optional<Entity> created = original.call(level, tag);
        if (level instanceof WorldGenRegion) {
            created.ifPresent(entity -> {
                if (entity instanceof ItemFrame frame) {
                    ItemFrameMarker.onStructureFrameSpawned(frame);
                }
            });
        }
        return created;
    }

    @WrapOperation(
            method = "fillFromWorld",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;saveWithId(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag lootr$stripBlockEntityLootState(BlockEntity blockEntity, HolderLookup.Provider provider,
                                                        Operation<CompoundTag> original) {
        CompoundTag saved = original.call(blockEntity, provider);
        LootStateStripper.strip(saved);
        return saved;
    }

    @WrapOperation(
            method = "fillEntityList",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;save(Lnet/minecraft/nbt/CompoundTag;)Z"))
    private boolean lootr$stripEntityLootState(Entity entity, CompoundTag tag, Operation<Boolean> original) {
        boolean result = original.call(entity, tag);
        LootStateStripper.strip(tag);
        return result;
    }
}
