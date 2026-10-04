package io.github.autyism.keybindprofilesplus.input;

import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
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
    public static Text keypadName(int keyCode) {
        if (keyCode >= InputUtil.GLFW_KEY_KP_0 && keyCode <= InputUtil.GLFW_KEY_KP_9) {
            return Text.translatable(KEYPAD_KEY, String.valueOf(keyCode - InputUtil.GLFW_KEY_KP_0));
        }

        return switch (keyCode) {
            case InputUtil.GLFW_KEY_KP_DECIMAL -> Text.translatable(KEYPAD_KEY, ".");
            case GLFW.GLFW_KEY_KP_DIVIDE -> Text.translatable(KEYPAD_KEY, "/");
            case InputUtil.GLFW_KEY_KP_MULTIPLY -> Text.translatable(KEYPAD_KEY, "*");
            case GLFW.GLFW_KEY_KP_SUBTRACT -> Text.translatable(KEYPAD_KEY, "-");
            case InputUtil.GLFW_KEY_KP_ADD -> Text.translatable(KEYPAD_KEY, "+");
            case InputUtil.GLFW_KEY_KP_EQUAL -> Text.translatable(KEYPAD_KEY, "=");
            case InputUtil.GLFW_KEY_KP_ENTER -> Text.translatable(KEYPAD_KEY, Text.translatable("key.keyboard.enter"));
            default -> null;
        };
    }
}
