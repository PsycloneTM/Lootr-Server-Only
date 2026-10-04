package net.lootr.serveronly.mixin;

import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemFrame.class)
public interface AccessorItemFrame {
    @Accessor("fixed")
    boolean lootr$isFixed();

    @Accessor("DATA_ITEM")
    static net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> lootr$getDataItem() {
        throw new UnsupportedOperationException();
    }
}
