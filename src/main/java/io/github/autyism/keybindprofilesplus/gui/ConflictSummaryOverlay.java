package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A one-line running total ("2 conflicts, 1 possible") in the top-right corner of the vanilla
 * Key Binds screen. It is recomputed whenever any key changes, so it updates while rebinding.
 */
public final class ConflictSummaryOverlay {
    private int lastKeyHash;
    private boolean computed;
    private KeyConflicts.Summary summary = new KeyConflicts.Summary(0, 0);

    public KeyConflicts.Summary current(Minecraft client) {
        int hash = 1;
        for (KeyMapping binding : client.options.keyMappings) {
            hash = 31 * hash + binding.saveString().hashCode();
        }
        if (!computed || hash != lastKeyHash) {
            computed = true;
            lastKeyHash = hash;
            summary = KeyConflicts.summarize(client.options);
        }
        return summary;
    }

    public void render(Screen screen, GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        KeyConflicts.Summary now = current(client);
        if (now.isEmpty()) {
            return;
        }

        Font font = client.font;
        Component text = text(now);
        int x = screen.width - 8 - font.width(text);
        context.drawString(font, text, Math.max(4, x), 6, (now.hard() > 0 ? KeyConflicts.Level.HARD : KeyConflicts.Level.SOFT).color());
    }

    public static Component text(KeyConflicts.Summary summary) {
        return Component.translatable("keybindprofilesplus.conflict.summary", summary.hard(), summary.soft());
    }
}
