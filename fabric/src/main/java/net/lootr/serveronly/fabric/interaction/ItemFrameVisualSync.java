package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.registry.ItemFrameMarker;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.lootr.serveronly.mixin.AccessorItemFrame;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ItemFrameVisualSync {

    public static void hideIfLooted(ServerPlayer player, ItemFrame frame) {
        if (!ItemFrameMarker.isMarked(frame) || frame.getItem().isEmpty()) {
            return;
        }

        var stored = frame.getAttachedOrElse(ModAttachments.LOOT_STATE, new net.minecraft.nbt.CompoundTag());
        LootrLootState state = new LootrLootState(27);
        state.load(stored, player.level().registryAccess());
        if (!state.hasGeneratedFor(TeamResolver.resolve(player))) {
            return;
        }

        sendHiddenItem(player, frame);
    }

    public static void sendHiddenItem(ServerPlayer player, ItemFrame frame) {
        sendItem(player, ItemStack.EMPTY, frame);
    }

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
