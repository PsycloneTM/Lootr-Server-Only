package net.lootr.serveronly.config;

import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class TeamResolver {

    public static UUID resolve(Player player) {
        if (!LootrSettings.teamLoot()) {
            return player.getUUID();
        }
        return TeamResolvers.resolveFor(player);
    }

    private TeamResolver() {}
}
