package net.lootr.serveronly.fabric.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Observes what happens to loot containers, the server-only counterpart of upstream Lootr's block-entity and entity
 * "processors". Upstream's processors run while a vanilla container is being CONVERTED into a Lootr one; this mod
 * never converts anything (the vanilla block entity or entity stays and carries a data attachment), so there is no
 * conversion step to hook. The two moments that matter to an add-on here are a player's loot being rolled and a
 * container decaying, so those are the events.
 * <p>
 * {@code holder} is the vanilla {@link net.minecraft.world.level.block.entity.BlockEntity} (chest, trapped chest,
 * barrel, shulker box, decorated pot, suspicious block) or {@link net.minecraft.world.entity.Entity} (chest minecart)
 * involved. Both methods run on the server thread, inside the action that caused them, so keep them quick and do not
 * block. An exception from a listener is caught and logged once; it never stops the player getting their loot.
 * Register with {@link LootListeners#register} or list an implementation in
 * {@code META-INF/services/net.lootr.serveronly.fabric.api.LootListener}.
 */
public interface LootListener {

    /** Lower runs first; equal priorities keep registration order. */
    default int priority() {
        return 0;
    }

    /** Used in the log if this listener throws. */
    default String name() {
        return getClass().getName();
    }

    /**
     * Loot was just rolled for {@code looter} (and their team, if team loot is on) from this container, for the first
     * time. Fired once per looter per container (until a refresh or {@code /lootr clear} makes them eligible again),
     * after the per-player record is saved and before the items reach the player, for every roll path (menus, break-to-drop
     * loot, pots and suspicious blocks). Filters have already run. Not fired for a repeat open.
     */
    default void onLooted(ServerLevel level, Object holder, BlockPos pos, ServerPlayer looter, ResourceKey<LootTable> table) {
    }

    /**
     * This container is about to be removed or reset by decay: the object is still in the world with its loot table. If
     * {@code replace_when_decayed} is on it stays as an ordinary vanilla container, otherwise it is destroyed.
     */
    default void onDecaying(ServerLevel level, Object holder, BlockPos pos, ResourceKey<LootTable> table) {
    }

    /** Called exactly once after a successful refresh has been persisted. */
    default void onRefreshed(ServerLevel level, Object holder, BlockPos pos, ResourceKey<LootTable> table) {
    }
}
