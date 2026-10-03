package net.lootr.serveronly.fabric.interaction;

import net.lootr.serveronly.fabric.api.LootFilters;

import net.lootr.serveronly.fabric.config.LootrConfig;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/**
 * The single place loot is rolled, so every handler follows the same two rules.
 * <p>
 * <b>Seed.</b> With {@code randomise_seed} on (the default) every roll is random,
 * so each player, and each roll after a reset or refresh, gets different loot.
 * With it off, the roll is seeded with the container's own {@code LootTableSeed},
 * so everyone gets the same loot, and the same loot again after a reset. A seed
 * of 0 is vanilla's "no seed", so a container placed without one (for example
 * with {@code /setblock}) stays random even with the option off; structure
 * chests normally have a real seed.
 * <p>
 * <b>Placement.</b> {@link #rollInto} rolls through vanilla's own
 * {@code LootTable.fill}, into a scratch container, so items land in random
 * slots across the whole inventory and stacks are split to fill empty slots,
 * exactly like a vanilla chest. Rolling with {@code getRandomItems} and
 * copying the list into slots 0, 1, 2... (what this mod used to do) packs
 * everything into the top-left corner.
 */
public final class LootRoller {

    /** 0 (vanilla's "no seed", i.e. random) unless {@code randomise_seed} is off. */
    public static long effectiveSeed(long containerSeed) {
        return LootrConfig.randomiseSeed() ? 0L : containerSeed;
    }

    /** Rolls into a fresh inventory of {@code size} slots, placed the way vanilla places chest loot. */
    public static NonNullList<ItemStack> rollInto(LootTable table, LootParams params, long containerSeed, int size) {
        SimpleContainer scratch = new SimpleContainer(size);
        table.fill(scratch, params, effectiveSeed(containerSeed));
        NonNullList<ItemStack> out = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size; i++) {
            out.set(i, scratch.getItem(i));
        }
        return LootFilters.isEmpty() ? out : LootFilters.applyToInventory(out, table, params);
    }

    /** For callers that hand items straight to the player (pots): same seed rule, no placement. */
    public static List<ItemStack> roll(LootTable table, LootParams params, long containerSeed) {
        List<ItemStack> items = table.getRandomItems(params, effectiveSeed(containerSeed));
        if (!LootFilters.isEmpty()) {
            LootFilters.apply(items, table, params);
        }
        return items;
    }

    /**
     * Fires vanilla's {@code minecraft:generate_loot} advancement trigger for a roll this mod performs.
     * <p>
     * Vanilla fires it itself from {@code RandomizableContainer.unpackLootTable} (and the brushable
     * equivalent), but this mod cancels those and rolls on its own, so without this call a datapack
     * advancement listening for {@code generate_loot} would never fire for Lootr containers.
     * <p>
     * Matches upstream Lootr: it is called on <b>every</b> roll for a player, not only their first
     * (a refresh or reset that rolls again fires it again), and before the roll itself, as vanilla does.
     * It fires even when the table is unresolved, as upstream does. Call it once per roll, right after
     * {@code UnresolvedTables.check}.
     */
    public static void triggerGenerateLoot(ServerPlayer player, ResourceKey<LootTable> lootTable) {
        CriteriaTriggers.GENERATE_LOOT.trigger(player, lootTable);
    }

    private LootRoller() {}
}
