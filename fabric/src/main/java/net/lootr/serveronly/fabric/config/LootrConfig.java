package net.lootr.serveronly.fabric.config;

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

/**
 * Hand-rolled JSON config ({@code config/lootr_serveronly.json}) - Fabric has
 * no built-in config system, and pulling in Cloth Config for four values isn't
 * worth the extra dependency. Same settings as the NeoForge side's
 * {@code LootrConfig}. Loaded once at startup; edit the file and restart.
 */
public final class LootrConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Shape of the JSON file. Field names are the on-disk keys. */
    private static final class Data {
        // Master switch: true = nothing is converted, everything behaves like vanilla.
        boolean disable = false;
        boolean team_loot = false;
        // Id of the team resolver to use. "" = the highest-priority one; built-in scoreboard resolver is
        // "minecraft:vanilla_default" (the older "lootr_serveronly:scoreboard" still works) at priority -1000, so add-ons win. See TeamResolvers.
        String pinned_team_resolver = "";
        List<String> dimension_whitelist = new ArrayList<>();
        List<String> dimension_blacklist = new ArrayList<>();
        // Dimension NAMESPACES ("minecraft", "somemod"): covers every dimension a mod adds. Checked first.
        List<String> modid_dimension_whitelist = new ArrayList<>();
        List<String> modid_dimension_blacklist = new ArrayList<>();
        // Loot tables never converted (exact ids), and namespaces never converted.
        List<String> loot_table_blacklist = new ArrayList<>();
        List<String> mod_id_blacklist = new ArrayList<>();
        // Same as mod_id_blacklist under upstream Lootr's own name; both are merged.
        List<String> loot_modid_blacklist = new ArrayList<>();
        // Tables known to misbehave when converted: treated like the table blacklist, but the forced whitelist wins.
        List<String> problematic_loot_tables = new ArrayList<>();
        // Unresolved tables are always logged once; true = also tell the player who opened the container.
        boolean report_unresolved_tables = false;
        // Ticks until a container refreshes (24000 = 20 minutes); needs refresh_all or a list below.
        int refresh_value = 24000;
        // Refresh filters: refresh_all covers every converted container; otherwise only the two lists.
        // refresh_dimensions empty = every dimension.
        boolean refresh_all = false;
        List<String> refresh_loot_tables = new ArrayList<>();
        List<String> refresh_modids = new ArrayList<>();
        List<String> refresh_dimensions = new ArrayList<>();
        // Decay: a looted container disappears after this many ticks (0 = off).
        // decay_all covers every converted container; otherwise only the two lists.
        int decay_value = 6000;
        boolean decay_all = false;
        List<String> decay_loot_tables = new ArrayList<>();
        List<String> decay_modids = new ArrayList<>();
        // Empty = decay in every dimension; otherwise only these (e.g. "minecraft:overworld").
        List<String> decay_dimensions = new ArrayList<>();
        // true = no chat message about a container's decay timer when it is opened.
        boolean disable_notifications = false;
        // Announce decay only once this many ticks or fewer remain (the "started" message is always sent); -1 = always.
        int notification_delay = 600;
        boolean disable_message_styles = false;
        // true = a comparator on a loot container outputs 1, false = 0 (see MixinAbstractContainerMenu).
        boolean power_comparators = true;
        // true = a decayed container becomes an ordinary empty vanilla container instead of vanishing.
        boolean replace_when_decayed = false;
        // false = no background decay sweep; containers only decay when a player next opens them.
        boolean perform_decay_while_ticking = true;
        // true = containers looted but not yet watched for decay are picked up when their chunk loads.
        boolean start_decay_while_ticking = false;
        // false = no background refresh sweep; containers only refresh when a player next opens them.
        boolean perform_refresh_while_ticking = true;
        // true = containers looted but not yet watched for refresh are picked up when their chunk loads.
        boolean start_refresh_while_ticking = true;
        // Ticks between background decay sweeps (20 = once a second).
        int tick_delay = 20;
        // Tables always converted, even if the table or mod-id blacklists would exclude them.
        List<String> loot_table_forced_whitelist = new ArrayList<>();
        // Key names deliberately match upstream Lootr's own config keys (and
        // the NeoForge side's), so an admin migrating between them can copy
        // values across. Upstream "converts" a frame into a different entity
        // type; this mod cannot, so it MARKS the vanilla frame instead - see
        // ItemFrameMarker.
        boolean convert_item_frames = true;
        boolean convert_elytras_to_item_frames = true;
        // Replaces the End City Elytra frame with a chest holding a guaranteed Elytra (frames take priority).
        boolean convert_elytras_to_chests = false;
        // Background sweeps and chunk discovery ignore containers outside the world border.
        boolean check_world_border = false;
        // Structure-tag checks also test each structure piece, not just the overall box (as upstream).
        boolean perform_piecewise_check = true;
        // Survival players can't break loot containers; explosions skip them.
        // true = every roll random; false = seeded by the container's own loot seed (see LootRoller).
        boolean randomise_seed = true;
        boolean protect_containers = true;
        // Break rules - see ContainerProtection.handleBreak for the exact order.
        boolean break_to_drop_loot = false;
        boolean require_sneak_to_break = false;
        boolean should_drop_player_loot = false;
        // Upstream break/blast parity - see ContainerProtection.handleBreak and blastSpares.
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
                // The write-back below would otherwise overwrite the admin's
                // hand-edited file with defaults, so one typo would silently
                // erase all their settings. Keep a copy first.
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

        // Write back so the file always exists and contains every key.
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

    /** True (default): every roll is random. False: rolls use the container's own loot seed. */
    public static boolean randomiseSeed() {
        return data.randomise_seed;
    }

    /** Non-sneaking survival break collects the player's loot instead of breaking. */
    public static boolean breakToDropLoot() {
        return data.break_to_drop_loot;
    }

    /** Only used when protect_containers is false: survival players must sneak to break. */
    public static boolean requireSneakToBreak() {
        return data.require_sneak_to_break;
    }

    /** When a container is actually destroyed by a player, their own loot spills at the block. */
    public static boolean shouldDropPlayerLoot() {
        return data.should_drop_player_loot;
    }

    /** Anyone may break loot containers; overrides every other break setting. */
    public static boolean enableBreak() {
        return data.enable_break;
    }

    /** Strict mode: survival never breaks; creative only while sneaking. */
    public static boolean disableBreak() {
        return data.disable_break;
    }

    /** Fake players (automation) may break loot containers. */
    public static boolean enableFakePlayerBreak() {
        return data.enable_fake_player_break;
    }

    /** Loot containers get a blast resistance of 16 (upstream's rule); see MixinExplosionDamageCalculator. */
    public static boolean blastResistant() {
        return data.blast_resistant;
    }

    /** No explosion destroys a loot container; entities ignore explosion damage. */
    public static boolean blastImmune() {
        return data.blast_immune;
    }

    /** Players may USE loot containers inside spawn protection. */
    public static boolean bypassSpawnProtection() {
        return data.bypass_spawn_protection;
    }

    /** Loot suspicious blocks do not fall when their support is removed. */
    public static boolean brushablesSelfSupport() {
        return data.brushables_self_support;
    }

    /** Marked loot item frames are not popped off when their support is removed. */
    public static boolean itemFramesSelfSupport() {
        return data.item_frames_self_support;
    }

    /** True when the {@code disable} master switch is on (nothing is converted). */
    public static boolean isDisabled() {
        return data.disable;
    }

    /** True if players get no chat message about a container's decay timer. */
    public static int notificationDelay() { return data.notification_delay; }
    public static boolean messageStyles() { return !data.disable_message_styles; }
    public static boolean checkWorldBorder() { return data.check_world_border; }
    public static boolean performPiecewiseCheck() { return data.perform_piecewise_check; }
    public static boolean convertElytrasToChests() { return data.convert_elytras_to_chests; }
    public static String pinnedTeamResolver() { return data.pinned_team_resolver; }

    public static boolean disableNotifications() {
        return data.disable_notifications;
    }

    /** Master switch off, then blacklist wins; an empty whitelist means "all dimensions". */
    public static boolean isDimensionEnabled(ResourceKey<Level> dimension) {
        if (data.disable) {
            return false;
        }
        // Namespace lists first, as upstream: a listed namespace covers every dimension a mod adds.
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

    /**
     * False for a null table (nothing to convert) and for tables the admin
     * blacklisted by exact id or by namespace. Every place that decides
     * "is this a Lootr container" goes through here, so a blacklisted table
     * is left completely vanilla.
     */
    public static boolean isLootTableEnabled(ResourceKey<LootTable> table) {
        if (table == null) {
            return false;
        }
        ResourceLocation id = table.location();
        // As upstream: the forced whitelist exempts a table from the table blacklist and the problematic set,
        // but NOT from the mod-id blacklist, which is checked independently.
        return !modBlacklist.contains(id.getNamespace())
                && (forcedTables.contains(id.toString())
                    || (!tableBlacklist.contains(id.toString()) && !ProblematicLootTables.contains(table)));
    }

    /** Decay length in ticks; 0 means decay is off. */
    public static int decayTicks() {
        return data.decay_value;
    }

    /** The {@code problematic_loot_tables} config list (raw ids). */
    public static List<String> problematicLootTables() {
        return data.problematic_loot_tables;
    }

    /** True if the player who opens a container with an unresolvable table is told in chat. */
    public static boolean reportUnresolvedTables() {
        return data.report_unresolved_tables;
    }

    /** True if decay settings cover this table (the caller also checks {@link #isLootTableEnabled}). */
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

    /**
     * The refresh interval that applies to a container with this loot table in this dimension:
     * {@code refresh_value} if refreshing is on and the refresh filters cover it, otherwise 0
     * (which {@code LootrLootState.refreshIfDue} treats as "never"). Callers have already
     * established that the table is converted and the dimension enabled.
     */
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
        // refresh_dimensions is one more way IN (as upstream), not a restriction on the routes above.
        return refreshDimensions.contains(dimension) ? ticks : 0;
    }

    /**
     * Position-aware {@link #refreshTicksFor(ResourceKey, ResourceKey)}: additionally covers a container that sits in a
     * structure tagged {@code lootr_serveronly:refresh}, even when {@code refresh_all}, the table list and the modid
     * list do not. {@code refresh_dimensions} still restricts it. Use this wherever the container's position is known.
     */
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

    /** True if a decayed container stays as an ordinary empty vanilla container. */
    public static boolean replaceWhenDecayed() {
        return data.replace_when_decayed;
    }

    /** Comparator output of a loot container: true = 1, false = 0. */
    public static boolean powerComparators() {
        return data.power_comparators;
    }

    /** True if the background decay sweep runs (otherwise decay only happens on open). */
    public static boolean performDecayWhileTicking() {
        return data.perform_decay_while_ticking;
    }

    /** True if looted-but-unwatched containers are picked up for decay when their chunk loads. */
    public static boolean startDecayWhileTicking() {
        return data.start_decay_while_ticking;
    }

    /** True if the background refresh sweep runs (otherwise refresh only happens on open). */
    public static boolean performRefreshWhileTicking() {
        return data.perform_refresh_while_ticking;
    }

    /** True if looted-but-unwatched containers are picked up for refresh when their chunk loads. */
    public static boolean startRefreshWhileTicking() {
        return data.start_refresh_while_ticking;
    }

    /** Ticks between background decay sweeps. */
    public static int tickDelay() {
        return data.tick_delay;
    }

    /** True if decay may happen in this dimension: {@code decay_dimensions} is empty, or lists it. */
    public static boolean isDecayDimension(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return false;
        }
        return !decayDimensions.isEmpty() && decayDimensions.contains(dimension);
    }

    private LootrConfig() {}
}
