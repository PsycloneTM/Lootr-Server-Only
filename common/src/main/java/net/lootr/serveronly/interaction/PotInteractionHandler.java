package net.lootr.serveronly.interaction;

import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.interaction.LootRoller;
import net.lootr.serveronly.interaction.UnresolvedTables;
import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.registry.ModLootTags;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class PotInteractionHandler {
    public static InteractionOutcome use(ServerPlayer serverPlayer, ServerLevel level, BlockPos pos, InteractionHand hand) {
        if (serverPlayer.isSpectator()) {
            return InteractionOutcome.PASS;
        }
        DecoratedPotBlockEntity pot = lootPotAt(level, pos);
        if (pot == null || isPlacingBlockAgainst(serverPlayer)) {
            return InteractionOutcome.PASS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionOutcome.CONSUME;
        }
        smash(serverPlayer, pot, level);
        return InteractionOutcome.SUCCESS;
    }

    public static boolean attack(ServerPlayer serverPlayer, ServerLevel level, BlockPos pos) {
        if (serverPlayer.isSpectator() || serverPlayer.isCreative()) {
            return false;
        }
        DecoratedPotBlockEntity pot = lootPotAt(level, pos);
        if (pot == null) {
            return false;
        }
        smash(serverPlayer, pot, level);
        return true;
    }

    @Nullable
    private static DecoratedPotBlockEntity lootPotAt(ServerLevel level, BlockPos pos) {
        if (!LootrSettings.isDimensionEnabled(level.dimension())) {
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
        LootrLootState state = LootStateStore.getOrCreate(pot, level);

        UUID lootKey = TeamResolver.resolve(player);
        if (state.refreshIfDue(level.getGameTime(), LootrSettings.refreshTicksFor(level, pot.getBlockPos(), pot.getLootTable()))) {
            LootStateStore.save(pot, state, level);
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
        LootStateStore.save(pot, state, level);
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
