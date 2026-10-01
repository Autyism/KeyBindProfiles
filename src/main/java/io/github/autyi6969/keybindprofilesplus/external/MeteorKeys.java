package io.github.autyi6969.keybindprofilesplus.external;

import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reads the key binds Meteor Client keeps in {@code meteor-client/modules.nbt} (and in each
 * {@code meteor-client/profiles/<name>/modules.nbt}). Read-only: the files are opened for reading
 * and never written.
 *
 * <p>Layout of the file (uncompressed NBT): a list "modules"; every module has a "name", a
 * "keybind" and a "settings" tree that may contain further key bind settings of the same shape.
 * A key bind is written in one of two ways, depending on the Meteor version:
 * <ul>
 *   <li>older: {@code {isKey, value, modifiers}} - a GLFW key or mouse button code and the GLFW
 *       modifier bits;</li>
 *   <li>newer: {@code {key, modifiers}} - the game's own key name ("key.keyboard.z") and a list
 *       of modifier names ("CONTROL", "SHIFT", "ALT", "SUPER").</li>
 * </ul>
 * A module's own key toggles it during play (Meteor ignores it while a screen is open or F3 is
 * held); keys in its settings only matter inside that module.
 */
final class MeteorKeys {
    static final String MOD_ID = "meteor-client";
    private static final String DIRECTORY = "meteor-client";
    private static final String MODULES_FILE = "modules.nbt";
    private static final int GLFW_MOD_SUPER = 8;

    private MeteorKeys() {
    }

    static List<ExternalBinding> read(Path gameDirectory) {
        List<ExternalBinding> bindings = new ArrayList<>();
        Path root = gameDirectory.resolve(DIRECTORY);
        readFile(root.resolve(MODULES_FILE), DIRECTORY + "/" + MODULES_FILE,
                Text.translatable("keybindprofilesplus.external.meteor"), true, bindings);

        Path profiles = root.resolve("profiles");
        if (Files.isDirectory(profiles)) {
            try (Stream<Path> entries = Files.list(profiles)) {
                for (Path profile : entries.filter(Files::isDirectory).sorted().toList()) {
                    String name = profile.getFileName().toString();
                    readFile(profile.resolve(MODULES_FILE), DIRECTORY + "/profiles/" + name + "/" + MODULES_FILE,
                            Text.translatable("keybindprofilesplus.external.meteor_profile", name), false, bindings);
                }
            } catch (IOException e) {
                KeyBindProfilesPlus.LOGGER.warn("Could not list Meteor profiles in '{}'", profiles, e);
            }
        }
        return bindings;
    }

    private static void readFile(Path file, String relativePath, Text group, boolean active, List<ExternalBinding> out) {
        if (!Files.isRegularFile(file)) {
            return;
        }

        NbtCompound root;
        try {
            root = NbtIo.read(file);
        } catch (IOException | RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.warn("Could not read Meteor key binds from '{}': {}", file, e.toString());
            return;
        }
        if (root == null) {
            return;
        }

        for (NbtCompound module : root.getListOrEmpty("modules").streamCompounds().toList()) {
            String moduleName = title(module.getString("name", ""));
            if (moduleName.isEmpty()) {
                continue;
            }
            module.getCompound("keybind").ifPresent(keybind -> add(moduleName, keybind, ExternalBinding.When.IN_GAME, group, active, relativePath, out));
            scanSettings(module.get("settings"), moduleName, group, active, relativePath, out);
        }
    }

    /** Key bind settings inside a module ("value" holding the same {isKey, value, modifiers} shape). */
    private static void scanSettings(NbtElement element, String moduleName, Text group, boolean active, String file, List<ExternalBinding> out) {
        if (element instanceof NbtCompound compound) {
            if (compound.get("value") instanceof NbtCompound value && isKeybind(value)) {
                String settingName = title(compound.getString("name", ""));
                // A key inside a module's settings only does something within that module's own feature.
                add(settingName.isEmpty() ? moduleName : moduleName + " / " + settingName, value, ExternalBinding.When.SITUATIONAL, group, active, file, out);
                return;
            }
            for (String key : compound.getKeys()) {
                scanSettings(compound.get(key), moduleName, group, active, file, out);
            }
        } else if (element instanceof NbtList list) {
            for (NbtElement child : list) {
                scanSettings(child, moduleName, group, active, file, out);
            }
        }
    }

    private static boolean isKeybind(NbtCompound tag) {
        return (tag.contains("isKey") && tag.contains("value")) || (tag.contains("key") && tag.contains("modifiers"));
    }

    private static void add(String name, NbtCompound keybind, ExternalBinding.When when, Text group, boolean active, String file, List<ExternalBinding> out) {
        InputUtil.Key key;
        int modifiers = 0;
        boolean needsSuper = false;
        if (keybind.contains("key")) {
            try {
                key = InputUtil.fromTranslationKey(keybind.getString("key", ""));
            } catch (IllegalArgumentException e) {
                return;
            }
            NbtList names = keybind.getListOrEmpty("modifiers");
            for (int i = 0; i < names.size(); i++) {
                switch (names.getString(i, "")) {
                    case "SHIFT" -> modifiers |= KeyCombo.SHIFT;
                    case "CONTROL" -> modifiers |= KeyCombo.CTRL;
                    case "ALT" -> modifiers |= KeyCombo.ALT;
                    case "SUPER" -> needsSuper = true;
                    default -> {
                        // Caps Lock / Num Lock states are never part of a key press as the game reports it.
                    }
                }
            }
        } else {
            boolean isKey = keybind.getBoolean("isKey", true);
            int value = keybind.getInt("value", -1);
            // -1 is "not bound"; key code 0 does not exist either.
            if (value < 0 || (isKey && value == 0)) {
                return;
            }
            int bits = isKey ? keybind.getInt("modifiers", 0) : 0;
            modifiers = bits & KeyCombo.ALL;
            needsSuper = (bits & GLFW_MOD_SUPER) != 0;
            key = (isKey ? InputUtil.Type.KEYSYM : InputUtil.Type.MOUSE).createFromCode(value);
        }
        if (key.equals(InputUtil.UNKNOWN_KEY)) {
            return;
        }
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            // A modifier key as the key itself reports its own bit.
            modifiers &= ~KeyCombo.modifierOfKeyCode(key.getCode());
        }

        Text keyText = KeyCombo.withModifiers(modifiers, key.getLocalizedText());
        if (needsSuper) {
            // The Windows / Command key is not something a game key binding can ask for: shown, but not compared.
            out.add(new ExternalBinding("meteor", group, name, 0, null, Text.literal("Super + ").append(keyText), when, active, file));
            return;
        }
        out.add(new ExternalBinding("meteor", group, name, modifiers, key, keyText, when, active, file));
    }

    /** "auto-totem" -> "Auto Totem". */
    static String title(String kebabCase) {
        StringBuilder title = new StringBuilder();
        for (String word : kebabCase.trim().split("[-_\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return title.toString();
    }
}
