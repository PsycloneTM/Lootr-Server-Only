package net.lootr.serveronly.config;

import java.util.List;

public final class LootrSettings {

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
