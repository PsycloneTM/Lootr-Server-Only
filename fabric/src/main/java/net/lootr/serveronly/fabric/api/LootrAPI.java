package net.lootr.serveronly.fabric.api;

import net.lootr.serveronly.fabric.config.IProblematicLootTableProcessor;
import net.lootr.serveronly.fabric.config.ITeamResolver;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.config.ProblematicLootTables;
import net.lootr.serveronly.fabric.config.TeamResolvers;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.data.PlayerClears;
import net.lootr.serveronly.fabric.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.lootr.serveronly.fabric.interaction.Decay;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.lootr.serveronly.fabric.registry.ModLootTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * The server-side facade an add-on mod uses to inspect and extend this mod: the server-only counterpart of
 * upstream Lootr's {@code LootrAPI}. One class, so an add-on does not have to know which internal class owns what.
 * <p>
 * <b>Read-mostly and optional.</b> Nothing inside this mod calls it; it only wraps what the mod already decides,
 * so the answers are the ones the mod itself acts on, and removing or changing it cannot change gameplay.
 * <p>
 * <b>Threading.</b> Call from the server thread (block entities and entities are not thread-safe).
 * <p>
 * <b>What it covers.</b> "Containers" here are the objects this mod manages: loot chests, trapped chests, barrels
 * and shulker boxes ({@link BlockEntity}), chest minecarts and marked item frames ({@link Entity}). Decorated pots
 * and suspicious blocks also hold per-player state, so {@link #hasLooted} and {@link #getLooterCount} work for
 * them, but they have no decay or refresh timer for {@link #getTicksUntilDecay} / {@link #getTicksUntilRefresh}.
 * <p>
 * <b>Not upstream-binary-compatible.</b> Method names follow upstream's where the idea maps over, but upstream's
 * signatures belong to its custom block/entity types, which a server-only mod does not have, so a mod written against
 * upstream's {@code LootrAPI} must be adapted, not just recompiled.
 */
public final class LootrAPI {

    // ---- what is a Lootr container ----------------------------------------------------------------------------

    /**
     * True if this block entity is a loot container this mod manages right now: its loot table is converted, and its
     * dimension is enabled. Covers chests, trapped chests, barrels, shulker boxes, decorated pots and suspicious blocks.
     */
    public static boolean isLootrContainer(@Nullable BlockEntity blockEntity) {
        return blockEntity != null
                && blockEntity.getLevel() instanceof ServerLevel level
                && LootrConfig.isDimensionEnabled(level.dimension())
                && ContainerProtection.isManaged(blockEntity);
    }

    /**
     * True for a chest minecart whose loot table is converted, or an item frame this mod has marked as a loot
     * frame (and that still holds an item), in an enabled dimension.
     */
    public static boolean isLootrContainer(@Nullable Entity entity) {
        if (entity instanceof MinecartChest cart) {
            return cart.getLootTable() != null
                    && cart.level() instanceof ServerLevel level
                    && LootrConfig.isDimensionEnabled(level.dimension())
                    && ModLootTags.isTableEnabled(cart.getLootTable());
        }
        return entity instanceof ItemFrame frame && ContainerProtection.isManagedFrame(frame);
    }

    // ---- who has looted what ----------------------------------------------------------------------------------

    /**
     * The key this player's loot is stored under: their own UUID, or their team's shared UUID when {@code team_loot}
     * is on, as decided by the active team resolver.
     */
    public static UUID getLootKey(Player player) {
        return TeamResolvers.resolveFor(player);
    }

    /** True if this player (or their team) has already looted the container. False if there is no loot state. */
    public static boolean hasLooted(@Nullable BlockEntity blockEntity, Player player) {
        LootrLootState state = stateOf(blockEntity);
        return state != null && state.hasGeneratedFor(getLootKey(player));
    }

    /** Entity version of {@link #hasLooted(BlockEntity, Player)}: chest minecarts and loot item frames. */
    public static boolean hasLooted(@Nullable Entity entity, Player player) {
        LootrLootState state = stateOf(entity);
        return state != null && state.hasGeneratedFor(getLootKey(player));
    }

    /** How many players (or teams) have looted it. Records wiped by {@code /lootr clear} are not counted. */
    public static int getLooterCount(@Nullable BlockEntity blockEntity) {
        LootrLootState state = stateOf(blockEntity);
        return state == null ? 0 : state.lootedCount();
    }

    /** Entity version of {@link #getLooterCount(BlockEntity)}. */
    public static int getLooterCount(@Nullable Entity entity) {
        LootrLootState state = stateOf(entity);
        return state == null ? 0 : state.lootedCount();
    }

    // ---- timers -----------------------------------------------------------------------------------------------

    /**
     * Ticks until this container decays, or empty if no decay timer applies: decay is off or does not cover this
     * container, nobody has looted it yet, or it is not a chest/barrel/shulker box. 0 means it is due now.
     */
    public static OptionalLong getTicksUntilDecay(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                || ContainerInteractionHandler.kindOf(container) == null || container.getLevel() == null) {
            return OptionalLong.empty();
        }
        return decayLeft(container.getLevel(), container.getBlockPos(), container.getLootTable(), stateOf(container));
    }

    /** Chest minecart version of {@link #getTicksUntilDecay(BlockEntity)}; empty for any other entity. */
    public static OptionalLong getTicksUntilDecay(@Nullable Entity entity) {
        if (!(entity instanceof MinecartChest cart)) {
            return OptionalLong.empty();
        }
        return decayLeft(cart.level(), cart.blockPosition(), cart.getLootTable(), stateOf(cart));
    }

    /**
     * Ticks until this container refreshes (its loot resets for everyone), or empty if refreshing does not apply to it
     * or nobody has looted it yet. 0 means it is due now.
     */
    public static OptionalLong getTicksUntilRefresh(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                || ContainerInteractionHandler.kindOf(container) == null || container.getLevel() == null) {
            return OptionalLong.empty();
        }
        return refreshLeft(container.getLevel(), container.getBlockPos(), container.getLootTable(), stateOf(container));
    }

    /** Chest minecart version of {@link #getTicksUntilRefresh(BlockEntity)}; empty for any other entity. */
    public static OptionalLong getTicksUntilRefresh(@Nullable Entity entity) {
        if (!(entity instanceof MinecartChest cart)) {
            return OptionalLong.empty();
        }
        return refreshLeft(cart.level(), cart.blockPosition(), cart.getLootTable(), stateOf(cart));
    }

    // ---- admin ------------------------------------------------------------------------------------------------

    /**
     * Forgets everything this player has looted, exactly like {@code /lootr clear <player>}: they can loot every
     * container, pot, suspicious block and item frame again. Lazy (see {@code PlayerClears}). With {@code team_loot}
     * on this also clears the record shared with their team.
     */
    public static void clearLootRecords(ServerPlayer player) {
        PlayerClears clears = PlayerClears.get(player.getServer());
        clears.clear(getLootKey(player));
        if (!getLootKey(player).equals(player.getUUID())) {
            clears.clear(player.getUUID());
        }
    }

    // ---- extension points -------------------------------------------------------------------------------------
    // One-line shortcuts to the registries that already exist, so an add-on has a single class to import.

    /** Adds a {@link LootFilter}; see {@link LootFilters}. */
    public static void registerFilter(LootFilter filter) {
        LootFilters.register(filter);
    }

    public static void registerListener(LootListener listener) {
        LootListeners.register(listener);
    }

    /** Adds a team resolver; see {@link TeamResolvers}. Select it with {@code pinned_team_resolver} or by priority. */
    public static void registerTeamResolver(ITeamResolver resolver) {
        TeamResolvers.registerResolver(resolver);
    }

    /** Adds a source of loot tables that should not be converted; see {@link ProblematicLootTables}. */
    public static void registerProblematicLootTableProcessor(IProblematicLootTableProcessor processor) {
        ProblematicLootTables.registerProcessor(processor);
    }

    // ---- internals --------------------------------------------------------------------------------------------

    private static OptionalLong decayLeft(Level level, BlockPos pos, @Nullable ResourceKey<LootTable> table,
                                          @Nullable LootrLootState state) {
        if (state == null || !(level instanceof ServerLevel serverLevel)
                || !LootrConfig.isDimensionEnabled(serverLevel.dimension())) {
            return OptionalLong.empty();
        }
        long left = Decay.ticksLeft(level, pos, state.getFirstGeneratedGameTime(), table, level.getGameTime());
        return left < 0 ? OptionalLong.empty() : OptionalLong.of(left);
    }

    private static OptionalLong refreshLeft(Level level, BlockPos pos, @Nullable ResourceKey<LootTable> table,
                                            @Nullable LootrLootState state) {
        if (state == null || state.getFirstGeneratedGameTime() < 0 || table == null
                || !(level instanceof ServerLevel serverLevel)
                || !LootrConfig.isDimensionEnabled(serverLevel.dimension())
                || !ModLootTags.isTableEnabled(table)) {
            return OptionalLong.empty();
        }
        int interval = LootrConfig.refreshTicksFor(serverLevel, pos, table);
        if (interval <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(0, state.getFirstGeneratedGameTime() + interval - level.getGameTime()));
    }

    @Nullable
    private static LootrLootState stateOf(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getLevel() == null) {
            return null;
        }
        return load(blockEntity.getAttached(ModAttachments.LOOT_STATE), blockEntity.getLevel());
    }

    @Nullable
    private static LootrLootState stateOf(@Nullable Entity entity) {
        return entity == null ? null : load(entity.getAttached(ModAttachments.LOOT_STATE), entity.level());
    }

    /** Fabric keeps the state as a raw tag, so a query loads a throwaway copy (never written back). */
    @Nullable
    private static LootrLootState load(@Nullable CompoundTag tag, Level level) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        LootrLootState state = new LootrLootState(27);
        state.load(tag, level.registryAccess());
        return state;
    }

    private LootrAPI() {}
}
