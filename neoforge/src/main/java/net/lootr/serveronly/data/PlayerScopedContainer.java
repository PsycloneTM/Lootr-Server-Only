package net.lootr.serveronly.data;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A plain vanilla {@link Container}. This is the entire trick that makes the
 * "unique loot per player" mechanic work with zero client-side code: vanilla's
 * {@link net.minecraft.world.inventory.ChestMenu} (and ShulkerBoxMenu, etc.)
 * only ever ask the {@link Container} they were built with for items -
 * they don't care whether that container is "the real block" or a per-player
 * view over server-only data. A vanilla client rendering that menu has no
 * way to tell the difference.
 * <p>
 * One instance of this class is created per (block, player) pair, lazily,
 * the moment a specific player opens the container - see
 * LootrChestBlockEntity#openMenuFor.
 */
public class PlayerScopedContainer implements Container {
    private final LootrLootState state;
    private final UUID playerId;
    private final Consumer<NonNullList<ItemStack>> onChanged;
    private final Predicate<Player> stillValid;
    private final Consumer<Player> onOpen;
    private final Consumer<Player> onClose;
    private Object owner;
    private final NonNullList<ItemStack> live;

    public PlayerScopedContainer(LootrLootState state, UUID playerId, Consumer<NonNullList<ItemStack>> onChanged,
                                 Predicate<Player> stillValid) {
        this(state, playerId, onChanged, stillValid, null, null);
    }

    /**
     * {@code onOpen}/{@code onClose} (nullable) run when vanilla's menu calls
     * {@code startOpen}/{@code stopOpen}; used to animate the real block.
     */
    public PlayerScopedContainer(LootrLootState state, UUID playerId, Consumer<NonNullList<ItemStack>> onChanged,
                                 Predicate<Player> stillValid, Consumer<Player> onOpen, Consumer<Player> onClose) {
        this.onOpen = onOpen;
        this.onClose = onClose;
        this.state = state;
        this.playerId = playerId;
        this.onChanged = onChanged;
        this.stillValid = stillValid;
        // Work on a live, mutable copy; written back via setChanged().
        this.live = state.getContentsOrEmpty(playerId);
    }

    @Override
    public int getContainerSize() {
        return state.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : live) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return live.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = net.minecraft.world.ContainerHelper.removeItem(live, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(live, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        live.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        state.setContents(playerId, live);
        onChanged.accept(live);
    }

    @Override
    public boolean stillValid(Player player) {
        // Vanilla closes a menu the moment this returns false, so it must answer
        // "may this player still use the real container?" - distance, and that the
        // block or entity still exists - NOT "is this the key's owner?".
        // `playerId` is the LOOT key, which is a team's id when team loot is on, so
        // comparing it to player.getUUID() was always false for team members and
        // closed their menu the instant it opened; with team loot off it was always
        // true, so nothing ever checked range or that the chest still existed.
        return stillValid.test(player);
    }

    /** Tags this view with the block entity / entity it belongs to (used by decay to see who has it open). */
    public PlayerScopedContainer owner(Object owner) {
        this.owner = owner;
        return this;
    }

    public Object getOwner() {
        return owner;
    }

    @Override
    public void startOpen(Player player) {
        if (onOpen != null) {
            onOpen.accept(player);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (onClose != null) {
            onClose.accept(player);
        }
    }

    @Override
    public void clearContent() {
        live.clear();
        setChanged();
    }
}
