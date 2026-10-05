package net.lootr.serveronly.api;

import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.api.LootFilter;
import net.lootr.serveronly.api.LootFilters;
import net.lootr.serveronly.api.LootListener;
import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.config.IProblematicLootTableProcessor;
import net.lootr.serveronly.config.ITeamResolver;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.config.ProblematicLootTables;
import net.lootr.serveronly.config.TeamResolvers;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PlayerClears;
import net.lootr.serveronly.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.interaction.ContainerProtection;
import net.lootr.serveronly.interaction.Decay;
import net.lootr.serveronly.registry.ModLootTags;
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

public final class LootrAPI {
    public static boolean isLootrContainer(@Nullable BlockEntity blockEntity) {
        return blockEntity != null
                && blockEntity.getLevel() instanceof ServerLevel level
                && LootrSettings.isDimensionEnabled(level.dimension())
                && ContainerProtection.isManaged(blockEntity);
    }

    public static boolean isLootrContainer(@Nullable Entity entity) {
        if (entity instanceof MinecartChest cart) {
            return cart.getLootTable() != null
                    && cart.level() instanceof ServerLevel level
                    && LootrSettings.isDimensionEnabled(level.dimension())
                    && ModLootTags.isTableEnabled(cart.getLootTable());
        }
        return entity instanceof ItemFrame frame && ContainerProtection.isManagedFrame(frame);
    }

    public static UUID getLootKey(Player player) {
        return TeamResolvers.resolveFor(player);
    }

    public static boolean hasLooted(@Nullable BlockEntity blockEntity, Player player) {
        LootrLootState state = stateOf(blockEntity);
        return state != null && state.hasGeneratedFor(getLootKey(player));
    }

    public static boolean hasLooted(@Nullable Entity entity, Player player) {
        LootrLootState state = stateOf(entity);
        return state != null && state.hasGeneratedFor(getLootKey(player));
    }

    public static int getLooterCount(@Nullable BlockEntity blockEntity) {
        LootrLootState state = stateOf(blockEntity);
        return state == null ? 0 : state.lootedCount();
    }

    public static int getLooterCount(@Nullable Entity entity) {
        LootrLootState state = stateOf(entity);
        return state == null ? 0 : state.lootedCount();
    }

    public static OptionalLong getTicksUntilDecay(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                || ContainerInteractionHandler.kindOf(container) == null || container.getLevel() == null) {
            return OptionalLong.empty();
        }
        return decayLeft(container.getLevel(), container.getBlockPos(), container.getLootTable(), stateOf(container));
    }

    public static OptionalLong getTicksUntilDecay(@Nullable Entity entity) {
        if (!(entity instanceof MinecartChest cart)) {
            return OptionalLong.empty();
        }
        return decayLeft(cart.level(), cart.blockPosition(), cart.getLootTable(), stateOf(cart));
    }

    public static OptionalLong getTicksUntilRefresh(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)
                || ContainerInteractionHandler.kindOf(container) == null || container.getLevel() == null) {
            return OptionalLong.empty();
        }
        return refreshLeft(container.getLevel(), container.getBlockPos(), container.getLootTable(), stateOf(container));
    }

    public static OptionalLong getTicksUntilRefresh(@Nullable Entity entity) {
        if (!(entity instanceof MinecartChest cart)) {
            return OptionalLong.empty();
        }
        return refreshLeft(cart.level(), cart.blockPosition(), cart.getLootTable(), stateOf(cart));
    }

    public static void clearLootRecords(ServerPlayer player) {
        PlayerClears clears = PlayerClears.get(player.getServer());
        clears.clear(getLootKey(player));
        if (!getLootKey(player).equals(player.getUUID())) {
            clears.clear(player.getUUID());
        }
    }

    public static void registerFilter(LootFilter filter) {
        LootFilters.register(filter);
    }

    public static void registerListener(LootListener listener) {
        LootListeners.register(listener);
    }

    public static void registerTeamResolver(ITeamResolver resolver) {
        TeamResolvers.registerResolver(resolver);
    }

    public static void registerProblematicLootTableProcessor(IProblematicLootTableProcessor processor) {
        ProblematicLootTables.registerProcessor(processor);
    }

    private static OptionalLong decayLeft(Level level, BlockPos pos, @Nullable ResourceKey<LootTable> table,
                                          @Nullable LootrLootState state) {
        if (state == null || !(level instanceof ServerLevel serverLevel)
                || !LootrSettings.isDimensionEnabled(serverLevel.dimension())) {
            return OptionalLong.empty();
        }
        long left = Decay.ticksLeft(level, pos, state.getFirstGeneratedGameTime(), table, level.getGameTime());
        return left < 0 ? OptionalLong.empty() : OptionalLong.of(left);
    }

    private static OptionalLong refreshLeft(Level level, BlockPos pos, @Nullable ResourceKey<LootTable> table,
                                            @Nullable LootrLootState state) {
        if (state == null || state.getFirstGeneratedGameTime() < 0 || table == null
                || !(level instanceof ServerLevel serverLevel)
                || !LootrSettings.isDimensionEnabled(serverLevel.dimension())
                || !ModLootTags.isTableEnabled(table)) {
            return OptionalLong.empty();
        }
        int interval = LootrSettings.refreshTicksFor(serverLevel, pos, table);
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
        return LootStateStore.peek(blockEntity, blockEntity.getLevel());
    }

    @Nullable
    private static LootrLootState stateOf(@Nullable Entity entity) {
        return entity == null ? null : LootStateStore.peek(entity, entity.level());
    }

    private LootrAPI() {}
}
