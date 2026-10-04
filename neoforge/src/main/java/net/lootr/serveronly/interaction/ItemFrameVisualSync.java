package net.lootr.serveronly.interaction;

import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.mixin.AccessorItemFrame;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Reproduces Lootr's per-player item-frame visuals without requiring a client mod.
 *
 * <p>The real ItemFrame entity keeps its actual framed item so other players can
 * still loot it. We send only the looting player a normal entity metadata update
 * whose DATA_ITEM is empty, so vanilla renders an empty frame for that player.</p>
 */
public final class ItemFrameVisualSync {

    public static void hideIfLooted(ServerPlayer player, ItemFrame frame) {
        if (!ItemFrameMarker.isMarked(frame) || frame.getItem().isEmpty()) {
            return;
        }
        LootrLootState state = frame.getData(ModAttachments.LOOT_STATE);
        if (!state.hasGeneratedFor(TeamResolver.resolve(player))) {
            return;
        }
        sendHiddenItem(player, frame);
    }

    public static void sendHiddenItem(ServerPlayer player, ItemFrame frame) {
        sendItem(player, ItemStack.EMPTY, frame);
    }

    /** Restores the server-side framed item in a client that previously saw it hidden. */
    public static void sendVisibleItem(ServerPlayer player, ItemFrame frame) {
        sendItem(player, frame.getItem().copy(), frame);
    }

    private static void sendItem(ServerPlayer player, ItemStack stack, ItemFrame frame) {
        SynchedEntityData.DataValue<ItemStack> value =
                SynchedEntityData.DataValue.create(AccessorItemFrame.lootr$getDataItem(), stack);
        player.connection.send(new ClientboundSetEntityDataPacket(frame.getId(), List.of(value)));
    }

    private ItemFrameVisualSync() {}
}
