package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.api.LootListeners;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.data.RefreshTracker;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.List;

/**
 * Background refresh, the twin of {@link Decay}'s sweep: loot containers whose refresh timer has run
 * out are reset (every player's loot forgotten, timer cleared) without anyone having to open them.
 * <p>
 * Same design as decay: block containers are found through {@link RefreshTracker} (a saved position set
 * filled when a container is looted or opened, and, with {@code start_refresh_while_ticking}, when its
 * chunk loads - see {@link ChunkDiscovery}); unloaded chunks are skipped, never loaded; chest minecarts
 * are found by listing the loaded ones. It runs from {@link Decay}'s tick entry every {@code tick_delay}
 * ticks, after the decay sweep.
 * <p>
 * <b>Never while the menu is open.</b> Resetting a container under a player who is looking inside it
 * would empty the logical inventory their menu is backed by (upstream has an open issue about exactly
 * this). A due container that someone is viewing is left alone and refreshed on a later sweep, or on the
 * next open, once the menu has closed. {@link #refreshIfDue} gives the open-time paths the same rule.
 * <p>
 * Pots and suspicious blocks are not swept: their only stored state is an "already looted" marker nobody
 * can observe, so refreshing them when next touched is equivalent. Not verified by a build.
 */
public final class Refresh {

    /**
     * Open-time refresh with the viewing rule: resets {@code state} if its timer is due AND nobody has
     * {@code owner} open. Returns true only if it actually reset.
     */
    public static boolean refreshIfDue(ServerLevel level, Object owner, LootrLootState state, long refreshTicks) {
        long now = level.getGameTime();
        if (!state.refreshDue(now, refreshTicks) || Decay.isViewed(level, owner)) {
            return false;
        }
        return state.refreshIfDue(now, refreshTicks);
    }


    /** Fires the add-on refresh event after a successful reset has been applied. */
    public static void notifyRefreshed(ServerLevel level, Object owner) {
        if (owner instanceof BlockEntity blockEntity) {
            LootListeners.refreshed(level, owner, blockEntity.getBlockPos(), lootTableOf(owner));
        } else if (owner instanceof Entity entity) {
            LootListeners.refreshed(level, owner, entity.blockPosition(), lootTableOf(owner));
        }
    }

    @Nullable
    private static ResourceKey<LootTable> lootTableOf(Object owner) {
        if (owner instanceof RandomizableContainerBlockEntity container) {
            return container.getLootTable();
        }
        if (owner instanceof MinecartChest cart) {
            return cart.getLootTable();
        }
        if (owner instanceof DecoratedPotBlockEntity pot) {
            return pot.getLootTable();
        }
        if (owner instanceof BrushableBlockEntity brushable) {
            return ((net.lootr.serveronly.fabric.mixin.AccessorBrushableBlockEntity) brushable).lootr$getLootTable();
        }
        return null;
    }

    /** Remember this container so the background sweep checks it. Call after its loot has been rolled. */
    public static void track(ServerLevel level, RandomizableContainerBlockEntity container) {
        if (LootrConfig.refreshTicksFor(level, container.getBlockPos(), container.getLootTable()) > 0) {
            RefreshTracker.get(level.getServer()).add(level.dimension(), container.getBlockPos());
        }
    }

    // ---- the sweep -----------------------------------------------------------

    /** Called from {@link Decay}'s tick entry every {@code tick_delay} ticks. */
    static void sweep(MinecraftServer server) {
        if (!(LootrConfig.performRefreshWhileTicking() && LootrConfig.refreshTicks() > 0)) {
            return; // off: leave the tracker alone so turning it back on still finds everything
        }
        RefreshTracker tracker = RefreshTracker.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            if (!LootrConfig.isDimensionEnabled(level.dimension())) {
                continue;
            }
            sweepBlocks(level, tracker);
            sweepCarts(level);
        }
        failureLogged = false;
    }

    private static void sweepBlocks(ServerLevel level, RefreshTracker tracker) {
        long now = level.getGameTime();
        for (BlockPos pos : tracker.snapshot(level.dimension())) {
            try {
                // getBlockEntity would LOAD (or even generate) the chunk. Wait for it instead.
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                if (LootrConfig.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(pos)) {
                    continue; // check_world_border
                }
                CompoundTag saved;
                if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)
                        || ContainerInteractionHandler.kindOf(container) == null
                        || (saved = container.getAttached(ModAttachments.LOOT_STATE)) == null) {
                    tracker.remove(level.dimension(), pos); // broken, replaced, or never a loot container
                    continue;
                }
                long first = LootrLootState.peekFirstGeneratedGameTime(saved);
                int ticks = LootrConfig.refreshTicksFor(level, pos, container.getLootTable());
                if (first < 0 || ticks <= 0) {
                    tracker.remove(level.dimension(), pos); // reset, or no longer covered by the config
                    continue;
                }
                if (now - first < ticks || Decay.isViewed(level, container)) {
                    continue; // not due yet, or deferred until the menu closes
                }
                HolderLookup.Provider provider = level.registryAccess();
                LootrLootState state = new LootrLootState(27);
                state.load(saved, provider);
                if (state.refreshIfDue(now, ticks)) {
                    CompoundTag reset = new CompoundTag();
                    state.save(reset, provider);
                    container.setAttached(ModAttachments.LOOT_STATE, reset);
                    container.setChanged();
                    notifyRefreshed(level, container);
                }
                tracker.remove(level.dimension(), pos); // timer cleared; the next loot starts and tracks a new one
            } catch (RuntimeException e) {
                logFailure(e); // one bad container must not stop the rest refreshing
            }
        }
    }

    private static void sweepCarts(ServerLevel level) {
        long now = level.getGameTime();
        List<? extends MinecartChest> carts = level.getEntities(EntityType.CHEST_MINECART,
                cart -> cart.hasAttached(ModAttachments.LOOT_STATE));
        for (MinecartChest cart : carts) {
            try {
                CompoundTag saved = cart.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag());
                long first = LootrLootState.peekFirstGeneratedGameTime(saved);
                int ticks = LootrConfig.refreshTicksFor(level, cart.blockPosition(), cart.getLootTable());
                if (first >= 0 && ticks > 0 && now - first >= ticks && !Decay.isViewed(level, cart)) {
                    HolderLookup.Provider provider = level.registryAccess();
                    LootrLootState state = new LootrLootState(27);
                    state.load(saved, provider);
                    if (state.refreshIfDue(now, ticks)) {
                        CompoundTag reset = new CompoundTag();
                        state.save(reset, provider);
                        cart.setAttached(ModAttachments.LOOT_STATE, reset);
                        notifyRefreshed(level, cart);
                    }
                }
            } catch (RuntimeException e) {
                logFailure(e);
            }
        }
    }

    /** Set after a failure is logged so a persistent fault is reported once, not once per sweep. */
    private static boolean failureLogged = false;

    private static void logFailure(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            net.lootr.serveronly.fabric.LootrServerOnlyFabric.LOGGER.error("Refresh sweep failed on one container; skipping it and carrying on. "
                    + "Repeats of this are not logged until a sweep completes cleanly.", e);
        }
    }

    private Refresh() {}
}
