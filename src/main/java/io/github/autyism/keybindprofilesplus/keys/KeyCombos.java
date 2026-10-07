package io.github.autyism.keybindprofilesplus.keys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
//? if <1.21.9
/*import java.util.Collection;*/
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Modifier combinations (Ctrl / Shift / Alt + key) for the game's own key bindings.
 *
 * <p>Minecraft stores one key per binding and nothing else, so the modifiers live here, per
 * binding id, and are saved to combos.json next to the profiles. A binding with modifiers only
 * reacts when they are held; while they are held, bindings on the same key without modifiers stay
 * quiet. Bindings on keys nobody has a combination on behave exactly as in vanilla.
 */
public final class KeyCombos {
    private static final String FILE_NAME = "combos.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Integer> MODIFIERS = new HashMap<>();
    private static File file;
    /** False until the saved combinations were read; until then key changes are just the game loading options.txt. */
    private static boolean live;
    private static boolean binding;
    private static int batchDepth;
    private static boolean dirty;
    private static IntSupplier heldModifiers = KeyCombos::readHeldModifiers;

    private KeyCombos() {
    }

    // ------------------------------------------------------------------ queries

    public static int modifiersOf(KeyMapping keyBinding) {
        return MODIFIERS.getOrDefault(keyBinding.getName(), 0);
    }

    public static boolean hasAny() {
        return !MODIFIERS.isEmpty();
    }

    /** The binding's trigger in text form: "ctrl+key.keyboard.x", or just the key when it has no modifiers. */
    public static String valueOf(KeyMapping keyBinding) {
        //? if >=26.3 {
        /*return KeyCombo.encode(modifiersOf(keyBinding), io.github.autyism.keybindprofilesplus.input.SdlKeys.toStoredName(keyBinding.saveString()));
        *///?} else
        return KeyCombo.encode(modifiersOf(keyBinding), keyBinding.saveString());
    }

    /** Whether two bindings react to exactly the same key press. */
    public static boolean sameTrigger(KeyMapping first, KeyMapping second) {
        return first.same(second) && modifiersOf(first) == modifiersOf(second);
    }

    /** Modifier keys held right now, as a {@link KeyCombo} bit mask. */
    public static int heldModifiers() {
        return heldModifiers.getAsInt();
    }

    // ------------------------------------------------------------------ changes

    /** Binds a key together with modifiers (0 for none) and remembers it. */
    public static void bind(KeyMapping keyBinding, InputConstants.Key key, int modifiers) {
        binding = true;
        try {
            keyBinding.setKey(key);
        } finally {
            binding = false;
        }

        int mask = modifiers & KeyCombo.ALL;
        // A modifier cannot require itself: "Ctrl + Left Control" is just Left Control.
        if (key.getType() == InputConstants.Type.KEYSYM) {
            mask &= ~KeyCombo.modifierOfKeyCode(key.getValue());
        }
        Integer previous = mask == 0 ? MODIFIERS.remove(keyBinding.getName()) : MODIFIERS.put(keyBinding.getName(), mask);
        if ((previous == null ? 0 : previous) != mask) {
            markDirty();
        }
    }

    /** Applies a text value ("ctrl+key.keyboard.x"). Returns false when the key name is unknown. */
    public static boolean applyValue(KeyMapping keyBinding, String encoded) {
        KeyCombo combo = KeyCombo.parse(encoded);
        InputConstants.Key key = combo.inputKey();
        if (key == null) {
            return false;
        }
        bind(keyBinding, key, combo.modifiers());
        return true;
    }

    /** Called whenever the game changes a binding's key: its old modifiers no longer apply. */
    public static void onBoundKeyChanged(KeyMapping keyBinding) {
        if (live && !binding && MODIFIERS.remove(keyBinding.getName()) != null) {
            markDirty();
        }
    }

