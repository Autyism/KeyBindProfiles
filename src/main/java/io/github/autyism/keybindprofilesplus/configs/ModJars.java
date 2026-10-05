package io.github.autyism.keybindprofilesplus.configs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads mods straight from their jar files: the ids, names and versions in fabric.mod.json (also of
 * the mods packed inside other mods), and the evidence in their code. Used for mods that are in the
 * mods folder but switched off (".disabled"), and to scan a game folder from outside the game.
 */
public final class ModJars {
    private ModJars() {
    }

    /** Every mod in the mods folder: jars are installed, ".disabled" files are not. Nested mods are listed too. */
    public static List<ModInfo> read(Path modsDir, EvidenceCache cache, boolean includeEnabled) {
        Map<String, ModInfo> byId = new LinkedHashMap<>();
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
            stream.forEach(jars::add);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        jars.sort(null);
        for (Path jar : jars) {
            String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean enabled = name.endsWith(".jar");
            boolean disabled = name.endsWith(".disabled") && name.contains(".jar");
            if (!Files.isRegularFile(jar) || !(enabled || disabled) || (enabled && !includeEnabled)) {
                continue;
            }
            try {
                long size = Files.size(jar);
                long modified = Files.getLastModifiedTime(jar).toMillis();
                byte[] bytes = Files.readAllBytes(jar);
                readJar(bytes, jar.getFileName() + "|" + size + "|" + modified, enabled, cache, byId, null);
            } catch (IOException | RuntimeException e) {
                // not a readable mod
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static void readJar(byte[] jarBytes, String key, boolean installed, EvidenceCache cache, Map<String, ModInfo> byId, String parentId)
            throws IOException {
        JsonObject metadata = null;
        List<byte[]> nested = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("fabric.mod.json")) {
                    metadata = parse(zip.readAllBytes());
                } else if (name.startsWith("META-INF/jars/") && name.endsWith(".jar")) {
                    nested.add(zip.readAllBytes());
                }
            }
        }
        if (metadata == null || !metadata.has("id")) {
            return;
        }
        String id = metadata.get("id").getAsString();
        Set<String> aliases = new LinkedHashSet<>();
        if (metadata.has("provides") && metadata.get("provides").isJsonArray()) {
            metadata.getAsJsonArray("provides").forEach(e -> aliases.add(e.getAsString()));
        }
        String name = metadata.has("name") ? metadata.get("name").getAsString() : id;
        String version = metadata.has("version") ? metadata.get("version").getAsString() : "";
        BytecodeEvidence.Evidence evidence = cache.get(id + "@" + version + "|" + key, () -> evidenceOf(jarBytes));
        ModInfo mod = new ModInfo(id, name, version, aliases, installed, evidence, parentId, dependencies(metadata), entrypointPackages(metadata));
        ModInfo existing = byId.get(id);
        if (existing == null || (!existing.installed() && installed)) {
            byId.put(id, mod);
        }
        int index = 0;
        for (byte[] inner : nested) {
            readJar(inner, key + "#" + index++, installed, cache, byId, parentId == null ? id : parentId);
        }
    }

    /** What the classes of one jar (without the jars inside it) say about files. */
    public static BytecodeEvidence.Evidence evidenceOf(byte[] jarBytes) {
        BytecodeEvidence collector = new BytecodeEvidence();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/versions/") && !name.endsWith("module-info.class")) {
                    collector.addClass(zip.readAllBytes());
                }
            }
        } catch (IOException | RuntimeException e) {
            // what was read so far still counts
        }
        return collector.build();
    }

    /** fabric.mod.json, read as leniently as Fabric reads it. */
    static JsonObject parse(byte[] bytes) {
        try {
            String text = new String(bytes, StandardCharsets.UTF_8).replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", " ");
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The mod ids in "depends" of a fabric.mod.json. */
    static Set<String> dependencies(JsonObject metadata) {
        Set<String> out = new LinkedHashSet<>();
        if (metadata != null && metadata.has("depends") && metadata.get("depends").isJsonObject()) {
            metadata.getAsJsonObject("depends").keySet().forEach(id -> out.add(id.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    /**
     * The Java packages of the classes a fabric.mod.json names as entry points ("a.b.c.Main" and
     * "a.b.c.Main::init" -> "a.b.c"). Packages of one part only say too little and are left out.
     */
    static Set<String> entrypointPackages(JsonObject metadata) {
        Set<String> out = new LinkedHashSet<>();
        if (metadata == null || !metadata.has("entrypoints") || !metadata.get("entrypoints").isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entrypoint : metadata.getAsJsonObject("entrypoints").entrySet()) {
            if (!entrypoint.getValue().isJsonArray()) {
                continue;
            }
            for (JsonElement element : entrypoint.getValue().getAsJsonArray()) {
                String value = null;
                if (element.isJsonPrimitive()) {
                    value = element.getAsString();
                } else if (element.isJsonObject() && element.getAsJsonObject().has("value")) {
                    value = element.getAsJsonObject().get("value").getAsString();
                }
                if (value == null) {
                    continue;
                }
                int method = value.indexOf("::");
                String type = method >= 0 ? value.substring(0, method) : value;
                int dot = type.lastIndexOf('.');
                if (dot > 0 && type.substring(0, dot).contains(".")) {
                    out.add(type.substring(0, dot));
                }
            }
        }
        return out;
    }

    /** Reads every class below the given roots (folders or the root of an opened jar). */
    public static BytecodeEvidence.Evidence evidenceOfRoots(List<Path> roots) {
        BytecodeEvidence collector = new BytecodeEvidence();
        for (Path root : roots) {
            try (var stream = Files.walk(root)) {
                stream.filter(p -> {
                    String name = p.toString().replace('\\', '/');
                    return name.endsWith(".class") && !name.contains("META-INF/versions/") && !name.endsWith("module-info.class");
                }).forEach(p -> {
                    try (InputStream in = Files.newInputStream(p)) {
                        collector.addClass(in.readAllBytes());
                    } catch (IOException | RuntimeException ignored) {
                        // skip this class
                    }
                });
            } catch (IOException | RuntimeException ignored) {
                // what was read so far still counts
            }
        }
        return collector.build();
    }
}
