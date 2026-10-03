package net.lootr.serveronly.fabric.mixin;

import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code ItemFrame#fixed}, which is not public. Accessor interfaces
 * are the one kind of mixin class that ordinary code may reference (by
 * casting), which is how {@code ItemFrameMarker} uses it. Identical to the
 * NeoForge side's accessor of the same name; only the package differs.
 */
@Mixin(ItemFrame.class)
public interface AccessorItemFrame {
    @Accessor("fixed")
    boolean lootr$isFixed();

    @Accessor("DATA_ITEM")
    static net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> lootr$getDataItem() {
        throw new UnsupportedOperationException();
    }
}
