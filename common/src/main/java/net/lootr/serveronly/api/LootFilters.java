package net.lootr.serveronly.api;

import net.minecraft.core.NonNullList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LootFilters {
    private static final Logger LOG = LoggerFactory.getLogger("lootr_serveronly");
    private static final List<LootFilter> FILTERS = new CopyOnWriteArrayList<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static volatile boolean servicesLoaded = false;

    private static void loadServices() {
        if (servicesLoaded) {
            return;
        }
        synchronized (LootFilters.class) {
            if (servicesLoaded) {
                return;
            }
            servicesLoaded = true;
            try {
                for (LootFilter found : java.util.ServiceLoader.load(LootFilter.class, LootFilter.class.getClassLoader())) {
                    register(found);
                }
            } catch (java.util.ServiceConfigurationError | RuntimeException e) {
                LOG.error("Could not load loot filters from META-INF/services; continuing without them.", e);
            }
        }
    }

    public static void register(LootFilter filter) {
        FILTERS.add(filter);
        FILTERS.sort(Comparator.comparingInt(LootFilter::priority));
    }

    public static void unregister(LootFilter filter) {
        FILTERS.remove(filter);
    }

    public static boolean isEmpty() {
        loadServices();
        return FILTERS.isEmpty();
    }

    public static void apply(List<ItemStack> items, LootTable table, LootParams params) {
        LootFilter.Context context = new LootFilter.Context(params.getLevel(),
                params.getOptionalParameter(LootContextParams.THIS_ENTITY), table, params.getLevel().getRandom());
        for (LootFilter filter : FILTERS) {
            try {
                if (filter.mutate(items, context)) {
                    break;
                }
            } catch (RuntimeException e) {
                if (FAILED.add(filter.name())) {
                    LOG.error("Loot filter '{}' threw an exception and was skipped for this roll "
                            + "(further failures from it are not logged).", filter.name(), e);
                }
            }
        }
    }

    public static NonNullList<ItemStack> applyToInventory(NonNullList<ItemStack> inventory, LootTable table, LootParams params) {
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.get(i).isEmpty()) {
                slots.add(i);
                items.add(inventory.get(i));
            }
        }
        apply(items, table, params);

        NonNullList<ItemStack> out = NonNullList.withSize(inventory.size(), ItemStack.EMPTY);
        List<ItemStack> extras = new ArrayList<>();
        int placed = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            if (placed < slots.size()) {
                out.set(slots.get(placed++), stack);
            } else {
                extras.add(stack);
            }
        }
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).isEmpty()) {
                free.add(i);
            }
        }
        RandomSource random = params.getLevel().getRandom();
        for (ItemStack stack : extras) {
            if (free.isEmpty()) {
                LOG.warn("A loot filter added more stacks than the container has slots; {} was dropped.", stack);
                continue;
            }
            out.set(free.remove(random.nextInt(free.size())), stack);
        }
        return out;
    }

    private LootFilters() {}
}
