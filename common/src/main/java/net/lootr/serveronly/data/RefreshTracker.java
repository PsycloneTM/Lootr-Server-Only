package net.lootr.serveronly.data;

import net.minecraft.server.MinecraftServer;

public final class RefreshTracker {

    public static PositionTracker get(MinecraftServer server) {
        return new PositionTracker(server, "lootr_serveronly_refresh_s", "lootr_serveronly_refresh");
    }

    private RefreshTracker() {}
}
