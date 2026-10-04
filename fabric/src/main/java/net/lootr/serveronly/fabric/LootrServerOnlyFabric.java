package net.lootr.serveronly.fabric;

import net.fabricmc.api.ModInitializer;
import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.fabric.command.FabricCommands;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.data.FabricLootStateBackend;
import net.lootr.serveronly.fabric.event.FabricEvents;
import net.lootr.serveronly.interaction.ContainerProtection;
import org.slf4j.Logger;

public final class LootrServerOnlyFabric implements ModInitializer {
    public static final String MOD_ID = LootrServerOnlyConstants.MOD_ID;
    public static final Logger LOGGER = LootrServerOnlyConstants.LOGGER;

    @Override
    public void onInitialize() {
        LootrConfig.installSettings();
        LootStateStore.install(new FabricLootStateBackend());
        ContainerProtection.installHooks();
        LootrConfig.load();
        FabricEvents.register();
        FabricCommands.register();
        LOGGER.info("Lootr (Server-Only) Fabric: container, minecart, pot and item frame interaction handlers and /lootr command registered.");
    }
}
