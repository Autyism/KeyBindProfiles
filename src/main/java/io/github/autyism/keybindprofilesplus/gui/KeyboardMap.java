package io.github.autyism.keybindprofilesplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.keys.KeyTrigger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A picture of a full-size keyboard and the mouse buttons: which key is free, which is used, which has a conflict.
 * The Key Binds screen draws it instead of its list; a click on a key shows the list of that key only.
 */
final class KeyboardMap {
    /** One binding on a key: the line for the tooltip and how bad its conflicts are. */
    record Use(Component line, KeyConflicts.Level level) {
    }

    /** A key on the map, in key units (a letter key is 1 x 1). */
    private record Cap(String key, float x, float y, float w, float h) {
    }

    private static final List<Cap> CAPS = new ArrayList<>();
    private static final float WIDTH = 23f;
    private static final float HEIGHT = 8f;

    static {
        float y = 0;
        // function row
        cap("escape", 0, y, 1);
        String[] f = {"f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12"};
        for (int i = 0; i < 12; i++) cap(f[i], 2 + i + (i / 4) * 0.5f, y, 1);
        cap("print.screen", 15.5f, y, 1);
        cap("scroll.lock", 16.5f, y, 1);
        cap("pause", 17.5f, y, 1);
        // number row
        y = 1.5f;
        row(new String[]{"grave.accent", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "minus", "equal"}, 0, y);
        cap("backspace", 13, y, 2);
        cap("insert", 15.5f, y, 1);
        cap("home", 16.5f, y, 1);
        cap("page.up", 17.5f, y, 1);
        cap("num.lock", 19, y, 1);
        cap("keypad.divide", 20, y, 1);
        cap("keypad.multiply", 21, y, 1);
        cap("keypad.subtract", 22, y, 1);
        // top letter row
        y = 2.5f;
        cap("tab", 0, y, 1.5f);
        row(new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "left.bracket", "right.bracket"}, 1.5f, y);
        cap("backslash", 13.5f, y, 1.5f);
        cap("delete", 15.5f, y, 1);
        cap("end", 16.5f, y, 1);
        cap("page.down", 17.5f, y, 1);
        row(new String[]{"keypad.7", "keypad.8", "keypad.9"}, 19, y);
        CAPS.add(new Cap("key.keyboard.keypad.add", 22, y, 1, 2));
        // home row
        y = 3.5f;
        cap("caps.lock", 0, y, 1.75f);
        row(new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l", "semicolon", "apostrophe"}, 1.75f, y);
        cap("enter", 12.75f, y, 2.25f);
        row(new String[]{"keypad.4", "keypad.5", "keypad.6"}, 19, y);
        // bottom letter row
        y = 4.5f;
        cap("left.shift", 0, y, 2.25f);
        row(new String[]{"z", "x", "c", "v", "b", "n", "m", "comma", "period", "slash"}, 2.25f, y);
        cap("right.shift", 12.25f, y, 2.75f);
        cap("up", 16.5f, y, 1);
        row(new String[]{"keypad.1", "keypad.2", "keypad.3"}, 19, y);
        CAPS.add(new Cap("key.keyboard.keypad.enter", 22, y, 1, 2));
        // space row
        y = 5.5f;
        cap("left.control", 0, y, 1.25f);
        cap("left.win", 1.25f, y, 1.25f);
        cap("left.alt", 2.5f, y, 1.25f);
        cap("space", 3.75f, y, 6.25f);
        cap("right.alt", 10, y, 1.25f);
        cap("right.win", 11.25f, y, 1.25f);
        cap("menu", 12.5f, y, 1.25f);
        cap("right.control", 13.75f, y, 1.25f);
        cap("left", 15.5f, y, 1);
        cap("down", 16.5f, y, 1);
        cap("right", 17.5f, y, 1);
        cap("keypad.0", 19, y, 2);
        cap("keypad.decimal", 21, y, 1);
        // mouse
        y = 7f;
        String[] mouse = {"key.mouse.left", "key.mouse.middle", "key.mouse.right", "key.mouse.4", "key.mouse.5", "key.mouse.6", "key.mouse.7", "key.mouse.8"};
        for (int i = 0; i < mouse.length; i++) CAPS.add(new Cap(mouse[i], i * 2.5f, y, 2.4f, 1));
    }

