package net.lootr.serveronly.fabric.registry;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.lootr.serveronly.fabric.LootrServerOnlyFabric;
import net.lootr.serveronly.fabric.data.LootrLootState;

/**
 * Fabric-side equivalent of the NeoForge side's
 * {@code net.lootr.serveronly.registry.ModAttachments}. Same architectural
 * role, different underlying API:
 * <p>
 * NeoForge's {@code AttachmentType.builder(...)} becomes Fabric API's
 * {@code AttachmentRegistry.create(...)} - confirmed against FabricMC's own
 * Data Attachments documentation. This is a genuinely separate Maven
 * artifact ({@code fabric-data-attachment-api-v1}) from
 * {@code fabric-registry-sync-v0} (the module that would actually risk
 * kicking vanilla clients) - see {@code fabric/build.gradle}'s comment for
 * the verification trail. Depending on this one module via
 * {@code fabricApi.module(...)} does not pull in registry-sync at all.
 * <p>
 * Persistence: {@code .persistent(Codec<T>)} is Fabric API's equivalent of
 * NeoForge's {@code .serialize(...)} - both need a {@code Codec}/serializer
 * that round-trips {@link LootrLootState} through NBT. Unlike the NeoForge
 * side (which had genuine signature uncertainty around
 * {@code IAttachmentSerializer} across NeoForge versions), Fabric API's
 * {@code persistent(Codec<T>)} overload is stable and CODEC-based only - no
 * equivalent ambiguity here. The one real wrinkle carried over from the
 * NeoForge side: {@link LootrLootState#load}/{@link LootrLootState#save}
 * need a {@code HolderLookup.Provider} to correctly round-trip 1.21.1's
 * data-component {@code ItemStack}s via {@code ContainerHelper}, and a plain
 * {@code Codec<CompoundTag>} does not thread one through. This registry
 * works around it by NOT using a provider-aware save at the attachment-codec
 * level - instead, {@link #LOOT_STATE_CODEC} encodes/decodes a raw
 * {@link CompoundTag} via {@code CompoundTag.CODEC}, and callers
 * (see {@code ContainerInteractionHandler}) obtain the real
 * {@code HolderLookup.Provider} from the server/level at read/write time and
 * pass it into {@link LootrLootState#load}/{@link LootrLootState#save}
 * directly, rather than threading it through the attachment codec itself.
 * This sidesteps needing Fabric API's codec chain to know about
 * {@code HolderLookup.Provider} at all.
 */
public final class ModAttachments {

    /**
     * Raw NBT round-trip codec. Deliberately does NOT attempt to encode
     * {@link LootrLootState} directly - {@code LootrLootState.load/save}
     * need a {@code HolderLookup.Provider} that a plain {@code Codec} has no
     * access to (same constraint documented on the NeoForge side). Instead,
     * this attachment stores the already-serialized {@link CompoundTag}
     * form; {@code ContainerInteractionHandler} is responsible for calling
     * {@code LootrLootState.load}/{@code save} against that tag with a real
     * provider obtained from the server at interaction time.
     */
    public static final AttachmentType<CompoundTag> LOOT_STATE = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(LootrServerOnlyFabric.MOD_ID, "lootr_loot_state"),
            builder -> builder
                    .initializer(CompoundTag::new)
                    .persistent(CompoundTag.CODEC)
    );

    private ModAttachments() {}
}
