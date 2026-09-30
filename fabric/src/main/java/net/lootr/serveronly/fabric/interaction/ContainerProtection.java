package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.registry.ModLootTags;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.registry.ItemFrameMarker;
import net.lootr.serveronly.fabric.mixin.AccessorBrushableBlockEntity;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps loot containers alive. Without this, one player breaking a loot
 * chest, barrel, shulker box, pot or suspicious block destroys it for everyone,
 * and an explosion does the same.
 * <p>
 * "Loot container" = a vanilla block entity that still has an unresolved loot
 * table (the same test every handler in this mod uses). Survival and
 * adventure players cannot break one; creative players can, like the other
 * handlers. Toggle: {@code protect_containers} in the config.
 * <p>
 * Explosions: handled by MixinExplosion (Fabric has no explosion event).
 * <p>
 * Not verified by a build.
 */
public final class ContainerProtection {

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) {
                return true;
            }
            boolean[] denied = {false};
            handleBreak(player, level, pos, () -> denied[0] = true);
            return !denied[0];
        });
    }

    /** True if {@code be} is a vanilla loot container Lootr manages. */
    public static boolean isManaged(@Nullable BlockEntity be) {
        if (be instanceof RandomizableContainerBlockEntity container) {
            return ModLootTags.isLootrEnabled(container);
        }
        if (be instanceof BrushableBlockEntity brushable) {
            return ModLootTags.isTableEnabled(((AccessorBrushableBlockEntity) brushable).lootr$getLootTable());
        }
        // A decorated pot is a plain BlockEntity (not a RandomizableContainerBlockEntity),
        // so it needs its own case or explosions and projectiles would destroy it
        // for everyone. Its loot table is never unpacked by this mod, so a pot
        // that still has one is a pot that is still Lootr-managed.
        if (be instanceof DecoratedPotBlockEntity pot) {
            return ModLootTags.isTableEnabled(pot.getLootTable());
        }
        return false;
    }

    /**
     * True for an entity this mod keeps alive: a marked, non-empty item frame
     * or a chest minecart that still has an enabled loot table. Used to make
     * such entities immune to everything except a creative player, because an
     * arrow, explosion, fire or mob destroying one would pop the shared loot
     * out for everyone (and end it for every player who has not looted it yet).
     * <p>
     * Called for EVERY damage check on EVERY entity, so the type test comes
     * first and everything else is skipped for the overwhelmingly common case.
     */
    public static boolean isManagedEntity(Entity entity) {
        if (!(entity instanceof ItemFrame) && !(entity instanceof MinecartChest)) {
            return false;
        }
        if (!(entity.level() instanceof ServerLevel level)
                || !LootrConfig.protectContainers()
                || !LootrConfig.isDimensionEnabled(level.dimension())) {
            return false;
        }
        if (entity instanceof ItemFrame frame) {
            return ItemFrameMarker.isMarked(frame) && !frame.getItem().isEmpty();
        }
        return ModLootTags.isTableEnabled(((MinecartChest) entity).getLootTable());
    }

    /** Managed AND in a dimension this mod is enabled for. */
    public static boolean isManagedAt(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel
                && LootrConfig.isDimensionEnabled(serverLevel.dimension())
                && isManaged(level.getBlockEntity(pos));
    }

    /**
     * True when vanilla must NOT resolve this container's loot table on its
     * own (hopper, comparator, break-drop path). Resolving it would put one
     * shared copy of the loot into the real inventory and clear the table,
     * ending per-player loot for that block.
     */
    public static boolean blocksUnpack(RandomizableContainerBlockEntity container) {
        return ModLootTags.isLootrEnabled(container)
                && container.getLevel() instanceof ServerLevel level
                && LootrConfig.isDimensionEnabled(level.dimension());
    }

    /**
     * Decides what a player breaking a managed block does. Order for a non-creative player:
     * <ol>
     *     <li>{@code break_to_drop_loot}, not sneaking, chest/barrel/shulker: collect the
     *     player's loot and cancel the break.</li>
     *     <li>{@code protect_containers}: cancel.</li>
     *     <li>{@code require_sneak_to_break}, not sneaking: cancel with a hint.</li>
     * </ol>
     * Creative players skip all three (like every other handler in this mod). If the
     * break goes ahead and {@code should_drop_player_loot} is on, the breaker's own loot
     * spills at the block first. With every option at its default this is exactly the
     * old behavior: survivors cannot break, creative can.
     */
    private static void handleBreak(Player player, ServerLevel level, BlockPos pos, Runnable cancel) {
        if (player.isSpectator() || !isManagedAt(level, pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        RandomizableContainerBlockEntity container = be instanceof RandomizableContainerBlockEntity c ? c : null;
        ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;

        if (!player.isCreative()) {
            if (LootrConfig.breakToDropLoot() && !player.isShiftKeyDown()
                    && container != null && serverPlayer != null
                    && ContainerInteractionHandler.kindOf(container) != null) {
                ContainerInteractionHandler.collectLoot(serverPlayer, container, level);
                cancel.run();
                return;
            }
            if (LootrConfig.protectContainers()) {
                cancel.run();
                player.displayClientMessage(Component.literal("Loot containers can't be broken."), true);
                return;
            }
            if (LootrConfig.requireSneakToBreak() && !player.isShiftKeyDown()) {
                cancel.run();
                player.displayClientMessage(Component.literal(
                        "Sneak while breaking to destroy this loot container for everyone."), true);
                return;
            }
        }

        // The container is going to be destroyed.
        if (LootrConfig.shouldDropPlayerLoot() && container != null && serverPlayer != null
                && ContainerInteractionHandler.kindOf(container) != null) {
            for (ItemStack stack : ContainerInteractionHandler.takeLoot(serverPlayer, container, level)) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    private ContainerProtection() {}
}
