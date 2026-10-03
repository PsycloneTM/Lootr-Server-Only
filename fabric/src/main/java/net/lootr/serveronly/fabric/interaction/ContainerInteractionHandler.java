package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.api.LootListeners;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.lootr.serveronly.fabric.advancement.OpenedAdvancements;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.config.TeamResolver;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.data.PlayerScopedContainer;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.lootr.serveronly.fabric.registry.ModLootTags;

/**
 * Fabric-side equivalent of the NeoForge side's
 * {@code net.lootr.serveronly.interaction.ContainerInteractionHandler}. Same
 * per-type routing (chest/trapped chest, barrel, shulker box), same
 * loot-generation logic - only the event hookup differs, because Fabric API
 * has no direct analogue of NeoForge's {@code PlayerInteractEvent.RightClickBlock}.
 * <p>
 * The Fabric API equivalent is {@link UseBlockCallback} (module
 * {@code fabric-events-interaction-v0}), confirmed against FabricMC's own
 * published Javadoc across several Fabric API versions:
 * {@code ActionResult interact(PlayerEntity player, World world, Hand hand,
 * BlockHitResult hitResult)}. Two behavioral differences from the NeoForge
 * event that this handler has to account for itself, both called out in
 * that same Javadoc:
 * <ul>
 *     <li>It fires <b>before</b> the spectator-mode check, unlike NeoForge's
 *     event - so this handler cannot rely on the caller to have already
 *     filtered out spectators. That said, upstream Lootr's own containers
 *     don't add a spectator check either (vanilla's own menu-opening code
 *     downstream of a {@code PASS} result already denies spectators from
 *     opening a real menu) - see the "let it PASS" note below.</li>
 *     <li>It is not pre-split into client-side/server-side callers the way
 *     NeoForge's bus can be with {@code @EventBusSubscriber(Dist...)} - this
 *     handler must check {@code world.isClientSide()} itself. Fabric API's
 *     own docs are explicit that on the logical client, {@code SUCCESS} from
 *     this callback also triggers a packet to the server (so the server-side
 *     invocation is where all actual container logic must live; the
 *     client-side invocation is a complete no-op here).</li>
 * </ul>
 * <p>
 * Only {@link InteractionHand#MAIN_HAND} is handled here. Vanilla's client
 * sends a single {@code ServerboundUseItemOnPacket} for a block-use
 * interaction from whichever hand initiated it, so the off-hand branch of
 * this callback is not expected to be hit for a genuine block-open click in
 * normal play; it is ignored defensively (falls through to
 * {@code InteractionResult.PASS}) rather than risking a double
 * loot-generation/menu-open if some other mod or edge case does fire it
 * for both hands.
 * <p>
 * Barrel needs no separate branch at all. Trapped chest needs no separate
 * *menu-handling* branch (identical to plain chest), but does need its own
 * branch purely to pick the right advancement trigger - see the trapped-chest
 * case below and the NeoForge side's javadoc for the full explanation; none
 * of that reasoning is loader-specific. Shulker boxes mined-and-carried
 * round-trip their attachment automatically via the block entity's normal
 * NBT lifecycle.
 * <p>
 * <b>Attachment access differs from NeoForge and is the other reason this
 * class cannot be copied verbatim.</b> Unlike the NeoForge side (where
 * {@code AttachmentType<LootrLootState>} holds the live object directly and
 * {@code getData(LOOT_STATE)} loads/creates it transparently), Fabric API's
 * {@link ModAttachments#LOOT_STATE} is deliberately typed
 * {@code AttachmentType<CompoundTag>} - see that class's javadoc for why a
 * plain Fabric attachment {@code Codec} can't thread a
 * {@code HolderLookup.Provider} through to {@link LootrLootState#load}/
 * {@link LootrLootState#save}. This handler is therefore the one place that
 * does the load/mutate/save round-trip by hand: read the raw tag off the
 * block entity (via {@code getAttachedOrElse}, defaulting to an empty tag
 * for a container that's never had this attachment written before),
 * {@code LootrLootState.load(tag, provider)} it into a live state object,
 * let the existing menu/loot-generation logic mutate that object, then
 * {@code LootrLootState.save(tag, provider)} it back into a (possibly new)
 * tag and {@code setAttached} that tag onto the block entity. The
 * {@code HolderLookup.Provider} itself comes from {@code level.registryAccess()},
 * exactly as the NeoForge attachment serializer obtains it from the
 * server/level rather than from the Codec chain.
 */
public final class ContainerInteractionHandler {

    public static void register() {
        UseBlockCallback.EVENT.register(ContainerInteractionHandler::onUseBlock);
        // Vanilla closes a disconnecting player's menu without telling the container.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> OpenTracker.forget(handler.getPlayer()));
    }

