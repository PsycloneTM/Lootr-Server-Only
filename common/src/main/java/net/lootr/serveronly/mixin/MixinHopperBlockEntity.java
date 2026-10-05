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
