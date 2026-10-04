package net.lootr.serveronly.interaction;

import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.registry.ModLootTags;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class BrushableLoot {

    public static boolean isManaged(BrushableBlockEntity be, ResourceKey<LootTable> lootTable) {
        return ModLootTags.isTableEnabled(lootTable)
                && be.getLevel() instanceof ServerLevel level
                && LootrConfig.isDimensionEnabled(level.dimension());
    }

    public static boolean alreadyLooted(BrushableBlockEntity be, ResourceKey<LootTable> lootTable, Player player) {
        if (!isManaged(be, lootTable) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
            return false;
        }
        ServerLevel level = (ServerLevel) be.getLevel();
        if (stateOf(be, level, lootTable).hasGeneratedFor(TeamResolver.resolve(sp))) {
            sp.displayClientMessage(Component.literal("You have already searched this block."), true);
            return true;
        }
        return false;
    }

    public static void complete(BrushableBlockEntity be, ResourceKey<LootTable> tableKey, Player player) {
        if (!(player instanceof ServerPlayer sp) || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        LootrLootState state = stateOf(be, level, tableKey);
        UUID lootKey = TeamResolver.resolve(sp);

        if (!state.hasGeneratedFor(lootKey)) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
            UnresolvedTables.check(sp, tableKey, table);
            LootRoller.triggerGenerateLoot(sp, tableKey);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.THIS_ENTITY, sp)
                    .withLuck(sp.getLuck())
                    .create(LootContextParamSets.CHEST);
            List<ItemStack> loot = LootRoller.roll(table, params, 0L);

            state.setContents(lootKey, NonNullList.withSize(state.getContainerSize(), ItemStack.EMPTY));
            state.markFirstGeneratedIfAbsent(level.getGameTime());
            persist(be, state, level);
            LootListeners.looted(level, be, pos, sp, tableKey);

            for (ItemStack stack : loot) {
                if (!stack.isEmpty()) {
                    sp.getInventory().placeItemBackInInventory(stack);
                }
            }
            OpenedAdvancements.award(sp, OpenedAdvancements.Kind.BRUSHABLE);
        }

        BlockState blockState = be.getBlockState();
        level.levelEvent(3008, pos, Block.getId(blockState));
        if (blockState.hasProperty(BlockStateProperties.DUSTED)) {
            level.setBlock(pos, blockState.setValue(BlockStateProperties.DUSTED, 0), 3);
        }
    }

    private static LootrLootState stateOf(BrushableBlockEntity be, ServerLevel level, ResourceKey<LootTable> table) {
        LootrLootState state = be.getData(ModAttachments.LOOT_STATE);
        if (state.refreshIfDue(level.getGameTime(), LootrConfig.refreshTicksFor(level, be.getBlockPos(), table))) {
            be.setChanged();
            Refresh.notifyRefreshed(level, be);
        }
        return state;
    }

    private static void persist(BrushableBlockEntity be, LootrLootState state, ServerLevel level) {
        be.setChanged();
    }

    private BrushableLoot() {}
}
