package net.lootr.serveronly.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.LootrLootStateAttachmentSerializer;

import java.util.function.Supplier;

public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    LootrServerOnly.MOD_ID
            );

    public static final Supplier<AttachmentType<LootrLootState>> LOOT_STATE =
            ATTACHMENT_TYPES.register(
                    "lootr_loot_state",
                    () -> AttachmentType.builder(
                                    () -> new LootrLootState(27)
                            )
                            .serialize(LootrLootStateAttachmentSerializer.CODEC)
                            .build()
            );

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    private ModAttachments() {}
}
