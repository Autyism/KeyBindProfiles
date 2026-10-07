package io.github.autyism.keybindprofilesplus.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
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
    }
}
