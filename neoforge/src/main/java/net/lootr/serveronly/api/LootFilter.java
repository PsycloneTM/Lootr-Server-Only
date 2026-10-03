package net.lootr.serveronly.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A server-side loot filter, the server-only counterpart of upstream Lootr's {@code ILootrFilter}: it can change the
 * loot rolled for a player before they receive it (remove, replace or add stacks). Register one with
 * {@link LootFilters#register}.
 * <p>
 * Runs for every Lootr roll: chests, trapped chests, barrels, shulker boxes, chest minecarts, decorated pots and
 * suspicious sand/gravel. It does NOT run for vanilla loot that Lootr does not manage.
 * <p>
 * <b>Not binary-compatible with upstream's interface.</b> Upstream hands filters its {@code LootFillerState}, which
 * belongs to its own custom containers; this one hands a small {@link Context} instead. Also, for containers the filter
 * sees the stacks AFTER vanilla has scattered and split them across slots (upstream filters before), so a filter that
 * only removes or replaces stacks keeps vanilla's layout, and stacks it adds go into random free slots.
 */
public interface LootFilter {
    /** Lower runs first; equal priorities keep registration order. */
    int priority();

    /** Used in the log if this filter throws. */
    String name();

    /**
     * Changes {@code items} in place. Empty stacks are ignored afterwards. Return true to stop the remaining
     * (lower-priority) filters for this roll.
     */
    boolean mutate(List<ItemStack> items, Context context);

    /**
     * @param level  the level the container is in
     * @param looter the player the loot is rolled for (null only if the roll has no looter)
     * @param table  the loot table being rolled
     * @param random the level's random source
     */
    record Context(ServerLevel level, @Nullable Entity looter, LootTable table, RandomSource random) {}
}
