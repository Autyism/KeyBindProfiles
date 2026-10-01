package io.github.autyi6969.keybindprofilesplus.external;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the hotkeys of malilib-based mods (Litematica, MiniHUD, Tweakeroo, Item Scroller...) from
 * their {@code config/<modid>.json}. Read-only: the files are opened for reading and never written.
 *
 * <p>malilib writes a hotkey as an object with a "keys" text such as {@code "LEFT_CONTROL,X"}:
 * the GLFW key names without the "GLFW_KEY_" prefix, mouse buttons as "BUTTON_n". Its optional
 * "settings.context" says whether it works in game, in screens ("GUI") or both.
 */
final class MalilibKeys {
    static final String LIBRARY_ID = "malilib";
    private static final Map<String, Integer> KEY_CODES = glfwKeyCodes();

    private MalilibKeys() {
    }

    static List<ExternalBinding> read(Path gameDirectory, String modId, String modName) {
        List<ExternalBinding> bindings = new ArrayList<>();
        String relativePath = "config/" + modId + ".json";
        Path file = gameDirectory.resolve("config").resolve(modId + ".json");
        if (!Files.isRegularFile(file)) {
            return bindings;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            walk(JsonParser.parseReader(reader), "", modId, Text.literal(modName), relativePath, bindings);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            KeyBindProfilesPlus.LOGGER.warn("Could not read malilib hotkeys from '{}': {}", file, e.toString());
        }
        return bindings;
    }

    private static void walk(JsonElement element, String name, String modId, Text group, String file, List<ExternalBinding> out) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement keys = object.get("keys");
        if (keys != null && keys.isJsonPrimitive() && keys.getAsJsonPrimitive().isString()) {
            add(name, keys.getAsString(), object, modId, group, file, out);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            // A toggle with a hotkey is written as {"enabled": ..., "hotkey": {"keys": ...}}: keep the toggle's name.
            String childName = entry.getKey().equals("hotkey") && !name.isEmpty() ? name : entry.getKey();
            walk(entry.getValue(), childName, modId, group, file, out);
        }
    }

    private static void add(String name, String keysText, JsonObject hotkey, String modId, Text group, String file, List<ExternalBinding> out) {
        List<String> names = new ArrayList<>();
        for (String part : keysText.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        if (names.isEmpty()) {
            return;
        }

        int modifiers = 0;
        List<InputUtil.Key> ordinary = new ArrayList<>();
        List<InputUtil.Key> modifierKeys = new ArrayList<>();
        MutableText chord = Text.empty();
        boolean allKnown = true;
        for (int i = 0; i < names.size(); i++) {
            InputUtil.Key key = toKey(names.get(i));
            if (i > 0) {
                chord.append(" + ");
            }
            if (key == null) {
                allKnown = false;
                chord.append(names.get(i));
                continue;
            }
            chord.append(key.getLocalizedText());
            int modifier = key.getCategory() == InputUtil.Type.KEYSYM ? KeyCombo.modifierOfKeyCode(key.getCode()) : 0;
            if (modifier != 0) {
                modifiers |= modifier;
                modifierKeys.add(key);
            } else {
                ordinary.add(key);
            }
        }

        // Comparable with a game key binding only when it is one key plus (optionally) Ctrl / Shift / Alt.
        InputUtil.Key mainKey = null;
        Text keyText = chord;
        if (allKnown && ordinary.size() == 1) {
            mainKey = ordinary.get(0);
            keyText = KeyCombo.withModifiers(modifiers, mainKey.getLocalizedText());
        } else if (allKnown && ordinary.isEmpty() && modifierKeys.size() == 1) {
            mainKey = modifierKeys.get(0);
            modifiers = 0;
        } else {
            modifiers = 0;
        }

        boolean screenOnly = false;
        if (hotkey.get("settings") instanceof JsonObject settings && settings.get("context") != null && settings.get("context").isJsonPrimitive()) {
            screenOnly = "GUI".equalsIgnoreCase(settings.get("context").getAsString());
        }
        out.add(new ExternalBinding(modId, group, readableName(name), modifiers, mainKey, keyText, screenOnly, true, file));
    }

    /** "LEFT_CONTROL" / "BUTTON_3" -> the game's key object; null for names that are not keys (scroll wheel...). */
    static InputUtil.Key toKey(String malilibName) {
        String name = malilibName.trim().toUpperCase(Locale.ROOT);
        if (name.startsWith("BUTTON_")) {
            try {
                int button = Integer.parseInt(name.substring("BUTTON_".length()));
                return button >= 1 && button <= 8 ? InputUtil.Type.MOUSE.createFromCode(button - 1) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        Integer code = KEY_CODES.get(name);
        return code == null ? null : InputUtil.Type.KEYSYM.createFromCode(code);
    }

    /** "toggleAllRendering" -> "Toggle All Rendering". */
    static String readableName(String camelCase) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (c == '_' || c == '-') {
                name.append(' ');
                continue;
            }
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(camelCase.charAt(i - 1)) && name.charAt(name.length() - 1) != ' ') {
                name.append(' ');
            }
            name.append(i == 0 || name.charAt(name.length() - 1) == ' ' ? Character.toUpperCase(c) : c);
        }
        return name.toString().trim();
    }

    /** Every GLFW_KEY_* constant by its name without the prefix. GLFW is a library, so its names are stable. */
    private static Map<String, Integer> glfwKeyCodes() {
        Map<String, Integer> codes = new HashMap<>();
        for (Field field : GLFW.class.getFields()) {
            if (field.getName().startsWith("GLFW_KEY_") && field.getType() == int.class && Modifier.isStatic(field.getModifiers())) {
                try {
                    codes.put(field.getName().substring("GLFW_KEY_".length()), field.getInt(null));
                } catch (IllegalAccessException ignored) {
                    // Public constants are always readable.
                }
            }
        }
        codes.remove("UNKNOWN");
        codes.remove("LAST");
        return codes;
    }
}
