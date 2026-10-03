package net.lootr.serveronly.interaction;

import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.registry.ModLootTags;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Per-player loot for decorated pots (trail-ruins pots, ancient-city pots and
 * any pot given a loot table by a datapack or structure).
 * <p>
 * Unlike the four container blocks, a pot never opens a menu: vanilla resolves
 * its loot table when the pot is broken, dropping the items into the world for
 * whoever hit it first, and the pot is gone for everyone else. So this handler
 * substitutes the whole interaction instead of swapping a Container. The first
 * time a given player (or team) hits or right-clicks a loot pot, they are
 * rolled their own loot, which goes straight into their inventory (overflow
 * drops at their feet) while the pot wobbles and the shatter sound plays. The
 * pot itself is never removed, so every other player still finds it intact.
 * A player who has already looted it just gets the "won't take that" wobble.
 * <p>
 * Loot state is the same per-block-entity attachment the containers use; an
 * all-empty entry is the "already looted" marker, which
 * {@link LootrLootState} keeps and saves like any other.
 * <p>
 * <b>Unverified at time of writing (needs a real playtest):</b> cancelling
 * {@code LeftClickBlock} stops the server breaking the pot, but a vanilla
 * client predicts the break locally. Whether that client-side prediction is
 * reverted cleanly, and whether a sherd-decorated pot keeps its sherds
 * afterwards, was not confirmed. The exact {@code LeftClickBlock} members
 * used here ({@code getPos}, {@code getEntity}, and its cancel semantics)
 * were written from the documented event shape without a NeoForge jar to
 * check against - the compiler is the first real check.
 */
@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class PotInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        DecoratedPotBlockEntity pot = lootPotAt(level, event.getPos());
        if (pot == null || isPlacingBlockAgainst(player)) {
            return;
        }
        // Cancel for BOTH hands. The event fires once per hand, and letting
        // the off-hand pass through could let vanilla insert the off-hand
        // item into the pot and resolve its loot table for everyone.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            smash(player, pot, level);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // Creative players are left alone so an admin can still remove a pot.
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator() || player.isCreative()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        DecoratedPotBlockEntity pot = lootPotAt(level, event.getPos());
        if (pot == null) {
            return;
        }
        event.setCanceled(true);
        smash(player, pot, level);
    }

    /** The pot at {@code pos} if it still has an unresolved loot table and this dimension is enabled. */
    @Nullable
    private static DecoratedPotBlockEntity lootPotAt(ServerLevel level, BlockPos pos) {
        if (!LootrConfig.isDimensionEnabled(level.dimension())) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot
                && ModLootTags.isTableEnabled(pot.getLootTable())) {
            return pot;
        }
        return null;
    }

    /** Sneaking with something in a hand makes vanilla skip the block's own interaction. */
    private static boolean isPlacingBlockAgainst(ServerPlayer player) {
        return player.isSecondaryUseActive()
                && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty());
    }

    private static void smash(ServerPlayer player, DecoratedPotBlockEntity pot, ServerLevel level) {
        BlockPos pos = pot.getBlockPos();
        LootrLootState state = pot.getData(ModAttachments.LOOT_STATE);
        UUID lootKey = TeamResolver.resolve(player);
        if (state.refreshIfDue(level.getGameTime(), LootrConfig.refreshTicksFor(level, pot.getBlockPos(), pot.getLootTable()))) {
            pot.setChanged();
            Refresh.notifyRefreshed(level, pot);
        }

        if (state.hasGeneratedFor(lootKey)) {
            level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT_FAIL, SoundSource.BLOCKS, 1.0F, 1.0F);
            pot.wobble(DecoratedPotBlockEntity.WobbleStyle.NEGATIVE);
            return;
        }

        ResourceKey<LootTable> tableKey = pot.getLootTable();
        if (tableKey == null) {
            return; // cannot happen after lootPotAt, but never NPE on a server thread
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
        UnresolvedTables.check(player, tableKey, table);
        LootRoller.triggerGenerateLoot(player, tableKey);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        List<ItemStack> loot = LootRoller.roll(table, params, pot.getLootTableSeed());

        // Record "looted" before handing anything out, so a failure while
        // giving items can never let the same player roll twice.
        state.setContents(lootKey, NonNullList.withSize(state.getContainerSize(), ItemStack.EMPTY));
        state.markFirstGeneratedIfAbsent(level.getGameTime());
        pot.setChanged();
        LootListeners.looted(level, pot, pos, player, tableKey);

        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }

        level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.0F, 1.0F);
        pot.wobble(DecoratedPotBlockEntity.WobbleStyle.POSITIVE);
        OpenedAdvancements.award(player, OpenedAdvancements.Kind.POT);
    }

    private PotInteractionHandler() {}
}
