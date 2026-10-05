package net.lootr.serveronly.interaction;

import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class ItemFrameInteractionHandler {
    public static boolean attack(ServerPlayer player, ItemFrame frame, ServerLevel level, boolean take) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!isLootrFrame(frame, level)) {
            return false;
        }
        if (take) {
            take(player, frame, level);
        }
        return true;
    }

    public static boolean use(ServerPlayer player, ItemFrame frame, ServerLevel level, boolean mainHand) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!isLootrFrame(frame, level)) {
            return false;
        }
        if (mainHand) {
            player.displayClientMessage(Component.literal("Hit this frame to take your own copy."), true);
        }
        return true;
    }

    private static boolean isLootrFrame(ItemFrame frame, ServerLevel level) {
        return LootrSettings.isDimensionEnabled(level.dimension())
                && ItemFrameMarker.isMarked(frame)
                && !frame.getItem().isEmpty();
    }

    private static void take(ServerPlayer player, ItemFrame frame, ServerLevel level) {
        LootrLootState state = LootStateStore.getOrCreate(frame, level);

        UUID lootKey = TeamResolver.resolve(player);
        if (state.hasGeneratedFor(lootKey)) {
            return;
        }
        ItemStack framed = frame.getItem();
        if (framed.isEmpty()) {
            return;
        }

        state.setContents(lootKey, NonNullList.withSize(state.getContainerSize(), ItemStack.EMPTY));
        state.markFirstGeneratedIfAbsent(level.getGameTime());
        LootStateStore.save(frame, state, level);

        player.getInventory().placeItemBackInInventory(framed.copyWithCount(1));

        ItemFrameVisualSync.sendHiddenItem(player, frame);

        frame.playSound(frame.getRemoveItemSound(), 1.0F, 1.0F);
        OpenedAdvancements.award(player, OpenedAdvancements.Kind.ITEM_FRAME);
    }

    private ItemFrameInteractionHandler() {}
}