    private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ServerLevel level = (ServerLevel) world;
        if (!LootrConfig.isDimensionEnabled(level.dimension())) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hitResult.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity container) || !ModLootTags.isLootrEnabled(container)) {
            return InteractionResult.PASS;
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
            return InteractionResult.PASS;
        }

        if (isPlacingBlockAgainst(serverPlayer)) {
            return InteractionResult.PASS; // vanilla would place the held block, not open this
        }
        if (serverPlayer.isSpectator()) {
            // Vanilla would let a spectator resolve the loot table for everyone.
            return InteractionResult.CONSUME;
        }
        if (!vanillaWouldOpen(level.getBlockState(pos), level, pos, be)) {
            return InteractionResult.PASS; // blocked chest / obstructed shulker: let vanilla refuse
        }
        if (!container.canOpen(serverPlayer)) {
            // Locked (a /data merge Lock, or a LOCK component): vanilla's own menu path would refuse, but this
            // handler replaces that path. canOpen has already shown "is locked" and played the lock sound.
            return InteractionResult.CONSUME;
        }
        if (openChestLikeMenu(serverPlayer, container, level, menu, kind)) {
            afterOpen(serverPlayer, kind);
        }
        return InteractionResult.SUCCESS;
    }

    /** Which vanilla menu factory to hand the {@link PlayerScopedContainer} to. */
    private enum SimpleMenuProviderKind { CHEST_MENU, SHULKER_MENU }

    /** @return false if the container decayed instead of opening (nothing was opened). */
    private static boolean openChestLikeMenu(ServerPlayer player, RandomizableContainerBlockEntity entity,
                                           ServerLevel level, SimpleMenuProviderKind kind,
                                           OpenedAdvancements.Kind advancement) {
        HolderLookup.Provider provider = level.registryAccess();

        // Load: read the raw tag (or an empty default) and hydrate a live
        // LootrLootState from it - see class javadoc for why this manual
        // round-trip replaces NeoForge's transparent getData(LOOT_STATE).
        CompoundTag storedTag = entity.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag());
        LootrLootState state = new LootrLootState(27);
        state.load(storedTag, provider);

        java.util.UUID lootKey = TeamResolver.resolve(player);
        if (Refresh.refreshIfDue(level, entity, state, LootrConfig.refreshTicksFor(level, entity.getBlockPos(), entity.getLootTable()))) {
            CompoundTag resetTag = new CompoundTag();
            state.save(resetTag, provider);
            entity.setAttached(ModAttachments.LOOT_STATE, resetTag);
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
            // Only fire once per player per container, and only if loot was
            // actually generated - matches the NeoForge side's identical
            // check (see its javadoc for the upstream-equivalence note).
            OpenedAdvancements.award(player, advancement);
        }

        PlayerScopedContainer container = new PlayerScopedContainer(
                state, lootKey,
                // Save: every time the container's contents change, write
                // the live state straight back into a fresh tag and store
                // it on the attachment immediately - there's no separate
                // "on block entity save" hook to hang this off of here.
                items -> {
                    CompoundTag saveTag = new CompoundTag();
                    state.save(saveTag, provider);
                    entity.setAttached(ModAttachments.LOOT_STATE, saveTag);
                    entity.setChanged();
                },
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

        // Freshly generated loot must be persisted immediately, the same
        // way PlayerScopedContainer's onChanged callback does it - otherwise
        // a player who never touches a slot (peeks, then closes the menu)
        // would lose their generated loot on next open. Mirrors the
        // NeoForge side's entity.setChanged() call here, but on Fabric that
        // alone doesn't persist LootrLootState (see class javadoc), so the
        // manual tag round-trip is repeated here too.
        CompoundTag saveTag = new CompoundTag();
        state.save(saveTag, level.registryAccess());
        entity.setAttached(ModAttachments.LOOT_STATE, saveTag);
        entity.setChanged();
        Decay.track(level, entity);
        Refresh.track(level, entity);
        LootListeners.looted(level, entity, entity.getBlockPos(), player, lootTableKey);
    }

    /**
     * The advancement kind for one of the four loot container types, or null for
     * anything else. TrappedChest is tested before Chest because it extends it.
     * Mirrors the dispatch in {@link #onUseBlock}.
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
     * menu: rolls it if they have not looted it yet, empties their entry, and leaves
     * that (now empty) entry behind so the container counts as looted for them. The
     * caller decides where the returned stacks go. The state is saved before
     * returning, so a break that another mod cancels afterwards cannot duplicate the
     * loot. Uses the same load/mutate/save round-trip as the open path, because the
     * Fabric attachment is a raw tag (see the class javadoc).
     */
    public static java.util.List<ItemStack> takeLoot(ServerPlayer player, RandomizableContainerBlockEntity entity,
                                                      ServerLevel level) {
        HolderLookup.Provider provider = level.registryAccess();
        LootrLootState state = new LootrLootState(27);
        state.load(entity.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag()), provider);

        java.util.UUID lootKey = TeamResolver.resolve(player);
        // A due refresh empties the state; it is persisted by the save at the end.
        boolean refreshed = Refresh.refreshIfDue(level, entity, state, LootrConfig.refreshTicksFor(level, entity.getBlockPos(), entity.getLootTable()));
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

        CompoundTag saveTag = new CompoundTag();
        state.save(saveTag, provider);
        entity.setAttached(ModAttachments.LOOT_STATE, saveTag);
        entity.setChanged();
        if (refreshed) {
            Refresh.notifyRefreshed(level, entity);
        }
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
