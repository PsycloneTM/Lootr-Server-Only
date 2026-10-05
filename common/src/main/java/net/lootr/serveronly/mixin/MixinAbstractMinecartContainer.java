package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecartContainer.class)
public abstract class MixinAbstractMinecartContainer {
    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void lootr$getItem(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (LootrHooks.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "removeItem", at = @At("HEAD"), cancellable = true)
    private void lootr$removeItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (LootrHooks.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void lootr$removeItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (LootrHooks.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void lootr$setItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (LootrHooks.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "clearContent", at = @At("HEAD"), cancellable = true)
    private void lootr$clearContent(CallbackInfo ci) {
        if (LootrHooks.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setLootTable(Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"), cancellable = true)
    private void lootr$keepLootTable(@Nullable ResourceKey<LootTable> table, CallbackInfo ci) {
        if (table == null && !LootrHooks.isClearingCartTable()
                && LootrHooks.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            ci.cancel();
        }
    }
}
