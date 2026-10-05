package net.lootr.serveronly.interaction;

import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.data.DecayTracker;
import net.lootr.serveronly.data.PlayerClears;
import net.lootr.serveronly.data.PositionTracker;
import net.lootr.serveronly.data.RefreshTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The single entry point for background decay and refresh, called from each loader's server tick.
 * One pass handles blocks (all decay, then all refresh, so decay wins when both are due) and then
 * chest minecarts.
 *
 * <p>Decay and refresh stay separate operations ({@link Decay}, {@link Refresh}), each with its own
 * saved tracker, so the save format is unchanged. They share the sweep loop in {@link BlockSweep}
 * and the deadline arithmetic in {@code LootSchedule}, and failures are logged in one place.
 */
public final class LootScheduler {
    private static boolean failureLogged;

    public static void onServerTick(MinecraftServer server) {
        try {
            PlayerClears.get(server);
            if (server.getTickCount() % LootrSettings.tickDelay() == 0) {
                sweep(server);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    private static void sweep(MinecraftServer server) {
        boolean decay = Decay.backgroundEnabled();
        boolean refresh = Refresh.backgroundEnabled();
        PositionTracker decayTracker = null;
        PositionTracker refreshTracker = null;
        if (decay) {
            decayTracker = DecayTracker.get(server);
            decayTracker.syncSetting(LootrSettings.decayTicks());
        }
        if (refresh) {
            refreshTracker = RefreshTracker.get(server);
            refreshTracker.syncSetting(LootrSettings.refreshTicks());
        }
        if (decay) {
            for (ServerLevel level : server.getAllLevels()) {
                if (LootrSettings.isDimensionEnabled(level.dimension())) {
                    BlockSweep.run(level, decayTracker, Decay::decideBlock, LootScheduler::logFailure);
                }
            }
        }
        if (refresh) {
            for (ServerLevel level : server.getAllLevels()) {
                if (LootrSettings.isDimensionEnabled(level.dimension())) {
                    BlockSweep.run(level, refreshTracker, Refresh::decideBlock, LootScheduler::logFailure);
                }
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (LootrSettings.isDimensionEnabled(level.dimension())) {
                CartScheduler.sweep(level);
            }
        }
        failureLogged = false;
    }

    private static void logFailure(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            LootrServerOnlyConstants.LOGGER.error("Decay/refresh sweep failed on one target; skipping it and carrying on. "
                    + "Repeats of this are not logged until a sweep completes cleanly.", e);
        }
    }

    private LootScheduler() {}
}
