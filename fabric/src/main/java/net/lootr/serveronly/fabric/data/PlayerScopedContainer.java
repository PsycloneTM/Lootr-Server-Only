package net.lootr.serveronly.fabric.data;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    public PlayerScopedContainer(LootrLootState state, UUID playerId, Consumer<NonNullList<ItemStack>> onChanged,
                                 Predicate<Player> stillValid, Consumer<Player> onOpen, Consumer<Player> onClose) {
        this.onOpen = onOpen;
        this.onClose = onClose;
        this.state = state;
        this.playerId = playerId;
        this.onChanged = onChanged;
        this.stillValid = stillValid;
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
        return stillValid.test(player);
    }

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
