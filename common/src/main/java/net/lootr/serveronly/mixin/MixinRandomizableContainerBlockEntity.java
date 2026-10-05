package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomizableContainerBlockEntity.class)
public abstract class MixinRandomizableContainerBlockEntity {
    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void lootr$isEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (LootrHooks.blocksUnpack((RandomizableContainerBlockEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void lootr$getItem(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (LootrHooks.blocksUnpack((RandomizableContainerBlockEntity) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "removeItem", at = @At("HEAD"), cancellable = true)
    private void lootr$removeItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (LootrHooks.blocksUnpack((RandomizableContainerBlockEntity) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void lootr$removeItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (LootrHooks.blocksUnpack((RandomizableContainerBlockEntity) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void lootr$setItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (LootrHooks.blocksUnpack((RandomizableContainerBlockEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
