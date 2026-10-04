package io.github.autyism.keybindprofilesplus.keys;

import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A key together with the modifier keys that must be held with it: "Ctrl + X".
 *
 * <p>As text it is written {@code ctrl+shift+key.keyboard.x}; a plain key is just its vanilla
 * name ({@code key.keyboard.x}), so values written before combinations existed still read fine.
 *
 * @param modifiers bit mask of {@link #SHIFT}, {@link #CTRL}, {@link #ALT} (the GLFW modifier bits)
 * @param key       the vanilla translation key of the main key, e.g. {@code key.keyboard.x}
 */
public record KeyCombo(int modifiers, String key) {
    public static final int SHIFT = 1;
    public static final int CTRL = 2;
    public static final int ALT = 4;
    public static final int ALL = SHIFT | CTRL | ALT;

    private static final String SEPARATOR = "+";

    public static KeyCombo plain(String key) {
        return new KeyCombo(0, key);
    }

    /** Reads the text form. Unknown modifier words are ignored, so the key itself always survives. */
    public static KeyCombo parse(String text) {
        if (text == null) {
            return plain("key.keyboard.unknown");
        }
        String[] parts = text.split("\\+");
        int modifiers = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            modifiers |= modifierBit(parts[i]);
        }
        return new KeyCombo(modifiers, parts[parts.length - 1].trim());
    }

    public String encode() {
        return encode(modifiers, key);
    }

    public static String encode(int modifiers, String key) {
        StringBuilder text = new StringBuilder();
        for (String name : modifierNames(modifiers)) {
            text.append(name).append(SEPARATOR);
        }
        return text.append(key).toString();
    }

    public boolean isPlain() {
        return modifiers == 0;
    }

    /** The main key, or null when the name is not a key the game knows. */
    public InputUtil.Key inputKey() {
        try {
            return InputUtil.fromTranslationKey(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "Ctrl + Shift + X" in the game's language. */
    public Text displayText() {
        InputUtil.Key inputKey = inputKey();
        return withModifiers(modifiers, inputKey == null ? Text.literal(key) : inputKey.getLocalizedText());
    }

    /** Formats a stored text value for display. */
    public static Text describe(String encoded) {
        return parse(encoded).displayText();
    }

    public static Text withModifiers(int modifiers, Text keyName) {
        if (modifiers == 0) {
            return keyName;
        }
        MutableText text = Text.empty();
        for (String name : modifierNames(modifiers)) {
            text.append(Text.translatable("keybindprofilesplus.modifier." + name)).append(" + ");
        }
        return text.append(keyName);
    }

    /** The modifier a key stands for when held ({@link #CTRL} for either Control key...), or 0. */
    public static int modifierOfKeyCode(int keyCode) {
        return switch (keyCode) {
            case InputUtil.GLFW_KEY_LEFT_CONTROL, InputUtil.GLFW_KEY_RIGHT_CONTROL -> CTRL;
            case InputUtil.GLFW_KEY_LEFT_SHIFT, InputUtil.GLFW_KEY_RIGHT_SHIFT -> SHIFT;
            case InputUtil.GLFW_KEY_LEFT_ALT, InputUtil.GLFW_KEY_RIGHT_ALT -> ALT;
            default -> 0;
        };
    }

    private static List<String> modifierNames(int modifiers) {
        List<String> names = new ArrayList<>(3);
        if ((modifiers & CTRL) != 0) {
            names.add("ctrl");
        }
        if ((modifiers & SHIFT) != 0) {
            names.add("shift");
        }
        if ((modifiers & ALT) != 0) {
            names.add("alt");
        }
        return names;
    }

    private static int modifierBit(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "ctrl", "control" -> CTRL;
            case "shift" -> SHIFT;
            case "alt" -> ALT;
            default -> 0;
        };
    }
}
