package io.github.autyi6969.keybindprofilesplus.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Reads what the upstream mod (KeyBindProfiles by sawiq_) left in options.txt, so that people
 * switching to this mod keep the key they had chosen for opening the profile screen.
 */
public final class LegacyOptions {
    private static final String LEGACY_OPEN_KEY_LINE = "key_key.keybindprofiles.open:";
    private static final String OPEN_KEY_LINE = "key_key.keybindprofilesplus.open:";

    private LegacyOptions() {
    }

    /**
     * Returns the key the old mod's "open" binding was set to, or null when there is nothing to
     * carry over (no old entry, or this mod's own entry already exists).
     */
    public static String readLegacyOpenKey(File gameDirectory) {
        File optionsFile = new File(gameDirectory, "options.txt");
        if (!optionsFile.isFile()) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(optionsFile.toPath(), StandardCharsets.UTF_8);
            return findLegacyOpenKey(lines);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static String findLegacyOpenKey(List<String> optionLines) {
        String legacyKey = null;
        for (String line : optionLines) {
            if (line.startsWith(OPEN_KEY_LINE)) {
                return null;
            }
            if (line.startsWith(LEGACY_OPEN_KEY_LINE)) {
                String value = line.substring(LEGACY_OPEN_KEY_LINE.length()).trim();
                legacyKey = value.isEmpty() ? null : value;
            }
        }
        return legacyKey;
    }
}
