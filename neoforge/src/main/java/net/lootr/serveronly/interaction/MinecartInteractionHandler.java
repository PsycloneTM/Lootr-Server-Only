package net.lootr.serveronly.interaction;

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

    private static void open(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        LootrLootState state = cart.getData(ModAttachments.LOOT_STATE);
        UUID lootKey = TeamResolver.resolve(player);
        // Entities are saved with their chunk automatically, so unlike the
        // block-entity handler there is no setChanged() to call after
        // mutating the attachment.
        state.refreshIfDue(level.getGameTime(), LootrConfig.REFRESH_TICKS.get());
        boolean first = !state.hasGeneratedFor(lootKey);
        if (first) {
            generate(player, lootKey, cart, state, level);
        }
        if (first && state.hasGeneratedFor(lootKey)) {
            OpenedAdvancements.award(player, OpenedAdvancements.Kind.MINECART);
        }

        PlayerScopedContainer container = new PlayerScopedContainer(state, lootKey, items -> { },
                // Same shape as vanilla: still present and within reach.
                p -> !cart.isRemoved() && p.distanceToSqr(cart) <= 64.0);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
                cart.getDisplayName()));
    }

    private static void generate(ServerPlayer player, UUID lootKey, MinecartChest cart,
                                 LootrLootState state, ServerLevel level) {
        ResourceKey<LootTable> key = cart.getLootTable();
        if (key == null) {
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, cart.position())
                .withParameter(LootContextParams.ATTACKING_ENTITY, cart)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);

        NonNullList<ItemStack> sized = NonNullList.withSize(state.getContainerSize(), ItemStack.EMPTY);
        int i = 0;
        for (ItemStack stack : table.getRandomItems(params)) {
            if (i >= sized.size()) break;
            sized.set(i++, stack);
        }
        state.setContents(lootKey, sized);
        state.markFirstGeneratedIfAbsent(level.getGameTime());
    }

    private MinecartInteractionHandler() {}
}
