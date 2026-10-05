package net.lootr.serveronly.fabric.registry;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.lootr.serveronly.fabric.LootrServerOnlyFabric;

public final class ModAttachments {
    public static final AttachmentType<CompoundTag> LOOT_STATE = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(LootrServerOnlyFabric.MOD_ID, "lootr_loot_state"),
            builder -> builder
                    .initializer(CompoundTag::new)
                    .persistent(CompoundTag.CODEC)
    );

    private ModAttachments() {}
}
