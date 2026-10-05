package net.lootr.serveronly.fabric.config;

import net.lootr.serveronly.config.LootrSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.lootr.serveronly.fabric.LootrServerOnlyFabric;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        LootrSettings.invalidate();

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LootrServerOnlyFabric.LOGGER.error("Failed to write {}", path, e);
        }
    }

    private LootrConfig() {}

    public static void installSettings() {
        net.lootr.serveronly.config.LootrSettings.install(new net.lootr.serveronly.config.LootrSettings.Source() {
            @Override public boolean teamLoot() { return data.team_loot; }
            @Override public boolean randomiseSeed() { return data.randomise_seed; }
            @Override public boolean messageStyles() { return !data.disable_message_styles; }
            @Override public boolean reportUnresolvedTables() { return data.report_unresolved_tables; }
            @Override public boolean performPiecewiseCheck() { return data.perform_piecewise_check; }
            @Override public boolean protectContainers() { return data.protect_containers; }
            @Override public boolean itemFramesSelfSupport() { return data.item_frames_self_support; }
            @Override public boolean powerComparators() { return data.power_comparators; }
            @Override public boolean convertItemFrames() { return data.convert_item_frames; }
            @Override public boolean convertElytrasToItemFrames() { return data.convert_elytras_to_item_frames; }
            @Override public boolean convertElytrasToChests() { return data.convert_elytras_to_chests; }
            @Override public boolean breakToDropLoot() { return data.break_to_drop_loot; }
            @Override public boolean requireSneakToBreak() { return data.require_sneak_to_break; }
            @Override public boolean shouldDropPlayerLoot() { return data.should_drop_player_loot; }
            @Override public boolean enableBreak() { return data.enable_break; }
            @Override public boolean disableBreak() { return data.disable_break; }
            @Override public boolean enableFakePlayerBreak() { return data.enable_fake_player_break; }
            @Override public boolean blastResistant() { return data.blast_resistant; }
            @Override public boolean blastImmune() { return data.blast_immune; }
            @Override public boolean bypassSpawnProtection() { return data.bypass_spawn_protection; }
            @Override public boolean brushablesSelfSupport() { return data.brushables_self_support; }
            @Override public boolean checkWorldBorder() { return data.check_world_border; }
            @Override public boolean disableNotifications() { return data.disable_notifications; }
            @Override public boolean replaceWhenDecayed() { return data.replace_when_decayed; }
            @Override public boolean performDecayWhileTicking() { return data.perform_decay_while_ticking; }
            @Override public boolean startDecayWhileTicking() { return data.start_decay_while_ticking; }
            @Override public boolean performRefreshWhileTicking() { return data.perform_refresh_while_ticking; }
            @Override public boolean startRefreshWhileTicking() { return data.start_refresh_while_ticking; }
            @Override public boolean disabled() { return data.disable; }
            @Override public boolean refreshAll() { return data.refresh_all; }
            @Override public boolean decayAll() { return data.decay_all; }
            @Override public int refreshTicks() { return data.refresh_value; }
            @Override public int decayTicks() { return data.decay_value; }
            @Override public int notificationDelay() { return data.notification_delay; }
            @Override public int tickDelay() { return data.tick_delay; }
            @Override public String pinnedTeamResolver() { return data.pinned_team_resolver; }
            @Override public java.util.List<? extends String> problematicLootTables() { return data.problematic_loot_tables; }
            @Override public java.util.List<? extends String> dimensionWhitelist() { return data.dimension_whitelist; }
            @Override public java.util.List<? extends String> dimensionBlacklist() { return data.dimension_blacklist; }
            @Override public java.util.List<? extends String> modidDimensionWhitelist() { return data.modid_dimension_whitelist; }
            @Override public java.util.List<? extends String> modidDimensionBlacklist() { return data.modid_dimension_blacklist; }
            @Override public java.util.List<? extends String> lootTableBlacklist() { return data.loot_table_blacklist; }
            @Override public java.util.List<? extends String> modIdBlacklist() { return data.mod_id_blacklist; }
            @Override public java.util.List<? extends String> lootModidBlacklist() { return data.loot_modid_blacklist; }
            @Override public java.util.List<? extends String> lootTableForcedWhitelist() { return data.loot_table_forced_whitelist; }
            @Override public java.util.List<? extends String> refreshLootTables() { return data.refresh_loot_tables; }
            @Override public java.util.List<? extends String> refreshModids() { return data.refresh_modids; }
            @Override public java.util.List<? extends String> refreshDimensions() { return data.refresh_dimensions; }
            @Override public java.util.List<? extends String> decayLootTables() { return data.decay_loot_tables; }
            @Override public java.util.List<? extends String> decayModids() { return data.decay_modids; }
            @Override public java.util.List<? extends String> decayDimensions() { return data.decay_dimensions; }
        });
    }
}