    /** Groups many changes into one write of combos.json. */
    public static void batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            batchDepth--;
            if (batchDepth == 0 && dirty) {
                save();
            }
        }
    }

    // ------------------------------------------------------------------ dispatch

    /**
     * Which of the bindings on one key should react, given the modifiers held: those whose own
     * modifiers are all held, and of those only the ones asking for the most modifiers. So with
     * "X" and "Ctrl + X" on the same key, Ctrl+X reaches only the second and a bare X only the first.
     */
    public static List<KeyMapping> eligible(List<KeyMapping> onSameKey, int held) {
        int most = -1;
        for (KeyMapping candidate : onSameKey) {
            int needed = modifiersOf(candidate);
            if ((needed & held) == needed) {
                most = Math.max(most, Integer.bitCount(needed));
            }
        }

        List<KeyMapping> result = new ArrayList<>();
        for (KeyMapping candidate : onSameKey) {
            int needed = modifiersOf(candidate);
            if ((needed & held) == needed && Integer.bitCount(needed) == most) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static boolean anyCombination(List<KeyMapping> bindings) {
        if (MODIFIERS.isEmpty() || bindings == null) {
            return false;
        }
        for (KeyMapping candidate : bindings) {
            if (modifiersOf(candidate) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether this binding is among the ones that react to its key with the given modifiers held. */
    public static boolean reactsWith(KeyMapping keyBinding, int held) {
        //? if >=1.21.9 {
        List<KeyMapping> onSameKey = KeyMapping.MAP.get(keyBinding.key);
        //?} else
        /*List<KeyMapping> onSameKey = bindingsOn(keyBinding.key);*/
        if (!anyCombination(onSameKey)) {
            return true;
        }
        return eligible(onSameKey, held & KeyCombo.ALL).contains(keyBinding);
    }

    // ------------------------------------------------------------------ persistence

    /** Reads combos.json. Entries whose key no longer matches the binding's key are dropped. */
    public static void load(File directory) {
        MODIFIERS.clear();
        file = directory == null ? null : new File(directory, FILE_NAME);
        if (file != null && file.isFile()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                        readEntry(entry.getKey(), entry.getValue());
                    }
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                KeyBindProfilesPlus.LOGGER.error("Failed to read '{}', key combinations are reset", file.getAbsolutePath(), e);
            }
        }
        live = true;
    }

    private static void readEntry(String bindingId, JsonElement value) {
        KeyMapping keyBinding = KeyMapping.get(bindingId);
        if (keyBinding == null || !value.isJsonPrimitive()) {
            return;
        }
        KeyCombo combo = KeyCombo.parse(value.getAsString());
        //? if >=26.3 {
        /*if (combo.modifiers() != 0 && combo.key().equals(io.github.autyism.keybindprofilesplus.input.SdlKeys.toStoredName(keyBinding.saveString()))) {
        *///?} else
        if (combo.modifiers() != 0 && combo.key().equals(keyBinding.saveString())) {
            MODIFIERS.put(bindingId, combo.modifiers());
        }
    }

    private static void markDirty() {
        dirty = true;
        if (batchDepth == 0) {
            save();
        }
    }

    private static void save() {
        dirty = false;
        if (file == null || !live) {
            return;
        }

        Map<String, String> entries = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : MODIFIERS.entrySet()) {
            KeyMapping keyBinding = KeyMapping.get(entry.getKey());
            if (keyBinding != null) {
                //? if >=26.3 {
                /*entries.put(entry.getKey(), KeyCombo.encode(entry.getValue(), io.github.autyism.keybindprofilesplus.input.SdlKeys.toStoredName(keyBinding.saveString())));
                *///?} else
                entries.put(entry.getKey(), KeyCombo.encode(entry.getValue(), keyBinding.saveString()));
            }
        }
        JsonObject json = new JsonObject();
        entries.forEach(json::addProperty);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to write '{}'", file.getAbsolutePath(), e);
        }
    }

    // ------------------------------------------------------------------ modifier state

    private static int readHeldModifiers() {
        //? if >=26.3 {
        /*// SDL keeps the keyboard state itself; there is no window to ask
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0;
        }
        int held = 0;
        if (InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)) {
            held |= KeyCombo.CTRL;
        }
        if (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) {
            held |= KeyCombo.SHIFT;
        }
        if (InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT)) {
            held |= KeyCombo.ALT;
        }
        return held;
        *///?} else if >=1.21.9 {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0;
        }
        Window window = client.getWindow();
        int held = 0;
        if (InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL)) {
            held |= KeyCombo.CTRL;
        }
        if (InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT)) {
            held |= KeyCombo.SHIFT;
        }
        if (InputConstants.isKeyDown(window, InputConstants.KEY_LALT) || InputConstants.isKeyDown(window, InputConstants.KEY_RALT)) {
            held |= KeyCombo.ALT;
        }
        return held;
        //?} else {
        /*// Before 1.21.9 the keyboard state was asked by the window's handle
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0;
        }
        long window = client.getWindow().getWindow();
        int held = 0;
        if (InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL)) {
            held |= KeyCombo.CTRL;
        }
        if (InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT)) {
            held |= KeyCombo.SHIFT;
        }
        if (InputConstants.isKeyDown(window, InputConstants.KEY_LALT) || InputConstants.isKeyDown(window, InputConstants.KEY_RALT)) {
            held |= KeyCombo.ALT;
        }
        return held;
        *///?}
    }

    //? if <1.21.9 {
    /*// Before 1.21.9 the game kept one binding per key and passed a key press to that one only. A key
    // with combinations on it has several bindings that must react, so they are looked up here.

    /^* The bindings on one key, or null when there are none. ^/
    public static List<KeyMapping> bindingsOn(InputConstants.Key key) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return null;
        }
        List<KeyMapping> onKey = new ArrayList<>();
        for (KeyMapping binding : client.options.keyMappings) {
            if (binding.key.equals(key)) {
                onKey.add(binding);
            }
        }
        return onKey.isEmpty() ? null : onKey;
    }

    /^* All bindings, grouped by their key. ^/
    public static Collection<List<KeyMapping>> bindingsByKey() {
        Map<InputConstants.Key, List<KeyMapping>> byKey = new HashMap<>();
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            for (KeyMapping binding : client.options.keyMappings) {
                byKey.computeIfAbsent(binding.key, key -> new ArrayList<>()).add(binding);
            }
        }
        return byKey.values();
    }
    *///?}

    /** For the self-test only: pretend these modifiers are held (null restores the real keyboard). */
    public static void setHeldModifiersForTesting(IntSupplier supplier) {
        heldModifiers = supplier == null ? KeyCombos::readHeldModifiers : supplier;
    }
}
