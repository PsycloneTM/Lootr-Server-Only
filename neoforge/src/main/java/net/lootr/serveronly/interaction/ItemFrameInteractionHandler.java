package net.lootr.serveronly.interaction;

import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class ItemFrameInteractionHandler {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ItemFrame frame)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!isLootrFrame(frame, level)) {
            return;
        }
        event.setCanceled(true);
        take(player, frame, level);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ItemFrame frame)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!isLootrFrame(frame, (ServerLevel) event.getLevel())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Hit this frame to take your own copy."), true);
        }
    }

    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (!event.getSource().isCreativePlayer() && ContainerProtection.isManagedEntity(event.getEntity(), event.getSource())) {
            event.setInvulnerable(true);
        }
    }

    private static boolean isLootrFrame(ItemFrame frame, ServerLevel level) {
        return LootrConfig.isDimensionEnabled(level.dimension())
                && ItemFrameMarker.isMarked(frame)
                && !frame.getItem().isEmpty();
    }

    private static void take(ServerPlayer player, ItemFrame frame, ServerLevel level) {
        LootrLootState state = frame.getData(ModAttachments.LOOT_STATE);
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

        ItemStack copy = framed.copyWithCount(1);
        player.getInventory().placeItemBackInInventory(copy);

        ItemFrameVisualSync.sendHiddenItem(player, frame);

        frame.playSound(frame.getRemoveItemSound(), 1.0F, 1.0F);
        OpenedAdvancements.award(player, OpenedAdvancements.Kind.ITEM_FRAME);
    }

    private ItemFrameInteractionHandler() {}
}
