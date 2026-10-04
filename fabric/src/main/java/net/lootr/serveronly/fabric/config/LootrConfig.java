package net.lootr.serveronly.fabric.config;

import net.lootr.serveronly.config.ProblematicLootTables;
import net.lootr.serveronly.config.StructureTags;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.lootr.serveronly.fabric.LootrServerOnlyFabric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LootrConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Data {
        boolean disable = false;
        boolean team_loot = false;
        String pinned_team_resolver = "";
        List<String> dimension_whitelist = new ArrayList<>();
        List<String> dimension_blacklist = new ArrayList<>();
        List<String> modid_dimension_whitelist = new ArrayList<>();
        List<String> modid_dimension_blacklist = new ArrayList<>();
        List<String> loot_table_blacklist = new ArrayList<>();
        List<String> mod_id_blacklist = new ArrayList<>();
        List<String> loot_modid_blacklist = new ArrayList<>();
        List<String> problematic_loot_tables = new ArrayList<>();
        boolean report_unresolved_tables = false;
        int refresh_value = 24000;
        boolean refresh_all = false;
        List<String> refresh_loot_tables = new ArrayList<>();
        List<String> refresh_modids = new ArrayList<>();
        List<String> refresh_dimensions = new ArrayList<>();
        int decay_value = 6000;
        boolean decay_all = false;
        List<String> decay_loot_tables = new ArrayList<>();
        List<String> decay_modids = new ArrayList<>();
        List<String> decay_dimensions = new ArrayList<>();
        boolean disable_notifications = false;
        int notification_delay = 600;
        boolean disable_message_styles = false;
        boolean power_comparators = true;
        boolean replace_when_decayed = false;
        boolean perform_decay_while_ticking = true;
        boolean start_decay_while_ticking = false;
        boolean perform_refresh_while_ticking = true;
        boolean start_refresh_while_ticking = true;
        int tick_delay = 20;
        List<String> loot_table_forced_whitelist = new ArrayList<>();
        boolean convert_item_frames = true;
        boolean convert_elytras_to_item_frames = true;
        boolean convert_elytras_to_chests = false;
        boolean check_world_border = false;
        boolean perform_piecewise_check = true;
        boolean randomise_seed = true;
        boolean protect_containers = true;
        boolean break_to_drop_loot = false;
        boolean require_sneak_to_break = false;
        boolean should_drop_player_loot = false;
        boolean enable_break = false;
        boolean disable_break = false;
        boolean enable_fake_player_break = false;
        boolean blast_resistant = false;
        boolean blast_immune = false;
        boolean bypass_spawn_protection = true;
        boolean brushables_self_support = false;
        boolean item_frames_self_support = false;
    }

    private static Data data = new Data();
    private static Set<ResourceKey<Level>> whitelist = new HashSet<>();
    private static Set<ResourceKey<Level>> blacklist = new HashSet<>();
    private static Set<String> modidDimWhitelist = new HashSet<>();
    private static Set<String> modidDimBlacklist = new HashSet<>();
    private static Set<String> tableBlacklist = new HashSet<>();
    private static Set<String> modBlacklist = new HashSet<>();
    private static Set<String> decayTables = new HashSet<>();
    private static Set<String> decayMods = new HashSet<>();
    private static Set<ResourceKey<Level>> decayDimensions = new HashSet<>();
    private static Set<String> refreshTables = new HashSet<>();
    private static Set<String> forcedTables = new HashSet<>();
    private static Set<String> refreshMods = new HashSet<>();
    private static Set<ResourceKey<Level>> refreshDimensions = new HashSet<>();

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(LootrServerOnlyFabric.MOD_ID + ".json");
        Data loaded = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                loaded = GSON.fromJson(reader, Data.class);
            } catch (Exception e) {
                LootrServerOnlyFabric.LOGGER.error("Failed to read {}, using defaults", path, e);
                Path backup = path.resolveSibling(path.getFileName() + ".bak");
                try {
                    Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    LootrServerOnlyFabric.LOGGER.error("Your malformed config was saved as {}", backup);
                } catch (IOException backupError) {
                    LootrServerOnlyFabric.LOGGER.error("Could not back up {}", path, backupError);
                }
            }
        }
        data = loaded != null ? loaded : new Data();
        if (data.dimension_whitelist == null) data.dimension_whitelist = new ArrayList<>();
        if (data.dimension_blacklist == null) data.dimension_blacklist = new ArrayList<>();
        if (data.modid_dimension_whitelist == null) data.modid_dimension_whitelist = new ArrayList<>();
        if (data.modid_dimension_blacklist == null) data.modid_dimension_blacklist = new ArrayList<>();
        if (data.loot_table_blacklist == null) data.loot_table_blacklist = new ArrayList<>();
        if (data.mod_id_blacklist == null) data.mod_id_blacklist = new ArrayList<>();
        if (data.loot_modid_blacklist == null) data.loot_modid_blacklist = new ArrayList<>();
        if (data.problematic_loot_tables == null) data.problematic_loot_tables = new ArrayList<>();
        if (data.pinned_team_resolver == null) data.pinned_team_resolver = "";
        if (data.refresh_value < 0) data.refresh_value = 0;
        if (data.refresh_loot_tables == null) data.refresh_loot_tables = new ArrayList<>();
        if (data.refresh_modids == null) data.refresh_modids = new ArrayList<>();
        if (data.refresh_dimensions == null) data.refresh_dimensions = new ArrayList<>();
        if (data.decay_loot_tables == null) data.decay_loot_tables = new ArrayList<>();
        if (data.decay_modids == null) data.decay_modids = new ArrayList<>();
        if (data.decay_dimensions == null) data.decay_dimensions = new ArrayList<>();
        if (data.decay_value < 0) data.decay_value = 0;
        if (data.loot_table_forced_whitelist == null) data.loot_table_forced_whitelist = new ArrayList<>();
        if (data.tick_delay < 1) data.tick_delay = 1;
        whitelist = parse(data.dimension_whitelist);
        blacklist = parse(data.dimension_blacklist);
        modidDimWhitelist = new HashSet<>(data.modid_dimension_whitelist);
        modidDimBlacklist = new HashSet<>(data.modid_dimension_blacklist);
        tableBlacklist = new HashSet<>(data.loot_table_blacklist);
        modBlacklist = new HashSet<>(data.mod_id_blacklist);
        modBlacklist.addAll(data.loot_modid_blacklist);
        decayTables = new HashSet<>(data.decay_loot_tables);
        decayMods = new HashSet<>(data.decay_modids);
        decayDimensions = parse(data.decay_dimensions);
        refreshTables = new HashSet<>(data.refresh_loot_tables);
        forcedTables = new HashSet<>(data.loot_table_forced_whitelist);
        ProblematicLootTables.invalidate();
        refreshMods = new HashSet<>(data.refresh_modids);
        refreshDimensions = parse(data.refresh_dimensions);

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LootrServerOnlyFabric.LOGGER.error("Failed to write {}", path, e);
        }
    }

    private static Set<ResourceKey<Level>> parse(List<String> raw) {
        Set<ResourceKey<Level>> out = new HashSet<>();
        for (String s : raw) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) {
                out.add(ResourceKey.create(Registries.DIMENSION, id));
            }
        }
        return out;
    }

    public static boolean teamLoot() {
        return data.team_loot;
    }

    public static int refreshTicks() {
        return data.refresh_value;
    }

    public static boolean convertItemFrames() {
        return data.convert_item_frames;
    }

    public static boolean convertElytrasToItemFrames() {
        return data.convert_elytras_to_item_frames;
    }

    public static boolean protectContainers() {
        return data.protect_containers;
    }

    public static boolean randomiseSeed() {
        return data.randomise_seed;
    }

    public static boolean breakToDropLoot() {
        return data.break_to_drop_loot;
    }

    public static boolean requireSneakToBreak() {
        return data.require_sneak_to_break;
    }

    public static boolean shouldDropPlayerLoot() {
        return data.should_drop_player_loot;
    }

    public static boolean enableBreak() {
        return data.enable_break;
    }

    public static boolean disableBreak() {
        return data.disable_break;
    }

    public static boolean enableFakePlayerBreak() {
        return data.enable_fake_player_break;
    }

    public static boolean blastResistant() {
        return data.blast_resistant;
    }

    public static boolean blastImmune() {
        return data.blast_immune;
    }

    public static boolean bypassSpawnProtection() {
        return data.bypass_spawn_protection;
    }

    public static boolean brushablesSelfSupport() {
        return data.brushables_self_support;
    }

    public static boolean itemFramesSelfSupport() {
        return data.item_frames_self_support;
    }

    public static boolean isDisabled() {
        return data.disable;
    }

    public static int notificationDelay() { return data.notification_delay; }
    public static boolean messageStyles() { return !data.disable_message_styles; }
    public static boolean checkWorldBorder() { return data.check_world_border; }
    public static boolean performPiecewiseCheck() { return data.perform_piecewise_check; }
    public static boolean convertElytrasToChests() { return data.convert_elytras_to_chests; }
    public static String pinnedTeamResolver() { return data.pinned_team_resolver; }

    public static boolean disableNotifications() {
        return data.disable_notifications;
    }

    public static boolean isDimensionEnabled(ResourceKey<Level> dimension) {
        if (data.disable) {
            return false;
        }
        String namespace = dimension.location().getNamespace();
        if (modidDimBlacklist.contains(namespace)
                || (!modidDimWhitelist.isEmpty() && !modidDimWhitelist.contains(namespace))) {
            return false;
        }
        if (blacklist.contains(dimension)) {
            return false;
        }
        return whitelist.isEmpty() || whitelist.contains(dimension);
    }

    public static boolean isLootTableEnabled(ResourceKey<LootTable> table) {
        if (table == null) {
            return false;
        }
        ResourceLocation id = table.location();
        return !modBlacklist.contains(id.getNamespace())
                && (forcedTables.contains(id.toString())
                    || (!tableBlacklist.contains(id.toString()) && !ProblematicLootTables.contains(table)));
    }

    public static int decayTicks() {
        return data.decay_value;
    }

    public static List<String> problematicLootTables() {
        return data.problematic_loot_tables;
    }

    public static boolean reportUnresolvedTables() {
        return data.report_unresolved_tables;
    }

    public static boolean isDecayLootTable(ResourceKey<LootTable> table) {
        if (table == null) {
            return false;
        }
        if (data.decay_all) {
            return true;
        }
        ResourceLocation id = table.location();
        return decayTables.contains(id.toString()) || decayMods.contains(id.getNamespace());
    }

    public static int refreshTicksFor(ResourceKey<Level> dimension, ResourceKey<LootTable> table) {
        int ticks = data.refresh_value;
        if (ticks <= 0 || table == null) {
            return 0;
        }
        if (data.refresh_all) {
            return ticks;
        }
        ResourceLocation id = table.location();
        if (refreshTables.contains(id.toString()) || refreshMods.contains(id.getNamespace())) {
            return ticks;
        }
        return refreshDimensions.contains(dimension) ? ticks : 0;
    }

    public static int refreshTicksFor(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table) {
        int ticks = refreshTicksFor(level.dimension(), table);
        if (ticks > 0) {
            return ticks;
        }
        if (data.refresh_value <= 0 || table == null) {
            return 0;
        }
        return StructureTags.isIn(level, pos, StructureTags.REFRESH) ? data.refresh_value : 0;
    }

    public static boolean replaceWhenDecayed() {
        return data.replace_when_decayed;
    }

    public static boolean powerComparators() {
        return data.power_comparators;
    }

    public static boolean performDecayWhileTicking() {
        return data.perform_decay_while_ticking;
    }

    public static boolean startDecayWhileTicking() {
        return data.start_decay_while_ticking;
    }

    public static boolean performRefreshWhileTicking() {
        return data.perform_refresh_while_ticking;
    }

    public static boolean startRefreshWhileTicking() {
        return data.start_refresh_while_ticking;
    }

    public static int tickDelay() {
        return data.tick_delay;
    }

    public static boolean isDecayDimension(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return false;
        }
        return !decayDimensions.isEmpty() && decayDimensions.contains(dimension);
    }

    private LootrConfig() {}

    public static void installSettings() {
        net.lootr.serveronly.config.LootrSettings.install(new net.lootr.serveronly.config.LootrSettings.Source() {
            @Override public boolean teamLoot() { return LootrConfig.teamLoot(); }
            @Override public boolean randomiseSeed() { return LootrConfig.randomiseSeed(); }
            @Override public boolean messageStyles() { return LootrConfig.messageStyles(); }
            @Override public boolean reportUnresolvedTables() { return LootrConfig.reportUnresolvedTables(); }
            @Override public boolean performPiecewiseCheck() { return LootrConfig.performPiecewiseCheck(); }
            @Override public String pinnedTeamResolver() { return LootrConfig.pinnedTeamResolver(); }
            @Override public java.util.List<? extends String> problematicLootTables() { return LootrConfig.problematicLootTables(); }
            @Override public boolean protectContainers() { return LootrConfig.protectContainers(); }
            @Override public boolean itemFramesSelfSupport() { return LootrConfig.itemFramesSelfSupport(); }
            @Override public boolean powerComparators() { return LootrConfig.powerComparators(); }
        });
    }
}
