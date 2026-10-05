package io.github.autyism.keybindprofilesplus.configs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Writes a staged import into the game folder. It runs when the game starts, before any mod has read
 * its settings (see {@link EarlyConfigImport}), so it may only use plain Java.
 *
 * <p>The staged file ({@link #PENDING_FILE}) holds the files to write under {@code files/<path>} and,
 * when an earlier import is being undone, a {@code remove.txt} listing files that import created.
 * Before anything changes, every file that is about to be replaced or removed is copied into a
 * backup in the exports folder, together with the list of files this import creates - importing that
 * backup puts everything back the way it was.</p>
 */
public final class ConfigImportApplier {
    public static final String PENDING_FILE = "pending-config-import.zip";
    public static final String RESULT_FILE = "config-import-result.json";
    public static final String EXPORTS_DIR = "config-exports";
    public static final String BACKUP_PREFIX = "before-import-";
    public static final String FILES_PREFIX = "files/";
    public static final String REMOVE_LIST = "remove.txt";
    public static final String SOURCE_NOTE = "source.txt";
    private static final int MAX_ENTRIES = 20_000;
    private static final long MAX_TOTAL_BYTES = 256L << 20;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** What happened; also written to {@link #RESULT_FILE} for the screens to show. */
    public record Outcome(String source, int written, int removed, List<String> skipped, List<String> errors, String backup) {
    }

    private ConfigImportApplier() {
    }

    /** Applies the staged import of this game folder, if there is one. Returns null when there was nothing to do. */
    public static Outcome applyPending(Path gameDir, Path configDir) {
        Path ownDir = ownDir(gameDir, configDir);
        Path pending = ownDir.resolve(PENDING_FILE);
        if (!Files.isRegularFile(pending)) {
            return null;
        }
        Outcome outcome = apply(pending, gameDir, configDir, ownDir.resolve(EXPORTS_DIR), LocalDateTime.now());
        try {
            Files.deleteIfExists(pending);
        } catch (IOException e) {
            outcome.errors().add("could not remove " + PENDING_FILE + ": " + e);
        }
        writeResult(ownDir.resolve(RESULT_FILE), outcome, LocalDateTime.now());
        return outcome;
    }

    /** The folder this mod keeps its files in. */
    public static Path ownDir(Path gameDir, Path configDir) {
        return configDir.resolve(ConfigRules.OWN_DIR.substring(ConfigRules.CONFIG_PREFIX.length()));
    }

    /** Where a normalized relative path lives on disk ("config/..." is inside the config folder Fabric uses). */
    public static Path resolve(String normalizedPath, Path gameDir, Path configDir) {
        if (normalizedPath.equals("config") || normalizedPath.startsWith(ConfigRules.CONFIG_PREFIX)) {
            String rest = normalizedPath.length() > ConfigRules.CONFIG_PREFIX.length() ? normalizedPath.substring(ConfigRules.CONFIG_PREFIX.length()) : "";
            return rest.isEmpty() ? configDir : configDir.resolve(rest);
        }
        return gameDir.resolve(normalizedPath);
    }

    /**
     * True when the path resolves inside its base folder and no folder on the way is a link that could
     * lead somewhere else.
     */
    public static boolean staysInside(String normalizedPath, Path gameDir, Path configDir) {
        Path base = (normalizedPath.startsWith(ConfigRules.CONFIG_PREFIX) ? configDir : gameDir).toAbsolutePath().normalize();
        Path target = resolve(normalizedPath, gameDir, configDir).toAbsolutePath().normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            return false;
        }
        for (Path p = target; p != null && !p.equals(base); p = p.getParent()) {
            if (Files.isSymbolicLink(p)) {
                return false;
            }
        }
        return true;
    }

    /** Applies one staged file (the testable core of {@link #applyPending}). */
    public static Outcome apply(Path pending, Path gameDir, Path configDir, Path exportsDir, LocalDateTime now) {
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, byte[]> writes = new LinkedHashMap<>();
        List<String> removals = new ArrayList<>();
        String source = "";
        long total = 0;
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(pending))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    errors.add("too many entries, the rest was ignored");
                    break;
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.equals(SOURCE_NOTE)) {
                    source = new String(readLimited(zip, 4096), StandardCharsets.UTF_8).trim();
                    continue;
                }
                if (name.equals(REMOVE_LIST)) {
                    for (String line : new String(readLimited(zip, ConfigRules.MAX_FILE_BYTES), StandardCharsets.UTF_8).split("\\R")) {
                        String path = ConfigRules.normalize(line.trim());
                        if (!line.isBlank()) {
                            if (path != null && ConfigRules.isWritableTarget(path) && staysInside(path, gameDir, configDir)) {
                                removals.add(path);
                            } else {
                                skipped.add(line.trim() + " (not allowed)");
                            }
                        }
                    }
                    continue;
                }
                if (!name.startsWith(FILES_PREFIX)) {
                    continue;
                }
                String path = ConfigRules.normalize(name.substring(FILES_PREFIX.length()));
                byte[] data = readLimited(zip, ConfigRules.MAX_FILE_BYTES + 1);
                if (writes.containsKey(path)) {
                    skipped.add(path + " (listed twice)");
                    continue;
                }
                if (path == null || !ConfigRules.isWritableTarget(path) || !staysInside(path, gameDir, configDir)) {
                    skipped.add(name.substring(FILES_PREFIX.length()) + " (not allowed)");
                } else if (data.length > ConfigRules.MAX_FILE_BYTES) {
                    skipped.add(path + " (too large)");
                } else if (ConfigRules.contentProblem(path, data) != null) {
                    skipped.add(path + " (" + ConfigRules.contentProblem(path, data) + ")");
                } else if (SecretDetector.secretContent(path, data) != null) {
                    skipped.add(path + " (login data)");
                } else if ((total += data.length) > MAX_TOTAL_BYTES) {
                    skipped.add(path + " (import too large)");
                } else {
                    writes.put(path, data);
                }
            }
        } catch (IOException | RuntimeException e) {
            errors.add("could not read the staged import: " + e);
            return new Outcome(source, 0, 0, skipped, errors, null);
        }

        // Back up what is about to change, and note what is about to be created.
        Map<String, byte[]> before = new LinkedHashMap<>();
        List<String> created = new ArrayList<>();
        for (String path : writes.keySet()) {
            Path target = resolve(path, gameDir, configDir);
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    before.put(path, Files.readAllBytes(target));
                } catch (IOException e) {
                    errors.add("could not back up " + path + ": " + e);
                }
            } else if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                created.add(path);
            }
        }
        for (String path : removals) {
            Path target = resolve(path, gameDir, configDir);
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && !before.containsKey(path)) {
                try {
                    before.put(path, Files.readAllBytes(target));
                } catch (IOException e) {
                    errors.add("could not back up " + path + ": " + e);
                }
            }
        }
        String backupName = null;
        if (!before.isEmpty() || !created.isEmpty()) {
            backupName = BACKUP_PREFIX + now.format(STAMP) + ".zip";
            try {
                writeBackup(exportsDir.resolve(backupName), before, created, now);
            } catch (IOException e) {
                // Without a backup nothing is changed: the player could not get the old files back.
                errors.add("could not write the backup, nothing was changed: " + e);
                return new Outcome(source, 0, 0, skipped, errors, null);
            }
        }

        int written = 0;
        for (Map.Entry<String, byte[]> write : writes.entrySet()) {
            Path target = resolve(write.getKey(), gameDir, configDir);
            try {
                if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                    skipped.add(write.getKey() + " (a folder has this name)");
                    continue;
                }
                Files.createDirectories(target.getParent());
                Path temp = target.resolveSibling(target.getFileName() + ".kbp-import.tmp");
                Files.write(temp, write.getValue());
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                written++;
            } catch (IOException e) {
                errors.add("could not write " + write.getKey() + ": " + e);
            }
        }
        int removed = 0;
        for (String path : removals) {
            if (writes.containsKey(path)) {
                continue;
            }
            Path target = resolve(path, gameDir, configDir);
            try {
                if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && Files.deleteIfExists(target)) {
                    removed++;
                }
            } catch (IOException e) {
                errors.add("could not remove " + path + ": " + e);
            }
        }
        return new Outcome(source, written, removed, skipped, errors, backupName);
    }

    private static void writeBackup(Path file, Map<String, byte[]> before, List<String> created, LocalDateTime now) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            StringBuilder manifest = new StringBuilder();
            manifest.append("{\n  \"format\": 1,\n  \"kind\": \"backup\",\n  \"created\": ").append(quote(now.toString()))
                    .append(",\n  \"files\": [");
            boolean first = true;
            for (String path : before.keySet()) {
                manifest.append(first ? "\n" : ",\n").append("    {\"path\": ").append(quote(path)).append("}");
                first = false;
            }
            manifest.append(first ? "]" : "\n  ]").append(",\n  \"removes\": [");
            first = true;
            for (String path : created) {
                manifest.append(first ? "" : ", ").append(quote(path));
                first = false;
            }
            manifest.append("]\n}\n");
            put(zip, ConfigArchiveFormat.MANIFEST, manifest.toString().getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> entry : before.entrySet()) {
                put(zip, FILES_PREFIX + entry.getKey(), entry.getValue());
            }
            if (!created.isEmpty()) {
                put(zip, REMOVE_LIST, String.join("\n", created).getBytes(StandardCharsets.UTF_8));
            }
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    /**
     * Reads a zip entry, but never more than {@code limit} bytes into memory: a longer entry is read to
     * its end and comes back as an empty array of exactly {@code limit} bytes, which callers treat as
     * "too large".
     */
    static byte[] readLimited(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > limit) {
                while (in.read(buffer) > 0) {
                    // skip the rest of the entry
                }
                return new byte[(int) Math.min(limit, Integer.MAX_VALUE - 8)];
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void writeResult(Path file, Outcome outcome, LocalDateTime now) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"time\": ").append(quote(now.toString()))
                .append(",\n  \"source\": ").append(quote(outcome.source()))
                .append(",\n  \"written\": ").append(outcome.written())
                .append(",\n  \"removed\": ").append(outcome.removed())
                .append(",\n  \"backup\": ").append(outcome.backup() == null ? "null" : quote(outcome.backup()))
                .append(",\n  \"skipped\": ").append(list(outcome.skipped()))
                .append(",\n  \"errors\": ").append(list(outcome.errors()))
                .append("\n}\n");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
            // reported by the write below
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[KeyBind Profiles+] could not write " + file + ": " + e);
        }
    }

    private static String list(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            out.append(i == 0 ? "" : ", ").append(quote(values.get(i)));
        }
        return out.append(']').toString();
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
