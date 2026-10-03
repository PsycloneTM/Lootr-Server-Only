package net.lootr.serveronly.fabric.config;

import net.lootr.serveronly.fabric.LootrServerOnlyFabric;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loot tables that are known to misbehave when converted to per-player loot, and are therefore treated
 * as if they were on {@code loot_table_blacklist}. {@code loot_table_forced_whitelist} still overrides this,
 * exactly as it overrides the manual blacklists (see {@link LootrConfig#isLootTableEnabled}).
 * <p>
 * This follows upstream Lootr's two-pass design. Sources, all treated as {@link IProblematicLootTableProcessor}s:
 * <ul>
 *     <li>the built-in set {@link #BUILT_IN} (upstream ships the same two tables), priority -1000;</li>
 *     <li>processors found with {@link ServiceLoader} (a {@code META-INF/services} file naming the interface);</li>
 *     <li>processors added in code with {@link #registerProcessor} or the simpler {@link #register}.</li>
 * </ul>
 * They are sorted by priority, highest first (ties keep discovery order). Pass 1 unions every processor's
 * gathered tables; pass 2 hands that set through each processor's {@code processProblematicChests} in
 * order, each result feeding the next. Finally the {@code problematic_loot_tables} config list is added,
 * AFTER pass 2 on purpose: it is the admin's own list, so no processor can take an entry out of it.
 * <p>
 * Nothing a processor does can break loot conversion: an exception in any call is logged and that call is
 * skipped, leaving the set as it was.
 */
public final class ProblematicLootTables {

    /**
     * The tables upstream Lootr blocks out of the box (its issues #79 and #74): Twilight Forest's
     * stronghold boss chest and Atum's pharaoh chest. Override with {@code loot_table_forced_whitelist}.
     */
    public static final Set<ResourceKey<LootTable>> BUILT_IN = Stream.of(
                    ResourceLocation.fromNamespaceAndPath("twilightforest", "structures/stronghold_boss"),
                    ResourceLocation.fromNamespaceAndPath("atum", "chests/pharaoh"))
            .map(id -> ResourceKey.create(Registries.LOOT_TABLE, id))
            .collect(Collectors.toUnmodifiableSet());

    /** Supplies loot tables that should not be converted; the simple form kept for existing callers. */
    @FunctionalInterface
    public interface Processor {
        Collection<ResourceKey<LootTable>> problematicTables();
    }

    private record Entry(IProblematicLootTableProcessor processor, int priority) {}

    private static final IProblematicLootTableProcessor BUILT_IN_PROCESSOR = new IProblematicLootTableProcessor() {
        @Override
        public Set<ResourceKey<LootTable>> gatherProblematicChests() {
            return BUILT_IN;
        }

        @Override
        public int priority() {
            return -1000;
        }
    };

    private static final List<IProblematicLootTableProcessor> REGISTERED = new CopyOnWriteArrayList<>();
    private static volatile List<IProblematicLootTableProcessor> discovered = null;
    private static volatile Set<ResourceLocation> gathered = null;

    /** Registers a processor in code. Safe to call at any time. */
    public static void registerProcessor(IProblematicLootTableProcessor processor) {
        REGISTERED.add(Objects.requireNonNull(processor, "processor"));
        invalidate();
    }

    /**
     * Registers the simple form: just a list of tables, priority 0, no post-processing. Kept so existing
     * callers keep working; it is a thin adapter over {@link #registerProcessor}. (Deliberately a different
     * name from that method: both interfaces have one abstract method, so overloading {@code register}
     * would make a lambda argument ambiguous.)
     */
    public static void register(Processor processor) {
        Objects.requireNonNull(processor, "processor");
        registerProcessor(() -> new HashSet<>(processor.problematicTables()));
    }

    /** Drops the gathered set so it is rebuilt on next use (config reload, or a processor's data changed). */
    public static void invalidate() {
        gathered = null;
    }

    public static boolean contains(ResourceKey<LootTable> table) {
        Set<ResourceLocation> set = gathered;
        if (set == null) {
            set = gathered = gather();
        }
        return set.contains(table.location());
    }

    private static Set<ResourceLocation> gather() {
        List<Entry> entries = new ArrayList<>();
        addEntry(entries, BUILT_IN_PROCESSOR);
        discovered().forEach(p -> addEntry(entries, p));
        REGISTERED.forEach(p -> addEntry(entries, p));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed()); // stable: ties keep discovery order

        // Pass 1: union of everything every processor gathers.
        Set<ResourceKey<LootTable>> problematic = new HashSet<>();
        for (Entry entry : entries) {
            try {
                Set<ResourceKey<LootTable>> gatheredBy = entry.processor().gatherProblematicChests();
                if (gatheredBy != null) {
                    problematic.addAll(gatheredBy);
                }
            } catch (RuntimeException e) {
                LootrServerOnlyFabric.LOGGER.error("A problematic-loot-table processor threw while gathering; ignoring it", e);
            }
        }
        // Pass 2: each processor may edit the combined set, in priority order; each result feeds the next.
        for (Entry entry : entries) {
            try {
                Set<ResourceKey<LootTable>> processed = entry.processor().processProblematicChests(new HashSet<>(problematic));
                if (processed != null) {
                    problematic = new HashSet<>(processed);
                }
            } catch (RuntimeException e) {
                LootrServerOnlyFabric.LOGGER.error("A problematic-loot-table processor threw while processing; ignoring it", e);
            }
        }

        Set<ResourceLocation> out = new HashSet<>();
        for (ResourceKey<LootTable> key : problematic) {
            out.add(key.location());
        }
        // The admin's own list goes in last, so no processor can remove an entry from it.
        for (String raw : LootrConfig.problematicLootTables()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static void addEntry(List<Entry> entries, IProblematicLootTableProcessor processor) {
        int priority = 0;
        try {
            priority = processor.priority();
        } catch (RuntimeException e) {
            LootrServerOnlyFabric.LOGGER.error("A problematic-loot-table processor threw from priority(); using 0", e);
        }
        entries.add(new Entry(processor, priority));
    }

    /** Processors named in {@code META-INF/services}, loaded once (they cannot change while running). */
    private static List<IProblematicLootTableProcessor> discovered() {
        List<IProblematicLootTableProcessor> list = discovered;
        if (list == null) {
            synchronized (ProblematicLootTables.class) {
                list = discovered;
                if (list == null) {
                    list = discovered = loadServices();
                }
            }
        }
        return list;
    }

    private static List<IProblematicLootTableProcessor> loadServices() {
        List<IProblematicLootTableProcessor> found = new ArrayList<>();
        Iterator<IProblematicLootTableProcessor> iterator = ServiceLoader
                .load(IProblematicLootTableProcessor.class, IProblematicLootTableProcessor.class.getClassLoader())
                .iterator();
        // A provider that fails to load must not hide the ones after it. The cap guards against a
        // misbehaving loader that keeps throwing without advancing.
        for (int attempts = 0; attempts < 1000; attempts++) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                found.add(iterator.next());
            } catch (ServiceConfigurationError | RuntimeException e) {
                LootrServerOnlyFabric.LOGGER.error("A problematic-loot-table processor failed to load; skipping it", e);
            }
        }
        return List.copyOf(found);
    }

    private ProblematicLootTables() {}
}
