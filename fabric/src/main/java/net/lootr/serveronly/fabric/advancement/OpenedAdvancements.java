package net.lootr.serveronly.fabric.advancement;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards the "first open" advancements. Each advancement in
 * {@code data/lootr_serveronly/advancement/} uses vanilla's
 * {@code minecraft:impossible} trigger, so nothing can complete it except
 * this class awarding its single criterion directly. That avoids registering
 * a custom {@code CriterionTrigger}, which crashes at startup because the
 * trigger-type registry is already frozen when the mod initialises, and it
 * keeps the mod free of any new registry content.
 */
public final class OpenedAdvancements {
    public static final String MOD_ID = "lootr_serveronly";

    /** The criterion name used inside every *_opened.json advancement. */
    private static final String CRITERION = "opened";

    public enum Kind {
        CHEST("chest_opened"),
        TRAPPED_CHEST("trapped_chest_opened"),
        BARREL("barrel_opened"),
        SHULKER("shulker_opened"),
        MINECART("minecart_opened"),
        POT("pot_opened"),
        ITEM_FRAME("item_frame_opened"),
        BRUSHABLE("brushable_opened");

        private final ResourceLocation id;

        Kind(String path) {
            this.id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        }
    }

    public static void award(ServerPlayer player, Kind kind) {
        AdvancementHolder holder = player.server.getAdvancements().get(kind.id);
        if (holder == null) {
            return; // datapack removed or disabled it
        }
        player.getAdvancements().award(holder, CRITERION);
    }

    private OpenedAdvancements() {}
}
