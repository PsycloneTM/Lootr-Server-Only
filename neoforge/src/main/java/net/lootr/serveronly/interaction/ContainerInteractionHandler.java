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

/**
 * Replaces {@code ChestInteractionHandler} (chest-only) now that barrel and
 * shulker box are ported alongside chest/trapped chest. Trapped chest needs
 * no separate *menu-handling* branch: {@code TrappedChestBlockEntity extends
 * ChestBlockEntity} in vanilla, so it opens the identical {@code ChestMenu} -
 * confirmed against upstream Lootr's own {@code LootrTrappedChestBlockEntity},
 * which is itself just a thin subclass of {@code LootrChestBlockEntity} with
 * no menu-handling differences at all. Vanilla's own redstone-signal-on-open
 * behavior for trapped chests is unaffected by this substitution, since that
 * logic lives in the block/BE class itself (which we never touch), not in
 * which {@code Container} the menu happens to be backed by. It DOES need its
 * own branch (checked first, below) purely to select the right advancement
 * trigger - matching upstream's separate
 * {@code LootrTrappedChestBlockEntity#getTrigger()} - since
 * {@code instanceof ChestBlockEntity} alone can't distinguish the two.
 * <p>
 * Barrel is likewise opened via {@code ChestMenu.threeRows} in vanilla (a
 * barrel is a 27-slot chest-shaped container with its own open/close sound
 * and door-flap animation state, not a distinct menu class) - only the
 * sounds and the {@code BarrelBlockEntity.OPEN} blockstate-adjacent trivia
 * differ, and none of that is touched by this handler since vanilla's own
 * {@code BarrelBlockEntity} already manages its own open/close animation via
 * its container-openers-counter, exactly like chest does.
 * <p>
 * Shulker box is the one genuine branch: it opens {@code ShulkerBoxMenu}, a
 * distinct menu class from {@code ChestMenu}, and (per the design doc)
 * mined-and-carried shulker boxes must round-trip their attachment through
 * {@code saveToItem}/{@code loadFromItem} - NeoForge attachments already do
 * this automatically via the owning block entity's normal NBT lifecycle, so
 * no extra code is needed here for that case; it is a property of the
 * attachment API itself, not something this handler has to implement.
 */
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

        // TrappedChest must be tested before Chest (it extends it): it has its own advancement.
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
            return; // vanilla would place the held block, not open this
        }
        if (serverPlayer.isSpectator()) {
            // Vanilla would let a spectator resolve the loot table for everyone.
            event.setCanceled(true);
            return;
        }
        if (!vanillaWouldOpen(level.getBlockState(pos), level, pos, be)) {
            return; // blocked chest / obstructed shulker: let vanilla refuse
        }
        if (!container.canOpen(serverPlayer)) {
            // Locked (a /data merge Lock, or a LOCK component): vanilla's own menu path would refuse, but this
            // handler replaces that path. canOpen has already shown "is locked" and played the lock sound.
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

    /** Which vanilla menu factory to hand the {@link PlayerScopedContainer} to. */
    private enum SimpleMenuProviderKind { CHEST_MENU, SHULKER_MENU }

    /** @return false if the container decayed instead of opening (nothing was opened). */
    private static boolean openChestLikeMenu(ServerPlayer player, RandomizableContainerBlockEntity entity,
                                           ServerLevel level, SimpleMenuProviderKind kind,
                                           OpenedAdvancements.Kind advancement) {
        LootrLootState state = entity.getData(ModAttachments.LOOT_STATE);
        java.util.UUID lootKey = TeamResolver.resolve(player);
        if (Refresh.refreshIfDue(level, entity, state, LootrConfig.refreshTicksFor(level, entity.getBlockPos(), entity.getLootTable()))) {
            entity.setChanged();
            Refresh.notifyRefreshed(level, entity);
        }
        // Past its deadline (e.g. looted before decay was enabled): it decays instead of opening.
        if (Decay.decayIfDue(level, entity, state)) {
            return false;
        }
        boolean firstOpenForPlayer = !state.hasGeneratedFor(lootKey);
        generateLootIfNeeded(player, lootKey, entity, state, level);
        if (state.getFirstGeneratedGameTime() >= 0) {
            Decay.track(level, entity); // also picks up containers looted before decay existed
            Refresh.track(level, entity);
            if (firstOpenForPlayer) {
                Decay.announce(player, state, entity.getBlockPos(), entity.getLootTable(), level.getGameTime());
            }
        }
        if (firstOpenForPlayer && state.hasGeneratedFor(lootKey)) {
            // Only fire once per player per container, matching upstream's
            // ILootrInfoProvider.performTrigger (hasServerOpened check) -
            // and only if loot was actually generated (a container with no
            // assigned loot table never becomes "opened" for this purpose,
            // matching generateLootIfNeeded's own early-return when
            // ModLootTags.getAssignedLootTable(entity) is null).
            OpenedAdvancements.award(player, advancement);
        }

        PlayerScopedContainer container = new PlayerScopedContainer(
                state, lootKey,
                items -> entity.setChanged(),
                // Vanilla's own range + "block entity still there" check.
                p -> net.minecraft.world.Container.stillValidBlockEntity(entity, p),
                // Lid / door animation, sounds, barrel state, trapped-chest redstone.
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

        // Vanilla's own fill: items scatter across the whole inventory like a real chest,
        // and the container's loot seed is used only if randomise_seed is off. See LootRoller.
        NonNullList<ItemStack> sized = LootRoller.rollInto(
                lootTable, params, entity.getLootTableSeed(), state.getContainerSize());
        state.setContents(lootKey, sized);
        state.markFirstGeneratedIfAbsent(level.getGameTime());
        entity.setChanged();
        Decay.track(level, entity);
        Refresh.track(level, entity);
        LootListeners.looted(level, entity, entity.getBlockPos(), player, lootTableKey);
    }

    /**
     * The advancement kind for one of the four loot container types, or null for
     * anything else. TrappedChest is tested before Chest because it extends it.
     * Mirrors the dispatch in {@link #onRightClickBlock}.
     */
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

    /**
     * Takes this player's (or team's) loot out of {@code entity} WITHOUT opening a
     * menu: rolls it if they have not looted it yet, empties their entry in place,
     * and leaves that (now empty) entry behind so the container counts as looted
     * for them. The caller decides where the returned stacks go. Emptying before
     * returning means a break that another mod cancels afterwards cannot duplicate
     * the loot. The list is emptied in place so a teammate who has the menu open
     * on the same shared entry cannot still take the items.
     */
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

    /**
     * The "break to drop loot" action: hand the player their loot directly (overflow
     * drops at their feet) and award the container's advancement. Goes into the
     * inventory rather than spawning item entities at the block because a spawned
     * item is visible to, and pickable by, every other player nearby.
     */
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

    /** Sneaking with something in a hand makes vanilla place a block instead of using this one. */
    private static boolean isPlacingBlockAgainst(ServerPlayer player) {
        return player.isSecondaryUseActive()
                && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty());
    }

    /**
     * Whether vanilla itself would open this block right now. A chest with a solid
     * block or a cat on top, or a shulker box whose lid is obstructed, refuses to
     * open; the loot version must refuse too (we return without handling so vanilla
     * does the refusing).
     */
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

    /** What vanilla's block does after opening: the open stat and angering nearby piglins. */
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
