package io.github.autyism.keybindprofilesplus.configs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipOutputStream;

/**
 * Works out what importing an archive would do in this game - file by file: new, changed, already the
 * same, removed again (undoing an earlier import), meant for a mod that is not installed here, or not
 * allowed at all - and stages the chosen part for {@link ConfigImportApplier}, which writes it the
 * next time the game starts.
 */
public final class ConfigImport {
    public enum Status {
        /** The file does not exist here yet. */
        NEW,
        /** The file exists here with other content. */
        CHANGE,
        /** The file here is already exactly like this. */
        SAME,
        /** Undoing an import: this file was created by it and goes away again. */
        DELETE,
        /** The mod the file belongs to is not installed here. */
        MOD_MISSING,
        /** Never imported (unsafe path, login data, not a settings file...). */
        REJECTED
    }

    /**
     * One file of the archive. {@code owners}: mod ids in this game (or, for a missing mod, in the
     * exporting game); {@code detail}: why it is rejected, or the world it belongs to.
     */
    public record Item(String path, Status status, List<String> owners, String detail, boolean perWorld) {
        public boolean importable() {
            return status == Status.NEW || status == Status.CHANGE || status == Status.DELETE || status == Status.MOD_MISSING;
        }

        /** Ticked when the import screen opens. */
        public boolean suggested() {
            return status == Status.NEW || status == Status.CHANGE || status == Status.DELETE;
        }
    }

    /** {@code versionNotes}: mod id -> "exported version -> version here", for mods whose versions differ. */
    public record Plan(ConfigArchive.Archive archive, List<Item> items, boolean otherMinecraft, Map<String, String> versionNotes) {
        public Item item(String path) {
            return items.stream().filter(i -> i.path().equals(path)).findFirst().orElse(null);
        }
    }

    /** What the last import did, read back from the file the early importer wrote. */
    public record LastResult(String time, String source, int written, int removed, String backup, List<String> skipped, List<String> errors) {
    }

    private ConfigImport() {
    }

