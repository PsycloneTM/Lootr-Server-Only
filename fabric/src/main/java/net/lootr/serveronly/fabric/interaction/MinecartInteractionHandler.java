package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.interaction.LootRoller;
import net.lootr.serveronly.interaction.UnresolvedTables;
import net.lootr.serveronly.api.LootListeners;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import java.util.List;
import java.util.ArrayList;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.lootr.serveronly.fabric.registry.ModLootTags;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.lootr.serveronly.advancement.OpenedAdvancements;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.data.PlayerScopedContainer;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class MinecartInteractionHandler {

    public static void register() {
        UseEntityCallback.EVENT.register(MinecartInteractionHandler::onUseEntity);
        AttackEntityCallback.EVENT.register(MinecartInteractionHandler::onAttackEntity);
    }

    private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand,
                                                 Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof MinecartChest cart)) {
            return InteractionResult.PASS;
        }
        ServerLevel level = (ServerLevel) world;
        if (!LootrConfig.isDimensionEnabled(level.dimension())) {
            return InteractionResult.PASS;
        }
        if (!ModLootTags.isTableEnabled(cart.getLootTable()) || serverPlayer.isSpectator()) {
            return InteractionResult.PASS;
        }
        open(serverPlayer, cart, level);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult onAttackEntity(Player player, Level world, InteractionHand hand,
                                                    Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof MinecartChest cart)) {
            return InteractionResult.PASS;
        }
        ServerLevel level = (ServerLevel) world;
        if (!LootrConfig.isDimensionEnabled(level.dimension()) || !ModLootTags.isTableEnabled(cart.getLootTable())) {
            return InteractionResult.PASS;
        }
        return handleAttack(serverPlayer, cart, level) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static void open(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        HolderLookup.Provider provider = level.registryAccess();
        CompoundTag stored = cart.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag());
        LootrLootState state = new LootrLootState(27);
        state.load(stored, provider);

        UUID lootKey = TeamResolver.resolve(player);
        if (Refresh.refreshIfDue(level, cart, state, LootrConfig.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable()))) {
            persist(cart, state, provider);
            Refresh.notifyRefreshed(level, cart);
        }
        if (Decay.decayIfDue(level, cart, state)) {
            return;
        }
        boolean first = !state.hasGeneratedFor(lootKey);
        if (first) {
            generate(player, lootKey, cart, state, level);
            persist(cart, state, provider);
        }
        if (first && state.hasGeneratedFor(lootKey)) {
            OpenedAdvancements.award(player, OpenedAdvancements.Kind.MINECART);
        }
        if (first) {
            Decay.announce(player, state, cart.blockPosition(), cart.getLootTable(), level.getGameTime());
        }

        PlayerScopedContainer container = new PlayerScopedContainer(state, lootKey,
                items -> persist(cart, state, provider),
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

    private static void persist(MinecartChest cart, LootrLootState state, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        state.save(tag, provider);
        cart.setAttached(ModAttachments.LOOT_STATE, tag);
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
        boolean strict = LootrConfig.disableBreak();

        if (!ContainerProtection.breakExplicitlyAllowed(player) && (strict || !creative)) {
            if (LootrConfig.breakToDropLoot() && !sneaking && !fake) {
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
            } else if (LootrConfig.protectContainers()) {
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                return true;
            } else if (LootrConfig.requireSneakToBreak() && !sneaking) {
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                        "Sneak while breaking to destroy this loot container for everyone."), true);
                return true;
            }
        }

        if (LootrConfig.shouldDropPlayerLoot() && !fake) {
            for (ItemStack stack : takeLoot(player, cart, level)) {
                cart.spawnAtLocation(stack);
            }
        }
        return false;
    }

    public static List<ItemStack> takeLoot(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        HolderLookup.Provider provider = level.registryAccess();
        LootrLootState state = new LootrLootState(27);
        state.load(cart.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag()), provider);
        UUID lootKey = TeamResolver.resolve(player);
        boolean refreshed = Refresh.refreshIfDue(level, cart, state, LootrConfig.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable()));
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
        persist(cart, state, provider);
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
