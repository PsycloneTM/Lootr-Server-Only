package net.lootr.serveronly.interaction;

import net.lootr.serveronly.registry.ModLootTags;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.lootr.serveronly.mixin.AccessorBrushableBlockEntity;
import net.lootr.serveronly.LootrServerOnly;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
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
 * Explosions: handled here via NeoForge's ExplosionEvent.Detonate.
 * <p>
 * Not verified by a build.
 */
@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class ContainerProtection {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            handleBreak(event.getPlayer(), level, event.getPos(), () -> event.setCanceled(true));
        }
    }

    /** Explosions leave loot containers standing. */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && blastSpares()) {
            event.getAffectedBlocks().removeIf(pos -> isManagedAt(level, pos));
        }
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
     * This mod's own entity version of {@code blast_resistant} (upstream has none): TNT and an uncharged creeper are survived.
     * A damage source does not carry the explosion's power, so this goes by the entity that caused it;
     * anything else (end crystal, charged creeper, bed...) counts as a big blast.
     */
    private static boolean isSmallBlast(DamageSource source) {
        Entity cause = source.getDirectEntity();
        return cause instanceof PrimedTnt || (cause instanceof Creeper creeper && !creeper.isPowered());
    }

    /** {@code enable_break}, or a fake player with {@code enable_fake_player_break}: breaking is explicitly allowed. */
    public static boolean breakExplicitlyAllowed(Player player) {
        return LootrConfig.ENABLE_BREAK.get() || (isFakePlayer(player) && LootrConfig.ENABLE_FAKE_PLAYER_BREAK.get());
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
    public static boolean isManagedEntity(Entity entity, DamageSource source) {
        if (!(entity instanceof ItemFrame) && !(entity instanceof MinecartChest)) {
            return false;
        }
        // A chest minecart a player is explicitly allowed to break is not protected from that player.
        if (entity instanceof MinecartChest && source.getEntity() instanceof Player breaker
                && breakExplicitlyAllowed(breaker)) {
            return false;
        }
        // protect_containers: immune to everything. Otherwise blast_immune: immune to explosions only.
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!LootrConfig.PROTECT_CONTAINERS.get()
                && !(explosion && (LootrConfig.BLAST_IMMUNE.get() || (LootrConfig.BLAST_RESISTANT.get() && isSmallBlast(source))))) {
            return false;
        }
        if (!(entity.level() instanceof ServerLevel level)
                || !LootrConfig.isDimensionEnabled(level.dimension())) {
            return false;
        }
        if (entity instanceof ItemFrame frame) {
            return ItemFrameMarker.isMarked(frame) && !frame.getItem().isEmpty();
        }
        return ModLootTags.isTableEnabled(((MinecartChest) entity).getLootTable());
    }

    /**
     * A marked, non-empty item frame in an enabled dimension, regardless of any protection
     * setting. Used by the item-frame self-support mixin.
     */
    public static boolean isManagedFrame(ItemFrame frame) {
        return frame.level() instanceof ServerLevel level
                && LootrConfig.isDimensionEnabled(level.dimension())
                && ItemFrameMarker.isMarked(frame)
                && !frame.getItem().isEmpty();
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

    /** Set only while {@link #clearCartLootTable} runs; the cart mixin lets the table be cleared then. */
    private static boolean clearingCartTable = false;

    /** True while this mod itself is clearing a cart's loot table (decay), so the cart mixin must not cancel it. */
    public static boolean isClearingCartTable() {
        return clearingCartTable;
    }

    /**
     * Decay's way to turn a loot cart into an ordinary one (or to empty it before removal). The cart mixin cancels
     * {@code setLootTable(null)} for managed carts, because vanilla's unpack does that to end per-player loot; this
     * is the one deliberate exception. Server thread only.
     */
    public static void clearCartLootTable(AbstractMinecartContainer cart) {
        clearingCartTable = true;
        try {
            cart.setLootTable(null);
        } finally {
            clearingCartTable = false;
        }
    }

    /**
     * The chest-minecart twin of {@link #blocksUnpack(RandomizableContainerBlockEntity)}: true for a loot chest
     * minecart whose own inventory vanilla must not touch (a hopper above, below or beside it, a dispenser,
     * {@code /item}...). Vanilla would roll the loot table into the cart's shared inventory and clear the table,
     * ending per-player loot for that cart. Players never reach this path: {@code MinecartInteractionHandler}
     * cancels the open and hands them a per-player container.
     */
    public static boolean blocksCartUnpack(AbstractMinecartContainer cart) {
        return cart instanceof MinecartChest
                && cart.getLootTable() != null
                && cart.level() instanceof ServerLevel level
                && LootrConfig.isDimensionEnabled(level.dimension())
                && ModLootTags.isTableEnabled(cart.getLootTable());
    }

    /** Predicate for {@code MixinEntitySelector}: false for a managed loot cart, so hoppers never see it. */
    public static boolean isNotManagedCart(Entity entity) {
        return !(entity instanceof AbstractMinecartContainer cart && blocksCartUnpack(cart));
    }

    /** Resistance {@code blast_resistant} gives a managed container; upstream's value. */
    public static final float BLAST_RESISTANT_RESISTANCE = 16.0F;

    /**
     * True if every explosion must leave loot containers standing, by removing them from the blast list:
     * {@code protect_containers} or {@code blast_immune}. {@code blast_resistant} is deliberately not part
     * of this - it works through the explosion's own resistance lookup instead, see
     * {@link #blastResistantResistance}.
     */
    public static boolean blastSpares() {
        return LootrConfig.PROTECT_CONTAINERS.get()
                || LootrConfig.BLAST_IMMUNE.get();
    }

    /**
     * {@code blast_resistant}: the resistance vanilla's explosion should use for the block at {@code pos}.
     * Returns {@code base} itself (same instance) when nothing changes, so the caller can compare by identity.
     * For a managed loot container with the option on, returns {@link #BLAST_RESISTANT_RESISTANCE}
     * (upstream sets exactly 16; a base value already at or above it is left alone).
     * <p>
     * Runs for every block a ray steps through, so the cheap tests come first: the option, an actual
     * resistance to raise, then {@code hasBlockEntity} before any block entity lookup.
     */
    public static java.util.Optional<Float> blastResistantResistance(java.util.Optional<Float> base,
                                                                      net.minecraft.world.level.BlockGetter reader,
                                                                      BlockPos pos,
                                                                      net.minecraft.world.level.block.state.BlockState state) {
        if (!LootrConfig.BLAST_RESISTANT.get() || base.isEmpty() || base.get() >= BLAST_RESISTANT_RESISTANCE
                || !state.hasBlockEntity() || !(reader instanceof Level level)
                || !isManagedAt(level, pos)) {
            return base;
        }
        return java.util.Optional.of(BLAST_RESISTANT_RESISTANCE);
    }

    /**
     * Automation, not a real player: a fake-player class from the loader's API, or a server
     * player with no network connection. Same test as upstream.
     */
    public static boolean isFakePlayer(Player player) {
        return player instanceof FakePlayer
                || (player instanceof ServerPlayer sp && sp.connection == null);
    }

    /**
     * Decides what a player breaking a managed block does, in upstream Lootr's order
     * (plus this mod's own options, which keep working):
     * <ol>
     *     <li>{@code enable_break}, or a fake player with {@code enable_fake_player_break}:
     *     allowed.</li>
     *     <li>Outside {@code disable_break} mode a creative player is never restricted (this
     *     mod's long-standing behavior) and goes straight to the last step.</li>
     *     <li>{@code break_to_drop_loot}, not sneaking, chest/barrel/shulker, real player: collect
     *     the loot and cancel.</li>
     *     <li>{@code disable_break}: survival is always refused; creative is refused unless
     *     sneaking. Otherwise {@code protect_containers} refuses, and failing that
     *     {@code require_sneak_to_break} refuses a non-sneaking break.</li>
     * </ol>
     * If the break goes ahead and {@code should_drop_player_loot} is on, the breaker's own
     * loot spills at the block first (not for fake players). With every option at its default
     * this is the old behavior: survivors cannot break, creative can.
     */
    private static void handleBreak(Player player, ServerLevel level, BlockPos pos, Runnable cancel) {
        if (player.isSpectator() || !isManagedAt(level, pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        RandomizableContainerBlockEntity container = be instanceof RandomizableContainerBlockEntity c ? c : null;
        ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;
        boolean fake = isFakePlayer(player);
        boolean sneaking = player.isShiftKeyDown();
        boolean creative = player.isCreative();
        boolean strict = LootrConfig.DISABLE_BREAK.get();

        boolean allowed = LootrConfig.ENABLE_BREAK.get() || (fake && LootrConfig.ENABLE_FAKE_PLAYER_BREAK.get());
        if (!allowed && (strict || !creative)) {
            if (LootrConfig.BREAK_TO_DROP_LOOT.get() && !sneaking && !fake
                    && container != null && serverPlayer != null
                    && ContainerInteractionHandler.kindOf(container) != null) {
                ContainerInteractionHandler.collectLoot(serverPlayer, container, level);
                cancel.run();
                return;
            }
            if (strict) {
                if (!creative) {
                    cancel.run();
                    player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                    return;
                }
                if (!sneaking) {
                    cancel.run();
                    player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                            "Sneak while breaking to destroy this loot container for everyone."), true);
                    return;
                }
            } else if (LootrConfig.PROTECT_CONTAINERS.get()) {
                cancel.run();
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                return;
            } else if (LootrConfig.REQUIRE_SNEAK_TO_BREAK.get() && !sneaking) {
                cancel.run();
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                        "Sneak while breaking to destroy this loot container for everyone."), true);
                return;
            }
        }

        // The container is going to be destroyed.
        if (LootrConfig.SHOULD_DROP_PLAYER_LOOT.get() && container != null && serverPlayer != null && !fake
                && ContainerInteractionHandler.kindOf(container) != null) {
            for (ItemStack stack : ContainerInteractionHandler.takeLoot(serverPlayer, container, level)) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    private ContainerProtection() {}
}
