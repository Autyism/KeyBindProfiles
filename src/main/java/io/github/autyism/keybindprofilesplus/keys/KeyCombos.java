package io.github.autyism.keybindprofilesplus.keys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;

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

    public static int modifiersOf(KeyBinding keyBinding) {
        return MODIFIERS.getOrDefault(keyBinding.getId(), 0);
    }

    public static boolean hasAny() {
        return !MODIFIERS.isEmpty();
    }

    /** The binding's trigger in text form: "ctrl+key.keyboard.x", or just the key when it has no modifiers. */
    public static String valueOf(KeyBinding keyBinding) {
        return KeyCombo.encode(modifiersOf(keyBinding), keyBinding.getBoundKeyTranslationKey());
    }

    /** Whether two bindings react to exactly the same key press. */
    public static boolean sameTrigger(KeyBinding first, KeyBinding second) {
        return first.equals(second) && modifiersOf(first) == modifiersOf(second);
    }

    /** Modifier keys held right now, as a {@link KeyCombo} bit mask. */
    public static int heldModifiers() {
        return heldModifiers.getAsInt();
    }

    // ------------------------------------------------------------------ changes

    /** Binds a key together with modifiers (0 for none) and remembers it. */
    public static void bind(KeyBinding keyBinding, InputUtil.Key key, int modifiers) {
        binding = true;
        try {
            keyBinding.setBoundKey(key);
        } finally {
            binding = false;
        }

        int mask = modifiers & KeyCombo.ALL;
        // A modifier cannot require itself: "Ctrl + Left Control" is just Left Control.
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            mask &= ~KeyCombo.modifierOfKeyCode(key.getCode());
        }
        Integer previous = mask == 0 ? MODIFIERS.remove(keyBinding.getId()) : MODIFIERS.put(keyBinding.getId(), mask);
        if ((previous == null ? 0 : previous) != mask) {
            markDirty();
        }
    }

    /** Applies a text value ("ctrl+key.keyboard.x"). Returns false when the key name is unknown. */
    public static boolean applyValue(KeyBinding keyBinding, String encoded) {
        KeyCombo combo = KeyCombo.parse(encoded);
        InputUtil.Key key = combo.inputKey();
        if (key == null) {
            return false;
        }
        bind(keyBinding, key, combo.modifiers());
        return true;
    }

    /** Called whenever the game changes a binding's key: its old modifiers no longer apply. */
    public static void onBoundKeyChanged(KeyBinding keyBinding) {
        if (live && !binding && MODIFIERS.remove(keyBinding.getId()) != null) {
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
    public static List<KeyBinding> eligible(List<KeyBinding> onSameKey, int held) {
        int most = -1;
        for (KeyBinding candidate : onSameKey) {
            int needed = modifiersOf(candidate);
            if ((needed & held) == needed) {
                most = Math.max(most, Integer.bitCount(needed));
            }
        }

        List<KeyBinding> result = new ArrayList<>();
        for (KeyBinding candidate : onSameKey) {
            int needed = modifiersOf(candidate);
            if ((needed & held) == needed && Integer.bitCount(needed) == most) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static boolean anyCombination(List<KeyBinding> bindings) {
        if (MODIFIERS.isEmpty() || bindings == null) {
            return false;
        }
        for (KeyBinding candidate : bindings) {
            if (modifiersOf(candidate) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether this binding is among the ones that react to its key with the given modifiers held. */
    public static boolean reactsWith(KeyBinding keyBinding, int held) {
        List<KeyBinding> onSameKey = KeyBinding.KEY_TO_BINDINGS.get(keyBinding.boundKey);
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
        KeyBinding keyBinding = KeyBinding.byId(bindingId);
        if (keyBinding == null || !value.isJsonPrimitive()) {
            return;
        }
        KeyCombo combo = KeyCombo.parse(value.getAsString());
        if (combo.modifiers() != 0 && combo.key().equals(keyBinding.getBoundKeyTranslationKey())) {
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
            KeyBinding keyBinding = KeyBinding.byId(entry.getKey());
            if (keyBinding != null) {
                entries.put(entry.getKey(), KeyCombo.encode(entry.getValue(), keyBinding.getBoundKeyTranslationKey()));
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0;
        }
        Window window = client.getWindow();
        int held = 0;
        if (InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_RIGHT_CONTROL)) {
            held |= KeyCombo.CTRL;
        }
        if (InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_RIGHT_SHIFT)) {
            held |= KeyCombo.SHIFT;
        }
        if (InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_LEFT_ALT) || InputUtil.isKeyPressed(window, InputUtil.GLFW_KEY_RIGHT_ALT)) {
            held |= KeyCombo.ALT;
        }
        return held;
    }

    /** For the self-test only: pretend these modifiers are held (null restores the real keyboard). */
    public static void setHeldModifiersForTesting(IntSupplier supplier) {
        heldModifiers = supplier == null ? KeyCombos::readHeldModifiers : supplier;
    }
}
