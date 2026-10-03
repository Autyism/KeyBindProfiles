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
import java.util.Set;

/**
 * Reads the hotkeys of malilib-based mods (Litematica, MiniHUD, Tweakeroo, Item Scroller...) from
 * their {@code config/<modid>.json}. Read-only: the files are opened for reading and never written.
 *
 * <p>malilib writes a hotkey as an object with a "keys" text such as {@code "LEFT_CONTROL,X"}:
 * the GLFW key names without the "GLFW_KEY_" prefix, mouse buttons as "BUTTON_n". Its optional
 * "settings.context" says whether it works in game, in screens ("GUI") or both; see
 * {@link #when} for how the rest is worked out.
 */
final class MalilibKeys {
    static final String LIBRARY_ID = "malilib";
    /** Mods whose hotkeys work inside inventory screens unless their file says otherwise. */
    private static final Set<String> SCREEN_ONLY_MODS = Set.of("itemscroller");
    /** Hotkeys known to act only in a situation of the mod's own (tool item held, looking at a schematic block...). */
    private static final Map<String, Set<String>> SITUATIONAL_HOTKEYS = Map.of(
            "litematica", Set.of("toolPlaceCorner1", "toolPlaceCorner2", "toolSelectElements", "toolSelectModifierBlock1",
                    "toolSelectModifierBlock2", "pickBlockFirst", "pickBlockLast", "easyPlaceActivation", "renderOverlayThroughBlocks"));
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
        Trigger trigger = parse(keysText);
        if (trigger == null) {
            return;
        }
        String context = null;
        if (hotkey.get("settings") instanceof JsonObject settings && settings.get("context") != null && settings.get("context").isJsonPrimitive()) {
            context = settings.get("context").getAsString();
        }
        ExternalBinding.When when = when(modId, name, context, trigger.bareModifier(), trigger.bareMouseClick());
        out.add(ExternalBinding.readOnly(modId, group, readableName(name), trigger.modifiers(), trigger.key(), trigger.text(), when, true, file));
    }

    /**
     * What a malilib key text ("LEFT_CONTROL,X") means.
     *
     * @param key            the main key when the hotkey is one key plus (optionally) Ctrl / Shift / Alt, else null
     * @param modifiers      Ctrl / Shift / Alt bits held with {@code key} (0 when {@code key} is null)
     * @param text           the whole trigger as shown to the player
     * @param bareModifier   it is a single Ctrl / Shift / Alt key
     * @param bareMouseClick it is the bare left or right mouse button
     */
    record Trigger(InputUtil.Key key, int modifiers, Text text, boolean bareModifier, boolean bareMouseClick) {
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

        boolean bareModifier = allKnown && ordinary.isEmpty() && modifierKeys.size() == 1;
        boolean bareMouseClick = allKnown && modifiers == 0 && ordinary.size() == 1 && ordinary.get(0).getCategory() == InputUtil.Type.MOUSE
                && ordinary.get(0).getCode() <= 1;
        return new Trigger(mainKey, modifiers, keyText, bareModifier, bareMouseClick);
    }

    /**
     * When the hotkey does something. malilib only writes a hotkey's settings to the file once the
     * player has changed them, so most of this comes from what is known about these mods:
     * <ul>
     *   <li>Item Scroller's hotkeys work inside inventory screens;</li>
     *   <li>a hotkey that is one bare Ctrl / Shift / Alt key, or is called "...Modifier", is held
     *       together with something else (scrolling, clicking) and does nothing on its own;</li>
     *   <li>a hotkey on the bare left or right mouse button cannot be meant to replace attacking and
     *       using: it only acts in a situation of the mod's own, like Litematica's tool item being held;</li>
     *   <li>Litematica's tool and schematic pick-block hotkeys, which share their keys with the game
     *       on purpose.</li>
     * </ul>
     */
    static ExternalBinding.When when(String modId, String name, String context, boolean bareModifier, boolean bareMouseClick) {
        if ("GUI".equalsIgnoreCase(context)) {
            return ExternalBinding.When.SCREEN_ONLY;
        }
        if (context == null && SCREEN_ONLY_MODS.contains(modId) && !name.startsWith("openConfigGui") && !name.startsWith("openGui")) {
            return ExternalBinding.When.SCREEN_ONLY;
        }
        if (bareModifier || bareMouseClick || name.contains("Modifier")
                || SITUATIONAL_HOTKEYS.getOrDefault(modId, Set.of()).contains(name)) {
            return ExternalBinding.When.SITUATIONAL;
        }
        return "ANY".equalsIgnoreCase(context) ? ExternalBinding.When.ANYWHERE : ExternalBinding.When.IN_GAME;
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
