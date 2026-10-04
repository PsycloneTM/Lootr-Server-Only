package net.lootr.serveronly.interaction;

import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.data.DecayTracker;
import net.lootr.serveronly.data.PositionTracker;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PlayerClears;
import net.lootr.serveronly.data.PlayerScopedContainer;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.lootr.serveronly.config.StructureTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decay: a looted container disappears from the world after a configurable
 * time, for everyone. Ported from upstream Lootr's decay, minus the client-side
 * "decaying" particles (there is no client mod to draw them).
 * <p>
 * <b>The timer</b> is not stored separately. It is the same per-container
 * {@code firstGeneratedGameTime} that refresh uses: set when ANY player first
 * loots the container, cleared by a refresh or {@code /lootr reset}. Decay is
 * due at {@code firstGeneratedGameTime + decay_value}. Because nothing new is
 * saved, worlds looted before decay existed simply start decaying the next time
 * someone opens those containers.
 * <p>
 * <b>What decays:</b> chests, trapped chests, barrels, shulker boxes and chest
 * minecarts whose loot table is converted by this mod AND covered by the decay
 * settings ({@code decay_all}, {@code decay_loot_tables}, {@code decay_modids}).
 * Pots, suspicious blocks and item frames never decay: a pot or brushable block
 * is destroyed by being looted in vanilla and this mod keeps it, and upstream's
 * frames cannot decay either.
 * <p>
 * <b>How it runs.</b> A once-a-second sweep on the server tick:
 * <ul>
 *     <li>Block containers are found through {@link DecayTracker}, a saved set
 *     of positions to check. A position is added when a container is looted or
 *     re-opened and dropped once the sweep finds nothing to do there. Positions
 *     in unloaded chunks are skipped, never loaded, and picked up when the chunk
 *     loads: decay depends on game time, so a container that expired while its
 *     chunk was unloaded goes within a second of the chunk coming back.</li>
 *     <li>Chest minecarts are entities, so the sweep lists the loaded ones.</li>
 * </ul>
 * A container a player has open is never removed; it goes once they close it.
 * A player who right-clicks a container that is already past its deadline (for
 * example one looted before decay was enabled) watches it decay instead of
 * opening it, exactly as if they had arrived a moment late.
 * <p>
 * <b>Removal must not drop the loot.</b> Vanilla resolves a container's loot
 * table when it is destroyed, which would spill one shared copy of the loot into
 * the world. So the loot table is cleared first: the real inventory was never
 * filled (loot only ever lives in the per-player entries), so nothing drops.
 * <p>
 * <b>Interaction with refresh.</b> They share the timer. A refresh (which happens
 * when someone opens an expired-for-refresh container) restarts it, so with a
 * short {@code refresh_value} a container that keeps being opened never decays.
 * If {@code refresh_value} is not shorter than {@code decay_value}, decay always
 * wins, because it is checked first and also runs in the background.
 * <p>
 * Not verified by a build; see the README for what to test.
 */
