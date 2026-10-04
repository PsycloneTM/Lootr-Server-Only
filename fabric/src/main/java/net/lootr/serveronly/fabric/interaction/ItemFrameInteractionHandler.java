package net.lootr.serveronly.fabric.interaction;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.registry.ItemFrameMarker;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ItemFrameInteractionHandler {

    public static void register() {
        AttackEntityCallback.EVENT.register(ItemFrameInteractionHandler::onAttackEntity);
        UseEntityCallback.EVENT.register(ItemFrameInteractionHandler::onUseEntity);
    }

    private static InteractionResult onAttackEntity(Player player, Level world, InteractionHand hand,
                                                    Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(entity instanceof ItemFrame frame)) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.isCreative() || serverPlayer.isSpectator()) {
            return InteractionResult.PASS;
        }
        ServerLevel level = (ServerLevel) world;
        if (!isLootrFrame(frame, level)) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            take(serverPlayer, frame, level);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand,
                                                 Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(entity instanceof ItemFrame frame)) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.isCreative() || serverPlayer.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!isLootrFrame(frame, (ServerLevel) world)) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            serverPlayer.displayClientMessage(Component.literal("Hit this frame to take your own copy."), true);
        }
        return InteractionResult.CONSUME;
    }

    private static boolean isLootrFrame(ItemFrame frame, ServerLevel level) {
        return LootrConfig.isDimensionEnabled(level.dimension())
                && ItemFrameMarker.isMarked(frame)
                && !frame.getItem().isEmpty();
    }

    private static void take(ServerPlayer player, ItemFrame frame, ServerLevel level) {
        HolderLookup.Provider provider = level.registryAccess();
        CompoundTag stored = frame.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag());
        LootrLootState state = new LootrLootState(27);
        state.load(stored, provider);

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
        CompoundTag tag = new CompoundTag();
        state.save(tag, provider);
        frame.setAttached(ModAttachments.LOOT_STATE, tag);

        player.getInventory().placeItemBackInInventory(framed.copyWithCount(1));

        ItemFrameVisualSync.sendHiddenItem(player, frame);

        frame.playSound(frame.getRemoveItemSound(), 1.0F, 1.0F);
        OpenedAdvancements.award(player, OpenedAdvancements.Kind.ITEM_FRAME);
    }

    private ItemFrameInteractionHandler() {}
}
