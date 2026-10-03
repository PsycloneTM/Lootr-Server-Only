package net.lootr.serveronly.interaction;

import net.lootr.serveronly.api.LootListeners;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import java.util.List;
import java.util.ArrayList;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.lootr.serveronly.registry.ModLootTags;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PlayerScopedContainer;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Per-player loot for chest minecarts (e.g. mineshaft carts) - the entity
 * counterpart of {@link ContainerInteractionHandler}.
 * <p>
 * A {@link MinecartChest} implements vanilla's {@code ContainerEntity}: it has
 * a {@code getLootTable()} and opens a plain {@code ChestMenu.threeRows}, so
 * it fits the same "cancel the click, open a menu backed by a per-player
 * Container" pattern as the four block containers. The only differences are
 * the event ({@code EntityInteract}) and that the state lives on an entity
 * attachment instead of a block-entity attachment. Cancelling before vanilla
 * runs matters just as much as for blocks: vanilla's own path would unpack the
 * loot table into the shared item list for the first player and clear it for
 * everyone else.
 */
@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class MinecartInteractionHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        // The event fires once per hand; only act on the main hand so the
        // menu isn't opened twice.
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof MinecartChest cart)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        if (!LootrConfig.isDimensionEnabled(level.dimension())) {
            return;
        }
        if (!ModLootTags.isTableEnabled(cart.getLootTable())) {
            return; // not a loot cart, already looted by vanilla, or blacklisted - leave alone
        }
        if (player.isSpectator()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        open(player, cart, level);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof MinecartChest cart)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!LootrConfig.isDimensionEnabled(level.dimension()) || !ModLootTags.isTableEnabled(cart.getLootTable())) {
            return; // not a loot cart, already looted by vanilla, or blacklisted - leave alone
        }
        if (handleAttack(player, cart, level)) {
            event.setCanceled(true);
        }
    }

    private static void open(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        LootrLootState state = cart.getData(ModAttachments.LOOT_STATE);
        UUID lootKey = TeamResolver.resolve(player);
        // Entities are saved with their chunk automatically, so unlike the
        // block-entity handler there is no setChanged() to call after
        // mutating the attachment.
        if (Refresh.refreshIfDue(level, cart, state, LootrConfig.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable()))) {
            Refresh.notifyRefreshed(level, cart);
        }
        if (Decay.decayIfDue(level, cart, state)) {
            return; // past its deadline: it decays instead of opening
        }
        boolean first = !state.hasGeneratedFor(lootKey);
        if (first) {
            generate(player, lootKey, cart, state, level);
        }
        if (first && state.hasGeneratedFor(lootKey)) {
            OpenedAdvancements.award(player, OpenedAdvancements.Kind.MINECART);
        }
        if (first) {
            Decay.announce(player, state, cart.blockPosition(), cart.getLootTable(), level.getGameTime());
        }

        PlayerScopedContainer container = new PlayerScopedContainer(state, lootKey, items -> { },
                // Same shape as vanilla: still present and within reach.
                p -> !cart.isRemoved() && p.distanceToSqr(cart) <= 64.0,
                null,
                // MinecartChest.stopOpen: a vanilla cart sends this when a player closes it. Also runs when a
                // player disconnects with the menu open, as in vanilla.
                p -> level.gameEvent(GameEvent.CONTAINER_CLOSE, cart.position(), GameEvent.Context.of(p))).owner(cart);
        // What vanilla's MinecartChest.interact does after a successful open: the CONTAINER_OPEN game event
        // (sculk sensors, wardens) and angering nearby piglins. The close event is sent from stopOpen below.
        if (player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
                cart.getDisplayName())).isPresent()) {
            cart.gameEvent(GameEvent.CONTAINER_OPEN, player);
            PiglinAi.angerNearbyPiglins(player, true);
        }
    }

    private static void generate(ServerPlayer player, UUID lootKey, MinecartChest cart,
                                 LootrLootState state, ServerLevel level) {
        ResourceKey<LootTable> key = cart.getLootTable();
        if (key == null) {
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        UnresolvedTables.check(player, key, table);
        LootRoller.triggerGenerateLoot(player, key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, cart.position())
                .withParameter(LootContextParams.ATTACKING_ENTITY, cart)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);

        NonNullList<ItemStack> sized = LootRoller.rollInto(
                table, params, cart.getLootTableSeed(), state.getContainerSize());
        state.setContents(lootKey, sized);
        state.markFirstGeneratedIfAbsent(level.getGameTime());
        LootListeners.looted(level, cart, cart.blockPosition(), player, key);
    }


    /**
     * What a player hitting a managed chest minecart does, in the same order as {@code ContainerProtection}'s block
     * rules: {@code enable_break} (or a fake player with {@code enable_fake_player_break}) allows it;
     * {@code break_to_drop_loot} gives a non-sneaking real player their own loot and cancels the hit;
     * {@code disable_break} refuses survival always and creative unless sneaking; otherwise
     * {@code protect_containers} refuses, and failing that {@code require_sneak_to_break} refuses a
     * non-sneaking hit. If the hit goes ahead and {@code should_drop_player_loot} is on, the hitter's loot
     * drops at the cart first. With every option at its default survival cannot break a cart (the existing
     * invulnerability) and creative can. Returns true if the hit must be cancelled.
     */
    private static boolean handleAttack(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        if (player.isSpectator()) {
            return false;
        }
        boolean fake = ContainerProtection.isFakePlayer(player);
        boolean sneaking = player.isShiftKeyDown();
        boolean creative = player.isCreative();
        boolean strict = LootrConfig.DISABLE_BREAK.get();

        if (!ContainerProtection.breakExplicitlyAllowed(player) && (strict || !creative)) {
            if (LootrConfig.BREAK_TO_DROP_LOOT.get() && !sneaking && !fake) {
                collectLoot(player, cart, level);
                return true;
            }
            if (strict) {
                if (!creative) {
                    player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                    return true;
                }
                if (!sneaking) {
                    player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                            "Sneak while breaking to destroy this loot container for everyone."), true);
                    return true;
                }
            } else if (LootrConfig.PROTECT_CONTAINERS.get()) {
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                return true;
            } else if (LootrConfig.REQUIRE_SNEAK_TO_BREAK.get() && !sneaking) {
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                        "Sneak while breaking to destroy this loot container for everyone."), true);
                return true;
            }
        }

        // The hit goes ahead.
        if (LootrConfig.SHOULD_DROP_PLAYER_LOOT.get() && !fake) {
            for (ItemStack stack : takeLoot(player, cart, level)) {
                cart.spawnAtLocation(stack);
            }
        }
        return false;
    }

    /**
     * Takes this player's (or team's) loot out of {@code cart} WITHOUT opening a menu: rolls it if they have not
     * looted it yet and empties their entry in place, leaving it behind so the cart counts as looted for them.
     * The chest-minecart twin of {@code ContainerInteractionHandler.takeLoot}.
     */
    public static List<ItemStack> takeLoot(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        LootrLootState state = cart.getData(ModAttachments.LOOT_STATE);
        UUID lootKey = TeamResolver.resolve(player);
        if (Refresh.refreshIfDue(level, cart, state, LootrConfig.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable()))) {
            Refresh.notifyRefreshed(level, cart);
        }
        if (!state.hasGeneratedFor(lootKey)) {
            generate(player, lootKey, cart, state, level);
        }
        List<ItemStack> taken = new ArrayList<>();
        NonNullList<ItemStack> contents = state.getContentsOrEmpty(lootKey);
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty()) {
                taken.add(stack);
                contents.set(i, ItemStack.EMPTY);
            }
        }
        state.setContents(lootKey, contents); // entity attachments are saved with the entity
        return taken;
    }

    /** The "break to drop loot" action for carts: the loot goes straight into the player's inventory. */
    public static void collectLoot(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        List<ItemStack> loot = takeLoot(player, cart, level);
        for (ItemStack stack : loot) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        if (!loot.isEmpty()) {
            OpenedAdvancements.award(player, OpenedAdvancements.Kind.MINECART);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(loot.isEmpty()
                ? "You have already looted this."
                : "You collected your loot."), true);
    }

    private MinecartInteractionHandler() {}
}
