package net.lootr.serveronly;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.event.NeoEvents;
import net.lootr.serveronly.registry.ModAttachments;

@Mod(LootrServerOnly.MOD_ID)
public class LootrServerOnly {
    public static final String MOD_ID = LootrServerOnlyConstants.MOD_ID;
    public static final org.slf4j.Logger LOGGER = LootrServerOnlyConstants.LOGGER;

    public LootrServerOnly(IEventBus modBus, ModContainer container) {
        LootrConfig.installSettings();
        net.lootr.serveronly.data.LootStateStore.install(new net.lootr.serveronly.data.NeoLootStateBackend());
        net.lootr.serveronly.interaction.ContainerProtection.installHooks();
        NeoEvents.init();
        container.registerConfig(ModConfig.Type.COMMON, LootrConfig.SPEC);
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent e) -> LootrConfig.invalidateCaches());
        ModAttachments.register(modBus);
    }
}
