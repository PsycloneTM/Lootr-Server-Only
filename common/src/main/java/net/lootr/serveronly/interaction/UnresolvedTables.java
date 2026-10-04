package net.lootr.serveronly.interaction;

import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class UnresolvedTables {
    private static final Set<ResourceLocation> LOGGED = ConcurrentHashMap.newKeySet();

    public static void check(@Nullable Player player, ResourceKey<LootTable> key, LootTable table) {
        if (table != LootTable.EMPTY) {
            return;
        }
        if (LOGGED.add(key.location())) {
            LootrServerOnlyConstants.LOGGER.warn("Loot table {} could not be resolved; containers using it will be empty. "
                    + "Check the id and the data packs/mods that should provide it.", key.location());
        }
        if (player != null && LootrSettings.reportUnresolvedTables()) {
            player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.invalid("[Lootr] The loot table " + key.location()
                    + " could not be found, so this container is empty."), false);
        }
    }

    private UnresolvedTables() {}
}
