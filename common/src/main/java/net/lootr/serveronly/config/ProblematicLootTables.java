package net.lootr.serveronly.config;

import net.lootr.serveronly.common.LootrServerOnlyConstants;
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

public final class ProblematicLootTables {

    public static final Set<ResourceKey<LootTable>> BUILT_IN = Stream.of(
                    ResourceLocation.fromNamespaceAndPath("twilightforest", "structures/stronghold_boss"),
                    ResourceLocation.fromNamespaceAndPath("atum", "chests/pharaoh"))
            .map(id -> ResourceKey.create(Registries.LOOT_TABLE, id))
            .collect(Collectors.toUnmodifiableSet());

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

    public static void registerProcessor(IProblematicLootTableProcessor processor) {
        REGISTERED.add(Objects.requireNonNull(processor, "processor"));
        invalidate();
    }

    public static void register(Processor processor) {
        Objects.requireNonNull(processor, "processor");
        registerProcessor(() -> new HashSet<>(processor.problematicTables()));
    }

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
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());

        Set<ResourceKey<LootTable>> problematic = new HashSet<>();
        for (Entry entry : entries) {
            try {
                Set<ResourceKey<LootTable>> gatheredBy = entry.processor().gatherProblematicChests();
                if (gatheredBy != null) {
                    problematic.addAll(gatheredBy);
                }
            } catch (RuntimeException e) {
                LootrServerOnlyConstants.LOGGER.error("A problematic-loot-table processor threw while gathering; ignoring it", e);
            }
        }
        for (Entry entry : entries) {
            try {
                Set<ResourceKey<LootTable>> processed = entry.processor().processProblematicChests(new HashSet<>(problematic));
                if (processed != null) {
                    problematic = new HashSet<>(processed);
                }
            } catch (RuntimeException e) {
                LootrServerOnlyConstants.LOGGER.error("A problematic-loot-table processor threw while processing; ignoring it", e);
            }
        }

        Set<ResourceLocation> out = new HashSet<>();
        for (ResourceKey<LootTable> key : problematic) {
            out.add(key.location());
        }
        for (String raw : LootrSettings.problematicLootTables()) {
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
            LootrServerOnlyConstants.LOGGER.error("A problematic-loot-table processor threw from priority(); using 0", e);
        }
        entries.add(new Entry(processor, priority));
    }

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
        for (int attempts = 0; attempts < 1000; attempts++) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                found.add(iterator.next());
            } catch (ServiceConfigurationError | RuntimeException e) {
                LootrServerOnlyConstants.LOGGER.error("A problematic-loot-table processor failed to load; skipping it", e);
            }
        }
        return List.copyOf(found);
    }

    private ProblematicLootTables() {}
}