    private static void cap(String name, float x, float y, float w) {
        CAPS.add(new Cap("key.keyboard." + name, x, y, w, 1));
    }

    private static void row(String[] names, float x, float y) {
        for (int i = 0; i < names.length; i++) cap(names[i], x + i, y, 1);
    }

    private final Map<String, List<Use>> uses = new HashMap<>();
    private int left, top, unit;

    /** The key (InputConstants name) a press ends with; modifier-only triggers land on both keys of that modifier. */
    static List<String> keysOf(KeyTrigger trigger) {
        List<String> keys = new ArrayList<>();
        if (trigger == null || trigger.last() == null) return keys;
        KeyTrigger.Unit last = trigger.last();
        if (last.key() != null) {
            keys.add(last.key().getName());
        } else if ((last.modifier() & KeyCombo.CTRL) != 0) {
            keys.add("key.keyboard.left.control");
            keys.add("key.keyboard.right.control");
        } else if ((last.modifier() & KeyCombo.SHIFT) != 0) {
            keys.add("key.keyboard.left.shift");
            keys.add("key.keyboard.right.shift");
        } else if ((last.modifier() & KeyCombo.ALT) != 0) {
            keys.add("key.keyboard.left.alt");
            keys.add("key.keyboard.right.alt");
        }
        return keys;
    }

    void clear() {
        uses.clear();
    }

    void add(KeyTrigger trigger, Use use) {
        for (String key : keysOf(trigger)) uses.computeIfAbsent(key, k -> new ArrayList<>()).add(use);
    }

    List<Use> usesOf(String key) {
        return uses.getOrDefault(key, List.of());
    }

    /** Places the map inside the given box (centered, as big as fits). */
    void layout(int x, int y, int w, int h) {
        unit = Math.max(8, (int) Math.min((w - 4) / WIDTH, (h - 4) / HEIGHT));
        left = x + (w - (int) (WIDTH * unit)) / 2;
        top = y + (h - (int) (HEIGHT * unit)) / 2;
    }

