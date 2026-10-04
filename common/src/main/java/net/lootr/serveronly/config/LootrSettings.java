package net.lootr.serveronly.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LootrSettings {
    public interface Source {
        boolean teamLoot();

        boolean randomiseSeed();

        boolean messageStyles();

        boolean reportUnresolvedTables();

        boolean performPiecewiseCheck();

        boolean protectContainers();

        boolean itemFramesSelfSupport();

        boolean powerComparators();

        boolean convertItemFrames();

        boolean convertElytrasToItemFrames();

        boolean convertElytrasToChests();

        boolean breakToDropLoot();

        boolean requireSneakToBreak();

        boolean shouldDropPlayerLoot();

        boolean enableBreak();

        boolean disableBreak();

        boolean enableFakePlayerBreak();

        boolean blastResistant();

        boolean blastImmune();

        boolean bypassSpawnProtection();

        boolean brushablesSelfSupport();

        boolean checkWorldBorder();

        boolean disableNotifications();

        boolean replaceWhenDecayed();

        boolean performDecayWhileTicking();

        boolean startDecayWhileTicking();

        boolean performRefreshWhileTicking();

        boolean startRefreshWhileTicking();

        boolean disabled();

        boolean refreshAll();

        boolean decayAll();

        int refreshTicks();

        int decayTicks();

        int notificationDelay();

        int tickDelay();

        String pinnedTeamResolver();

        List<? extends String> problematicLootTables();

        List<? extends String> dimensionWhitelist();

        List<? extends String> dimensionBlacklist();

        List<? extends String> modidDimensionWhitelist();

        List<? extends String> modidDimensionBlacklist();

        List<? extends String> lootTableBlacklist();

        List<? extends String> modIdBlacklist();

        List<? extends String> lootModidBlacklist();

        List<? extends String> lootTableForcedWhitelist();

        List<? extends String> refreshLootTables();

        List<? extends String> refreshModids();

        List<? extends String> refreshDimensions();

        List<? extends String> decayLootTables();

        List<? extends String> decayModids();

        List<? extends String> decayDimensions();
    }

    private static final class Snapshot {
        final Set<ResourceKey<Level>> dimensionWhitelist;
        final Set<ResourceKey<Level>> dimensionBlacklist;
        final Set<ResourceKey<Level>> refreshDimensions;
        final Set<ResourceKey<Level>> decayDimensions;
        final Set<String> modidDimensionWhitelist;
        final Set<String> modidDimensionBlacklist;
        final Set<String> tableBlacklist;
        final Set<String> modBlacklist;
        final Set<String> forcedTables;
        final Set<String> refreshTables;
        final Set<String> refreshMods;
        final Set<String> decayTables;
        final Set<String> decayMods;

        Snapshot(Source s) {
            dimensionWhitelist = dimensions(s.dimensionWhitelist());
            dimensionBlacklist = dimensions(s.dimensionBlacklist());
            refreshDimensions = dimensions(s.refreshDimensions());
            decayDimensions = dimensions(s.decayDimensions());
            modidDimensionWhitelist = new HashSet<>(s.modidDimensionWhitelist());
            modidDimensionBlacklist = new HashSet<>(s.modidDimensionBlacklist());
            tableBlacklist = new HashSet<>(s.lootTableBlacklist());
            Set<String> mods = new HashSet<>(s.modIdBlacklist());
            mods.addAll(s.lootModidBlacklist());
            modBlacklist = mods;
            forcedTables = new HashSet<>(s.lootTableForcedWhitelist());
            refreshTables = new HashSet<>(s.refreshLootTables());
            refreshMods = new HashSet<>(s.refreshModids());
            decayTables = new HashSet<>(s.decayLootTables());
            decayMods = new HashSet<>(s.decayModids());
        }

        private static Set<ResourceKey<Level>> dimensions(List<? extends String> raw) {
            Set<ResourceKey<Level>> out = new HashSet<>();
            for (String s : raw) {
                ResourceLocation id = ResourceLocation.tryParse(s);
                if (id != null) {
                    out.add(ResourceKey.create(Registries.DIMENSION, id));
                }
            }
            return out;
        }
    }

    private static volatile Source source;
    private static volatile Snapshot snapshot;

    public static void install(Source installed) {
        source = installed;
        snapshot = null;
    }

    public static void invalidate() {
        snapshot = null;
        ProblematicLootTables.invalidate();
    }

    private static Source source() {
        Source current = source;
        if (current == null) {
            throw new IllegalStateException("LootrSettings used before LootrConfig.installSettings() ran");
        }
        return current;
    }

    private static Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            current = new Snapshot(source());
            snapshot = current;
        }
        return current;
    }

    public static boolean teamLoot() {
        return source().teamLoot();
    }

    public static boolean randomiseSeed() {
        return source().randomiseSeed();
    }

    public static boolean messageStyles() {
        return source().messageStyles();
    }

    public static boolean reportUnresolvedTables() {
        return source().reportUnresolvedTables();
    }

    public static boolean performPiecewiseCheck() {
        return source().performPiecewiseCheck();
    }

    public static boolean protectContainers() {
        return source().protectContainers();
    }

    public static boolean itemFramesSelfSupport() {
        return source().itemFramesSelfSupport();
    }

    public static boolean powerComparators() {
        return source().powerComparators();
    }

    public static boolean convertItemFrames() {
        return source().convertItemFrames();
    }

    public static boolean convertElytrasToItemFrames() {
        return source().convertElytrasToItemFrames();
    }

    public static boolean convertElytrasToChests() {
        return source().convertElytrasToChests();
    }

    public static boolean breakToDropLoot() {
        return source().breakToDropLoot();
    }

    public static boolean requireSneakToBreak() {
        return source().requireSneakToBreak();
    }

    public static boolean shouldDropPlayerLoot() {
        return source().shouldDropPlayerLoot();
    }

    public static boolean enableBreak() {
        return source().enableBreak();
    }

    public static boolean disableBreak() {
        return source().disableBreak();
    }

    public static boolean enableFakePlayerBreak() {
        return source().enableFakePlayerBreak();
    }

    public static boolean blastResistant() {
        return source().blastResistant();
    }

    public static boolean blastImmune() {
        return source().blastImmune();
    }

    public static boolean bypassSpawnProtection() {
        return source().bypassSpawnProtection();
    }

    public static boolean brushablesSelfSupport() {
        return source().brushablesSelfSupport();
    }

    public static boolean checkWorldBorder() {
        return source().checkWorldBorder();
    }

    public static boolean disableNotifications() {
        return source().disableNotifications();
    }

    public static boolean replaceWhenDecayed() {
        return source().replaceWhenDecayed();
    }

    public static boolean performDecayWhileTicking() {
        return source().performDecayWhileTicking();
    }

    public static boolean startDecayWhileTicking() {
        return source().startDecayWhileTicking();
    }

    public static boolean performRefreshWhileTicking() {
        return source().performRefreshWhileTicking();
    }

    public static boolean startRefreshWhileTicking() {
        return source().startRefreshWhileTicking();
    }

    public static int refreshTicks() {
        return source().refreshTicks();
    }

    public static int decayTicks() {
        return source().decayTicks();
    }

    public static int notificationDelay() {
        return source().notificationDelay();
    }

    public static int tickDelay() {
        return source().tickDelay();
    }

    public static String pinnedTeamResolver() {
        return source().pinnedTeamResolver();
    }

    public static List<? extends String> problematicLootTables() {
        return source().problematicLootTables();
    }

    public static boolean isDisabled() {
        return source().disabled();
    }

    public static boolean isDimensionEnabled(ResourceKey<Level> dimension) {
        if (source().disabled()) {
            return false;
        }
        Snapshot s = snapshot();
        String namespace = dimension.location().getNamespace();
        if (s.modidDimensionBlacklist.contains(namespace)
                || (!s.modidDimensionWhitelist.isEmpty() && !s.modidDimensionWhitelist.contains(namespace))) {
            return false;
        }
        if (s.dimensionBlacklist.contains(dimension)) {
            return false;
        }
        return s.dimensionWhitelist.isEmpty() || s.dimensionWhitelist.contains(dimension);
    }

    public static boolean isLootTableEnabled(ResourceKey<LootTable> table) {
        if (table == null) {
            return false;
        }
        Snapshot s = snapshot();
        ResourceLocation id = table.location();
        return !s.modBlacklist.contains(id.getNamespace())
                && (s.forcedTables.contains(id.toString())
                    || (!s.tableBlacklist.contains(id.toString()) && !ProblematicLootTables.contains(table)));
    }

    public static boolean isDecayLootTable(ResourceKey<LootTable> table) {
        if (table == null) {
            return false;
        }
        if (source().decayAll()) {
            return true;
        }
        Snapshot s = snapshot();
        ResourceLocation id = table.location();
        return s.decayTables.contains(id.toString()) || s.decayMods.contains(id.getNamespace());
    }

    public static boolean isDecayDimension(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return false;
        }
        Set<ResourceKey<Level>> allowed = snapshot().decayDimensions;
        return !allowed.isEmpty() && allowed.contains(dimension);
    }

    public static int refreshTicksFor(ResourceKey<Level> dimension, ResourceKey<LootTable> table) {
        int ticks = source().refreshTicks();
        if (ticks <= 0 || table == null) {
            return 0;
        }
        if (source().refreshAll()) {
            return ticks;
        }
        Snapshot s = snapshot();
        ResourceLocation id = table.location();
        if (s.refreshTables.contains(id.toString()) || s.refreshMods.contains(id.getNamespace())) {
            return ticks;
        }
        return s.refreshDimensions.contains(dimension) ? ticks : 0;
    }

    public static int refreshTicksFor(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table) {
        int ticks = refreshTicksFor(level.dimension(), table);
        if (ticks > 0) {
            return ticks;
        }
        int base = source().refreshTicks();
        if (base <= 0 || table == null) {
            return 0;
        }
        return StructureTags.isIn(level, pos, StructureTags.REFRESH) ? base : 0;
    }

    private LootrSettings() {}
}
