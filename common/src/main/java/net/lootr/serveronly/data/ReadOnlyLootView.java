package net.lootr.serveronly.data;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * A frozen COPY of one player's (or team's) loot entry, for {@code /lootr open_as}. Deliberately not a
 * {@link PlayerScopedContainer}: that class edits the live entry in place, so an admin who took or moved an
 * item would change what the real player has left. This one holds its own copies of the stacks and ignores
 * every write, so nothing an admin does to it can reach the target's entry, and (because it is not a
 * {@code PlayerScopedContainer}) {@code Decay.viewers} does not count the admin as someone who has the
 * container open, so viewing never blocks a decay or refresh.
 * <p>
 * The menus that show it ({@code AdminCommands.ViewOnlyChestMenu} / {@code ViewOnlyShulkerMenu}) also swallow
 * every click, so the writes below are a second line of defense, not the first.
 */
public final class ReadOnlyLootView implements Container {
    private final NonNullList<ItemStack> snapshot;
    private final Predicate<Player> stillValid;

    /** @param source the live entry; it is copied here, never kept. */
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
        // view only
    }

    @Override
    public void setChanged() {
        // view only
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
        // view only
    }
}
