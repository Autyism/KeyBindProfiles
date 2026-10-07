package io.github.autyism.keybindprofilesplus.keys;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
    public InputConstants.Key inputKey() {
        try {
            //? if >=26.3 {
            /*return InputConstants.getKey(io.github.autyism.keybindprofilesplus.input.SdlKeys.toGameName(key));
            *///?} else
            return InputConstants.getKey(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "Ctrl + Shift + X" in the game's language. */
    public Component displayText() {
        InputConstants.Key inputKey = inputKey();
        return withModifiers(modifiers, inputKey == null ? Component.literal(key) : inputKey.getDisplayName());
    }

    /** Formats a stored text value for display. */
    public static Component describe(String encoded) {
        return parse(encoded).displayText();
    }

    public static Component withModifiers(int modifiers, Component keyName) {
        if (modifiers == 0) {
            return keyName;
        }
        MutableComponent text = Component.empty();
        for (String name : modifierNames(modifiers)) {
            text.append(Component.translatable("keybindprofilesplus.modifier." + name)).append(" + ");
        }
        return text.append(keyName);
    }

    /** The modifier a key stands for when held ({@link #CTRL} for either Control key...), or 0. */
    public static int modifierOfKeyCode(int keyCode) {
        return switch (keyCode) {
            case InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL -> CTRL;
            case InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT -> SHIFT;
            case InputConstants.KEY_LALT, InputConstants.KEY_RALT -> ALT;
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
