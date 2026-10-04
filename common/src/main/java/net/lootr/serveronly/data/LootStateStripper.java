package net.lootr.serveronly.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class LootStateStripper {

    private static final String[] ATTACHMENT_KEYS = {"neoforge:attachments", "fabric:attachments"};
    private static final String STATE_ID = net.lootr.serveronly.common.LootrServerOnlyConstants.MOD_ID + ":lootr_loot_state";

    public static void strip(CompoundTag tag) {
        for (String key : ATTACHMENT_KEYS) {
            if (tag.contains(key, Tag.TAG_COMPOUND)) {
                CompoundTag attachments = tag.getCompound(key);
                if (attachments.contains(STATE_ID)) {
                    attachments.remove(STATE_ID);
                    if (attachments.isEmpty()) {
                        tag.remove(key);
                    }
                }
            }
        }
    }

    private LootStateStripper() {}
}
