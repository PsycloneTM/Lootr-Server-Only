package net.lootr.serveronly.fabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.lootr.serveronly.fabric.LootrServerOnlyFabric;
import net.minecraft.core.registries.Registries;
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
        boolean team_loot = false;
        List<String> dimension_whitelist = new ArrayList<>();
        List<String> dimension_blacklist = new ArrayList<>();
        // Loot tables never converted (exact ids), and namespaces never converted.
        List<String> loot_table_blacklist = new ArrayList<>();
        List<String> mod_id_blacklist = new ArrayList<>();
        int refresh_ticks = 0;
        // Key names deliberately match upstream Lootr's own config keys (and
        // the NeoForge side's), so an admin migrating between them can copy
        // values across. Upstream "converts" a frame into a different entity
        // type; this mod cannot, so it MARKS the vanilla frame instead - see
        // ItemFrameMarker.
        boolean convert_item_frames = true;
        boolean convert_elytras_to_item_frames = true;
        // Survival players can't break loot containers; explosions skip them.
        boolean protect_containers = true;
        // Break rules - see ContainerProtection.handleBreak for the exact order.
        boolean break_to_drop_loot = false;
        boolean require_sneak_to_break = false;
        boolean should_drop_player_loot = false;
    }

    private static Data data = new Data();
    private static Set<ResourceKey<Level>> whitelist = new HashSet<>();
    private static Set<ResourceKey<Level>> blacklist = new HashSet<>();
    private static Set<String> tableBlacklist = new HashSet<>();
    private static Set<String> modBlacklist = new HashSet<>();

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
        if (data.loot_table_blacklist == null) data.loot_table_blacklist = new ArrayList<>();
        if (data.mod_id_blacklist == null) data.mod_id_blacklist = new ArrayList<>();
        if (data.refresh_ticks < 0) data.refresh_ticks = 0;
        whitelist = parse(data.dimension_whitelist);
        blacklist = parse(data.dimension_blacklist);
        tableBlacklist = new HashSet<>(data.loot_table_blacklist);
        modBlacklist = new HashSet<>(data.mod_id_blacklist);

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
        return data.refresh_ticks;
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

    /** Blacklist wins; an empty whitelist means "all dimensions". */
    public static boolean isDimensionEnabled(ResourceKey<Level> dimension) {
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
        return !tableBlacklist.contains(id.toString()) && !modBlacklist.contains(id.getNamespace());
    }

    private LootrConfig() {}
}
