package io.github.autyism.keybindprofilesplus.notification;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the short "profile applied" notice above the hotbar. Registered as a Fabric HUD element,
 * so no mixin into the vanilla HUD is needed.
 */
public final class ProfileNoticeHud {
    private static final int TEXT_COLOR = 0xFF55FF55;
    private static final int OFFSET_FROM_BOTTOM = 59;

    private final ProfileNotification notification;

    public ProfileNoticeHud(ProfileNotification notification) {
        this.notification = notification;
    }

    public static Component messageFor(String profileName) {
        return Component.translatable("keybindprofilesplus.hud.profile_applied", profileName);
    }

    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        String profileName = notification.getVisibleProfileName();
        Minecraft client = Minecraft.getInstance();
        //? if >=26.2 {
        /*if (profileName == null || client.player == null || client.gui.hud.isHidden()) {
        *///?} else
        if (profileName == null || client.player == null || client.options.hideGui) {
            return;
        }

        Font textRenderer = client.font;
        Component message = messageFor(profileName);
        int x = (context.guiWidth() - textRenderer.width(message)) / 2;
        int y = context.guiHeight() - OFFSET_FROM_BOTTOM;
        context.drawString(textRenderer, message, x, y, TEXT_COLOR);
    }
}
