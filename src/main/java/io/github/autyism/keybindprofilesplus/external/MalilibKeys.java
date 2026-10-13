package io.github.autyism.keybindprofilesplus.external;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyTrigger;
//? if <26.3
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Reads the hotkeys of malilib-based mods (Litematica, MiniHUD, Tweakeroo, Item Scroller...) from
 * their {@code config/<modid>.json}. Read-only: the files are opened for reading and never written.
 *
 * <p>malilib writes a hotkey as an object with a "keys" text such as {@code "LEFT_CONTROL,X"}:
 * the GLFW key names without the "GLFW_KEY_" prefix, mouse buttons as "BUTTON_n". Its optional
 * "settings" (only written once the player changed them) say whether it works in game, in screens
 * ("GUI") or both, and whether further keys may be held; {@link KnownHotkeys} works out the rest.
 */
final class MalilibKeys {
    static final String LIBRARY_ID = "malilib";
    //? if <26.3
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
            walk(JsonParser.parseReader(reader), "", modId, Component.literal(modName), relativePath, bindings);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            KeyBindProfilesPlus.LOGGER.warn("Could not read malilib hotkeys from '{}': {}", file, e.toString());
        }
        return bindings;
    }

    private static void walk(JsonElement element, String name, String modId, Component group, String file, List<ExternalBinding> out) {
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

    private static void add(String name, String keysText, JsonObject hotkey, String modId, Component group, String file, List<ExternalBinding> out) {
        Trigger trigger = parse(keysText);
        if (trigger == null) {
            return;
        }
        // malilib only writes a hotkey's settings once the player changed them; otherwise its defaults apply.
        String context = null;
        boolean allowExtraKeys = false;
        boolean orderSensitive = true;
        if (hotkey.get("settings") instanceof JsonObject settings) {
            context = string(settings, "context");
            allowExtraKeys = "true".equalsIgnoreCase(string(settings, "allow_extra_keys"));
            orderSensitive = !"false".equalsIgnoreCase(string(settings, "order_sensitive"));
        }
        KeyTrigger keys = trigger.toTrigger(allowExtraKeys && !KnownHotkeys.onlyHeld(modId, name), orderSensitive);
        out.add(ExternalBinding.readOnly(modId, group, readableName(name), trigger.modifiers(), trigger.key(), keys, trigger.text(),
                KnownHotkeys.malilib(modId, name, context, keys), true, file));
    }

    private static String string(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /**
     * What a malilib key text ("LEFT_CONTROL,X") means.
     *
     * @param key       the main key when the hotkey is one key plus (optionally) Ctrl / Shift / Alt, else null
     * @param modifiers Ctrl / Shift / Alt bits held with {@code key} (0 when {@code key} is null)
     * @param text      the whole trigger as shown to the player
     * @param keys      every key in the order stored, or null when one of them is no key the game knows (scroll wheel...)
     */
    record Trigger(InputConstants.Key key, int modifiers, Component text, List<InputConstants.Key> keys) {
        /** The trigger with the hotkey's own rules on further keys held; null when it cannot be compared. */
        KeyTrigger toTrigger(boolean allowExtraKeys, boolean orderSensitive) {
            return keys == null ? null : KeyTrigger.chord(keys, allowExtraKeys, orderSensitive);
        }
    }

    /** Reads a malilib key text; null when it holds no key at all. */
    static Trigger parse(String keysText) {
        List<String> names = new ArrayList<>();
        for (String part : keysText.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        if (names.isEmpty()) {
            return null;
        }

        int modifiers = 0;
        List<InputConstants.Key> keys = new ArrayList<>();
        List<InputConstants.Key> ordinary = new ArrayList<>();
        List<InputConstants.Key> modifierKeys = new ArrayList<>();
        MutableComponent chord = Component.empty();
        boolean allKnown = true;
        for (int i = 0; i < names.size(); i++) {
            InputConstants.Key key = toKey(names.get(i));
            if (i > 0) {
                chord.append(" + ");
            }
            if (key == null) {
                allKnown = false;
                chord.append(names.get(i));
                continue;
            }
            keys.add(key);
            chord.append(key.getDisplayName());
            int modifier = key.getType() == InputConstants.Type.KEYSYM ? KeyCombo.modifierOfKeyCode(key.getValue()) : 0;
            if (modifier != 0) {
                modifiers |= modifier;
                modifierKeys.add(key);
            } else {
                ordinary.add(key);
            }
        }

        // Shown like a game key binding when it is one key plus (optionally) Ctrl / Shift / Alt.
        InputConstants.Key mainKey = null;
        Component keyText = chord;
        if (allKnown && ordinary.size() == 1) {
            mainKey = ordinary.get(0);
            keyText = KeyCombo.withModifiers(modifiers, mainKey.getDisplayName());
        } else if (allKnown && ordinary.isEmpty() && modifierKeys.size() == 1) {
            mainKey = modifierKeys.get(0);
            modifiers = 0;
        } else {
            modifiers = 0;
        }
        return new Trigger(mainKey, modifiers, keyText, allKnown ? List.copyOf(keys) : null);
    }

    /** "LEFT_CONTROL" / "BUTTON_3" -> the game's key object; null for names that are not keys (scroll wheel...). */
    static InputConstants.Key toKey(String malilibName) {
        String name = malilibName.trim().toUpperCase(Locale.ROOT);
        if (name.startsWith("BUTTON_")) {
            try {
                int button = Integer.parseInt(name.substring("BUTTON_".length()));
                //? if >=26.3 {
                /*// BUTTON_n is GLFW's button n - 1 (left, right, middle, ...), whatever malilib counts internally
                return button >= 1 && button <= 8 ? InputConstants.Type.MOUSE.getOrCreate(io.github.autyism.keybindprofilesplus.input.SdlKeys.mouseButtonOfGlfw(button - 1)) : null;
                *///?} else
                return button >= 1 && button <= 8 ? InputConstants.Type.MOUSE.getOrCreate(button - 1) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        //? if >=26.3 {
        /*return io.github.autyism.keybindprofilesplus.input.SdlKeys.keyOfGlfwName(name);
        *///?} else {
        Integer code = KEY_CODES.get(name);
        return code == null ? null : InputConstants.Type.KEYSYM.getOrCreate(code);
        //?}
    }

    /** "toggleAllRendering" -> "Toggle All Rendering", "toolPlaceCorner1" -> "Tool Place Corner 1". */
    static String readableName(String camelCase) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (c == '_' || c == '-') {
                name.append(' ');
                continue;
            }
            char previous = i > 0 ? camelCase.charAt(i - 1) : ' ';
            boolean newWord = (Character.isUpperCase(c) && !Character.isUpperCase(previous)) || (Character.isDigit(c) && Character.isLetter(previous));
            if (i > 0 && newWord && name.charAt(name.length() - 1) != ' ') {
                name.append(' ');
            }
            name.append(i == 0 || name.charAt(name.length() - 1) == ' ' ? Character.toUpperCase(c) : c);
        }
        return name.toString().trim();
    }

    //? if <26.3 {
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
    //?}
}
