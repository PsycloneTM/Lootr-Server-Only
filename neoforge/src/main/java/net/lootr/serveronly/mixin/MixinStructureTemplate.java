package net.lootr.serveronly.mixin;

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

/**
 * Marks item frames that a structure template spawns during world generation.
 * <p>
 * <b>Why this hooks {@code createEntityIgnoreException} and not the lambda.</b>
 * An earlier version wrapped {@code addFreshEntityWithPassengers} inside the
 * synthetic lambda that places each saved entity, copying upstream Lootr's
 * target ({@code lambda$addEntitiesToWorld$5}). On a real FABRIC run that
 * target was reported as not found. A lambda's name carries a compiler-assigned
 * index ({@code $5}) that depends on how the class was decompiled and
 * recompiled, so it is the least stable thing in the class to name.
 * <p>
 * This hooks a <i>named</i> method's <i>direct</i> call instead:
 * <ul>
 *   <li>NeoForge's own patch of {@code StructureTemplate} (1.21.1 branch)
 *       replaces vanilla's {@code placeEntities} with
 *       {@code addEntitiesToWorld}; that method calls
 *       {@code createEntityIgnoreException(level, tag)} directly in its body,
 *       and the lambda only runs afterwards on the result.</li>
 *   <li>{@code createEntityIgnoreException} is
 *       {@code private static Optional<Entity> (ServerLevelAccessor, CompoundTag)}
 *       in Mojang mappings (confirmed against mappings.dev for 1.21).</li>
 * </ul>
 * The frame it returns has already been loaded from the template's NBT - item,
 * {@code Fixed} and {@code Invisible} included - and has not been added to the
 * world, so tagging it here has the same effect as tagging it later, and
 * nothing else can see it half-configured. {@code placeEntities} is listed as
 * a second candidate in case a NeoForge release ever drops its patch; Mixin
 * only fails if <i>none</i> of the listed methods exist.
 * <p>
 * <b>World generation only</b> ({@code level instanceof WorldGenRegion}). A
 * structure block, {@code /place} or a schematic printer places through the
 * real {@code ServerLevel} instead, and those are player-driven - see
 * {@link ItemFrameMarker}.
 * <p>
 * <b>Not verified by a build:</b> the method and call names are taken from
 * NeoForge's published patch and the Mojang-mapped docs, not from a compiled
 * run. If this still fails to apply, Mixin refuses to start the server and
 * names this class, which is deliberate - a silent no-op would leave every
 * frame unmarked with no sign why.
 */
@Mixin(StructureTemplate.class)
public class MixinStructureTemplate {

    @WrapOperation(
            method = {/* NeoForge patched */ "addEntitiesToWorld", /* vanilla, if the patch is ever dropped */ "placeEntities"},
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
}
