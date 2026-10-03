package net.lootr.serveronly.fabric.interaction;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.lootr.serveronly.fabric.advancement.OpenedAdvancements;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.config.TeamResolver;
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

/**
 * Fabric twin of the NeoForge {@code ItemFrameInteractionHandler}; read that
 * class for the design (a frame is not a container: hitting it hands the
 * player one copy of the framed item, the frame itself is never changed, and
 * frames never refresh).
 * <p>
 * Differences from the NeoForge version:
 * <ul>
 *   <li>{@link AttackEntityCallback} / {@link UseEntityCallback} instead of
 *   {@code AttackEntityEvent} / {@code EntityInteract}. Both are hooked in
 *   <b>before</b> the spectator check, so spectators are filtered by hand.</li>
 *   <li><b>The hit returns {@code SUCCESS}, deliberately not {@code FAIL}.</b>
 *   Fabric API documents {@code AttackEntityCallback}'s {@code FAIL} as
 *   "cancels further processing and does not send a packet to the server" -
 *   the server-side "take" would then never run. {@code SUCCESS} also cancels
 *   further processing (so vanilla never pops the item) but still lets the
 *   packet through. That is different from {@code AttackBlockCallback}, which
 *   the pot handler uses, so do not "align" the two.</li>
 *   <li>The attachment is a raw {@link CompoundTag}, so state is loaded,
 *   mutated and written back by hand, exactly like the pot handler.</li>
 * </ul>
 * <p>
 * <b>Damage protection.</b> A marked frame is immune to arrows, explosions, fire
 * and mobs through {@code MixinEntity} (a hook on {@code Entity.isInvulnerableTo},
 * standing in for NeoForge's {@code EntityInvulnerabilityCheckEvent}). This class
 * only handles the <i>player's</i> hit; see {@code ContainerProtection.isManagedEntity}.
 * <p>
 * <b>Status:</b> the Fabric module builds and boots, but this handler has not
 * been confirmed in play. The callback signatures and result semantics above
 * are taken from Fabric API's own Javadoc.
 */
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
        // Creative players are left alone so an admin can still break a frame.
        if (serverPlayer.isCreative() || serverPlayer.isSpectator()) {
            return InteractionResult.PASS;
        }
        ServerLevel level = (ServerLevel) world;
        if (!isLootrFrame(frame, level)) {
            return InteractionResult.PASS;
        }
        // Main hand only: the callback can fire once per hand.
        if (hand == InteractionHand.MAIN_HAND) {
            take(serverPlayer, frame, level);
        }
        // SUCCESS, not FAIL - see the class javadoc.
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
        // Swallowed for both hands: vanilla would rotate the item or swap a
        // held item in, either of which alters state every other player uses.
        if (hand == InteractionHand.MAIN_HAND) {
            // Plain text: a vanilla client has no lang file for this mod to resolve.
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
            return; // already took theirs; the hit does nothing, quietly
        }
        ItemStack framed = frame.getItem();
        if (framed.isEmpty()) {
            return;
        }

        // Record "taken" BEFORE handing anything over, so a failure while
        // giving the item can never let the same player take it twice. The
        // all-empty entry is the marker - for a frame the "loot" is the framed
        // item itself, so only the fact of having taken it needs remembering.
        state.setContents(lootKey, NonNullList.withSize(state.getContainerSize(), ItemStack.EMPTY));
        state.markFirstGeneratedIfAbsent(level.getGameTime());
        CompoundTag tag = new CompoundTag();
        state.save(tag, provider);
        // Entities save with their chunk, so unlike a block entity there is
        // no setChanged() to call - setAttached is enough.
        frame.setAttached(ModAttachments.LOOT_STATE, tag);

        player.getInventory().placeItemBackInInventory(framed.copyWithCount(1));

        // Keep the shared server-side frame item intact for everyone else,
        // but hide the item in this player's client view just like upstream Lootr.
        ItemFrameVisualSync.sendHiddenItem(player, frame);

        frame.playSound(frame.getRemoveItemSound(), 1.0F, 1.0F);
        OpenedAdvancements.award(player, OpenedAdvancements.Kind.ITEM_FRAME);
    }

    private ItemFrameInteractionHandler() {}
}
