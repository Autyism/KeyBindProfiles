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
 * <p>Layout of the file (uncompressed NBT): a list "modules"; every module has a "name" and a
 * "keybind" {@code {isKey, value, modifiers}} where value is a GLFW key or mouse button code, and
 * a "settings" tree that may contain further key bind settings of the same shape.
 */
final class MeteorKeys {
    static final String MOD_ID = "meteor-client";
    private static final String DIRECTORY = "meteor-client";
    private static final String MODULES_FILE = "modules.nbt";

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
            module.getCompound("keybind").ifPresent(keybind -> add(moduleName, keybind, group, active, relativePath, out));
            scanSettings(module.get("settings"), moduleName, group, active, relativePath, out);
        }
    }

    /** Key bind settings inside a module ("value" holding the same {isKey, value, modifiers} shape). */
    private static void scanSettings(NbtElement element, String moduleName, Text group, boolean active, String file, List<ExternalBinding> out) {
        if (element instanceof NbtCompound compound) {
            if (compound.get("value") instanceof NbtCompound value && value.contains("isKey") && value.contains("value")) {
                String settingName = title(compound.getString("name", ""));
                add(settingName.isEmpty() ? moduleName : moduleName + " / " + settingName, value, group, active, file, out);
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

    private static void add(String name, NbtCompound keybind, Text group, boolean active, String file, List<ExternalBinding> out) {
        boolean isKey = keybind.getBoolean("isKey", true);
        int value = keybind.getInt("value", -1);
        // -1 is "not bound"; key code 0 does not exist either.
        if (value < 0 || (isKey && value == 0)) {
            return;
        }

        int modifiers = keybind.getInt("modifiers", 0) & KeyCombo.ALL;
        InputUtil.Key key = (isKey ? InputUtil.Type.KEYSYM : InputUtil.Type.MOUSE).createFromCode(value);
        if (isKey) {
            modifiers &= ~KeyCombo.modifierOfKeyCode(value);
        }
        out.add(new ExternalBinding("meteor", group, name, modifiers, key, KeyCombo.withModifiers(modifiers, key.getLocalizedText()),
                false, active, file));
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
