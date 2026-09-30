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

/**
 * Per-player loot for item frames (the End City elytra frame and any other
 * frame marked by {@link ItemFrameMarker}).
 * <p>
 * A frame is not a container: it holds exactly one item and never opens a
 * menu. Upstream Lootr models it the same way this does - the item shown in
 * the frame IS the loot, and the interaction is <i>hitting</i> the frame, not
 * right-clicking it. Each player (or team) may take one copy of the framed
 * item; the frame itself is never removed and its displayed item never
 * changes, so every other player still finds it intact.
 * <p>
 * Differences from the container handlers, all deliberate:
 * <ul>
 *   <li>Nothing is rolled from a loot table - the "loot" is a copy of what is
 *       already in the frame. (Upstream's placeholder table is empty too.)</li>
 *   <li>Frames never refresh. Upstream's item-frame type has
 *       {@code canRefresh() == false}, so {@code LootrConfig.REFRESH_TICKS}
 *       is intentionally ignored here.</li>
 *   <li>The frame is <b>marked</b> rather than detected: a vanilla frame has
 *       no loot table to key "is this a Lootr frame" off, and treating every
 *       item-holding frame as Lootr would let any player duplicate items out
 *       of anyone's base. See {@link ItemFrameMarker}.</li>
 * </ul>
 * <p>
 * <b>Unverified at time of writing (needs a compile + a real playtest):</b>
 * <ul>
 *   <li>The import package of {@code EntityInvulnerabilityCheckEvent}
 *       ({@code net.neoforged.neoforge.event.entity}) is inferred, not
 *       confirmed. The event itself is documented for 1.21.1 (added in
 *       NeoForge 21.0.31, fires for non-living entities), but no source I
 *       could reach printed its package. If the compiler reports it missing,
 *       fix the import first - that is the most likely first error here.
 *       Its {@code setInvulnerable(boolean)} and {@code getSource()} members
 *       are likewise from the documented shape.</li>
 *   <li>The {@code AttackEntityEvent} and {@code EntityInteract} members used
 *       ({@code getTarget}, {@code getEntity}, cancel semantics).
 *       {@code AttackEntityEvent} itself is confirmed to exist and to cancel
 *       the attack.</li>
 *   <li>A vanilla client predicts frame interaction locally, so whether
 *       cancelling the hit is reverted cleanly on the client was not
 *       confirmed.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class ItemFrameInteractionHandler {

    /** Hitting the frame is the "take" action, matching upstream. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ItemFrame frame)) {
            return;
        }
        // Creative players are left alone so an admin can still break a frame.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!isLootrFrame(frame, level)) {
            return;
        }
        // Whether or not this player can take anything, the hit must never
        // reach vanilla: vanilla would pop the item out of the frame for
        // everyone, destroying the shared state.
        event.setCanceled(true);
        take(player, frame, level);
    }

    /**
     * Right-click on a Lootr frame is swallowed entirely. Vanilla would use
     * it to rotate the item or, for a held item, swap it into the frame -
     * either of which would alter the state every other player relies on.
     * Cancelled for both hands, as the event fires once per hand.
     */
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
        // Only tell the player once per click, not once per hand.
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            // Plain text: a vanilla client has no lang file for this mod to resolve.
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Hit this frame to take your own copy."), true);
        }
    }

    /**
     * Makes a Lootr frame indestructible to everything except a creative
     * player. Cancelling the player's hit above only covers one way a frame
     * dies: an arrow, an explosion, a fire or a mob would each pop the item
     * out for everyone and end the frame for every other player. Upstream
     * Lootr's own frame defaults to invulnerable for every damage source
     * (its {@code isInvulnerableTo} ends in {@code return true}); this is the
     * server-only equivalent, done with the earliest damage hook NeoForge
     * offers, which also fires for non-living entities such as frames.
     * <p>
     * Only a creative player's own hit is let through, so an admin can still
     * remove a frame. The player-hit handler above never reaches here for a
     * survival player - it cancels first - so this is purely the non-player
     * and creative path.
     */
    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        // Covers marked frames AND loot chest minecarts; see
        // ContainerProtection.isManagedEntity. A creative player's own hit is
        // let through so an admin can still remove one.
        if (!event.getSource().isCreativePlayer() && ContainerProtection.isManagedEntity(event.getEntity())) {
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
            return; // already took theirs; the hit does nothing, quietly
        }
        ItemStack framed = frame.getItem();
        if (framed.isEmpty()) {
            return;
        }

        // Record "taken" BEFORE handing anything over, so a failure while
        // giving the item can never let the same player take it twice. The
        // stored entry is deliberately all-empty: for a frame the "loot" is
        // the framed item itself, so only the fact of having taken it needs
        // remembering. (Same all-empty-entry marker the pot handler uses.)
        state.setContents(lootKey, NonNullList.withSize(state.getContainerSize(), ItemStack.EMPTY));
        state.markFirstGeneratedIfAbsent(level.getGameTime());

        ItemStack copy = framed.copyWithCount(1);
        player.getInventory().placeItemBackInInventory(copy);

        frame.playSound(frame.getRemoveItemSound(), 1.0F, 1.0F);
        OpenedAdvancements.award(player, OpenedAdvancements.Kind.ITEM_FRAME);
    }

    private ItemFrameInteractionHandler() {}
}
