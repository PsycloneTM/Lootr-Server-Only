package net.lootr.serveronly.config;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Maps a player to the UUID their loot is keyed under. With team loot off
 * this is just the player's own UUID; with it on, everyone on the same
 * scoreboard team shares one UUID derived from the team name.
 */
public final class TeamResolver {

    public static UUID resolve(Player player) {
        if (!LootrConfig.TEAM_LOOT.get()) {
            return player.getUUID();
        }
        Team team = player.getTeam();
        if (team == null) {
            return player.getUUID();
        }
        return UUID.nameUUIDFromBytes(("lootr_serveronly:team:" + team.getName()).getBytes(StandardCharsets.UTF_8));
    }

    private TeamResolver() {}
}
