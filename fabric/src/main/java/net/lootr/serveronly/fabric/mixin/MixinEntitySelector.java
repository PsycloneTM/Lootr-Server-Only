package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Keeps a managed loot chest minecart out of hopper candidacy.
 * <p>
 * {@code HopperBlockEntity} finds container entities with {@code EntitySelector.CONTAINER_ENTITY_SELECTOR}.
 * Instead of targeting that selector's synthetic lambda by name (fragile: the name changes between versions and
 * the remapper cannot always resolve it), this appends a predicate to the field at the end of the static
 * initializer. Only a cart that {@code ContainerProtection.blocksCartUnpack} accepts is filtered out.
 */
@Mixin(EntitySelector.class)
public abstract class MixinEntitySelector {
    @Shadow
    @Final
    @Mutable
    public static Predicate<Entity> CONTAINER_ENTITY_SELECTOR;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void lootr$excludeManagedCart(CallbackInfo ci) {
        CONTAINER_ENTITY_SELECTOR = CONTAINER_ENTITY_SELECTOR.and(ContainerProtection::isNotManagedCart);
    }
}
