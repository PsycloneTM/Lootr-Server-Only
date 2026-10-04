package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * The second half of the hopper-versus-loot-cart defense, matching upstream Lootr's
 * {@code MixinHopperBlockEntity.getEntityContainer} wrapper.
 * <p>
 * {@code MixinEntitySelector} already keeps a managed loot cart out of the candidate list. This is the
 * backstop for anything that still returns one (another mod replacing the selector, say): a managed cart is
 * treated as "no container here". It also stops a {@code ClassCastException} from another mod that lets a
 * non-{@code Container} entity into the selector from crashing the block tick; that is logged once and the
 * hopper treats it as no container. Not verified by a build.
 */
@Mixin(HopperBlockEntity.class)
public abstract class MixinHopperBlockEntity {
    @Unique
    private static boolean lootr$loggedCastFailure;

    @WrapMethod(method = "getEntityContainer")
    private static Container lootr$guardEntityContainer(Level level, double x, double y, double z,
                                                        Operation<Container> original) {
        Container result;
        try {
            result = original.call(level, x, y, z);
        } catch (ClassCastException e) {
            if (!lootr$loggedCastFailure) {
                lootr$loggedCastFailure = true;
                net.lootr.serveronly.common.LootrServerOnlyConstants.LOGGER.error("Not caused by Lootr (Server-Only): another mod made HopperBlockEntity.getEntityContainer "
                        + "or EntitySelector.CONTAINER_ENTITY_SELECTOR return an entity that is not a Container. "
                        + "The hopper now ignores it so the tick does not crash; this message is shown once.", e);
            }
            return null;
        }
        if (result instanceof AbstractMinecartContainer cart && LootrHooks.blocksCartUnpack(cart)) {
            return null;
        }
        return result;
    }
}
