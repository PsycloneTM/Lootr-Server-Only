package net.lootr.serveronly.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public interface ITeamResolver {

    UUID resolveServerPlayer(Player player);

    default UUID resolveClientPlayer(Player player) {
        return resolveServerPlayer(player);
    }

    ResourceLocation resolverId();

    default void init() {
    }

    default int priority() {
        return 0;
    }
}
