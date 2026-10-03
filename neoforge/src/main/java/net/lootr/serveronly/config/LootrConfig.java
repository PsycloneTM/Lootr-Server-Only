package net.lootr.serveronly.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server config (COMMON type, so it lives in the world's serverconfig-adjacent
 * {@code config/lootr_serveronly-common.toml}). Deliberately small - see the
 * design doc §6: team loot, dimension whitelist/blacklist, refresh timer.
 */
public final class LootrConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISABLE;
    public static final ModConfigSpec.BooleanValue TEAM_LOOT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MODID_DIMENSION_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MODID_DIMENSION_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_TABLE_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_ID_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_MODID_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROBLEMATIC_LOOT_TABLES;
    public static final ModConfigSpec.BooleanValue REPORT_UNRESOLVED_TABLES;
    public static final ModConfigSpec.IntValue REFRESH_TICKS;
    public static final ModConfigSpec.BooleanValue REFRESH_ALL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REFRESH_LOOT_TABLES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REFRESH_MODIDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REFRESH_DIMENSIONS;
    public static final ModConfigSpec.IntValue DECAY_VALUE;
    public static final ModConfigSpec.BooleanValue DECAY_ALL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_LOOT_TABLES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_MODIDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_DIMENSIONS;
    public static final ModConfigSpec.BooleanValue DISABLE_NOTIFICATIONS;
    public static final ModConfigSpec.IntValue NOTIFICATION_DELAY;
    public static final ModConfigSpec.BooleanValue DISABLE_MESSAGE_STYLES;
    public static final ModConfigSpec.BooleanValue CHECK_WORLD_BORDER;
    public static final ModConfigSpec.BooleanValue PERFORM_PIECEWISE_CHECK;
    public static final ModConfigSpec.BooleanValue CONVERT_ELYTRAS_TO_CHESTS;
    public static final ModConfigSpec.ConfigValue<String> PINNED_TEAM_RESOLVER;
    public static final ModConfigSpec.BooleanValue POWER_COMPARATORS;
    public static final ModConfigSpec.BooleanValue REPLACE_WHEN_DECAYED;
    public static final ModConfigSpec.BooleanValue PERFORM_DECAY_WHILE_TICKING;
    public static final ModConfigSpec.BooleanValue START_DECAY_WHILE_TICKING;
    public static final ModConfigSpec.BooleanValue PERFORM_REFRESH_WHILE_TICKING;
    public static final ModConfigSpec.BooleanValue START_REFRESH_WHILE_TICKING;
    public static final ModConfigSpec.IntValue TICK_DELAY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LOOT_TABLE_FORCED_WHITELIST;
    public static final ModConfigSpec.BooleanValue CONVERT_ITEM_FRAMES;
    public static final ModConfigSpec.BooleanValue CONVERT_ELYTRAS_TO_ITEM_FRAMES;
    public static final ModConfigSpec.BooleanValue RANDOMISE_SEED;
    public static final ModConfigSpec.BooleanValue PROTECT_CONTAINERS;
    public static final ModConfigSpec.BooleanValue BREAK_TO_DROP_LOOT;
    public static final ModConfigSpec.BooleanValue REQUIRE_SNEAK_TO_BREAK;
    public static final ModConfigSpec.BooleanValue SHOULD_DROP_PLAYER_LOOT;
    public static final ModConfigSpec.BooleanValue ENABLE_BREAK;
    public static final ModConfigSpec.BooleanValue DISABLE_BREAK;
    public static final ModConfigSpec.BooleanValue ENABLE_FAKE_PLAYER_BREAK;
    public static final ModConfigSpec.BooleanValue BLAST_RESISTANT;
    public static final ModConfigSpec.BooleanValue BLAST_IMMUNE;
    public static final ModConfigSpec.BooleanValue BYPASS_SPAWN_PROTECTION;
    public static final ModConfigSpec.BooleanValue BRUSHABLES_SELF_SUPPORT;
    public static final ModConfigSpec.BooleanValue ITEM_FRAMES_SELF_SUPPORT;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");
        DISABLE = BUILDER
                .comment("Master switch. If true, nothing is converted: every container, pot, suspicious block, minecart and item",
                        "frame behaves exactly like vanilla and no protection applies. Loot a player has already taken is kept and",
                        "comes back if this is turned off again. Containers that were already per-player-looted still hold their",
                        "unrolled loot table, so with this on the first player to open one gets its loot, as in vanilla.")
                .define("disable", false);
        BUILDER.pop();

        BUILDER.push("team");
        TEAM_LOOT = BUILDER
                .comment("If true, loot is shared per scoreboard team instead of per player.",
                        "Players not on a team are treated as a team of one.")
                .define("team_loot", false);
        PINNED_TEAM_RESOLVER = BUILDER
                .comment("Id of the team resolver to use when team_loot is on. The built-in scoreboard resolver is",
                        "\"minecraft:vanilla_default\" (the older \"lootr_serveronly:scoreboard\" still works), and it has the lowest priority,",
                        "so any add-on resolver wins by default. Empty (or \"lootr:default\", upstream's blank value) = the highest-priority",
                        "resolver. Only needed with several team add-ons of equal priority; an unknown id is logged once and ignored.")
                .define("pinned_team_resolver", "");
        BUILDER.pop();

        BUILDER.push("dimensions");
        DIMENSION_WHITELIST = BUILDER
                .comment("If non-empty, containers only convert to per-player loot in these dimensions (e.g. \"minecraft:overworld\").")
                .defineListAllowEmpty("dimension_whitelist", List.of(), () -> "minecraft:overworld", o -> o instanceof String);
        DIMENSION_BLACKLIST = BUILDER
                .comment("Containers in these dimensions are never converted. Takes priority over the whitelist.")
                .defineListAllowEmpty("dimension_blacklist", List.of(), () -> "minecraft:the_end", o -> o instanceof String);
        MODID_DIMENSION_WHITELIST = BUILDER
                .comment("If non-empty, containers only convert in dimensions whose NAMESPACE is listed, e.g. \"minecraft\" covers",
                        "overworld, nether and end, and \"somemod\" covers every \"somemod:...\" dimension. Checked before dimension_whitelist.")
                .defineListAllowEmpty("modid_dimension_whitelist", List.of(), () -> "minecraft", o -> o instanceof String);
        MODID_DIMENSION_BLACKLIST = BUILDER
                .comment("Containers in dimensions whose NAMESPACE is listed are never converted. Takes priority over the whitelists.")
                .defineListAllowEmpty("modid_dimension_blacklist", List.of(), () -> "somemod", o -> o instanceof String);
        BUILDER.pop();

        BUILDER.push("loot_filters");
        LOOT_TABLE_BLACKLIST = BUILDER
                .comment("Loot tables that are never converted. Containers using them behave exactly like vanilla.",
                        "Exact ids, e.g. \"minecraft:chests/spawn_bonus_chest\".")
                .defineListAllowEmpty("loot_table_blacklist", List.of(), () -> "minecraft:chests/spawn_bonus_chest", o -> o instanceof String);
        LOOT_TABLE_FORCED_WHITELIST = BUILDER
                .comment("Loot tables that are always converted, even if loot_table_blacklist or mod_id_blacklist would exclude them.",
                        "Exact ids, e.g. \"minecraft:chests/spawn_bonus_chest\". Does not override the dimension lists.")
                .defineListAllowEmpty("loot_table_forced_whitelist", List.of(), () -> "minecraft:chests/spawn_bonus_chest", o -> o instanceof String);
        MOD_ID_BLACKLIST = BUILDER
                .comment("Namespaces whose loot tables are never converted, e.g. \"somemod\" covers every \"somemod:...\" table.")
                .defineListAllowEmpty("mod_id_blacklist", List.of(), () -> "somemod", o -> o instanceof String);
        LOOT_MODID_BLACKLIST = BUILDER
                .comment("Same as mod_id_blacklist, under upstream Lootr's own name so an upstream config can be copied over as-is.",
                        "Both lists are merged; a mod id in either one is never converted.")
                .defineListAllowEmpty("loot_modid_blacklist", List.of(), () -> "somemod", o -> o instanceof String);
        PROBLEMATIC_LOOT_TABLES = BUILDER
                .comment("Your own list of loot tables that misbehave when converted; treated like loot_table_blacklist, but",
                        "loot_table_forced_whitelist overrides them. Exact ids. Already built in, as in upstream Lootr:",
                        "twilightforest:structures/stronghold_boss and atum:chests/pharaoh, plus whatever other server-side mods",
                        "register. Add a built-in one to loot_table_forced_whitelist to convert it anyway.")
                .defineListAllowEmpty("problematic_loot_tables", List.of(), () -> "somemod:chests/odd_table", o -> o instanceof String);
        REPORT_UNRESOLVED_TABLES = BUILDER
                .comment("Loot tables a container names but the server cannot find are always logged once per table. If true, the",
                        "player who opened the container is also told in chat each time.")
                .define("report_unresolved_tables", false);
        BUILDER.pop();

        BUILDER.push("loot");
        RANDOMISE_SEED = BUILDER
                .comment("If true (default), every roll is random: each player, and each roll after a reset or refresh, gets",
                        "different loot. If false, rolls use the container's own loot seed, so everyone gets the same loot, and the",
                        "same loot again after a reset. Containers with no seed (e.g. placed with /setblock) stay random.",
                        "Applies to chests, barrels, shulker boxes, chest minecarts and pots; suspicious blocks are always random.")
                .define("randomise_seed", true);
        BUILDER.pop();

        BUILDER.push("refresh");
        REFRESH_TICKS = BUILDER
                .comment("Ticks after a container is first looted before it resets for everyone (24000 = 20 minutes). 0 disables refreshing.",
                        "Nothing refreshes unless refresh_all is true or the container is covered by the lists below, as upstream.",
                        "Checked when a player opens the container, not in the background.")
                .defineInRange("refresh_value", 24000, 0, Integer.MAX_VALUE);
        REFRESH_ALL = BUILDER
                .comment("If true, every converted loot container, pot and suspicious block refreshes (subject to refresh_value and",
                        "refresh_dimensions). If false, only those whose loot table is covered by the two lists below.")
                .define("refresh_all", false);
        REFRESH_LOOT_TABLES = BUILDER
                .comment("Loot table ids that refresh when refresh_all is false, e.g. \"minecraft:chests/simple_dungeon\".")
                .defineListAllowEmpty("refresh_loot_tables", List.of(), () -> "minecraft:chests/simple_dungeon", o -> o instanceof String);
        REFRESH_MODIDS = BUILDER
                .comment("Namespaces whose loot tables refresh when refresh_all is false, e.g. \"minecraft\".")
                .defineListAllowEmpty("refresh_modids", List.of(), () -> "minecraft", o -> o instanceof String);
        REFRESH_DIMENSIONS = BUILDER
                .comment("If non-empty, refreshing only happens in these dimensions (e.g. \"minecraft:overworld\"). Empty = every dimension.")
                .defineListAllowEmpty("refresh_dimensions", List.of(), () -> "minecraft:overworld", o -> o instanceof String);
        PERFORM_REFRESH_WHILE_TICKING = BUILDER
                .comment("If true (default), a background sweep (every tick_delay ticks) refreshes containers whose refresh timer has",
                        "run out, in loaded chunks. If false, a container only refreshes when a player next opens it.",
                        "Either way, a container someone has open is never refreshed: it waits until they close it.")
                .define("perform_refresh_while_ticking", true);
        START_REFRESH_WHILE_TICKING = BUILDER
                .comment("If true (default), containers that were looted but are not yet being watched for refresh (for example looted",
                        "before refreshing was turned on) are picked up when their chunk loads.")
                .define("start_refresh_while_ticking", true);
        BUILDER.pop();

        BUILDER.push("decay");
        DECAY_VALUE = BUILDER
                .comment("Ticks after a container is first looted before it disappears for everyone. 0 disables decay.",
                        "Applies to chests, trapped chests, barrels, shulker boxes and chest minecarts. Pots, suspicious blocks",
                        "and item frames never decay. Shares its timer with refresh_value (see the README).")
                .defineInRange("decay_value", 6000, 0, Integer.MAX_VALUE);
        DECAY_ALL = BUILDER
                .comment("If true, every converted loot container decays. If false, only those covered by the two lists below.")
                .define("decay_all", false);
        DECAY_LOOT_TABLES = BUILDER
                .comment("Loot table ids that decay when decay_all is false, e.g. \"minecraft:chests/simple_dungeon\".")
                .defineListAllowEmpty("decay_loot_tables", List.of(), () -> "minecraft:chests/simple_dungeon", o -> o instanceof String);
        DECAY_MODIDS = BUILDER
                .comment("Namespaces whose loot tables decay when decay_all is false, e.g. \"minecraft\".")
                .defineListAllowEmpty("decay_modids", List.of(), () -> "minecraft", o -> o instanceof String);
        DECAY_DIMENSIONS = BUILDER
                .comment("If non-empty, containers only decay in these dimensions (e.g. \"minecraft:overworld\"). Empty = every dimension.",
                        "This narrows decay only; conversion is still controlled by the dimension lists above.")
                .defineListAllowEmpty("decay_dimensions", List.of(), () -> "minecraft:overworld", o -> o instanceof String);
        REPLACE_WHEN_DECAYED = BUILDER
                .comment("If true, a decayed container is not removed: it becomes an ordinary, empty vanilla container (its loot table",
                        "is cleared, so it stops being a loot container). Chest minecarts stay in the world the same way.")
                .define("replace_when_decayed", false);
        PERFORM_DECAY_WHILE_TICKING = BUILDER
                .comment("If true (default), a background sweep removes expired containers in loaded chunks. If false, nothing decays",
                        "in the background: a container only decays when a player next tries to open it after its deadline.")
                .define("perform_decay_while_ticking", true);
        START_DECAY_WHILE_TICKING = BUILDER
                .comment("If true, containers that were looted but are not yet being watched for decay (for example looted before decay",
                        "was turned on) are picked up when their chunk loads, so they decay without anyone opening them again.",
                        "If false (default, as upstream), a container only starts being watched when a player next opens it.")
                .define("start_decay_while_ticking", false);
        TICK_DELAY = BUILDER
                .comment("Ticks between background decay sweeps (20 = once a second). Larger values cost less but let containers",
                        "linger slightly past their deadline.")
                .defineInRange("tick_delay", 20, 1, 12000);
        BUILDER.pop();

        BUILDER.push("redstone");
        POWER_COMPARATORS = BUILDER
                .comment("If true (default, as upstream), a comparator on a loot container outputs 1; if false, 0.",
                        "Loot containers look empty to vanilla, so without this a comparator always reads 0, and structure traps",
                        "wired to a comparator on a chest would fire as soon as the structure loads.")
                .define("power_comparators", true);
        BUILDER.pop();

        BUILDER.push("notifications");
        DISABLE_NOTIFICATIONS = BUILDER
                .comment("If true, players get no chat message about a container's decay timer when they open it.")
                .define("disable_notifications", false);
        NOTIFICATION_DELAY = BUILDER
                .comment("A container's decay timer is announced when it is opened only once this many ticks or fewer remain",
                        "(600 = 30 seconds). The message that says decay has just STARTED is always sent. -1 = always announce.")
                .defineInRange("notification_delay", 600, -1, Integer.MAX_VALUE);
        DISABLE_MESSAGE_STYLES = BUILDER
                .comment("If true, break/decay/invalid-table messages are plain text instead of coloured and bold.")
                .define("disable_message_styles", false);
        BUILDER.pop();

        // The two key names below deliberately match upstream Lootr's own
        // config keys so an admin migrating from it can copy their values.
        // (Upstream "converts" a frame into a different entity type; this mod
        // cannot, so it MARKS the vanilla frame instead - see ItemFrameMarker.)
        BUILDER.push("item_frames");
        CONVERT_ITEM_FRAMES = BUILDER
                .comment("If true, item frames that hold an item and are generated by a structure (vanilla, datapack or modded)",
                        "become Lootr frames: each player (or team) can take one copy of the framed item by hitting the frame.",
                        "Never applied to fixed frames, invisible frames, empty frames, or frames holding a map.",
                        "Only affects frames generated AFTER this is enabled. Existing frames can be marked by hand - see ItemFrameMarker.")
                .define("convert_item_frames", true);
        CONVERT_ELYTRAS_TO_ITEM_FRAMES = BUILDER
                .comment("If true, the Elytra item frame on End City ships becomes a Lootr frame.",
                        "Only affects End cities generated AFTER this is enabled.")
                .define("convert_elytras_to_item_frames", true);
        CONVERT_ELYTRAS_TO_CHESTS = BUILDER
                .comment("If true (and convert_elytras_to_item_frames is false - frames take priority, as upstream), the End City",
                        "ship's Elytra frame is replaced by a chest holding a guaranteed Elytra, which then loots per player.",
                        "Only affects End cities generated AFTER this is enabled.")
                .define("convert_elytras_to_chests", false);
        BUILDER.pop();

        BUILDER.push("protection");
        PROTECT_CONTAINERS = BUILDER
                .comment("If true, survival players cannot break loot containers and explosions leave them standing,",
                        "so one player can't destroy the loot for everyone. Also makes loot pots immune to projectiles",
                        "and marked item frames / loot chest minecarts immune to arrows, explosions, fire and mobs.",
                        "Creative players can still break them.")
                .define("protect_containers", true);
        BREAK_TO_DROP_LOOT = BUILDER
                .comment("If true, a survival player who breaks a loot container WITHOUT sneaking collects their loot into their",
                        "inventory instead (the container stays). Sneaking while breaking falls through to the rules below.",
                        "Breaking takes as long as it normally does; the loot is handed over when the break completes.",
                        "Creative players are unaffected. Applies to chests, barrels and shulker boxes.")
                .define("break_to_drop_loot", false);
        REQUIRE_SNEAK_TO_BREAK = BUILDER
                .comment("Only used when protect_containers is false. If true, survival players must sneak to break a loot",
                        "container (it is then destroyed for everyone); a normal break is refused with a hint.",
                        "This is upstream Lootr's default mode. If false (and protect_containers is false) breaking is vanilla.")
                .define("require_sneak_to_break", false);
        SHOULD_DROP_PLAYER_LOOT = BUILDER
                .comment("If true, when a loot container is actually destroyed by a player, that player's own loot spills at the",
                        "block (rolled first if they had not looted it). Only happens when a break is allowed: creative players, or",
                        "survival players when protect_containers is false. Applies to chests, barrels and shulker boxes.")
                .define("should_drop_player_loot", false);
        ENABLE_BREAK = BUILDER
                .comment("If true, anyone may break loot containers (destroying them for everyone); every other break setting is",
                        "ignored, except that should_drop_player_loot still applies. Overrides disable_break.")
                .define("enable_break", false);
        DISABLE_BREAK = BUILDER
                .comment("Upstream Lootr's strict mode. If true, survival players can never break loot containers and creative",
                        "players can only break them while sneaking. If false, protect_containers and require_sneak_to_break decide,",
                        "and creative players may break freely (this mod's default).")
                .define("disable_break", false);
        ENABLE_FAKE_PLAYER_BREAK = BUILDER
                .comment("If true, fake players (automation from other mods, such as block breakers) may break loot containers",
                        "even when breaking is otherwise restricted.")
                .define("enable_fake_player_break", false);
        BLAST_RESISTANT = BUILDER
                .comment("If true, loot containers have a blast resistance of 16 (upstream's rule), so only a strong, close",
                        "explosion destroys one: an uncharged creeper never can, TNT only when touching it, while charged creepers,",
                        "end crystals, beds and anchors can from a few blocks away. Not needed if protect_containers or blast_immune is on.")
                .define("blast_resistant", false);
        BLAST_IMMUNE = BUILDER
                .comment("If true, no explosion can destroy a loot container, and loot minecarts and marked item frames ignore",
                        "explosion damage. protect_containers already does this and more; use this to get explosion immunity only.")
                .define("blast_immune", false);
        BYPASS_SPAWN_PROTECTION = BUILDER
                .comment("If true (default), players can USE loot containers inside the server's spawn protection area, as in",
                        "upstream Lootr. Only using is allowed, never placing blocks or breaking; the world border is still respected.")
                .define("bypass_spawn_protection", true);
        BRUSHABLES_SELF_SUPPORT = BUILDER
                .comment("If true, loot suspicious sand/gravel does not fall when the block under it is removed.")
                .define("brushables_self_support", false);
        ITEM_FRAMES_SELF_SUPPORT = BUILDER
                .comment("If true, marked loot item frames are not popped off when the block they hang on is removed.")
                .define("item_frames_self_support", false);
        BUILDER.pop();

        BUILDER.push("performance");
        CHECK_WORLD_BORDER = BUILDER
                .comment("If true, the background refresh/decay sweeps and chunk discovery ignore containers outside the world border.")
                .define("check_world_border", false);
        PERFORM_PIECEWISE_CHECK = BUILDER
                .comment("If true (default, as upstream), structure-tag refresh/decay also checks each structure piece, not just the",
                        "structure's overall bounding box. More accurate, slightly more work.")
                .define("perform_piecewise_check", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static volatile Set<ResourceKey<Level>> whitelistCache = null;
    private static volatile Set<String> modidDimWhitelistCache = null;
    private static volatile Set<String> modidDimBlacklistCache = null;
    private static volatile Set<ResourceKey<Level>> blacklistCache = null;
    private static volatile Set<String> tableBlacklistCache = null;
    private static volatile Set<String> modBlacklistCache = null;

    /** Call from the config reload event so edited lists take effect. */
    public static void invalidateCaches() {
        modidDimWhitelistCache = null;
        modidDimBlacklistCache = null;
        whitelistCache = null;
        blacklistCache = null;
        tableBlacklistCache = null;
        modBlacklistCache = null;
        ProblematicLootTables.invalidate();
    }

    private static Set<ResourceKey<Level>> parse(List<? extends String> raw) {
        Set<ResourceKey<Level>> out = new HashSet<>();
        for (String s : raw) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) {
                out.add(ResourceKey.create(Registries.DIMENSION, id));
            }
        }
        return out;
    }

    /** True when the {@code disable} master switch is on (nothing is converted). */
    public static boolean isDisabled() {
        return DISABLE.get();
    }

    public static boolean isDimensionEnabled(ResourceKey<Level> dimension) {
        if (DISABLE.get()) {
            return false;
        }
        // Namespace lists first, as upstream: a listed namespace covers every dimension a mod adds.
        String namespace = dimension.location().getNamespace();
        Set<String> modBlack = modidDimBlacklistCache;
        if (modBlack == null) {
            modBlack = modidDimBlacklistCache = new HashSet<>(MODID_DIMENSION_BLACKLIST.get());
        }
        if (modBlack.contains(namespace)) {
            return false;
        }
        Set<String> modWhite = modidDimWhitelistCache;
        if (modWhite == null) {
            modWhite = modidDimWhitelistCache = new HashSet<>(MODID_DIMENSION_WHITELIST.get());
        }
        if (!modWhite.isEmpty() && !modWhite.contains(namespace)) {
            return false;
        }
        Set<ResourceKey<Level>> black = blacklistCache;
        if (black == null) {
            black = blacklistCache = parse(DIMENSION_BLACKLIST.get());
        }
        if (black.contains(dimension)) {
            return false;
        }
        Set<ResourceKey<Level>> white = whitelistCache;
        if (white == null) {
            white = whitelistCache = parse(DIMENSION_WHITELIST.get());
        }
        return white.isEmpty() || white.contains(dimension);
    }

    /**
     * False for a null table (nothing to convert) and for tables the admin
     * blacklisted by exact id or by namespace. Every place that decides
     * "is this a Lootr container" goes through here, so a blacklisted table
     * is left completely vanilla: no per-player loot, no break protection.
     */
    public static boolean isLootTableEnabled(ResourceKey<LootTable> table) {
        if (table == null) {
            return false;
        }
        Set<String> tables = tableBlacklistCache;
        if (tables == null) {
            tables = tableBlacklistCache = new HashSet<>(LOOT_TABLE_BLACKLIST.get());
        }
        Set<String> mods = modBlacklistCache;
        if (mods == null) {
            Set<String> merged = new HashSet<>(MOD_ID_BLACKLIST.get());
            merged.addAll(LOOT_MODID_BLACKLIST.get());
            mods = modBlacklistCache = merged;
        }
        ResourceLocation id = table.location();
        // As upstream: the forced whitelist exempts a table from the table blacklist and the problematic set,
        // but NOT from the mod-id blacklist, which is checked independently.
        return !mods.contains(id.getNamespace())
                && (LOOT_TABLE_FORCED_WHITELIST.get().contains(id.toString())
                    || (!tables.contains(id.toString()) && !ProblematicLootTables.contains(table)));
    }

    /** The {@code problematic_loot_tables} config list (raw ids). */
    public static List<? extends String> problematicLootTables() {
        return PROBLEMATIC_LOOT_TABLES.get();
    }

    /** Ticks until decay (0 = decay off). */
    public static int decayTicks() {
        return DECAY_VALUE.get();
    }

    /** True if decay settings cover this table (the caller also checks {@link #isLootTableEnabled}). */
    public static boolean isDecayLootTable(ResourceKey<LootTable> table) {
        if (table == null) {
            return false;
        }
        if (DECAY_ALL.get()) {
            return true;
        }
        ResourceLocation id = table.location();
        return DECAY_LOOT_TABLES.get().contains(id.toString()) || DECAY_MODIDS.get().contains(id.getNamespace());
    }

    /**
     * The refresh interval that applies to a container with this loot table in this dimension:
     * {@code refresh_value} if refreshing is on and the refresh filters cover it, otherwise 0
     * (which {@code LootrLootState.refreshIfDue} treats as "never"). Callers have already
     * established that the table is converted and the dimension enabled.
     */
    public static int refreshTicksFor(ResourceKey<Level> dimension, ResourceKey<LootTable> table) {
        int ticks = REFRESH_TICKS.get();
        if (ticks <= 0 || table == null) {
            return 0;
        }
        if (REFRESH_ALL.get()) {
            return ticks;
        }
        ResourceLocation id = table.location();
        if (REFRESH_LOOT_TABLES.get().contains(id.toString()) || REFRESH_MODIDS.get().contains(id.getNamespace())) {
            return ticks;
        }
        // refresh_dimensions is one more way IN (as upstream), not a restriction on the routes above.
        return parse(REFRESH_DIMENSIONS.get()).contains(dimension) ? ticks : 0;
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
        int base = REFRESH_TICKS.get();
        if (base <= 0 || table == null) {
            return 0;
        }
        return StructureTags.isIn(level, pos, StructureTags.REFRESH) ? base : 0;
    }

    /** True if decay may happen in this dimension: {@code decay_dimensions} is empty, or lists it. */
    public static boolean isDecayDimension(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return false;
        }
        Set<ResourceKey<Level>> allowed = parse(DECAY_DIMENSIONS.get());
        return !allowed.isEmpty() && allowed.contains(dimension);
    }

    public static int notificationDelay() { return NOTIFICATION_DELAY.get(); }
    public static boolean messageStyles() { return !DISABLE_MESSAGE_STYLES.get(); }
    public static boolean checkWorldBorder() { return CHECK_WORLD_BORDER.get(); }
    public static boolean performPiecewiseCheck() { return PERFORM_PIECEWISE_CHECK.get(); }
    public static boolean convertElytrasToChests() { return CONVERT_ELYTRAS_TO_CHESTS.get(); }
    public static boolean convertElytrasToItemFrames() { return CONVERT_ELYTRAS_TO_ITEM_FRAMES.get(); }
    public static String pinnedTeamResolver() { return PINNED_TEAM_RESOLVER.get(); }

    private LootrConfig() {}
}
