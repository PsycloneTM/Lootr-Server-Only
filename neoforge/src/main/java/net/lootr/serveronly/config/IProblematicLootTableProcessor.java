package net.lootr.serveronly.config;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Set;

/**
 * A source of loot tables that must not be converted to per-player loot, because they misbehave when
 * converted (a boss-room chest that another mod refills, for example). Everything gathered is treated as
 * if it were on {@code loot_table_blacklist}; {@code loot_table_forced_whitelist} still overrides it.
 * <p>
 * Same shape and semantics as upstream Lootr's {@code IProblematicLootTableProcessor}, so a mod that
 * already integrates with upstream only needs to change the import:
 * <ol>
 *     <li>Every processor's {@link #gatherProblematicChests()} is collected into one set.</li>
 *     <li>Then, in priority order, each processor's {@link #processProblematicChests(Set)} receives the
 *     set and returns the set to hand to the next one, so a processor may add, remove or replace entries.</li>
 * </ol>
 * Processors run from the highest {@link #priority()} to the lowest. This is NOT binary-compatible with
 * upstream's interface (different package, and upstream's classes are not on a server-only server):
 * integrations must be recompiled against this one.
 * <p>
 * Register either by listing the implementing class in
 * {@code META-INF/services/net.lootr.serveronly.config.IProblematicLootTableProcessor} (found with {@link java.util.ServiceLoader},
 * as upstream does), or in code with {@link ProblematicLootTables#registerProcessor}.
 */
public interface IProblematicLootTableProcessor {

    /** The tables this processor considers problematic. May be empty. */
    Set<ResourceKey<LootTable>> gatherProblematicChests();

    /**
     * Called after every processor has gathered, with the combined set; returns the set to pass on. The
     * default keeps it unchanged. A fresh mutable copy is passed each time.
     */
    default Set<ResourceKey<LootTable>> processProblematicChests(Set<ResourceKey<LootTable>> problematicChests) {
        return problematicChests;
    }

    /** Higher runs first. Upstream's built-in processor uses -1000. */
    default int priority() {
        return 0;
    }
}
