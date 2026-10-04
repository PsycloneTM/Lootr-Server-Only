package net.lootr.serveronly.interaction;

import net.lootr.serveronly.api.LootListeners;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PlayerScopedContainer;
import net.lootr.serveronly.registry.ModAttachments;
import net.lootr.serveronly.registry.ModLootTags;

@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class ContainerInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        if (!LootrConfig.isDimensionEnabled(level.dimension())) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container) || !ModLootTags.isLootrEnabled(container)) {
            return;
        }

        OpenedAdvancements.Kind kind;
        SimpleMenuProviderKind menu = SimpleMenuProviderKind.CHEST_MENU;
        if (be instanceof TrappedChestBlockEntity) {
            kind = OpenedAdvancements.Kind.TRAPPED_CHEST;
        } else if (be instanceof ChestBlockEntity) {
            kind = OpenedAdvancements.Kind.CHEST;
        } else if (be instanceof BarrelBlockEntity) {
            kind = OpenedAdvancements.Kind.BARREL;
        } else if (be instanceof ShulkerBoxBlockEntity) {
            kind = OpenedAdvancements.Kind.SHULKER;
            menu = SimpleMenuProviderKind.SHULKER_MENU;
        } else {
            return;
        }

        if (isPlacingBlockAgainst(serverPlayer)) {
            return;
        }
        if (serverPlayer.isSpectator()) {
            event.setCanceled(true);
            return;
        }
        if (!vanillaWouldOpen(level.getBlockState(pos), level, pos, be)) {
            return;
        }
        if (!container.canOpen(serverPlayer)) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (openChestLikeMenu(serverPlayer, container, level, menu, kind)) {
            afterOpen(serverPlayer, kind);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OpenTracker.forget(player);
        }
    }

    private enum SimpleMenuProviderKind { CHEST_MENU, SHULKER_MENU }

    private static boolean openChestLikeMenu(ServerPlayer player, RandomizableContainerBlockEntity entity,
                                           ServerLevel level, SimpleMenuProviderKind kind,
                                           OpenedAdvancements.Kind advancement) {
        LootrLootState state = entity.getData(ModAttachments.LOOT_STATE);
        java.util.UUID lootKey = TeamResolver.resolve(player);
        if (Refresh.refreshIfDue(level, entity, state, LootrConfig.refreshTicksFor(level, entity.getBlockPos(), entity.getLootTable()))) {
            entity.setChanged();
            Refresh.notifyRefreshed(level, entity);
        }
        if (Decay.decayIfDue(level, entity, state)) {
            return false;
        }
        boolean firstOpenForPlayer = !state.hasGeneratedFor(lootKey);
        generateLootIfNeeded(player, lootKey, entity, state, level);
        if (state.getFirstGeneratedGameTime() >= 0) {
            Decay.track(level, entity);
            Refresh.track(level, entity);
            if (firstOpenForPlayer) {
                Decay.announce(player, state, entity.getBlockPos(), entity.getLootTable(), level.getGameTime());
            }
        }
        if (firstOpenForPlayer && state.hasGeneratedFor(lootKey)) {
            OpenedAdvancements.award(player, advancement);
        }

        PlayerScopedContainer container = new PlayerScopedContainer(
                state, lootKey,
                items -> entity.setChanged(),
                p -> net.minecraft.world.Container.stillValidBlockEntity(entity, p),
                p -> OpenTracker.opened(level, entity.getBlockPos(), p),
                p -> OpenTracker.closed(level, entity.getBlockPos(), p)).owner(entity);

        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> switch (kind) {
                    case CHEST_MENU -> ChestMenu.threeRows(containerId, inventory, container);
                    case SHULKER_MENU -> new ShulkerBoxMenu(containerId, inventory, container);
                },
                entity.getDisplayName()
        ));
        return true;
    }

    private static void generateLootIfNeeded(ServerPlayer player, java.util.UUID lootKey,
                                              RandomizableContainerBlockEntity entity,
                                              LootrLootState state, ServerLevel level) {
        if (state.hasGeneratedFor(lootKey)) {
            return;
        }
        ResourceKey<LootTable> lootTableKey = ModLootTags.getAssignedLootTable(entity);
        if (lootTableKey == null) {
            return;
        }
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
        UnresolvedTables.check(player, lootTableKey, lootTable);
        LootRoller.triggerGenerateLoot(player, lootTableKey);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(entity.getBlockPos()))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);

        NonNullList<ItemStack> sized = LootRoller.rollInto(
                lootTable, params, entity.getLootTableSeed(), state.getContainerSize());
        state.setContents(lootKey, sized);
        state.markFirstGeneratedIfAbsent(level.getGameTime());
        entity.setChanged();
        Decay.track(level, entity);
        Refresh.track(level, entity);
        LootListeners.looted(level, entity, entity.getBlockPos(), player, lootTableKey);
    }

    @org.jetbrains.annotations.Nullable
    public static OpenedAdvancements.Kind kindOf(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) {
            return OpenedAdvancements.Kind.TRAPPED_CHEST;
        } else if (be instanceof ChestBlockEntity) {
            return OpenedAdvancements.Kind.CHEST;
        } else if (be instanceof BarrelBlockEntity) {
            return OpenedAdvancements.Kind.BARREL;
        } else if (be instanceof ShulkerBoxBlockEntity) {
            return OpenedAdvancements.Kind.SHULKER;
        }
        return null;
    }

    public static java.util.List<ItemStack> takeLoot(ServerPlayer player, RandomizableContainerBlockEntity entity,
                                                      ServerLevel level) {
        LootrLootState state = entity.getData(ModAttachments.LOOT_STATE);
        java.util.UUID lootKey = TeamResolver.resolve(player);
        if (Refresh.refreshIfDue(level, entity, state, LootrConfig.refreshTicksFor(level, entity.getBlockPos(), entity.getLootTable()))) {
            entity.setChanged();
            Refresh.notifyRefreshed(level, entity);
        }
        generateLootIfNeeded(player, lootKey, entity, state, level);

        java.util.List<ItemStack> taken = new java.util.ArrayList<>();
        NonNullList<ItemStack> contents = state.getContentsOrEmpty(lootKey);
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty()) {
                taken.add(stack);
                contents.set(i, ItemStack.EMPTY);
            }
        }
        state.setContents(lootKey, contents);
        entity.setChanged();
        return taken;
    }

    public static void collectLoot(ServerPlayer player, RandomizableContainerBlockEntity entity, ServerLevel level) {
        java.util.List<ItemStack> loot = takeLoot(player, entity, level);
        for (ItemStack stack : loot) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        OpenedAdvancements.Kind kind = kindOf(entity);
        if (!loot.isEmpty() && kind != null) {
            OpenedAdvancements.award(player, kind);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(loot.isEmpty()
                ? "You have already looted this."
                : "You collected your loot."), true);
    }

    private static boolean isPlacingBlockAgainst(ServerPlayer player) {
        return player.isSecondaryUseActive()
                && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty());
    }

    private static boolean vanillaWouldOpen(BlockState state, ServerLevel level, BlockPos pos, BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            return state.getMenuProvider(level, pos) != null;
        }
        if (be instanceof ShulkerBoxBlockEntity shulker && state.hasProperty(ShulkerBoxBlock.FACING)) {
            if (shulker.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
                return true;
            }
            return level.noCollision(Shulker.getProgressDeltaAabb(1.0F, state.getValue(ShulkerBoxBlock.FACING), 0.0F, 0.5F)
                    .move(pos).deflate(1.0E-6));
        }
        return true;
    }

    private static void afterOpen(ServerPlayer player, OpenedAdvancements.Kind kind) {
        ResourceLocation stat = switch (kind) {
            case TRAPPED_CHEST -> Stats.TRIGGER_TRAPPED_CHEST;
            case BARREL -> Stats.OPEN_BARREL;
            case SHULKER -> Stats.OPEN_SHULKER_BOX;
            default -> Stats.OPEN_CHEST;
        };
        player.awardStat(stat);
        PiglinAi.angerNearbyPiglins(player, true);
    }

    private ContainerInteractionHandler() {}
}
