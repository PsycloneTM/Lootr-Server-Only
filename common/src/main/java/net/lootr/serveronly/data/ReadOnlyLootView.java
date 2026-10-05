package net.lootr.serveronly.data;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public final class ReadOnlyLootView implements Container {
    private final NonNullList<ItemStack> snapshot;
    private final Predicate<Player> stillValid;

    public ReadOnlyLootView(NonNullList<ItemStack> source, int size, Predicate<Player> stillValid) {
        this.snapshot = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size && i < source.size(); i++) {
            snapshot.set(i, source.get(i).copy());
        }
        this.stillValid = stillValid;
    }

    @Override
    public int getContainerSize() {
        return snapshot.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : snapshot) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return snapshot.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid.test(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return false;
    }

    @Override
    public void clearContent() {
    }
}
