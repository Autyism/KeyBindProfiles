package io.github.autyi6969.keybindprofilesplus.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small drawing helpers shared by the mod's screens. */
final class GuiUtil {
    static final int CHECKBOX_SIZE = 10;

    static final int WHITE = 0xFFFFFFFF;
    static final int GRAY = 0xFFA0A0A0;
    static final int DARK_GRAY = 0xFF707070;
    static final int YELLOW = 0xFFFFFF80;
    static final int GREEN = 0xFF7CFC7C;
    static final int RED = 0xFFFF6B6B;
    static final int ROW_HOVER = 0x30FFFFFF;
    static final int VALUE_BOX = 0x80000000;

    enum CheckState {
        UNCHECKED,
        PARTIAL,
        CHECKED
    }

    private GuiUtil() {
    }

    /** Cuts the text and appends "..." when it is wider than maxWidth. */
    static String ellipsize(TextRenderer font, String text, int maxWidth) {
        if (font.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return font.trimToWidth(text, Math.max(0, maxWidth - font.getWidth(ellipsis))) + ellipsis;
    }

    /** A small square check box: empty, filled (checked) or with a bar (some children checked). */
    static void drawCheckbox(DrawContext context, int x, int y, CheckState state, boolean hovered) {
        int border = hovered ? WHITE : GRAY;
        context.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, border);
        context.fill(x + 1, y + 1, x + CHECKBOX_SIZE - 1, y + CHECKBOX_SIZE - 1, 0xFF101010);
        if (state == CheckState.CHECKED) {
            context.fill(x + 2, y + 2, x + CHECKBOX_SIZE - 2, y + CHECKBOX_SIZE - 2, GREEN);
        } else if (state == CheckState.PARTIAL) {
            context.fill(x + 2, y + 4, x + CHECKBOX_SIZE - 2, y + CHECKBOX_SIZE - 4, YELLOW);
        }
    }
}