@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class Decay {

    // ---- timer math (no world access) ---------------------------------------

    /**
     * Decay is on, this dimension allows decay ({@code decay_dimensions}), and this table is
     * converted by the mod and covered by the decay settings.
     */
    public static boolean covers(@Nullable Level level, @Nullable BlockPos pos, @Nullable ResourceKey<LootTable> table) {
        return level != null
                && LootrConfig.decayTicks() > 0
                && LootrConfig.isLootTableEnabled(table)
                // decay_all / the lists, decay_dimensions (one more way in, as upstream), or a container inside
                // a structure tagged lootr_serveronly:decay.
                && (LootrConfig.isDecayLootTable(table)
                    || LootrConfig.isDecayDimension(level.dimension())
                    || (level instanceof ServerLevel serverLevel && pos != null
                        && StructureTags.isIn(serverLevel, pos, StructureTags.DECAY)));
    }

    /**
     * Ticks until a container decays: {@code 0} if it is due now, {@code -1} if no timer applies
     * (decay off, table not covered, or nobody has looted it yet), otherwise the ticks remaining.
     *
     * @param level          the level the container is in
     * @param pos            the container's position (for the structure-tag check)
     * @param firstGenerated the container's {@code firstGeneratedGameTime}, -1 if unstarted
     */
    public static long ticksLeft(@Nullable Level level, @Nullable BlockPos pos, long firstGenerated,
                                 @Nullable ResourceKey<LootTable> table, long now) {
        if (firstGenerated < 0 || !covers(level, pos, table)) {
            return -1;
        }
        return Math.max(0, firstGenerated + LootrConfig.decayTicks() - now);
    }

    /** {@code 4:05} or {@code 1:02:03}. Rounds up so a timer never shows 0:00 before it is due. */
    public static String format(long ticks) {
        long seconds = (Math.max(0, ticks) + 19) / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /**
     * One sentence for {@code /lootr info}: empty for things that never decay (pots, brushables,
     * frames), otherwise whether and when this one will. {@code holder} is the block entity or entity.
     */
    public static String infoText(Object holder, long firstGenerated, long now) {
        ResourceKey<LootTable> table;
        Level level;
        BlockPos pos;
        if (holder instanceof RandomizableContainerBlockEntity container
                && ContainerInteractionHandler.kindOf(container) != null) {
            table = container.getLootTable();
            level = container.getLevel();
            pos = container.getBlockPos();
        } else if (holder instanceof MinecartChest cart) {
            table = cart.getLootTable();
            level = cart.level();
            pos = cart.blockPosition();
        } else {
            return "";
        }
        if (!covers(level, pos, table)) {
            return "Decay is off.";
        }
        if (firstGenerated < 0) {
            return "Decay timer has not started.";
        }
        long left = ticksLeft(level, pos, firstGenerated, table, now);
        return left == 0 ? "Decays any moment." : "Decays in " + format(left) + ".";
    }

    // ---- hooks called from the open / loot paths ----------------------------

    /** Remember this container so the background sweep checks it. Call after its loot has been rolled. */
    public static void track(ServerLevel level, RandomizableContainerBlockEntity container) {
        if (covers(level, container.getBlockPos(), container.getLootTable())) {
            DecayTracker.get(level.getServer()).add(level.dimension(), container.getBlockPos());
        }
    }

    /** Tell {@code player}, in chat, how long this container has left. Silent if it is not decaying. */
    public static void announce(ServerPlayer player, LootrLootState state, BlockPos pos,
                                @Nullable ResourceKey<LootTable> table, long now) {
        if (LootrConfig.DISABLE_NOTIFICATIONS.get()) {
            return; // disable_notifications
        }
        long left = ticksLeft(player.level(), pos, state.getFirstGeneratedGameTime(), table, now);
        if (left < 0) {
            return;
        }
        // The timer only just started (this very tick): always say so. Afterwards, only once it is
        // within notification_delay (-1 = always), as upstream's "remaining time before notifying".
        boolean justStarted = left >= LootrConfig.decayTicks();
        int delay = LootrConfig.notificationDelay();
        if (!justStarted && delay >= 0 && left > delay) {
            return;
        }
        String text = left == 0 ? "This container is about to decay."
                : justStarted ? "The container begins to crumble at your touch! It will decay in " + format(left) + "."
                : "This container will decay in " + format(left) + ".";
        player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(text), false);
    }

    /**
     * If this block container is past its deadline and nobody has it open, removes it now and
     * returns true (the caller must not open a menu). Used when a player right-clicks something
     * the sweep has not reached yet.
     */
    public static boolean decayIfDue(ServerLevel level, RandomizableContainerBlockEntity container, LootrLootState state) {
        if (ticksLeft(level, container.getBlockPos(), state.getFirstGeneratedGameTime(), container.getLootTable(), level.getGameTime()) != 0
                || isViewed(level, container)) {
            return false;
        }
        decayBlock(level, container);
        return true;
    }

    /** Chest minecart version of {@link #decayIfDue(ServerLevel, RandomizableContainerBlockEntity, LootrLootState)}. */
    public static boolean decayIfDue(ServerLevel level, MinecartChest cart, LootrLootState state) {
        if (ticksLeft(level, cart.blockPosition(), state.getFirstGeneratedGameTime(), cart.getLootTable(), level.getGameTime()) != 0
                || isViewed(level, cart)) {
            return false;
        }
        decayCart(level, cart);
        return true;
    }

    // ---- the sweep -----------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Loads /lootr clear data (and refreshes its static cache) every tick, independent of
        // tick_delay, so a long sweep interval can never leave a clear unapplied. See PlayerClears.
        try {
            PlayerClears.get(server);
            if (server.getTickCount() % LootrConfig.TICK_DELAY.get() == 0) {
                sweep(server);
            }
        } catch (RuntimeException e) {
            // An exception escaping a tick event stops the whole server, and decay is optional.
            logFailure(e);
        }
    }

    /** Set after a failure is logged so a persistent fault is reported once, not once per sweep. */
    private static boolean failureLogged = false;

    private static void logFailure(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            LootrServerOnly.LOGGER.error("Decay sweep failed on one container; skipping it and carrying on. "
                    + "Repeats of this are not logged until a sweep completes cleanly.", e);
        }
    }

    private static void sweep(MinecraftServer server) {
        if (LootrConfig.decayTicks() > 0 && LootrConfig.PERFORM_DECAY_WHILE_TICKING.get()) {
            PositionTracker tracker = DecayTracker.get(server);
            tracker.syncSetting(LootrConfig.decayTicks());
            for (ServerLevel level : server.getAllLevels()) {
                if (!LootrConfig.isDimensionEnabled(level.dimension())) {
                    continue;
                }
                sweepBlocks(level, tracker);
                sweepCarts(level);
            }
        }
        // Background refresh shares this tick entry and tick_delay; it gates itself.
        Refresh.sweep(server);
        failureLogged = false; // a full sweep completed: report the next failure afresh
    }

    /** How long to leave a position alone when its chunk is unloaded or outside the world border. */
    private static final long DEFER_TICKS = 200;

    private static void sweepBlocks(ServerLevel level, PositionTracker tracker) {
        long now = level.getGameTime();
        var dimension = level.dimension();
        // Only positions whose "check at" hint has arrived come back; the rest cost nothing this sweep.
        for (BlockPos pos : tracker.takeDue(dimension, now)) {
            try {
                // getBlockEntity would LOAD (or even generate) the chunk. Wait for it instead; the chunk-load
                // hook (ChunkDiscovery) wakes the position the moment that chunk loads.
                if (!level.hasChunkAt(pos)) {
                    tracker.schedule(dimension, pos, now + DEFER_TICKS);
                    continue;
                }
                if (LootrConfig.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(pos)) {
                    tracker.schedule(dimension, pos, now + DEFER_TICKS); // check_world_border
                    continue;
                }
                if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)
                        || ContainerInteractionHandler.kindOf(container) == null
                        || !container.hasData(ModAttachments.LOOT_STATE)) {
                    continue; // broken, replaced, or never a loot container: not handed back, so no longer tracked
                }
                long left = ticksLeft(level, pos, container.getData(ModAttachments.LOOT_STATE).getFirstGeneratedGameTime(),
                        container.getLootTable(), now);
                if (left < 0) {
                    continue; // reset, refreshed, or no longer covered by the config: no longer tracked
                }
                if (left == 0) {
                    if (isViewed(level, container)) {
                        tracker.schedule(dimension, pos, now + 1); // goes once they close it
                    } else {
                        decayBlock(level, container); // also drops it from the tracker
                    }
                } else {
                    tracker.schedule(dimension, pos, now + left); // the real deadline, from the container's own timer
                }
            } catch (RuntimeException e) {
                tracker.schedule(dimension, pos, now + DEFER_TICKS); // do not lose it; try again shortly
                logFailure(e); // one bad container must not stop the rest decaying
            }
        }
    }

    private static void sweepCarts(ServerLevel level) {
        long now = level.getGameTime();
        List<? extends MinecartChest> carts = level.getEntities(EntityType.CHEST_MINECART,
                cart -> cart.hasData(ModAttachments.LOOT_STATE));
        for (MinecartChest cart : carts) {
            try {
                long left = ticksLeft(level, cart.blockPosition(), cart.getData(ModAttachments.LOOT_STATE).getFirstGeneratedGameTime(),
                        cart.getLootTable(), now);
                if (left == 0 && !isViewed(level, cart)) {
                    decayCart(level, cart);
                }

            } catch (RuntimeException e) {
                logFailure(e); // one bad container must not stop the rest decaying
            }
        }
    }

    // ---- removal -------------------------------------------------------------

    private static void decayBlock(ServerLevel level, RandomizableContainerBlockEntity container) {
        BlockPos pos = container.getBlockPos();
        LootListeners.decaying(level, container, pos, container.getLootTable());
        // With the table gone, vanilla's own drop-on-removal path has nothing to unpack.
        container.setLootTable(null);
        if (LootrConfig.REPLACE_WHEN_DECAYED.get()) {
            // Keep the block as an ordinary, empty vanilla container: with no loot table it is no
            // longer managed, and its real inventory was never filled.
            container.setChanged();
            level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.25, 0.25, 0.25, 0.02);
            DecayTracker.get(level.getServer()).remove(level.dimension(), pos);
            return;
        }
        // false = no block drops. Still plays the break sound and particles, which is the
        // only "it decayed" cue a vanilla client gets.
        level.destroyBlock(pos, false);
        DecayTracker.get(level.getServer()).remove(level.dimension(), pos);
    }

    private static void decayCart(ServerLevel level, MinecartChest cart) {
        LootListeners.decaying(level, cart, cart.blockPosition(), cart.getLootTable());
        // discard() drops the cart's contents, and reading them resolves the loot table first.
        ContainerProtection.clearCartLootTable(cart); // the cart mixin otherwise keeps a loot cart's table
        level.sendParticles(ParticleTypes.POOF, cart.getX(), cart.getY(0.5), cart.getZ(), 8, 0.25, 0.25, 0.25, 0.02);
        level.playSound(null, cart.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (LootrConfig.REPLACE_WHEN_DECAYED.get()) {
            return; // stays as an ordinary empty minecart chest
        }
        cart.discard();
    }

    /**
     * True if any player in this level has a loot menu open on {@code owner} (the block entity or
     * entity a {@link PlayerScopedContainer} was created with). Every loot menu's first slot is a
     * slot of that container, for both {@code ChestMenu} and {@code ShulkerBoxMenu}.
     */
    public static boolean isViewed(ServerLevel level, Object owner) {
        for (ServerPlayer player : level.players()) {
            AbstractContainerMenu menu = player.containerMenu;
            if (menu != null && !menu.slots.isEmpty()
                    && menu.slots.get(0).container instanceof PlayerScopedContainer view
                    && view.getOwner() == owner) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every player in {@code level} who currently has a loot menu open on {@code owner} (the block
     * entity or entity a {@link PlayerScopedContainer} was created with). Used by {@code /lootr openers}.
     */
    public static List<ServerPlayer> viewers(ServerLevel level, Object owner) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            AbstractContainerMenu menu = player.containerMenu;
            if (menu != null && !menu.slots.isEmpty()
                    && menu.slots.get(0).container instanceof PlayerScopedContainer view
                    && view.getOwner() == owner) {
                out.add(player);
            }
        }
        return out;
    }

    private Decay() {}
}
