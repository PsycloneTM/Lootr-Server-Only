package net.lootr.serveronly.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

public final class LootrLootStateAttachmentSerializer implements IAttachmentSerializer<CompoundTag, LootrLootState> {
    public static final LootrLootStateAttachmentSerializer CODEC = new LootrLootStateAttachmentSerializer();

    public LootrLootState read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
        int size = tag.contains("Size") ? tag.getInt("Size") : 27;
        LootrLootState state = new LootrLootState(size);
        state.load(tag, provider);
        return state;
    }

    public CompoundTag write(LootrLootState state, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", state.getContainerSize());
        state.save(tag, provider);
        return tag;
    }
}
