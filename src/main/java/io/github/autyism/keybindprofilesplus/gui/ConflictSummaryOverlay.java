package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

/**
 * A one-line running total ("2 conflicts, 1 possible") in the top-right corner of the vanilla
 * Key Binds screen. It is recomputed whenever any key changes, so it updates while rebinding.
 */
public final class ConflictSummaryOverlay {
    private int lastKeyHash;
    private boolean computed;
    private KeyConflicts.Summary summary = new KeyConflicts.Summary(0, 0);

    public KeyConflicts.Summary current(MinecraftClient client) {
        int hash = 1;
        for (KeyBinding binding : client.options.allKeys) {
            hash = 31 * hash + binding.getBoundKeyTranslationKey().hashCode();
        }
        if (!computed || hash != lastKeyHash) {
            computed = true;
            lastKeyHash = hash;
            summary = KeyConflicts.summarize(client.options);
        }
        return summary;
    }

    public void render(Screen screen, DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        KeyConflicts.Summary now = current(client);
        if (now.isEmpty()) {
            return;
        }

        TextRenderer font = client.textRenderer;
        Text text = text(now);
        int x = screen.width - 8 - font.getWidth(text);
        context.drawTextWithShadow(font, text, Math.max(4, x), 6, (now.hard() > 0 ? KeyConflicts.Level.HARD : KeyConflicts.Level.SOFT).color());
    }

    public static Text text(KeyConflicts.Summary summary) {
        return Text.translatable("keybindprofilesplus.conflict.summary", summary.hard(), summary.soft());
    }
}
