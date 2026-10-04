package net.lootr.serveronly.config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class MessageStyles {
    public static MutableComponent decay(String text) { return styled(text, ChatFormatting.RED, true); }
    public static MutableComponent refresh(String text) { return styled(text, ChatFormatting.BLUE, true); }
    public static MutableComponent invalid(String text) { return styled(text, ChatFormatting.DARK_RED, true); }

    private static MutableComponent styled(String text, ChatFormatting color, boolean bold) {
        MutableComponent out = Component.literal(text);
        return LootrSettings.messageStyles() ? out.setStyle(Style.EMPTY.withColor(color).withBold(bold)) : out;
    }
    private MessageStyles() {}
}
