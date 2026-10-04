package net.lootr.serveronly.advancement;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class OpenedAdvancements {
    public static final String MOD_ID = "lootr_serveronly";

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
            return;
        }
        player.getAdvancements().award(holder, CRITERION);
    }

    private OpenedAdvancements() {}
}
