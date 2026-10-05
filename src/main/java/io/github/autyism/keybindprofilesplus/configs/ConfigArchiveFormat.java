package io.github.autyism.keybindprofilesplus.configs;

/**
 * Layout of an exported config file (a .zip): {@code manifest.json} describes it (which game and mod
 * versions, which mod every file belongs to), {@code files/<path>} holds the files themselves under
 * their path in the game folder, and a backup made before an import may also have {@code remove.txt}
 * (files the import created, deleted again when the backup is imported).
 */
public final class ConfigArchiveFormat {
    public static final String MANIFEST = "manifest.json";
    public static final int FORMAT = 1;
    public static final String KIND_EXPORT = "export";
    public static final String KIND_BACKUP = "backup";
    public static final String EXPORT_PREFIX = "configs-";

    private ConfigArchiveFormat() {
    }
}
