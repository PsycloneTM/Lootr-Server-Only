package net.lootr.serveronly.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

/**
 * NeoForge attachments serialize via {@link IAttachmentSerializer}, which
 * hooks into the owning block entity's own NBT read/write - so this data
 * rides along with the chest's normal save data with no extra packet, save
 * file, or network channel of our own.
 * <p>
 * <b>UNVERIFIED - fix this first if the build fails here.</b> This mod's
 * per-player item stacks are saved via {@code ContainerHelper.saveAllItems}/
 * {@code loadAllItems}, which need a {@code HolderLookup.Provider} to
 * correctly round-trip 1.21.1's data-component-based ItemStacks. That
 * requirement is why this uses {@code IAttachmentSerializer} directly
 * instead of the simpler {@code .serialize(Codec)} builder overload shown
 * in NeoForge's own docs (a plain Codec has no HolderLookup.Provider
 * threaded through it without deliberately building a RegistryOps, which
 * the attachment builder's Codec overload does not appear to do for you).
 * <p>
 * What is NOT verified: whether {@code IAttachmentSerializer}'s exact
 * method signatures in NeoForge 21.1.219 match what's written below.
 * Confirmed evidence only covers the 1.20.4-era javadoc, which shows
 * {@code read(IAttachmentHolder, S tag) -> T} and
 * {@code write(T) -> @Nullable S} - i.e. NEITHER method took a
 * HolderLookup.Provider in that version. If 1.21.1 matches the 1.20.4
 * shape, delete the `HolderLookup.Provider provider` parameter from both
 * methods below and get the provider some other way (e.g. threading it in
 * via LootrLootState's own field state instead of a serializer parameter).
 * If the compiler instead reports a missing-override error pointing at a
 * provider-taking signature, this file's current form is correct.
 * Check this against your IDE's decompiled NeoForge sources before anything
 * else in this file - it's a two-minute check with a real IDE/jar and was
 * the one thing this sandbox had no way to confirm directly.
 */
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
