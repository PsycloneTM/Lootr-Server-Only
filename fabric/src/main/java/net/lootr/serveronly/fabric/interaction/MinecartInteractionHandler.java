package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.registry.ModLootTags;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.lootr.serveronly.fabric.advancement.OpenedAdvancements;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.config.TeamResolver;
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

/**
 * Fabric twin of the NeoForge {@code MinecartInteractionHandler}; see that
 * class for the reasoning. Differences: {@link UseEntityCallback} instead of
 * {@code EntityInteract}, and the Fabric attachment stores a raw
 * {@link CompoundTag}, so state is loaded, mutated and written back by hand
 * exactly as {@link ContainerInteractionHandler} does for blocks.
 */
public final class MinecartInteractionHandler {

    public static void register() {
        UseEntityCallback.EVENT.register(MinecartInteractionHandler::onUseEntity);
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

    private static void open(ServerPlayer player, MinecartChest cart, ServerLevel level) {
        HolderLookup.Provider provider = level.registryAccess();
        CompoundTag stored = cart.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag());
        LootrLootState state = new LootrLootState(27);
        state.load(stored, provider);

        UUID lootKey = TeamResolver.resolve(player);
        if (state.refreshIfDue(level.getGameTime(), LootrConfig.refreshTicks())) {
            persist(cart, state, provider);
        }
        boolean first = !state.hasGeneratedFor(lootKey);
        if (first) {
            generate(player, lootKey, cart, state, level);
            persist(cart, state, provider);
        }
        if (first && state.hasGeneratedFor(lootKey)) {
            OpenedAdvancements.award(player, OpenedAdvancements.Kind.MINECART);
        }

        PlayerScopedContainer container = new PlayerScopedContainer(state, lootKey,
                items -> persist(cart, state, provider),
                // Same shape as vanilla: still present and within reach.
                p -> !cart.isRemoved() && p.distanceToSqr(cart) <= 64.0);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
                cart.getDisplayName()));
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