    public static Plan plan(ConfigArchive.Archive archive, Path gameDir, Path configDir, ConfigScan target, Map<String, ModInfo> mods,
                            String minecraft) {
        List<Item> items = new ArrayList<>();
        Map<String, String> versionNotes = new java.util.LinkedHashMap<>();
        for (ConfigArchive.Entry entry : archive.entries().values()) {
            String path = ConfigRules.normalize(entry.path());
            byte[] data = archive.files().get(entry.path());
            if (data == null) {
                continue;
            }
            String shown = path == null ? entry.path() : path;
            String rejection = rejection(path, data, gameDir, configDir, target);
            if (rejection != null) {
                items.add(new Item(shown, Status.REJECTED, entry.owners(), rejection, false));
                continue;
            }
            List<String> owners = installedOwners(entry.owners(), target.ownerOf(path), mods);
            boolean perWorld = entry.perWorld() || target.perWorldReason(path) != null;
            if (owners.isEmpty()) {
                items.add(new Item(path, Status.MOD_MISSING, entry.owners(), "", perWorld));
                continue;
            }
            for (String owner : owners) {
                ConfigArchive.ModRef exported = archive.mods().get(owner);
                ModInfo here = mods.get(owner);
                if (exported != null && here != null && !exported.version().isEmpty() && !exported.version().equals(here.version())) {
                    versionNotes.put(owner, exported.version() + " -> " + here.version());
                }
            }
            Path target0 = ConfigImportApplier.resolve(path, gameDir, configDir);
            Status status;
            try {
                status = !Files.exists(target0, LinkOption.NOFOLLOW_LINKS) ? Status.NEW
                        : Arrays.equals(Files.readAllBytes(target0), data) ? Status.SAME : Status.CHANGE;
            } catch (IOException e) {
                status = Status.CHANGE;
            }
            items.add(new Item(path, status, owners, perWorld ? nonNull(target.perWorldReason(path)) : "", perWorld));
        }
        // Undoing an import: the files it created are removed again.
        for (String line : archive.removes()) {
            String path = ConfigRules.normalize(line);
            if (path == null || !ConfigRules.isWritableTarget(path) || !ConfigImportApplier.staysInside(path, gameDir, configDir)) {
                continue;
            }
            if (archive.files().containsKey(path) || !Files.isRegularFile(ConfigImportApplier.resolve(path, gameDir, configDir), LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            ConfigScan.PathOwner owner = target.ownerOf(path);
            items.add(new Item(path, Status.DELETE, owner.installed(), "", target.perWorldReason(path) != null));
        }
        items.sort((a, b) -> a.path().compareToIgnoreCase(b.path()));
        boolean otherMinecraft = !archive.minecraft().isEmpty() && !archive.minecraft().equals(minecraft);
        return new Plan(archive, List.copyOf(items), otherMinecraft, Map.copyOf(versionNotes));
    }

    /** Null when the file may be imported, else the reason key it may not. */
    private static String rejection(String path, byte[] data, Path gameDir, Path configDir, ConfigScan target) {
        if (path == null || !ConfigImportApplier.staysInside(path, gameDir, configDir)) {
            return "path";
        }
        if (target.ownerOf(path).own() || ConfigRules.isOwnWorkingFile(path)) {
            return "own";
        }
        if (!ConfigRules.isWritableTarget(path)) {
            return SecretDetector.secretName(path) != null ? "login" : "type";
        }
        if (data.length > ConfigRules.MAX_FILE_BYTES) {
            return "size";
        }
        if (ConfigScan.contentProblem(path.substring(path.lastIndexOf('/') + 1), data) != null) {
            return "broken";
        }
        if (SecretDetector.secretContent(path, data) != null) {
            return "login";
        }
        return null;
    }

    /** The archive's owners that are installed here, else the owners this game finds for the path. */
    private static List<String> installedOwners(List<String> archiveOwners, ConfigScan.PathOwner here, Map<String, ModInfo> mods) {
        Set<String> owners = new LinkedHashSet<>();
        for (String id : archiveOwners) {
            ModInfo mod = mods.get(id);
            if (mod != null && mod.installed()) {
                owners.add(id);
            }
        }
        if (owners.isEmpty()) {
            owners.addAll(here.installed());
        }
        return List.copyOf(owners);
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    /** Writes the chosen items as the staged import; replaces an import that was staged before. */
    public static void stage(Plan plan, Collection<Item> chosen, Path ownDir) throws IOException {
        Files.createDirectories(ownDir);
        Path pending = ownDir.resolve(ConfigImportApplier.PENDING_FILE);
        Path temp = pending.resolveSibling(pending.getFileName() + ".tmp");
        List<String> removes = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            ConfigImportApplier.put(zip, ConfigImportApplier.SOURCE_NOTE, plan.archive().file().getFileName().toString().getBytes(StandardCharsets.UTF_8));
            for (Item item : chosen) {
                if (item.status() == Status.DELETE) {
                    removes.add(item.path());
                } else if (item.importable() && plan.archive().files().containsKey(item.path())) {
                    ConfigImportApplier.put(zip, ConfigImportApplier.FILES_PREFIX + item.path(), plan.archive().files().get(item.path()));
                }
            }
            if (!removes.isEmpty()) {
                ConfigImportApplier.put(zip, ConfigImportApplier.REMOVE_LIST, String.join("\n", removes).getBytes(StandardCharsets.UTF_8));
            }
        }
        Files.move(temp, pending, StandardCopyOption.REPLACE_EXISTING);
    }

    /** The archive name of the staged import, or null when nothing is staged. */
    public static String pendingSource(Path ownDir) {
        Path pending = ownDir.resolve(ConfigImportApplier.PENDING_FILE);
        if (!Files.isRegularFile(pending)) {
            return null;
        }
        try (var zip = new java.util.zip.ZipFile(pending.toFile())) {
            var note = zip.getEntry(ConfigImportApplier.SOURCE_NOTE);
            return note == null ? "" : new String(zip.getInputStream(note).readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /** Number of files the staged import writes or removes. */
    public static int pendingCount(Path ownDir) {
        Path pending = ownDir.resolve(ConfigImportApplier.PENDING_FILE);
        try (var zip = new java.util.zip.ZipFile(pending.toFile())) {
            int[] count = new int[1];
            zip.stream().forEach(e -> {
                if (e.getName().startsWith(ConfigImportApplier.FILES_PREFIX)) {
                    count[0]++;
                }
            });
            var remove = zip.getEntry(ConfigImportApplier.REMOVE_LIST);
            if (remove != null) {
                count[0] += (int) new String(zip.getInputStream(remove).readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).count();
            }
            return count[0];
        } catch (IOException e) {
            return 0;
        }
    }

    public static boolean cancelPending(Path ownDir) {
        try {
            return Files.deleteIfExists(ownDir.resolve(ConfigImportApplier.PENDING_FILE));
        } catch (IOException e) {
            return false;
        }
    }

    public static LastResult lastResult(Path ownDir) {
        Path file = ownDir.resolve(ConfigImportApplier.RESULT_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return new LastResult(root.get("time").getAsString(), root.get("source").getAsString(), root.get("written").getAsInt(),
                    root.get("removed").getAsInt(), root.get("backup").isJsonNull() ? null : root.get("backup").getAsString(),
                    strings(root.getAsJsonArray("skipped")), strings(root.getAsJsonArray("errors")));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }
}
