package net.lootr.serveronly.interaction;

import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.interaction.LootRoller;
import net.lootr.serveronly.interaction.UnresolvedTables;
import net.lootr.serveronly.api.LootListeners;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import java.util.List;
import java.util.ArrayList;
import net.lootr.serveronly.registry.ModLootTags;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PlayerScopedContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.UUID;

public final class MinecartInteractionHandler {
    public static InteractionOutcome use(ServerPlayer serverPlayer, MinecartChest cart, ServerLevel level) {
        if (!LootrSettings.isDimensionEnabled(level.dimension())) {
            return InteractionOutcome.PASS;
        }
        if (!ModLootTags.isTableEnabled(cart.getLootTable()) || serverPlayer.isSpectator()) {
            return InteractionOutcome.PASS;
        }
        open(serverPlayer, cart, level);
        return InteractionOutcome.CONSUME;
    }

    public static boolean attack(ServerPlayer serverPlayer, MinecartChest cart, ServerLevel level) {
        if (!LootrSettings.isDimensionEnabled(level.dimension()) || !ModLootTags.isTableEnabled(cart.getLootTable())) {
            return false;
        }
        return handleAttack(serverPlayer, cart, level);
    }

    private static void open(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        LootrLootState state = LootStateStore.getOrCreate(cart, level);

        UUID lootKey = TeamResolver.resolve(player);
        if (Refresh.refreshIfDue(level, cart, state, LootrSettings.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable()))) {
            LootStateStore.save(cart, state, level);
            Refresh.notifyRefreshed(level, cart);
        }
        if (Decay.decayIfDue(level, cart, state)) {
            return;
        }
        boolean first = !state.hasGeneratedFor(lootKey);
        if (first) {
            generate(player, lootKey, cart, state, level);
            LootStateStore.save(cart, state, level);
        }
        if (first && state.hasGeneratedFor(lootKey)) {
            OpenedAdvancements.award(player, OpenedAdvancements.Kind.MINECART);
        }
        if (first) {
            Decay.announce(player, state, cart.blockPosition(), cart.getLootTable(), level.getGameTime());
        }

        PlayerScopedContainer container = new PlayerScopedContainer(state, lootKey,
                items -> LootStateStore.save(cart, state, level),
                p -> !cart.isRemoved() && p.distanceToSqr(cart) <= 64.0,
                null,
                p -> level.gameEvent(GameEvent.CONTAINER_CLOSE, cart.position(), GameEvent.Context.of(p))).owner(cart);
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

    private static boolean handleAttack(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        if (player.isSpectator()) {
            return false;
        }
        boolean fake = ContainerProtection.isFakePlayer(player);
        boolean sneaking = player.isShiftKeyDown();
        boolean creative = player.isCreative();
        boolean strict = LootrSettings.disableBreak();

        if (!ContainerProtection.breakExplicitlyAllowed(player) && (strict || !creative)) {
            if (LootrSettings.breakToDropLoot() && !sneaking && !fake) {
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
            } else if (LootrSettings.protectContainers()) {
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                return true;
            } else if (LootrSettings.requireSneakToBreak() && !sneaking) {
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                        "Sneak while breaking to destroy this loot container for everyone."), true);
                return true;
            }
        }

        if (LootrSettings.shouldDropPlayerLoot() && !fake) {
            for (ItemStack stack : takeLoot(player, cart, level)) {
                cart.spawnAtLocation(stack);
            }
        }
        return false;
    }

    public static List<ItemStack> takeLoot(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        LootrLootState state = LootStateStore.getOrCreate(cart, level);
        UUID lootKey = TeamResolver.resolve(player);
        boolean refreshed = Refresh.refreshIfDue(level, cart, state, LootrSettings.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable()));
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
        state.setContents(lootKey, contents);
        LootStateStore.save(cart, state, level);
        if (refreshed) {
            Refresh.notifyRefreshed(level, cart);
        }
        return taken;
    }

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
