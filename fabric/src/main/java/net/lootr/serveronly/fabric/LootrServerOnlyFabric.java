package net.lootr.serveronly.fabric;

import net.fabricmc.api.ModInitializer;
import net.lootr.serveronly.fabric.command.LootrCommands;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.fabric.interaction.ItemFrameInteractionHandler;
import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.lootr.serveronly.fabric.interaction.MinecartInteractionHandler;
import net.lootr.serveronly.fabric.interaction.PotInteractionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point.
 *
 * <p>Registers {@link ContainerInteractionHandler} against
 * {@code UseBlockCallback}, bringing chest/trapped-chest/barrel/shulker-box
 * per-player loot generation to parity with the NeoForge side. No new
 * Block/Item/BlockEntityType is registered here or anywhere else in this
 * mod - see fabric/build.gradle's top comment for why that's the load-
 * bearing constraint that keeps this mod's client-optionality intact.</p>
 */
public final class LootrServerOnlyFabric implements ModInitializer {
    public static final String MOD_ID = "lootr_serveronly";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
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
