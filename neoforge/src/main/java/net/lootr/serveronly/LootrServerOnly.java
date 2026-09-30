package net.lootr.serveronly;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.registry.ModAttachments;

/**
 * Mod entry point.
 * <p>
 * Notice what is absent compared to a typical NeoForge mod class: there is
 * no {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} call, no
 * {@code @Mod.EventBusSubscriber(Dist.CLIENT)}, no new Block/Item/BlockEntity
 * registration, and no reference to any renderer/screen/particle/model class
 * anywhere in this project. We attach our data to vanilla's OWN block
 * entities (see ModAttachments) rather than registering new ones - that is
 * what actually makes the mod safe to run with no client counterpart: there
 * is nothing here a vanilla client would need to already know about.
 * {@link net.lootr.serveronly.interaction.ContainerInteractionHandler} is
 * loaded automatically via {@code @EventBusSubscriber} and needs no explicit
 * wiring here. It now covers chest, trapped chest, barrel, and shulker box -
 * see its own class javadoc for the per-container notes.
 */
@Mod(LootrServerOnly.MOD_ID)
public class LootrServerOnly {
    public static final String MOD_ID = "lootr_serveronly";

    public LootrServerOnly(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, LootrConfig.SPEC);
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent e) -> LootrConfig.invalidateCaches());
        ModAttachments.register(modBus);
    }
}
