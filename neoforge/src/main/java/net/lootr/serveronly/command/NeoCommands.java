package net.lootr.serveronly.command;

import net.lootr.serveronly.LootrServerOnly;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class NeoCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(LootrCommands.build(null));
    }

    private NeoCommands() {}
}
