package io.github.autyi6969.keybindprofilesplus.profile;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombos;
import io.github.autyi6969.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyi6969.keybindprofilesplus.options.OptionCatalog;
import io.github.autyi6969.keybindprofilesplus.storage.ProfileFileStore;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProfileService {
    private final ProfileFileStore fileStore;
    private final Map<String, Map<String, String>> profiles = new HashMap<>();
    private final Map<String, List<String>> profileHotkeys = new HashMap<>();
    private final Map<String, List<String>> profileAutoSwitchServers = new HashMap<>();
    /** Other game settings a profile saves besides key bindings: options.txt name -> raw value. */
    private final Map<String, Map<String, String>> profileOptions = new HashMap<>();

    private String currentProfile;
    private Runnable autoSwitchResetCallback = () -> {
    };

    public ProfileService(ProfileFileStore fileStore) {
        this.fileStore = fileStore;
    }

    public Map<String, Map<String, String>> profiles() {
        return profiles;
    }

    public Map<String, List<String>> profileHotkeys() {
        return profileHotkeys;
    }

    public Map<String, List<String>> profileAutoSwitchServers() {
        return profileAutoSwitchServers;
    }

    public void setAutoSwitchResetCallback(Runnable autoSwitchResetCallback) {
        this.autoSwitchResetCallback = autoSwitchResetCallback == null ? () -> {
        } : autoSwitchResetCallback;
    }

    public void loadProfiles() {
        fileStore.loadProfiles(profiles, profileHotkeys, profileAutoSwitchServers, profileOptions);
    }

    public void reloadProfiles() {
        profiles.clear();
        profileHotkeys.clear();
        profileAutoSwitchServers.clear();
        profileOptions.clear();
        loadProfiles();
    }

    public void loadCurrentProfile() {
        currentProfile = fileStore.loadCurrentProfile();
        if (currentProfile != null && profiles.containsKey(currentProfile)) {
            applyProfile(currentProfile);
        }
    }

    public void saveProfile(String name, KeyBinding[] bindings) {
        fileStore.saveProfile(name, bindings, profiles);
        exportProfile(name);
    }

    public void applyProfile(String name) {
        Map<String, String> keyMap = profiles.get(name);
        MinecraftClient client = MinecraftClient.getInstance();
        if (keyMap == null || client == null || client.options == null) {
            return;
        }

        KeyCombos.batch(() -> applyKeyBindings(client.options.allKeys, keyMap));
        KeyBinding.updateKeysByCode();
        releaseAllKeys(client.options.allKeys);
        applyGameOptions(client, profileOptions.get(name));
        writeOptions(client);

        currentProfile = name;
        saveCurrentProfile(name);
    }

    public void deleteProfile(String name) {
        Objects.requireNonNull(name, "Profile name cannot be null");
        if (profiles.remove(name) == null) {
            return;
        }

        profileHotkeys.remove(name);
        profileAutoSwitchServers.remove(name);
        profileOptions.remove(name);
        fileStore.deleteProfileFile(name);

        if (Objects.equals(currentProfile, name)) {
            saveCurrentProfile(null);
        }
    }

    public boolean renameProfile(String oldName, String newName) {
        Map<String, String> keyMap = profiles.get(oldName);
        if (keyMap == null || newName == null || newName.isBlank() || profiles.containsKey(newName)) {
            return false;
        }

        List<String> hotkeys = profileHotkeys.remove(oldName);
        List<String> autoSwitchServers = profileAutoSwitchServers.remove(oldName);
        Map<String, String> options = profileOptions.remove(oldName);
        profiles.remove(oldName);
        profiles.put(newName, keyMap);
        if (options != null) {
            profileOptions.put(newName, options);
        }
        if (hotkeys != null) {
            profileHotkeys.put(newName, hotkeys);
        }
        if (autoSwitchServers != null) {
            profileAutoSwitchServers.put(newName, autoSwitchServers);
        }

        fileStore.deleteProfileFile(oldName);
        exportProfile(newName);
        if (Objects.equals(currentProfile, oldName)) {
            saveCurrentProfile(newName);
        }
        autoSwitchResetCallback.run();
        return true;
    }

    /** Creates a profile from explicit contents (new-profile dialog, share code import). False if the name is taken. */
    public boolean createProfile(String name, Map<String, String> keyBindings, Map<String, String> options) {
        if (name == null || ProfileNames.containsIgnoreCase(profiles.keySet(), name)) {
            return false;
        }
        profiles.put(name, new HashMap<>(keyBindings));
        if (options != null && !options.isEmpty()) {
            profileOptions.put(name, new LinkedHashMap<>(options));
        }
        exportProfile(name);
        return true;
    }

    public void exportProfile(String name) {
        fileStore.exportProfile(name, profiles, profileHotkeys, profileAutoSwitchServers, profileOptions);
    }

    /** The other game settings saved in a profile (options.txt name -> raw value); empty if none. */
    public Map<String, String> getProfileOptions(String name) {
        Map<String, String> options = profileOptions.get(name);
        return options == null ? Map.of() : Collections.unmodifiableMap(options);
    }

    /**
     * Replaces what a profile saves: exactly these key bindings (id -> key) and exactly these other
     * game settings. Hotkey and auto-switch servers are kept.
     */
    public void setProfileContents(String name, Map<String, String> keyBindings, Map<String, String> options) {
        if (!profiles.containsKey(name)) {
            return;
        }

        profiles.put(name, new HashMap<>(keyBindings));
        if (options == null || options.isEmpty()) {
            profileOptions.remove(name);
        } else {
            profileOptions.put(name, new LinkedHashMap<>(options));
        }
        exportProfile(name);
    }

    /** Everything that applying the profile right now would change. Empty if it already matches. */
    public List<ProfileChange> previewApply(String name) {
        List<ProfileChange> changes = new ArrayList<>();
        Map<String, String> keyMap = profiles.get(name);
        MinecraftClient client = MinecraftClient.getInstance();
        if (keyMap == null || client == null || client.options == null) {
            return changes;
        }

        KeyBinding[] bindings = client.options.allKeys.clone();
        Arrays.sort(bindings);
        for (KeyBinding binding : bindings) {
            String savedKey = keyMap.get(binding.getId());
            // An unreadable key in the file is skipped when applying, so it is no change either.
            if (savedKey == null || savedKey.equals(KeyCombos.valueOf(binding)) || KeyCombo.parse(savedKey).inputKey() == null) {
                continue;
            }
            changes.add(new ProfileChange(ProfileChange.Kind.KEY_BINDING, binding.getId(), Text.translatable(binding.getId()),
                    binding.getBoundKeyLocalizedText(), KeyCombo.describe(savedKey)));
        }

        Map<String, String> options = profileOptions.get(name);
        if (options != null && !options.isEmpty()) {
            Map<String, GameOptionsBridge.Entry> current = GameOptionsBridge.readAll(client.options);
            for (Map.Entry<String, String> saved : options.entrySet()) {
                GameOptionsBridge.Entry entry = current.get(saved.getKey());
                if (entry != null && OptionCatalog.isOffered(saved.getKey()) && !saved.getValue().equals(entry.rawValue())) {
                    changes.add(new ProfileChange(ProfileChange.Kind.OPTION, saved.getKey(), entry.name(),
                            entry.describe(entry.rawValue()), entry.describe(saved.getValue())));
                }
            }
        }
        return changes;
    }

    public void setProfileHotkey(String profileName, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            profileHotkeys.remove(profileName);
        } else {
            profileHotkeys.put(profileName, new ArrayList<>(keys));
        }
        exportProfile(profileName);
    }

    public List<String> getProfileHotkey(String profileName) {
        return profileHotkeys.get(profileName);
    }

    public void setProfileAutoSwitchServers(String profileName, List<String> servers) {
        List<String> normalizedServers = normalizeServerList(servers);
        if (normalizedServers.isEmpty()) {
            profileAutoSwitchServers.remove(profileName);
        } else {
            profileAutoSwitchServers.put(profileName, normalizedServers);
        }

        exportProfile(profileName);
        autoSwitchResetCallback.run();
    }

    public List<String> getProfileAutoSwitchServers(String profileName) {
        return profileAutoSwitchServers.get(profileName);
    }

    public void saveCurrentProfile(String profile) {
        currentProfile = profile;
        fileStore.saveCurrentProfile(profile);
    }

    public String getCurrentProfile() {
        return currentProfile;
    }

    public File profilesDirectory() {
        return fileStore.profilesDirectory();
    }

    public boolean openProfilesFolder() {
        return fileStore.openProfilesFolder();
    }

    private void applyKeyBindings(KeyBinding[] bindings, Map<String, String> keyMap) {
        for (KeyBinding binding : bindings) {
            applyKeyBinding(binding, keyMap);
        }
    }

    private void applyKeyBinding(KeyBinding binding, Map<String, String> keyMap) {
        if (binding == null) {
            return;
        }

        String savedKey = keyMap.get(binding.getId());
        if (savedKey == null) {
            return;
        }

        // Invalid key values from old or manually edited profile files are ignored.
        KeyCombos.applyValue(binding, savedKey);
    }

    private void applyGameOptions(MinecraftClient client, Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return;
        }

        Map<String, String> allowed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (OptionCatalog.isOffered(entry.getKey())) {
                allowed.put(entry.getKey(), entry.getValue());
            }
        }
        try {
            GameOptionsBridge.apply(client.options, allowed);
        } catch (RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to apply the saved game settings of a profile", e);
        }
    }

    private void releaseAllKeys(KeyBinding[] bindings) {
        for (KeyBinding binding : bindings) {
            if (binding != null) {
                binding.setPressed(false);
            }
        }
    }

    private void writeOptions(MinecraftClient client) {
        try {
            client.options.write();
        } catch (RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to write Minecraft options after applying keybind profile", e);
        }
    }

    private List<String> normalizeServerList(List<String> servers) {
        List<String> normalizedServers = new ArrayList<>();
        if (servers == null) {
            return normalizedServers;
        }

        for (String server : servers) {
            addUniqueNonBlankServer(normalizedServers, server);
        }
        return normalizedServers;
    }

    private void addUniqueNonBlankServer(List<String> servers, String server) {
        if (server == null) {
            return;
        }

        String trimmed = server.trim();
        if (!trimmed.isEmpty() && !servers.contains(trimmed)) {
            servers.add(trimmed);
        }
    }
}
