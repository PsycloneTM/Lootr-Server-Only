package net.lootr.serveronly.fabric.config;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Fabric twin of the NeoForge {@code TeamResolver}; see that class. */
public final class TeamResolver {

    public static UUID resolve(Player player) {
        if (!LootrConfig.teamLoot()) {
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