    /** The key under the mouse, or null. */
    String keyAt(double mouseX, double mouseY) {
        for (Cap cap : CAPS) {
            int x0 = left + (int) (cap.x() * unit), y0 = top + (int) (cap.y() * unit);
            int x1 = left + (int) ((cap.x() + cap.w()) * unit) - 1, y1 = top + (int) ((cap.y() + cap.h()) * unit) - 1;
            if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) return cap.key();
        }
        return null;
    }

    private static final Map<String, String> SHORT = new HashMap<>();

    static {
        String[][] pairs = {{"escape", "Esc"}, {"print.screen", "PrtSc"}, {"scroll.lock", "ScrLk"}, {"pause", "Pause"}, {"grave.accent", "`"},
                {"backspace", "Bksp"}, {"insert", "Ins"}, {"home", "Home"}, {"page.up", "PgUp"}, {"delete", "Del"}, {"end", "End"},
                {"page.down", "PgDn"}, {"num.lock", "NumLk"}, {"keypad.divide", "/"}, {"keypad.multiply", "*"}, {"keypad.subtract", "-"},
                {"keypad.add", "+"}, {"keypad.enter", "Ent"}, {"keypad.decimal", "."}, {"caps.lock", "Caps"}, {"tab", "Tab"},
                {"enter", "Enter"}, {"left.shift", "Shift"}, {"right.shift", "Shift"}, {"left.control", "Ctrl"}, {"right.control", "Ctrl"},
                {"left.alt", "Alt"}, {"right.alt", "Alt"}, {"left.win", "Win"}, {"right.win", "Win"}, {"menu", "Menu"}, {"space", "Space"},
                {"up", "↑"}, {"down", "↓"}, {"left", "←"}, {"right", "→"}, {"left.bracket", "["}, {"right.bracket", "]"},
                {"backslash", "\\"}, {"semicolon", ";"}, {"apostrophe", "'"}, {"comma", ","}, {"period", "."}, {"slash", "/"},
                {"minus", "-"}, {"equal", "="}};
        for (String[] p : pairs) SHORT.put("key.keyboard." + p[0], p[1]);
        for (int i = 1; i <= 12; i++) SHORT.put("key.keyboard.f" + i, "F" + i);
        for (int i = 0; i <= 9; i++) SHORT.put("key.keyboard.keypad." + i, String.valueOf(i));
        SHORT.put("key.mouse.left", "LMB");
        SHORT.put("key.mouse.middle", "MMB");
        SHORT.put("key.mouse.right", "RMB");
        for (int i = 4; i <= 8; i++) SHORT.put("key.mouse." + i, "M" + i);
    }

    /** The full name (tooltip, search). */
    static String label(String key) {
        return InputConstants.getKey(key).getDisplayName().getString();
    }

    /** What fits on the key cap. */
    private static String capLabel(String key) {
        String s = SHORT.get(key);
        return s != null ? s : label(key);
    }

    void render(GuiGraphics context, Font font, int mouseX, int mouseY) {
        String hovered = keyAt(mouseX, mouseY);
        for (Cap cap : CAPS) {
            int x0 = left + (int) (cap.x() * unit), y0 = top + (int) (cap.y() * unit);
            int x1 = left + (int) ((cap.x() + cap.w()) * unit) - 1, y1 = top + (int) ((cap.y() + cap.h()) * unit) - 1;
            List<Use> list = usesOf(cap.key());
            KeyConflicts.Level worst = KeyConflicts.Level.NONE;
            for (Use u : list) if (u.level().ordinal() > worst.ordinal()) worst = u.level();
            int fill = list.isEmpty() ? 0xFF262626 : worst == KeyConflicts.Level.NONE ? 0xFF2F4F6F : worst.color() & 0x00FFFFFF | 0xFF000000;
            if (!list.isEmpty() && worst != KeyConflicts.Level.NONE) fill = darken(fill);
            int border = cap.key().equals(hovered) ? GuiUtil.WHITE : list.isEmpty() ? 0xFF4A4A4A : 0xFF8AA8C8;
            // a pixel key cap: dark outline, face, a lighter top edge and a darker bottom edge
            context.fill(x0, y0, x1, y1, 0xFF0C0C0C);
            context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
            context.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, lighten(fill));
            context.fill(x0 + 1, y1 - 3, x1 - 1, y1 - 1, darken(fill));
            if (cap.key().equals(hovered)) {
                context.fill(x0, y0, x1, y0 + 1, border);
                context.fill(x0, y1 - 1, x1, y1, border);
                context.fill(x0, y0, x0 + 1, y1, border);
                context.fill(x1 - 1, y0, x1, y1, border);
            }
            String label = capLabel(cap.key());
            // F10 and the like on a small screen: the part that tells keys apart
            if (font.width(label) > x1 - x0 - 2 && label.length() > 2 && label.startsWith("F") && Character.isDigit(label.charAt(1))) {
                label = label.substring(1);
            }
            String text = GuiUtil.ellipsize(font, label, Math.max(4, x1 - x0 - 2));
            int textColor = list.isEmpty() ? GuiUtil.DARK_GRAY : GuiUtil.WHITE;
            context.drawString(font, text, x0 + Math.max(1, (x1 - x0 - font.width(text)) / 2), y0 + Math.max(1, (y1 - y0 - font.lineHeight) / 2 + 1), textColor);
        }
    }

    private static int lighten(int argb) {
        int r = Math.min(255, (argb >> 16 & 0xFF) + 40), g = Math.min(255, (argb >> 8 & 0xFF) + 40), b = Math.min(255, (argb & 0xFF) + 40);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int darken(int argb) {
        int r = (argb >> 16 & 0xFF) * 3 / 5, g = (argb >> 8 & 0xFF) * 3 / 5, b = (argb & 0xFF) * 3 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Tooltip of a key: its name, then every binding on it (or "free"). */
    List<Component> tooltip(String key) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(label(key)));
        List<Use> list = usesOf(key);
        if (list.isEmpty()) {
            lines.add(Component.translatable("keybindprofilesplus.keyboard.free").withStyle(net.minecraft.ChatFormatting.GRAY));
            return lines;
        }
        int shown = 0;
        for (Use u : list) {
            if (shown++ == 14) {
                lines.add(Component.translatable("keybindprofilesplus.keyboard.more", list.size() - 14).withStyle(net.minecraft.ChatFormatting.GRAY));
                break;
            }
            lines.add(u.line());
        }
        lines.add(Component.translatable("keybindprofilesplus.keyboard.click").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return lines;
    }

    int bottom() {
        return top + (int) (HEIGHT * unit);
    }
}
