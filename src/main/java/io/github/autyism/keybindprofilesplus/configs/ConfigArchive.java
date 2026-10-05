package io.github.autyism.keybindprofilesplus.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Writing and reading exported config files (see {@link ConfigArchiveFormat} for the layout). */
public final class ConfigArchive {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int MAX_ENTRIES = 20_000;
    private static final long MAX_TOTAL_BYTES = 256L << 20;

    /** One file of an archive as the manifest describes it. */
    public record Entry(String path, List<String> owners, boolean perWorld) {
    }

    /** A mod of the exporting game, as far as the manifest says. */
    public record ModRef(String name, String version) {
    }

    /**
     * A read archive. {@code kind} is "export" or "backup"; {@code files} holds the content of every
     * readable file entry; {@code problem} is set when the file could not be read at all.
     */
    public record Archive(Path file, String kind, String created, String minecraft, Map<String, ModRef> mods, Map<String, Entry> entries,
                          Map<String, byte[]> files, List<String> removes, String problem) {
        public boolean isBackup() {
            return ConfigArchiveFormat.KIND_BACKUP.equals(kind);
        }
    }

    private ConfigArchive() {
    }

    /**
     * Writes the chosen files into a new archive in {@code exportsDir} and returns it. Files are read
     * again from disk and checked again, so nothing that changed into login data since the scan is
     * written.
     */
    public static Path export(Path exportsDir, ConfigScan.Result scan, Collection<ConfigScan.Found> selected, Path gameDir, Path configDir,
                              String minecraft, String appVersion, LocalDateTime now) throws IOException {
        Files.createDirectories(exportsDir);
        String base = ConfigArchiveFormat.EXPORT_PREFIX + now.format(STAMP);
        Path file = exportsDir.resolve(base + ".zip");
        for (int i = 2; Files.exists(file); i++) {
            file = exportsDir.resolve(base + "-" + i + ".zip");
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", ConfigArchiveFormat.FORMAT);
        manifest.addProperty("kind", ConfigArchiveFormat.KIND_EXPORT);
        manifest.addProperty("created", now.toString());
        manifest.addProperty("minecraft", minecraft);
        manifest.addProperty("app", "KeyBind Profiles+ " + appVersion);
        JsonObject mods = new JsonObject();
        JsonArray files = new JsonArray();

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            for (ConfigScan.Found found : selected) {
                String path = ConfigRules.normalize(found.path());
                if (path == null) {
                    continue;
                }
                Path source = ConfigImportApplier.resolve(path, gameDir, configDir);
                if (!Files.isRegularFile(source) || Files.size(source) > ConfigRules.MAX_FILE_BYTES) {
                    continue;
                }
                byte[] data = Files.readAllBytes(source);
                if (SecretDetector.secretName(path) != null || SecretDetector.secretContent(path, data) != null) {
                    continue;
                }
                ConfigImportApplier.put(zip, ConfigImportApplier.FILES_PREFIX + path, data);
                JsonObject entry = new JsonObject();
                entry.addProperty("path", path);
                JsonArray owners = new JsonArray();
                found.owners().forEach(owners::add);
                entry.add("owners", owners);
                if (found.kind() == ConfigScan.Kind.PER_WORLD) {
                    entry.addProperty("perWorld", true);
                }
                files.add(entry);
                for (String owner : found.owners()) {
                    ModInfo mod = scan.mod(owner);
                    if (mod != null && !mods.has(owner)) {
                        JsonObject ref = new JsonObject();
                        ref.addProperty("name", mod.name());
                        ref.addProperty("version", mod.version());
                        mods.add(owner, ref);
                    }
                }
            }
            manifest.add("mods", mods);
            manifest.add("files", files);
            ConfigImportApplier.put(zip, ConfigArchiveFormat.MANIFEST, GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e instanceof IOException io ? io : new IOException(e);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        return file;
    }

    /** Reads an archive; never throws (a problem is reported in the result). */
    public static Archive read(Path file) {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        List<String> removes = new ArrayList<>();
        byte[] manifestBytes = null;
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || ++count > MAX_ENTRIES) {
                    continue;
                }
                String name = entry.getName();
                if (name.equals(ConfigArchiveFormat.MANIFEST)) {
                    manifestBytes = ConfigImportApplier.readLimited(zip, 16L << 20);
                } else if (name.equals(ConfigImportApplier.REMOVE_LIST)) {
                    for (String line : new String(ConfigImportApplier.readLimited(zip, ConfigRules.MAX_FILE_BYTES), StandardCharsets.UTF_8).split("\\R")) {
                        if (!line.isBlank()) {
                            removes.add(line.trim());
                        }
                    }
                } else if (name.startsWith(ConfigImportApplier.FILES_PREFIX)) {
                    byte[] data = ConfigImportApplier.readLimited(zip, ConfigRules.MAX_FILE_BYTES + 1);
                    if ((total += data.length) <= MAX_TOTAL_BYTES) {
                        contents.put(name.substring(ConfigImportApplier.FILES_PREFIX.length()), data);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Archive(file, "", "", "", Map.of(), Map.of(), Map.of(), List.of(), e.getClass().getSimpleName());
        }

        String kind = ConfigArchiveFormat.KIND_EXPORT;
        String created = "";
        String minecraft = "";
        Map<String, ModRef> mods = new LinkedHashMap<>();
        Map<String, Entry> entries = new LinkedHashMap<>();
        if (manifestBytes != null) {
            try {
                JsonObject root = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
                kind = string(root, "kind", kind);
                created = string(root, "created", "");
                minecraft = string(root, "minecraft", "");
                if (root.has("mods") && root.get("mods").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> mod : root.getAsJsonObject("mods").entrySet()) {
                        JsonObject ref = mod.getValue().getAsJsonObject();
                        mods.put(mod.getKey(), new ModRef(string(ref, "name", mod.getKey()), string(ref, "version", "")));
                    }
                }
                if (root.has("files") && root.get("files").isJsonArray()) {
                    for (JsonElement element : root.getAsJsonArray("files")) {
                        JsonObject f = element.getAsJsonObject();
                        List<String> owners = new ArrayList<>();
                        if (f.has("owners") && f.get("owners").isJsonArray()) {
                            f.getAsJsonArray("owners").forEach(o -> owners.add(o.getAsString()));
                        }
                        String path = string(f, "path", "");
                        entries.put(path, new Entry(path, List.copyOf(owners), f.has("perWorld") && f.get("perWorld").getAsBoolean()));
                    }
                }
                if (root.has("removes") && root.get("removes").isJsonArray() && removes.isEmpty()) {
                    root.getAsJsonArray("removes").forEach(r -> removes.add(r.getAsString()));
                }
            } catch (RuntimeException e) {
                // A broken manifest: the files are still there, their owners are worked out again.
            }
        }
        for (String path : contents.keySet()) {
            entries.putIfAbsent(path, new Entry(path, List.of(), false));
        }
        return new Archive(file, kind, created, minecraft, mods, entries, contents, List.copyOf(removes), null);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
