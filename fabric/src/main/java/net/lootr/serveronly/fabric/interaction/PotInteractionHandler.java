package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.interaction.LootRoller;
import net.lootr.serveronly.interaction.UnresolvedTables;
import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.fabric.registry.ModLootTags;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class PotInteractionHandler {

    public static void register() {
        UseBlockCallback.EVENT.register(PotInteractionHandler::onUseBlock);
        AttackBlockCallback.EVENT.register(PotInteractionHandler::onAttackBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || serverPlayer.isSpectator()) {
            return InteractionResult.PASS;
        }
        ServerLevel level = (ServerLevel) world;
        DecoratedPotBlockEntity pot = lootPotAt(level, hit.getBlockPos());
        if (pot == null || isPlacingBlockAgainst(serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.CONSUME;
        }
        smash(serverPlayer, pot, level);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult onAttackBlock(Player player, Level world, InteractionHand hand,
                                                   BlockPos pos, Direction direction) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || serverPlayer.isSpectator() || serverPlayer.isCreative()) {
            return InteractionResult.PASS;
        }
        ServerLevel level = (ServerLevel) world;
        DecoratedPotBlockEntity pot = lootPotAt(level, pos);
        if (pot == null) {
            return InteractionResult.PASS;
        }
        smash(serverPlayer, pot, level);
        return InteractionResult.SUCCESS;
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
        HolderLookup.Provider provider = level.registryAccess();
        CompoundTag stored = pot.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag());
        LootrLootState state = new LootrLootState(27);
        state.load(stored, provider);

        UUID lootKey = TeamResolver.resolve(player);
        if (state.refreshIfDue(level.getGameTime(), LootrConfig.refreshTicksFor(level, pot.getBlockPos(), pot.getLootTable()))) {
            persist(pot, state, provider);
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
        persist(pot, state, provider);
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

    private static void persist(DecoratedPotBlockEntity pot, LootrLootState state, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        state.save(tag, provider);
        pot.setAttached(ModAttachments.LOOT_STATE, tag);
        pot.setChanged();
    }

    private PotInteractionHandler() {}
}
