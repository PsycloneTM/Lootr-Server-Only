package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.LootrServerOnlyFabric;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notices a container that names a loot table the server cannot find (typo, removed data pack, a mod that
 * failed to load), which otherwise just silently yields an empty container.
 * <p>
 * Vanilla's lookup returns the shared {@code LootTable.EMPTY} for a missing table, so identity with it means
 * "unresolved". A table that exists but is deliberately empty is a different object and is not reported.
 * Every unresolved table is logged once per server run; with {@code report_unresolved_tables} the player
 * who triggered the roll is also told in chat (every time, as that is what the option is for).
 */
public final class UnresolvedTables {
    private static final Set<ResourceLocation> LOGGED = ConcurrentHashMap.newKeySet();

    /** Call right after looking a table up, with the player the roll is for (may be null). */
    public static void check(@Nullable Player player, ResourceKey<LootTable> key, LootTable table) {
        if (table != LootTable.EMPTY) {
            return;
        }
        if (LOGGED.add(key.location())) {
            LootrServerOnlyFabric.LOGGER.warn("Loot table {} could not be resolved; containers using it will be empty. "
                    + "Check the id and the data packs/mods that should provide it.", key.location());
        }
        if (player != null && LootrConfig.reportUnresolvedTables()) {
            player.displayClientMessage(net.lootr.serveronly.fabric.config.MessageStyles.invalid("[Lootr] The loot table " + key.location()
                    + " could not be found, so this container is empty."), false);
        }
    }

    private UnresolvedTables() {}
}
