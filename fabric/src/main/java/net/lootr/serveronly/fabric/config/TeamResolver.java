package net.lootr.serveronly.fabric.config;

import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Fabric twin of the NeoForge {@code TeamResolver}; see that class. */
public final class TeamResolver {

    public static UUID resolve(Player player) {
        if (!LootrConfig.teamLoot()) {
            return player.getUUID();
        }
        return TeamResolvers.resolveFor(player);
    }

    private TeamResolver() {}
}
