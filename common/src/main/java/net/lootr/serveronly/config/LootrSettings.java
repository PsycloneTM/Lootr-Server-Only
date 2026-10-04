package net.lootr.serveronly.config;

import java.util.List;

/**
 * Loader-independent view of the few config values that code in {@code common} needs
 * (COMMON_MODULE_PLAN.md section 4A). {@code LootrConfig} lives in each loader module (NeoForge's
 * {@code ModConfigSpec} versus Fabric's JSON file), so {@code common} cannot call it. Each loader's
 * {@code LootrConfig.installSettings()} passes a {@link Source} that reads that loader's own config on every
 * call; the entry point calls it first. A getter used before {@link #install} throws, so a wiring mistake is
 * not mistaken for "config ignored". Add one getter here (and to both sources) whenever a further file moves.
 */
public final class LootrSettings {

    /** What a loader must provide. Implementations must read the live config on every call. */
    public interface Source {
        boolean teamLoot();

        boolean randomiseSeed();

        boolean messageStyles();

        boolean reportUnresolvedTables();

        boolean performPiecewiseCheck();

        String pinnedTeamResolver();

        List<? extends String> problematicLootTables();

        boolean protectContainers();

        boolean itemFramesSelfSupport();

        boolean powerComparators();
    }

    private static volatile Source source;

    public static void install(Source installed) {
        source = installed;
    }

    private static Source source() {
        Source current = source;
        if (current == null) {
            throw new IllegalStateException("LootrSettings used before LootrConfig.installSettings() ran");
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

    public static String pinnedTeamResolver() {
        return source().pinnedTeamResolver();
    }

    public static List<? extends String> problematicLootTables() {
        return source().problematicLootTables();
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

    private LootrSettings() {}
}
