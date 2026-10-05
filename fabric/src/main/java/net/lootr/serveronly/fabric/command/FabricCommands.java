package net.lootr.serveronly.fabric.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.lootr.serveronly.command.LootrCommands;
import net.lootr.serveronly.fabric.config.LootrConfig;

public final class FabricCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(LootrCommands.build(() -> {
                    LootrConfig.load();
                    return "Reloaded lootr_serveronly.json.";
                })));
    }

    private FabricCommands() {}
}
