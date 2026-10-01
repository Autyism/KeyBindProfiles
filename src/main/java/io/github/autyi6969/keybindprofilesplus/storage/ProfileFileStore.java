package io.github.autyi6969.keybindprofilesplus.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Util;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProfileFileStore {
    private static final String CONFIG_DIRECTORY = "config/keybindprofilesplus";
    // Folder and key binding id used by the upstream mod (KeyBindProfiles by sawiq_).
    private static final String LEGACY_CONFIG_DIRECTORY = "config/keybindprofiles";
    private static final String LEGACY_OPEN_KEY_ID = "key.keybindprofiles.open";
    private static final String OPEN_KEY_ID = "key.keybindprofilesplus.open";
    private static final String CURRENT_PROFILE_FILE = "current_profile.txt";
    private static final String PROFILE_EXTENSION = ".kbp";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File profilesDir;
    private File currentProfileFile;

    public void loadProfiles(
            Map<String, Map<String, String>> profiles,
            Map<String, List<String>> profileHotkeys,
            Map<String, List<String>> profileAutoSwitchServers,
            Map<String, Map<String, String>> profileOptions
    ) {
        File dir = getProfilesDir();
        if (dir == null || !dir.exists()) {
            return;
        }

        File[] files = dir.listFiles((ignored, name) -> name.endsWith(PROFILE_EXTENSION));
        if (files == null) {
            return;
        }

        for (File file : files) {
            readProfileFile(file, profiles, profileHotkeys, profileAutoSwitchServers, profileOptions);
        }
    }

    public void saveProfile(String name, KeyBinding[] bindings, Map<String, Map<String, String>> profiles) {
        Objects.requireNonNull(name, "Profile name cannot be null");
        Objects.requireNonNull(bindings, "Bindings cannot be null");

        Map<String, String> keyMap = new HashMap<>();
        for (KeyBinding binding : bindings) {
            if (binding != null) {
                keyMap.put(binding.getId(), binding.getBoundKeyTranslationKey());
            }
        }

        profiles.put(name, keyMap);
    }

    public void exportProfile(
            String name,
            Map<String, Map<String, String>> profiles,
            Map<String, List<String>> profileHotkeys,
            Map<String, List<String>> profileAutoSwitchServers,
            Map<String, Map<String, String>> profileOptions
    ) {
        Map<String, String> keyMap = profiles.get(name);
        File dir = getProfilesDir();
        if (keyMap == null || dir == null) {
            return;
        }

        Map<String, Object> exportData = new HashMap<>();
        exportData.put("name", name);
        exportData.put("keybindings", keyMap);

        if (profileHotkeys.containsKey(name)) {
            exportData.put("hotkeys", profileHotkeys.get(name));
        }

        if (profileAutoSwitchServers.containsKey(name)) {
            exportData.put("autoSwitchServers", profileAutoSwitchServers.get(name));
        }

        Map<String, String> options = profileOptions.get(name);
        if (options != null && !options.isEmpty()) {
            exportData.put("options", options);
        }

        File exportFile = new File(dir, name + PROFILE_EXTENSION);
        try (FileWriter writer = new FileWriter(exportFile)) {
            gson.toJson(exportData, writer);
        } catch (IOException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to export keybind profile '{}'", name, e);
        }
    }

    public void deleteProfileFile(String name) {
        Objects.requireNonNull(name, "Profile name cannot be null");

        File dir = getProfilesDir();
        if (dir == null) {
            return;
        }

        File profileFile = new File(dir, name + PROFILE_EXTENSION);
        if (profileFile.exists() && !profileFile.delete()) {
            KeyBindProfilesPlus.LOGGER.error("Failed to delete keybind profile file '{}'", profileFile.getAbsolutePath());
        }
    }

    public void saveCurrentProfile(String profile) {
        File file = getCurrentProfileFile();
        if (file == null) {
            return;
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(profile != null ? profile : "");
        } catch (IOException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to save current keybind profile '{}'", profile, e);
        }
    }

    public String loadCurrentProfile() {
        File file = getCurrentProfileFile();
        if (file == null || !file.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line != null && !line.trim().isEmpty() ? line.trim() : null;
        } catch (IOException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to load current keybind profile", e);
            return null;
        }
    }

    public boolean openProfilesFolder() {
        File dir = getProfilesDir();
        if (dir == null) {
            return false;
        }

        try {
            Util.getOperatingSystem().open(dir);
            return true;
        } catch (RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to open keybind profiles folder '{}'", dir.getAbsolutePath(), e);
            return false;
        }
    }

    private void readProfileFile(
            File file,
            Map<String, Map<String, String>> profiles,
            Map<String, List<String>> profileHotkeys,
            Map<String, List<String>> profileAutoSwitchServers,
            Map<String, Map<String, String>> profileOptions
    ) {
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            if (!isValidProfileData(data)) {
                return;
            }

            String name = (String) data.get("name");
            @SuppressWarnings("unchecked")
            Map<String, String> bindings = (Map<String, String>) data.get("keybindings");
            String legacyOpenKey = bindings.get(LEGACY_OPEN_KEY_ID);
            if (legacyOpenKey != null && !bindings.containsKey(OPEN_KEY_ID)) {
                bindings.put(OPEN_KEY_ID, legacyOpenKey);
            }
            profiles.put(name, bindings);

            List<String> hotkeys = readHotkeys(data.get("hotkeys"));
            if (!hotkeys.isEmpty()) {
                profileHotkeys.put(name, hotkeys);
            }

            List<String> autoSwitchServers = readStringList(data.get("autoSwitchServers"));
            if (!autoSwitchServers.isEmpty()) {
                profileAutoSwitchServers.put(name, autoSwitchServers);
            }

            Map<String, String> options = readStringMap(data.get("options"));
            if (!options.isEmpty()) {
                profileOptions.put(name, options);
            }
        } catch (IOException | JsonSyntaxException | ClassCastException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to read keybind profile file '{}'", file.getAbsolutePath(), e);
        }
    }

    private boolean isValidProfileData(Map<String, Object> data) {
        return data != null
                && data.get("name") instanceof String
                && data.get("keybindings") instanceof Map<?, ?>;
    }

    private List<String> readHotkeys(Object value) {
        List<String> hotkeys = new ArrayList<>();
        if (!(value instanceof List<?> rawHotkeys)) {
            return hotkeys;
        }

        for (Object item : rawHotkeys) {
            if (item instanceof String key) {
                hotkeys.add(key);
            } else if (item instanceof Number code) {
                hotkeys.add(InputUtil.fromKeyCode(new KeyInput(code.intValue(), -1, 0)).getTranslationKey());
            }
        }
        return hotkeys;
    }

    /** Other game settings saved in the profile: options.txt name -> raw value. */
    private Map<String, String> readStringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String text) {
                    result.put(key, text);
                }
            }
        }
        return result;
    }

    private List<String> readStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                addNonBlankString(result, item);
            }
        } else {
            addNonBlankString(result, value);
        }
        return result;
    }

    private void addNonBlankString(List<String> values, Object value) {
        if (!(value instanceof String text)) {
            return;
        }

        String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            values.add(trimmed);
        }
    }

    /**
     * One-time migration from the upstream mod: if its folder exists and ours does not yet,
     * copy its files over. The old folder is left untouched. Returns the number of files copied.
     */
    public static int migrateLegacyDirectory(File legacyDir, File targetDir) {
        if (!legacyDir.isDirectory() || targetDir.exists()) {
            return 0;
        }

        File[] files = legacyDir.listFiles(File::isFile);
        if (files == null || !targetDir.mkdirs()) {
            return 0;
        }

        int copied = 0;
        for (File file : files) {
            try {
                Files.copy(file.toPath(), new File(targetDir, file.getName()).toPath());
                copied++;
            } catch (IOException e) {
                KeyBindProfilesPlus.LOGGER.error("Failed to migrate '{}' from the old KeyBindProfiles folder", file.getAbsolutePath(), e);
            }
        }
        KeyBindProfilesPlus.LOGGER.info("Migrated {} file(s) from '{}' to '{}'", copied, legacyDir.getAbsolutePath(), targetDir.getAbsolutePath());
        return copied;
    }

    public File profilesDirectory() {
        return getProfilesDir();
    }

    private File getProfilesDir() {
        if (profilesDir != null) {
            return profilesDir;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            return null;
        }

        profilesDir = new File(client.runDirectory, CONFIG_DIRECTORY);
        migrateLegacyDirectory(new File(client.runDirectory, LEGACY_CONFIG_DIRECTORY), profilesDir);
        if (!profilesDir.exists() && !profilesDir.mkdirs()) {
            KeyBindProfilesPlus.LOGGER.error("Failed to create keybind profile directory '{}'", profilesDir.getAbsolutePath());
        }
        return profilesDir;
    }

    private File getCurrentProfileFile() {
        if (currentProfileFile != null) {
            return currentProfileFile;
        }

        File dir = getProfilesDir();
        if (dir == null) {
            return null;
        }

        currentProfileFile = new File(dir, CURRENT_PROFILE_FILE);
        return currentProfileFile;
    }
}
