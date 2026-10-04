package io.github.autyism.keybindprofilesplus.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** The mod's own few preferences, kept in settings.json next to the profiles. */
public final class ModSettings {
    private static final String FILE_NAME = "settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Supplier<File> directory;
    private Data data;

    public ModSettings(Supplier<File> directory) {
        this.directory = directory;
    }

    /** Whether the Apply button first shows what will change and asks for confirmation. */
    public boolean confirmApply() {
        return data().confirmApply;
    }

    public void setConfirmApply(boolean confirmApply) {
        data().confirmApply = confirmApply;
        save();
    }

    /** Whether joining a world or server applies the profile whose rule matches it. */
    public boolean autoSwitch() {
        return data().autoSwitch;
    }

    public void setAutoSwitch(boolean autoSwitch) {
        data().autoSwitch = autoSwitch;
        save();
    }

    /** The profile to fall back to when leaving a world or server; null when none is chosen. */
    public String defaultProfile() {
        String name = data().defaultProfile;
        return name == null || name.isBlank() ? null : name;
    }

    public void setDefaultProfile(String defaultProfile) {
        data().defaultProfile = defaultProfile == null || defaultProfile.isBlank() ? null : defaultProfile;
        save();
    }

    /** Whether leaving a world or server switches back to {@link #defaultProfile()}. */
    public boolean returnToDefault() {
        return data().returnToDefault;
    }

    public void setReturnToDefault(boolean returnToDefault) {
        data().returnToDefault = returnToDefault;
        save();
    }

    /** Whether the mod's own Key Binds screen is shown in place of the vanilla one. */
    public boolean replaceKeyBinds() {
        return data().replaceKeyBinds;
    }

    public void setReplaceKeyBinds(boolean replaceKeyBinds) {
        data().replaceKeyBinds = replaceKeyBinds;
        save();
    }

    /**
     * The player's own say on when a mod's key binding (or, under an "external:" name, a hotkey
     * another mod manages itself) is in use: "general" (during play), "screen" (only while a screen
     * is open) or "situational" (only in a special situation, never a conflict). Null means the
     * mod decides by its built-in rules.
     */
    public String scopeOverride(String bindingId) {
        Map<String, String> overrides = data().scopeOverrides;
        return overrides == null ? null : overrides.get(bindingId);
    }

    public void setScopeOverride(String bindingId, String scope) {
        if (data().scopeOverrides == null) {
            data().scopeOverrides = new TreeMap<>();
        }
        if (scope == null) {
            data().scopeOverrides.remove(bindingId);
        } else {
            data().scopeOverrides.put(bindingId, scope);
        }
        save();
    }

    /** Forgets what was read so the next access reads the file again. */
    public void reload() {
        data = null;
    }

    private Data data() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    private Data load() {
        File file = file();
        if (file != null && file.isFile()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException | JsonParseException e) {
                KeyBindProfilesPlus.LOGGER.error("Failed to read '{}', using defaults", file.getAbsolutePath(), e);
            }
        }
        return new Data();
    }

    private void save() {
        File file = file();
        if (file == null) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(data(), writer);
        } catch (IOException e) {
            KeyBindProfilesPlus.LOGGER.error("Failed to write '{}'", file.getAbsolutePath(), e);
        }
    }

    private File file() {
        File dir = directory.get();
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    private static final class Data {
        boolean confirmApply = true;
        boolean autoSwitch = true;
        String defaultProfile;
        boolean returnToDefault;
        boolean replaceKeyBinds = true;
        Map<String, String> scopeOverrides;
    }
}
