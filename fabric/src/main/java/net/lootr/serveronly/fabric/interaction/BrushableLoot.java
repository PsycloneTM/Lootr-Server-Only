package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.registry.ModLootTags;
import net.lootr.serveronly.fabric.advancement.OpenedAdvancements;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.config.TeamResolver;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.registry.ModAttachments;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

/**
 * Per-player loot for suspicious sand/gravel, called from
 * {@code MixinBrushableBlockEntity}. Vanilla keeps ONE shared loot item and
 * turns the block into plain sand/gravel when the first player finishes
 * brushing. Here the block keeps its loot table for ever and never turns
 * into sand: each player (or team) who finishes brushing it once is rolled
 * their own loot, straight into their inventory. A player who has already
 * looted it can brush all day and nothing happens.
 * <p>
 * State is the same per-block-entity attachment the other containers use; an
 * all-empty entry is the "already looted" marker (same as pots).
 * <p>
 * Shared on purpose: the dust animation and the completion sound/particles are
 * visible to everyone nearby, and finishing resets the block's dust level for
 * all players. Not verified by a build or playtest.
 */
public final class BrushableLoot {

    /** True when this brushable is a loot block Lootr manages in this dimension. */
    public static boolean isManaged(BrushableBlockEntity be, ResourceKey<LootTable> lootTable) {
        return ModLootTags.isTableEnabled(lootTable)
                && be.getLevel() instanceof ServerLevel level
                && LootrConfig.isDimensionEnabled(level.dimension());
    }

    /** True if {@code player} must not make brushing progress (already looted it). */
    public static boolean alreadyLooted(BrushableBlockEntity be, ResourceKey<LootTable> lootTable, Player player) {
        if (!isManaged(be, lootTable) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
            return false;
        }
        ServerLevel level = (ServerLevel) be.getLevel();
        if (stateOf(be, level).hasGeneratedFor(TeamResolver.resolve(sp))) {
            sp.displayClientMessage(Component.literal("You have already searched this block."), true);
            return true;
        }
        return false;
    }

    /** Called when {@code player} finishes brushing; replaces vanilla's shared drop. */
    public static void complete(BrushableBlockEntity be, ResourceKey<LootTable> tableKey, Player player) {
        if (!(player instanceof ServerPlayer sp) || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        LootrLootState state = stateOf(be, level);
        UUID lootKey = TeamResolver.resolve(sp);

        if (!state.hasGeneratedFor(lootKey)) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.THIS_ENTITY, sp)
                    .withLuck(sp.getLuck())
                    .create(LootContextParamSets.CHEST);
            List<ItemStack> loot = table.getRandomItems(params);

            // Mark as looted before handing anything out so a failure can never allow a second roll.
            state.setContents(lootKey, NonNullList.withSize(state.getContainerSize(), ItemStack.EMPTY));
            state.markFirstGeneratedIfAbsent(level.getGameTime());
            persist(be, state, level);

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

    private static LootrLootState stateOf(BrushableBlockEntity be, ServerLevel level) {
        HolderLookup.Provider provider = level.registryAccess();
        LootrLootState state = new LootrLootState(27);
        state.load(be.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag()), provider);
        if (state.refreshIfDue(level.getGameTime(), LootrConfig.refreshTicks())) {
            persist(be, state, level);
        }
        return state;
    }

    private static void persist(BrushableBlockEntity be, LootrLootState state, ServerLevel level) {
        CompoundTag tag = new CompoundTag();
        state.save(tag, level.registryAccess());
        be.setAttached(ModAttachments.LOOT_STATE, tag);
        be.setChanged();
    }

    private BrushableLoot() {}
}
