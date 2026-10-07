//? if >=26.3 {
/*package io.github.autyism.keybindprofilesplus.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLMouse;

/^*
 * Minecraft 26.3+ reads the keyboard through SDL: keys are SDL scancodes, mouse buttons start at 1
 * and the modifier bits of key events are SDL's. What this mod saves stays the same on every version:
 * the game's key names as they were up to 26.2 ("key.keyboard.keypad.decimal" for the numpad dot)
 * and its own Ctrl / Shift / Alt bits, so profiles, share codes and settings move between versions
 * and still mean the same physical keys.
 ^/
public final class SdlKeys {
    // The numpad dot and the context menu key: SDL gives them other names than the game had before.
    private static final String NUMPAD_DOT = "key.keyboard.keypad.decimal";
    private static final String NUMPAD_DOT_SDL = "key.keyboard.keypad.period";
    private static final String MENU_KEY = "key.keyboard.menu";
    private static final String MENU_KEY_SDL = "key.keyboard.application";
    // The SDL keys that now carry those two names have no counterpart before 26.3; saved by their number.
    private static final String SDL_DECIMAL_SAVED = "key.keyboard.220";
    private static final String SDL_MENU_SAVED = "key.keyboard.118";

    /^* GLFW key code -> GLFW key name and the game's key name up to 26.2, for files written before 26.3. ^/
    private static final Map<Integer, String> GLFW_CODE_TO_KEY = new HashMap<>();
    /^* GLFW key name (what malilib and libIPN write, without "GLFW_KEY_") -> the game's key name up to 26.2. ^/
    private static final Map<String, String> GLFW_NAME_TO_KEY = new HashMap<>();
    /^* malilib's names on 26.3+ that differ from the GLFW names it wrote before. ^/
    private static final Map<String, String> MALILIB_SDL_TO_GLFW = Map.of(
            "RETURN", "ENTER", "EQUALS", "EQUAL", "GRAVE", "GRAVE_ACCENT", "LEFT_GUI", "LEFT_SUPER",
            "RIGHT_GUI", "RIGHT_SUPER", "KP_PLUS", "KP_ADD", "KP_MINUS", "KP_SUBTRACT", "KP_EQUALS", "KP_EQUAL",
            "KP_PERIOD", "KP_DECIMAL", "APPLICATION", "MENU");
    private static final Map<String, String> MALILIB_GLFW_TO_SDL = new HashMap<>();

    static {
        MALILIB_SDL_TO_GLFW.forEach((sdl, glfw) -> MALILIB_GLFW_TO_SDL.put(glfw, sdl));
        row(32, "SPACE", "space");
        row(39, "APOSTROPHE", "apostrophe");
        row(44, "COMMA", "comma");
        row(45, "MINUS", "minus");
        row(46, "PERIOD", "period");
        row(47, "SLASH", "slash");
        row(48, "0", "0");
        row(49, "1", "1");
        row(50, "2", "2");
        row(51, "3", "3");
        row(52, "4", "4");
        row(53, "5", "5");
        row(54, "6", "6");
        row(55, "7", "7");
        row(56, "8", "8");
        row(57, "9", "9");
        row(59, "SEMICOLON", "semicolon");
        row(61, "EQUAL", "equal");
        row(65, "A", "a");
        row(66, "B", "b");
        row(67, "C", "c");
        row(68, "D", "d");
        row(69, "E", "e");
        row(70, "F", "f");
        row(71, "G", "g");
        row(72, "H", "h");
        row(73, "I", "i");
        row(74, "J", "j");
        row(75, "K", "k");
        row(76, "L", "l");
        row(77, "M", "m");
        row(78, "N", "n");
        row(79, "O", "o");
        row(80, "P", "p");
        row(81, "Q", "q");
        row(82, "R", "r");
        row(83, "S", "s");
        row(84, "T", "t");
        row(85, "U", "u");
        row(86, "V", "v");
        row(87, "W", "w");
        row(88, "X", "x");
        row(89, "Y", "y");
        row(90, "Z", "z");
        row(91, "LEFT_BRACKET", "left.bracket");
        row(92, "BACKSLASH", "backslash");
        row(93, "RIGHT_BRACKET", "right.bracket");
        row(96, "GRAVE_ACCENT", "grave.accent");
        row(161, "WORLD_1", "world.1");
        row(162, "WORLD_2", "world.2");
        row(256, "ESCAPE", "escape");
        row(257, "ENTER", "enter");
        row(258, "TAB", "tab");
        row(259, "BACKSPACE", "backspace");
        row(260, "INSERT", "insert");
        row(261, "DELETE", "delete");
        row(262, "RIGHT", "right");
        row(263, "LEFT", "left");
        row(264, "DOWN", "down");
        row(265, "UP", "up");
        row(266, "PAGE_UP", "page.up");
        row(267, "PAGE_DOWN", "page.down");
        row(268, "HOME", "home");
        row(269, "END", "end");
        row(280, "CAPS_LOCK", "caps.lock");
        row(281, "SCROLL_LOCK", "scroll.lock");
        row(282, "NUM_LOCK", "num.lock");
        row(283, "PRINT_SCREEN", "print.screen");
        row(284, "PAUSE", "pause");
        row(290, "F1", "f1");
        row(291, "F2", "f2");
        row(292, "F3", "f3");
        row(293, "F4", "f4");
        row(294, "F5", "f5");
        row(295, "F6", "f6");
        row(296, "F7", "f7");
        row(297, "F8", "f8");
        row(298, "F9", "f9");
        row(299, "F10", "f10");
        row(300, "F11", "f11");
        row(301, "F12", "f12");
        row(302, "F13", "f13");
        row(303, "F14", "f14");
        row(304, "F15", "f15");
        row(305, "F16", "f16");
        row(306, "F17", "f17");
        row(307, "F18", "f18");
        row(308, "F19", "f19");
        row(309, "F20", "f20");
        row(310, "F21", "f21");
        row(311, "F22", "f22");
        row(312, "F23", "f23");
        row(313, "F24", "f24");
        row(314, "F25", "f25");
        row(320, "KP_0", "keypad.0");
        row(321, "KP_1", "keypad.1");
        row(322, "KP_2", "keypad.2");
        row(323, "KP_3", "keypad.3");
        row(324, "KP_4", "keypad.4");
        row(325, "KP_5", "keypad.5");
        row(326, "KP_6", "keypad.6");
        row(327, "KP_7", "keypad.7");
        row(328, "KP_8", "keypad.8");
        row(329, "KP_9", "keypad.9");
        row(330, "KP_DECIMAL", "keypad.decimal");
        row(331, "KP_DIVIDE", "keypad.divide");
        row(332, "KP_MULTIPLY", "keypad.multiply");
        row(333, "KP_SUBTRACT", "keypad.subtract");
        row(334, "KP_ADD", "keypad.add");
        row(335, "KP_ENTER", "keypad.enter");
        row(336, "KP_EQUAL", "keypad.equal");
        row(340, "LEFT_SHIFT", "left.shift");
        row(341, "LEFT_CONTROL", "left.control");
        row(342, "LEFT_ALT", "left.alt");
        row(343, "LEFT_SUPER", "left.win");
        row(344, "RIGHT_SHIFT", "right.shift");
        row(345, "RIGHT_CONTROL", "right.control");
        row(346, "RIGHT_ALT", "right.alt");
        row(347, "RIGHT_SUPER", "right.win");
        row(348, "MENU", "menu");
    }

    private SdlKeys() {
    }

    private static void row(int glfwCode, String glfwName, String keyName) {
        GLFW_CODE_TO_KEY.put(glfwCode, "key.keyboard." + keyName);
        GLFW_NAME_TO_KEY.put(glfwName, "key.keyboard." + keyName);
    }

    // ------------------------------------------------------------------ key names

    /^* The game's key name (26.3+) -> the name this mod saves (the one the game used up to 26.2). ^/
    public static String toStoredName(String gameName) {
        if (gameName == null) {
            return null;
        }
        return switch (gameName) {
            case NUMPAD_DOT_SDL -> NUMPAD_DOT;
            case MENU_KEY_SDL -> MENU_KEY;
            case NUMPAD_DOT -> SDL_DECIMAL_SAVED;
            case MENU_KEY -> SDL_MENU_SAVED;
            default -> gameName;
        };
    }

    /^* A saved key name -> the game's key name (26.3+). ^/
    public static String toGameName(String storedName) {
        if (storedName == null) {
            return null;
        }
        return switch (storedName) {
            case NUMPAD_DOT -> NUMPAD_DOT_SDL;
            case MENU_KEY -> MENU_KEY_SDL;
            case SDL_DECIMAL_SAVED -> NUMPAD_DOT;
            case SDL_MENU_SAVED -> MENU_KEY;
            default -> storedName;
        };
    }

    /^* A GLFW key code from a file written before 26.3 -> the saved key name, or the "unknown" key's name. ^/
    public static String nameOfGlfwCode(int glfwCode) {
        return GLFW_CODE_TO_KEY.getOrDefault(glfwCode, InputConstants.UNKNOWN.getName());
    }

    /^* A GLFW key code from a file written before 26.3 -> the game's key (the "unknown" key when SDL has none). ^/
    public static InputConstants.Key keyOfGlfwCode(int glfwCode) {
        try {
            return InputConstants.getKey(toGameName(nameOfGlfwCode(glfwCode)));
        } catch (IllegalArgumentException e) {
            return InputConstants.UNKNOWN;
        }
    }

    /^*
     * A GLFW mouse button (0 left, 1 right, 2 middle, from 3 on the side buttons) -> the game's button
     * number on 26.3+, where SDL counts left, middle, right from 1.
     ^/
    public static int mouseButtonOfGlfw(int glfwButton) {
        return switch (glfwButton) {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> InputConstants.MOUSE_BUTTON_4 + glfwButton - 3;
        };
    }

    /^* A GLFW key name ("LEFT_CONTROL", "KP_5"; malilib also writes its 26.3 names) -> the game's key, or null. ^/
    public static InputConstants.Key keyOfGlfwName(String name) {
        String upper = name.trim().toUpperCase(Locale.ROOT);
        String stored = GLFW_NAME_TO_KEY.get(MALILIB_SDL_TO_GLFW.getOrDefault(upper, upper));
        if (stored == null) {
            return null;
        }
        try {
            return InputConstants.getKey(toGameName(stored));
        } catch (IllegalArgumentException e) {
            // A key SDL does not have (F25).
            return null;
        }
    }

    // ------------------------------------------------------------------ malilib on 26.3+

    /^* malilib's hotkey text on 26.3+ -> the text saved in profiles (the GLFW names malilib wrote before). ^/
    public static String malilibToStored(String keys) {
        return renameKeys(keys, MALILIB_SDL_TO_GLFW);
    }

    /^* Saved hotkey text -> the names malilib 26.3+ reads as the same keys. ^/
    public static String storedToMalilib(String keys) {
        return renameKeys(keys, MALILIB_GLFW_TO_SDL);
    }

    private static String renameKeys(String keys, Map<String, String> names) {
        if (keys == null || keys.isBlank()) {
            return keys;
        }
        String[] parts = keys.split(",");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            String part = parts[i].trim();
            text.append(names.getOrDefault(part.toUpperCase(Locale.ROOT), part));
        }
        return text.toString();
    }

    /^* The SDL key code of a scancode with the current keyboard layout (the key code a key event carries). ^/
    public static int sdlKeyCode(int scancode) {
        return SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) SDLKeycode.SDL_KMOD_NONE, false);
    }

    // ------------------------------------------------------------------ modifiers and held keys

    /^* SDL modifier bits of an input event -> this mod's Ctrl / Shift / Alt bits. ^/
    public static int toKbpModifiers(int sdlModifiers) {
        int modifiers = 0;
        if ((sdlModifiers & SDLKeycode.SDL_KMOD_SHIFT) != 0) {
            modifiers |= KeyCombo.SHIFT;
        }
        if ((sdlModifiers & SDLKeycode.SDL_KMOD_CTRL) != 0) {
            modifiers |= KeyCombo.CTRL;
        }
        if ((sdlModifiers & SDLKeycode.SDL_KMOD_ALT) != 0) {
            modifiers |= KeyCombo.ALT;
        }
        return modifiers;
    }

    /^* This mod's Ctrl / Shift / Alt bits -> SDL modifier bits (the left-hand keys), for events made by the self-test. ^/
    public static int toSdlModifiers(int kbpModifiers) {
        int modifiers = SDLKeycode.SDL_KMOD_NONE;
        if ((kbpModifiers & KeyCombo.SHIFT) != 0) {
            modifiers |= SDLKeycode.SDL_KMOD_LSHIFT;
        }
        if ((kbpModifiers & KeyCombo.CTRL) != 0) {
            modifiers |= SDLKeycode.SDL_KMOD_LCTRL;
        }
        if ((kbpModifiers & KeyCombo.ALT) != 0) {
            modifiers |= SDLKeycode.SDL_KMOD_LALT;
        }
        return modifiers;
    }

    /^* Whether a mouse button (the game's numbering, which is SDL's) is held right now. ^/
    public static boolean isMouseButtonDown(int button) {
        int buttons = SDLMouse.SDL_GetMouseState(null, null);
        return button > 0 && (buttons & (1 << (button - 1))) != 0;
    }
}
*///?}
