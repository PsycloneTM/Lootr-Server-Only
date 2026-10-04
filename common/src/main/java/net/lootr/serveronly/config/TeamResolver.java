package net.lootr.serveronly.config;

import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Maps a player to the UUID their loot is keyed under. With team loot off
 * this is just the player's own UUID; with it on, everyone on the same
 * scoreboard team shares one UUID derived from the team name.
 */
public final class TeamResolver {

    public static UUID resolve(Player player) {
        if (!LootrSettings.teamLoot()) {
            return player.getUUID();
        }
        return TeamResolvers.resolveFor(player);
    }

    private TeamResolver() {}
}
