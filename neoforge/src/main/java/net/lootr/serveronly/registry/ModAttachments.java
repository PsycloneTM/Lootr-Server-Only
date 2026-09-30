package net.lootr.serveronly.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.LootrLootStateAttachmentSerializer;

import java.util.function.Supplier;

/**
 * THE key architectural piece for genuine zero-client-code operation.
 * <p>
 * Earlier drafts of this scaffold subclassed {@code ChestBlockEntity} and
 * registered a brand new {@code BlockEntityType}. That does NOT satisfy a
 * true "no client mod at all" requirement: registering any new
 * BlockEntityType that should render as a chest still requires a client-side
 * {@code registerBlockEntityRenderer} call (confirmed by inspecting upstream
 * Lootr's own neoforge/.../setup/ClientSetup.java, which does exactly this
 * for its own chest/shulker/pot block entities) - vanilla's ChestRenderer is
 * bound per-BlockEntityType, not applied automatically from block shape.
 * <p>
 * The fix: don't register a new block entity type at all. Instead, attach
 * our per-player loot data directly onto vanilla's own
 * {@code minecraft:chest} block entity instances (and barrel, shulker box,
 * etc.) using NeoForge's Data Attachment API. A vanilla client never needs
 * to know this attachment exists.
 * <p>
 * {@code AttachmentType.builder(...)} takes a plain no-arg
 * {@code Supplier<T>} (or a holder-aware {@code Function<IAttachmentHolder,T>}
 * overload we don't need yet) - an earlier draft used a typed
 * {@code Function<BlockEntity,T>} lambda, which matches neither overload and
 * fails to compile. Fixed here to the plain Supplier form.
 * <p>
 * 27 slots is correct for a normal chest; barrel, trapped chest, and shulker
 * box are also 27 in vanilla. Now that all four are ported (see
 * {@link net.lootr.serveronly.interaction.ContainerInteractionHandler}), this
 * single attachment is deliberately still shared across all of them rather
 * than split per-block-entity-type: NeoForge attachments are keyed per
 * BLOCK ENTITY INSTANCE, not per BlockEntityType, so one {@code
 * AttachmentType<LootrLootState>} works correctly no matter which of the
 * four vanilla block entity classes it's attached to - each physical
 * chest/barrel/shulker box in the world gets its own independent
 * {@code LootrLootState} instance the moment {@code getData(LOOT_STATE)} is
 * first called on it. If a future container needs a different slot count
 * (e.g. a 5-slot hopper-like container), that WOULD need its own attachment
 * registered here, since the default-value supplier is fixed at
 * registration time - but none of the four current containers need that.
 */
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
