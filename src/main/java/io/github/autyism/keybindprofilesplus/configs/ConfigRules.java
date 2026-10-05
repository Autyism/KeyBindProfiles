package io.github.autyism.keybindprofilesplus.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The fixed rules every mod config file has to pass, no matter which mod it belongs to: where it may
 * live, what it may be called, what kind of file it may be. Used when scanning, when exporting, when
 * importing and by the early importer that writes files before the mods start - so this class must
 * not touch Minecraft or any library.
 *
 * <p>Paths are always relative to the game folder, separated by '/', e.g. {@code config/sodium-options.json}
 * or {@code meteor-client/modules.nbt}. A path starting with {@code config/} means the config folder
 * Fabric reports, wherever it is.</p>
 */
public final class ConfigRules {
    /** Bigger files are data (maps, caches, recordings), not settings. */
    public static final long MAX_FILE_BYTES = 1L << 20;
    /** This mod's own folder: its profiles have their own sharing; it is never exported or overwritten. */
    public static final String OWN_DIR = "config/keybindprofilesplus";
    public static final String CONFIG_PREFIX = "config/";

    /** File types settings are stored in. Anything else (images, sounds, zips, region files, logs...) is data. */
    static final Set<String> CONFIG_EXTENSIONS = Set.of(
            "json", "json5", "jsonc", "hjson", "toml", "properties", "cfg", "conf", "config", "ini", "txt",
            "yml", "yaml", "xml", "nbt", "snbt", "options", "settings", "prefs");

    /** Top-level folders of the game that hold worlds, packs, downloads or the game itself - never touched. */
    static final Set<String> PROTECTED_TOP_LEVEL = Set.of(
            "mods", "saves", "versions", "libraries", "assets", "natives", "resourcepacks", "texturepacks",
            "shaderpacks", "screenshots", "logs", "crash-reports", "schematics", "structures", "downloads",
            "server-resource-packs", "backups", "backup", "debug", "profilekeys", "replay_recordings",
            "replay_videos", ".fabric", ".mixin.out", ".cache", "cache", "datapacks", "global_packs",
            "journeymap", "temp", "tmp");

    /** Files of the game itself in the game folder (the game's own settings are saved by profiles, not here). */
    static final Set<String> VANILLA_ROOT_FILES = Set.of(
            "options.txt", "servers.dat", "servers.dat_old", "usercache.json", "usernamecache.json",
            "command_history.txt", "realms_persistence.json", "hotbar.nbt", "launcher_profiles.json",
            "launcher_accounts.json", "launcher_settings.json", "launcher_msa_credentials.bin",
            "debug-profile.json", "level.dat");

    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private ConfigRules() {
    }

    /**
     * The canonical form of a relative path ('/' separated, no empty, "." or ".." parts), or null when
     * it is not a safe relative path on every system (absolute, a drive letter, characters Windows does
     * not allow, reserved names, parts ending in a dot or a space).
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 512) {
            return null;
        }
        String path = raw.replace('\\', '/');
        if (path.startsWith("/") || (path.length() > 1 && path.charAt(1) == ':')) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            if (part.equals(".") || part.equals("..") || part.length() > 255 || part.endsWith(" ") || part.endsWith(".")) {
                return null;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < 0x20 || c == 0x7f || "<>:\"|?*".indexOf(c) >= 0) {
                    return null;
                }
            }
            String stem = part.contains(".") ? part.substring(0, part.indexOf('.')) : part;
            if (WINDOWS_RESERVED.contains(stem.toLowerCase(Locale.ROOT))) {
                return null;
            }
            parts.add(part);
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    /**
     * Null when the content fits the file type, else what is wrong with it: settings in text form have
     * no zero bytes, and .nbt files must be readable NBT.
     */
    public static String contentProblem(String fileName, byte[] data) {
        if (extension(fileName).equals("nbt")) {
            return NbtReader.walk(data, (name, text) -> {
            }) ? null : "not NBT";
        }
        int check = Math.min(data.length, 8192);
        for (int i = 0; i < check; i++) {
            if (data[i] == 0) {
                return "binary";
            }
        }
        return null;
    }

    /** Lower-case extension without the dot ("" when there is none). */
    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The name without a settings-file extension ("sodium-options.json" -> "sodium-options"). */
    public static String stem(String fileName) {
        return isConfigExtension(fileName) ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
    }

    public static boolean isConfigExtension(String fileName) {
        return CONFIG_EXTENSIONS.contains(extension(fileName));
    }

    /** Backups, old copies, locks and half-written files: never settings in use. */
    public static boolean isBackupOrTemp(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith("~") || name.endsWith(".bak") || name.endsWith(".old") || name.endsWith(".tmp")
                || name.endsWith(".temp") || name.endsWith(".lock") || name.endsWith(".orig") || name.endsWith(".backup")
                || name.endsWith(".disabled") || name.endsWith(".swp") || name.endsWith("_old") || name.contains(".bak.")
                || name.contains(".backup.") || name.matches(".*\\.(json|toml|properties|txt|nbt|cfg)\\.\\d+");
    }

    public static boolean isProtectedTopLevel(String firstSegment) {
        return PROTECTED_TOP_LEVEL.contains(firstSegment.toLowerCase(Locale.ROOT));
    }

    public static boolean isVanillaRootFile(String fileName) {
        return VANILLA_ROOT_FILES.contains(fileName.toLowerCase(Locale.ROOT));
    }

    /** True for this mod's own folder and everything in it. */
    public static boolean isOwnFile(String normalizedPath) {
        String lower = normalizedPath.toLowerCase(Locale.ROOT);
        return lower.equals(OWN_DIR) || lower.startsWith(OWN_DIR + "/");
    }

    /**
     * Whether a file at this path may be written by an import (or deleted by undoing one). The path
     * must already be normalized. This is the last line of defence: the import screen applies the
     * stricter, mod-aware rules before anything gets this far.
     */
    public static boolean isWritableTarget(String normalizedPath) {
        if (normalizedPath == null || isOwnFile(normalizedPath)) {
            return false;
        }
        String[] parts = normalizedPath.split("/");
        String fileName = parts[parts.length - 1];
        if (parts.length == 1 && isVanillaRootFile(fileName)) {
            return false;
        }
        if (isProtectedTopLevel(parts[0])) {
            return false;
        }
        return isConfigExtension(fileName) && !isBackupOrTemp(fileName) && SecretDetector.secretName(normalizedPath) == null;
    }
}
