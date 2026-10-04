package net.lootr.serveronly.mixin;

import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.interaction.LootrHooks;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu {
    @Inject(method = "getRedstoneSignalFromContainer", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lootr$comparatorPower(@Nullable Container container, CallbackInfoReturnable<Integer> cir) {
        if (container instanceof RandomizableContainerBlockEntity be && LootrHooks.blocksUnpack(be)) {
            cir.setReturnValue(LootrSettings.powerComparators() ? 1 : 0);
        }
    }
}
