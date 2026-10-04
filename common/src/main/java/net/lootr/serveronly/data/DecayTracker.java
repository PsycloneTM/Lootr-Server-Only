package net.lootr.serveronly.data;

import net.minecraft.server.MinecraftServer;

/**
 * The decay sweep's set of positions to look at. See {@link PositionTracker} for how it is stored and swept.
 * The old single-file format ({@code lootr_serveronly_decay}) is migrated automatically the first time this
 * loads. Not verified by a build.
 */
public final class DecayTracker {

    public static PositionTracker get(MinecraftServer server) {
        return new PositionTracker(server, "lootr_serveronly_decay_s", "lootr_serveronly_decay");
    }

    private DecayTracker() {}
}
