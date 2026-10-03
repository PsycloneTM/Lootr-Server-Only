package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code power_comparators}: what a comparator reads from a loot container.
 * <p>
 * {@link MixinRandomizableContainerBlockEntity} makes a managed container look empty, so vanilla's
 * comparator reading (which counts items) would always be 0. Some structure traps are wired to a
 * comparator on a chest and fire when it reads 0, so as in upstream Lootr the default is a fixed
 * output of 1; set {@code power_comparators} to false for 0. Applies to chests, trapped chests,
 * barrels and shulker boxes whose loot table is converted by this mod.
 * <p>
 * <b>Verified against upstream:</b> upstream Lootr's own {@code redstone/MixinAbstractContainerMenu} (built
 * against 1.21.1) injects at HEAD of the same static {@code getRedstoneSignalFromContainer} and returns 1 when
 * its {@code power_comparators} is true and 0 when false, only for its own containers. Same behavior here.
 * <p>
 * {@code require = 0} is kept anyway because this module has not been built against a real jar: if the
 * injection ever fails to apply it is skipped (comparators then keep reading 0) instead of crashing
 * startup. Check the log for a Mixin warning.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu {

    @Inject(method = "getRedstoneSignalFromContainer", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lootr$comparatorPower(@Nullable Container container, CallbackInfoReturnable<Integer> cir) {
        if (container instanceof RandomizableContainerBlockEntity be && ContainerProtection.blocksUnpack(be)) {
            cir.setReturnValue(LootrConfig.powerComparators() ? 1 : 0);
        }
    }
}
