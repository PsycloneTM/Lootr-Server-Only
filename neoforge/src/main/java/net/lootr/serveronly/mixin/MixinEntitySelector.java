package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.ContainerProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps a managed loot chest minecart out of hopper candidacy altogether.
 * <p>
 * {@code HopperBlockEntity} finds container entities with {@code EntitySelector.CONTAINER_ENTITY_SELECTOR}
 * (an entity that is a {@code Container} and alive). {@code MixinAbstractMinecartContainer} already makes a
 * loot cart behave as an empty, closed container once a hopper has it, but a hopper (or hopper minecart)
 * would still find it every tick, ask it questions, and find nothing. Making the selector's {@code isAlive()}
 * check report false for a managed cart removes it from the candidate list instead.
 * <p>
 * Same hook as upstream Lootr's {@code MixinEntitySelector}; the lambda names are the ones it uses for this
 * Minecraft version ({@code lambda$static$2} in Mojang names, {@code method_5914} in Fabric's intermediary),
 * so a Minecraft update that reorders the lambdas in {@code EntitySelector} needs them rechecked. The test is
 * the same one the cart mixin uses ({@code blocksCartUnpack}), so only a cart that still has an enabled loot
 * table is affected. Not verified by a build.
 */
@Mixin(EntitySelector.class)
public abstract class MixinEntitySelector {
    @WrapOperation(method = {"lambda$static$2", "method_5914"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isAlive()Z"))
    private static boolean lootr$excludeManagedCart(Entity instance, Operation<Boolean> original) {
        if (instance instanceof AbstractMinecartContainer cart && ContainerProtection.blocksCartUnpack(cart)) {
            return false;
        }
        return original.call(instance);
    }
}
