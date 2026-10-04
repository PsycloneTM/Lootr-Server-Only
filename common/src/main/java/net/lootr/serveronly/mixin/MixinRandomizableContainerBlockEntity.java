package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops vanilla resolving a loot container's table behind Lootr's back.
 * <p>
 * Vanilla's {@code isEmpty/getItem/removeItem/removeItemNoUpdate/setItem} on
 * {@code RandomizableContainerBlockEntity} each call {@code unpackLootTable(null)}
 * first. So a hopper, a comparator, or breaking the block would roll ONE shared
 * copy of the loot into the real inventory and clear the loot table, ending
 * per-player loot for that container. For a managed container these methods
 * now behave as an empty container instead and never reach vanilla's unpack.
 * Players never hit this path: {@code ContainerInteractionHandler} cancels the
 * open and hands them a per-player container.
 * <p>
 * Side effect: hoppers see nothing in a loot chest and comparators read 0.
 * The five methods are declared in this class (not inherited), so the names
 * below are stable. Not verified by a build.
 */
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
