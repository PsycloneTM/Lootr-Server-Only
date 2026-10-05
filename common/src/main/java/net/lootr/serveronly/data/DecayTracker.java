package net.lootr.serveronly.data;

import net.minecraft.server.MinecraftServer;

public final class DecayTracker {
    public static PositionTracker get(MinecraftServer server) {
        return new PositionTracker(server, "lootr_serveronly_decay_s", "lootr_serveronly_decay");
    }

    private DecayTracker() {}
}
