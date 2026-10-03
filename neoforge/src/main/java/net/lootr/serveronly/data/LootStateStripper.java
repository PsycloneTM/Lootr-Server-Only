package net.lootr.serveronly.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Removes this mod's per-player loot state from a block entity's or entity's saved NBT.
 * <p>
 * Used when a structure template is saved (a structure block's SAVE, or anything calling
 * {@code StructureTemplate.fillFromWorld}). The state lives in a data attachment, and attachments are written
 * into the object's own NBT, so without this a looted chest saved into a structure would carry "player X has
 * already looted this" into every place the structure is later loaded. The loot table and seed are left in
 * place (that is what lets the placed copy become a loot container again), as are other mods' attachments.
 * <p>
 * Both loaders' attachment keys are tried on both: only the running loader's key is ever present, and trying
 * the other is harmless. Not verified by a build.
 */
public final class LootStateStripper {

    /** NeoForge's and Fabric API's NBT keys for the compound that holds every attachment. */
    private static final String[] ATTACHMENT_KEYS = {"neoforge:attachments", "fabric:attachments"};
    private static final String STATE_ID = net.lootr.serveronly.LootrServerOnly.MOD_ID + ":lootr_loot_state";

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
