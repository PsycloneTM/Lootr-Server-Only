package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ContainerProtection;
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

/**
 * Chest-minecart isolation: the entity twin of {@code MixinRandomizableContainerBlockEntity}.
 * <p>
 * A chest minecart is an entity, so the block-entity mixin never sees it. Without this, a hopper next to a loot
 * cart reaches vanilla's {@code ContainerEntity} methods, which unpack the loot table into the cart's one shared
 * inventory and clear the table: every player would then see that same shared chest.
 * <p>
 * For a managed loot cart the five methods declared on {@code AbstractMinecartContainer} act like an empty, closed
 * container, so vanilla never reaches the unpack code through them. {@code isEmpty} is not declared on this class
 * (it is an inherited interface default), so it cannot be targeted by name; it unpacks through
 * {@code ContainerEntity.unpackChestVehicleLootTable}, which does two things this mixin makes harmless: it clears
 * the loot table (the single-argument {@code setLootTable(null)} is cancelled here) and fills the inventory with
 * {@code setItem} (cancelled here). So even that path leaves the cart exactly as it was.
 * <p>
 * Consequences: hoppers and droppers see nothing in a loot cart and cannot insert into it, and a loot cart that is
 * broken drops no shared loot (its real inventory is empty by design). Applies only to {@code MinecartChest} with a
 * loot table that this mod converts, on the server. Targets are named methods, so a wrong name stops the server at
 * startup ({@code defaultRequire = 1}). Not verified by a build.
 */
@Mixin(AbstractMinecartContainer.class)
public abstract class MixinAbstractMinecartContainer {

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void lootr$getItem(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerProtection.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "removeItem", at = @At("HEAD"), cancellable = true)
    private void lootr$removeItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerProtection.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void lootr$removeItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerProtection.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void lootr$setItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (ContainerProtection.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "clearContent", at = @At("HEAD"), cancellable = true)
    private void lootr$clearContent(CallbackInfo ci) {
        if (ContainerProtection.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            ci.cancel();
        }
    }

    /** Only the null overload: loading a saved cart and structure placement set a real table, which must go through. */
    @Inject(method = "setLootTable(Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"), cancellable = true)
    private void lootr$keepLootTable(@Nullable ResourceKey<LootTable> table, CallbackInfo ci) {
        if (table == null && !ContainerProtection.isClearingCartTable()
                && ContainerProtection.blocksCartUnpack((AbstractMinecartContainer) (Object) this)) {
            ci.cancel();
        }
    }
}
