package net.lootr.serveronly.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Maps a player to the UUID their loot is keyed under when {@code team_loot} is on. Players that should
 * share loot return the same UUID; it may be the player's own, or one derived any other way, but it must
 * never be null (a resolver that returns null or throws falls back to the player's own UUID and is logged
 * once).
 * <p>
 * Same shape and semantics as upstream Lootr's {@code ITeamResolver}, so an add-on written for upstream
 * only needs to change the import. The resolver in use is the one named by {@code pinned_team_resolver}, or
 * otherwise the highest {@link #priority()}. Not binary-compatible with upstream's interface (different
 * package; upstream's classes are not on a server-only server), so add-ons must be recompiled against this.
 * <p>
 * Register by listing the implementing class in
 * {@code META-INF/services/net.lootr.serveronly.config.ITeamResolver} (found with {@link java.util.ServiceLoader}, as upstream
 * does), or in code with {@link TeamResolvers#registerResolver}.
 */
public interface ITeamResolver {

    /** The UUID this player's loot is keyed under. Never null. */
    UUID resolveServerPlayer(Player player);

    /**
     * Upstream calls this on the client to predict the server's answer. There is no client here, so it is
     * never called; it defaults to the server method so an implementation written for upstream compiles.
     */
    default UUID resolveClientPlayer(Player player) {
        return resolveServerPlayer(player);
    }

    /** The id to pin in {@code pinned_team_resolver}. Must be unique and stable. */
    ResourceLocation resolverId();

    /** Called once, when the resolver is first picked up. A resolver whose init throws is ignored. */
    default void init() {
    }

    /** Higher wins when nothing is pinned. The built-in scoreboard resolver is -1000, as upstream. */
    default int priority() {
        return 0;
    }
}
