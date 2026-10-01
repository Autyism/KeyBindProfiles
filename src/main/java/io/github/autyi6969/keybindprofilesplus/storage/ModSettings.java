package io.github.autyi6969.keybindprofilesplus.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    }
}
