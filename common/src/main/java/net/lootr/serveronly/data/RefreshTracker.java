package net.lootr.serveronly.data;

import net.minecraft.server.MinecraftServer;

/**
 * The refresh sweep's set of positions to look at. See {@link PositionTracker} for how it is stored and swept.
 * The old single-file format ({@code lootr_serveronly_refresh}) is migrated automatically the first time this
 * loads. Not verified by a build.
 */
public final class RefreshTracker {

    public static PositionTracker get(MinecraftServer server) {
        return new PositionTracker(server, "lootr_serveronly_refresh_s", "lootr_serveronly_refresh");
    }

    private RefreshTracker() {}
}
