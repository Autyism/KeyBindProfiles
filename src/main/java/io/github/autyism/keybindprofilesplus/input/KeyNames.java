package io.github.autyism.keybindprofilesplus.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
//? if <26.3
import org.lwjgl.glfw.GLFW;

/**
 * Display names for keys where vanilla's are misleading. Vanilla asks the keyboard layout for the
 * printed character, so numpad 5 and the top-row 5 both show up as "5".
 */
public final class KeyNames {
    private static final String KEYPAD_KEY = "keybindprofilesplus.key.keypad";

    private KeyNames() {
    }

    /** Returns "Num 5", "Num +", "Num Enter"... for numpad keys, or null for every other key. */
    public static Component keypadName(int keyCode) {
        //? if >=26.3 {
        /*// SDL numbers the numpad differently (0 comes after 9): go by the key's name instead
        String name = InputConstants.Type.KEYSYM.getOrCreate(keyCode).getName();
        if (!name.startsWith("key.keyboard.keypad.")) {
            return null;
        }
        String part = name.substring("key.keyboard.keypad.".length());
        return switch (part) {
            case "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> Component.translatable(KEYPAD_KEY, part);
            case "period", "decimal" -> Component.translatable(KEYPAD_KEY, ".");
            case "divide" -> Component.translatable(KEYPAD_KEY, "/");
            case "multiply" -> Component.translatable(KEYPAD_KEY, "*");
            case "subtract" -> Component.translatable(KEYPAD_KEY, "-");
            case "add" -> Component.translatable(KEYPAD_KEY, "+");
            case "equal" -> Component.translatable(KEYPAD_KEY, "=");
            case "enter" -> Component.translatable(KEYPAD_KEY, Component.translatable("key.keyboard.enter"));
            default -> null;
        };
        *///?} else {
        if (keyCode >= InputConstants.KEY_NUMPAD0 && keyCode <= InputConstants.KEY_NUMPAD9) {
            return Component.translatable(KEYPAD_KEY, String.valueOf(keyCode - InputConstants.KEY_NUMPAD0));
        }

        return switch (keyCode) {
            case InputConstants.KEY_NUMPADCOMMA -> Component.translatable(KEYPAD_KEY, ".");
            case GLFW.GLFW_KEY_KP_DIVIDE -> Component.translatable(KEYPAD_KEY, "/");
            case InputConstants.KEY_MULTIPLY -> Component.translatable(KEYPAD_KEY, "*");
            case GLFW.GLFW_KEY_KP_SUBTRACT -> Component.translatable(KEYPAD_KEY, "-");
            case InputConstants.KEY_ADD -> Component.translatable(KEYPAD_KEY, "+");
            case InputConstants.KEY_NUMPADEQUALS -> Component.translatable(KEYPAD_KEY, "=");
            case InputConstants.KEY_NUMPADENTER -> Component.translatable(KEYPAD_KEY, Component.translatable("key.keyboard.enter"));
            default -> null;
        };
        //?}
    }
}
