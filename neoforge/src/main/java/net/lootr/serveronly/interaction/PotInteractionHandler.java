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
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            smash(player, pot, level);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
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
            return;
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
