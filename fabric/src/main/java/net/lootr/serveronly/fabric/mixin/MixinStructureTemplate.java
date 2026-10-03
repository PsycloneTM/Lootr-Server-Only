package net.lootr.serveronly.fabric.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.HolderLookup;
import net.lootr.serveronly.fabric.data.LootStateStripper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.lootr.serveronly.fabric.registry.ItemFrameMarker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Fabric twin of the NeoForge {@code MixinStructureTemplate}; read that class
 * for the full reasoning. In short: hook the direct call to
 * {@code createEntityIgnoreException} inside the named placement method, tag the
 * vanilla frame it returns, and never name the compiler-indexed lambda.
 * <p>
 * Fabric runs vanilla's {@code StructureTemplate}, where that method is still
 * called {@code placeEntities} (NeoForge renames it with its own patch).
 * <p>
 * <b>This also fixes a latent Fabric bug in the previous version.</b> It named
 * only Mojang-style lambdas ({@code lambda$placeEntities$5}). A real Fabric
 * runtime uses intermediary names, where that lambda is {@code method_17917};
 * upstream lists that name explicitly and the previous copy here did not, and
 * Loom's refmap cannot remap a lambda name. Naming a real method and a real
 * call lets the refmap translate both to intermediary at build time.
 * <p>
 * <b>Status:</b> the Fabric module builds and boots a dedicated server, but a
 * mixin is applied only when its target class first loads, so this one is not
 * proven until structure placement has actually run. Specifically unconfirmed:
 * that Loom's annotation processor emits refmap entries for these two strings.
 * If Mixin reports it cannot find the target on Fabric, check the generated
 * {@code lootr_serveronly.refmap.json} first.
 */
@Mixin(StructureTemplate.class)
public class MixinStructureTemplate {

    @WrapOperation(
            method = "placeEntities",
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

    /**
     * Structure saving: a looted container saved into a structure must not carry this mod's per-player state
     * into every copy. See {@link LootStateStripper}. The loot table stays so the copy is a loot container again.
     */
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

    /** Same for entities saved into a template (a chest minecart's looted state, an item frame's takers). */
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
