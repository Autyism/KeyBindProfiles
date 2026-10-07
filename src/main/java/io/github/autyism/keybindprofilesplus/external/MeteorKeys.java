package io.github.autyism.keybindprofilesplus.external;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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

    /**
     * @param includeMain whether to read the configuration in use too ({@code meteor-client/modules.nbt});
     *                    false when those binds come from the running Meteor instead
     */
    static List<ExternalBinding> read(Path gameDirectory, boolean includeMain) {
        List<ExternalBinding> bindings = new ArrayList<>();
        Path root = gameDirectory.resolve(DIRECTORY);
        if (includeMain) {
            readFile(root.resolve(MODULES_FILE), DIRECTORY + "/" + MODULES_FILE,
                    Component.translatable("keybindprofilesplus.external.meteor"), true, bindings);
        }

        Path profiles = root.resolve("profiles");
        if (Files.isDirectory(profiles)) {
            try (Stream<Path> entries = Files.list(profiles)) {
                for (Path profile : entries.filter(Files::isDirectory).sorted().toList()) {
                    String name = profile.getFileName().toString();
                    readFile(profile.resolve(MODULES_FILE), DIRECTORY + "/profiles/" + name + "/" + MODULES_FILE,
                            Component.translatable("keybindprofilesplus.external.meteor_profile", name), false, bindings);
                }
            } catch (IOException e) {
                KeyBindProfilesPlus.LOGGER.warn("Could not list Meteor profiles in '{}'", profiles, e);
            }
        }
        return bindings;
    }

    private static void readFile(Path file, String relativePath, Component group, boolean active, List<ExternalBinding> out) {
        if (!Files.isRegularFile(file)) {
            return;
        }

        CompoundTag root;
        try {
            root = NbtIo.read(file);
        } catch (IOException | RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.warn("Could not read Meteor key binds from '{}': {}", file, e.toString());
            return;
        }
        if (root == null) {
            return;
        }

        for (CompoundTag module : root.getListOrEmpty("modules").compoundStream().toList()) {
            String moduleName = title(module.getStringOr("name", ""));
            if (moduleName.isEmpty()) {
                continue;
            }
            module.getCompound("keybind").ifPresent(keybind -> add(moduleName, keybind, ExternalBinding.When.IN_GAME, group, active, relativePath, out));
            scanSettings(module.get("settings"), moduleName, group, active, relativePath, out);
        }
    }

    /** Key bind settings inside a module ("value" holding the same {isKey, value, modifiers} shape). */
    private static void scanSettings(Tag element, String moduleName, Component group, boolean active, String file, List<ExternalBinding> out) {
        if (element instanceof CompoundTag compound) {
            if (compound.get("value") instanceof CompoundTag value && isKeybind(value)) {
                String settingName = title(compound.getStringOr("name", ""));
                // A key inside a module's settings only does something within that module's own feature.
                add(settingName.isEmpty() ? moduleName : moduleName + " / " + settingName, value, ExternalBinding.When.SITUATIONAL, group, active, file, out);
                return;
            }
            for (String key : compound.keySet()) {
                scanSettings(compound.get(key), moduleName, group, active, file, out);
            }
        } else if (element instanceof ListTag list) {
            for (Tag child : list) {
                scanSettings(child, moduleName, group, active, file, out);
            }
        }
    }

    private static boolean isKeybind(CompoundTag tag) {
        return (tag.contains("isKey") && tag.contains("value")) || (tag.contains("key") && tag.contains("modifiers"));
    }

    private static void add(String name, CompoundTag keybind, ExternalBinding.When when, Component group, boolean active, String file, List<ExternalBinding> out) {
        InputConstants.Key key;
        int modifiers = 0;
        boolean needsSuper = false;
        if (keybind.contains("key")) {
            try {
                key = InputConstants.getKey(keybind.getStringOr("key", ""));
            } catch (IllegalArgumentException e) {
                return;
            }
            ListTag names = keybind.getListOrEmpty("modifiers");
            for (int i = 0; i < names.size(); i++) {
                switch (names.getStringOr(i, "")) {
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
            boolean isKey = keybind.getBooleanOr("isKey", true);
            int value = keybind.getIntOr("value", -1);
            // -1 is "not bound"; key code 0 does not exist either.
            if (value < 0 || (isKey && value == 0)) {
                return;
            }
            int bits = isKey ? keybind.getIntOr("modifiers", 0) : 0;
            modifiers = bits & KeyCombo.ALL;
            needsSuper = (bits & GLFW_MOD_SUPER) != 0;
            //? if >=26.3 {
            /*// This format was written before 26.3: GLFW key codes and mouse buttons
            key = isKey ? io.github.autyism.keybindprofilesplus.input.SdlKeys.keyOfGlfwCode(value)
                    : InputConstants.Type.MOUSE.getOrCreate(io.github.autyism.keybindprofilesplus.input.SdlKeys.mouseButtonOfGlfw(value));
            *///?} else
            key = (isKey ? InputConstants.Type.KEYSYM : InputConstants.Type.MOUSE).getOrCreate(value);
        }
        if (key.equals(InputConstants.UNKNOWN)) {
            return;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            // A modifier key as the key itself reports its own bit.
            modifiers &= ~KeyCombo.modifierOfKeyCode(key.getValue());
        }

        Component keyText = KeyCombo.withModifiers(modifiers, key.getDisplayName());
        if (needsSuper) {
            // The Windows / Command key is not something a game key binding can ask for: shown, but not compared.
            out.add(ExternalBinding.readOnly("meteor", group, name, 0, null, Component.literal("Super + ").append(keyText), when, active, file));
            return;
        }
        out.add(ExternalBinding.readOnly("meteor", group, name, modifiers, key, keyText, when, active, file));
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
