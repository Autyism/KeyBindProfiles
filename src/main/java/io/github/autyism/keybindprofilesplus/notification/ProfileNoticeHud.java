package io.github.autyism.keybindprofilesplus.notification;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

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

    public static Text messageFor(String profileName) {
        return Text.translatable("keybindprofilesplus.hud.profile_applied", profileName);
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        String profileName = notification.getVisibleProfileName();
        MinecraftClient client = MinecraftClient.getInstance();
        if (profileName == null || client.player == null || client.options.hudHidden) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        Text message = messageFor(profileName);
        int x = (context.getScaledWindowWidth() - textRenderer.getWidth(message)) / 2;
        int y = context.getScaledWindowHeight() - OFFSET_FROM_BOTTOM;
        context.drawTextWithShadow(textRenderer, message, x, y, TEXT_COLOR);
    }
}
