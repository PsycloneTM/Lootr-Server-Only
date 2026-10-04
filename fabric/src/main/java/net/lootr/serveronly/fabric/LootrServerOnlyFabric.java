package net.lootr.serveronly.fabric;

import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.fabricmc.api.ModInitializer;
import net.lootr.serveronly.fabric.command.LootrCommands;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.fabric.interaction.ItemFrameInteractionHandler;
import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.lootr.serveronly.fabric.interaction.MinecartInteractionHandler;
import net.lootr.serveronly.fabric.interaction.PotInteractionHandler;
import org.slf4j.Logger;

public final class LootrServerOnlyFabric implements ModInitializer {
    public static final String MOD_ID = LootrServerOnlyConstants.MOD_ID;
    public static final Logger LOGGER = LootrServerOnlyConstants.LOGGER;

    @Override
    public void onInitialize() {
        LootrConfig.installSettings();
        ContainerProtection.installHooks();
        LootrConfig.load();
        ContainerInteractionHandler.register();
        MinecartInteractionHandler.register();
        PotInteractionHandler.register();
        ContainerProtection.register();
        ItemFrameInteractionHandler.register();
        LootrCommands.register();
        LOGGER.info("Lootr (Server-Only) Fabric: container, minecart, pot and item frame interaction handlers and /lootr command registered.");
    }
}
