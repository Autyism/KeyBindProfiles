package io.github.autyism.keybindprofilesplus.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Remembers what {@link BytecodeEvidence} found in each mod, so a mod's code is only read again when
 * the mod changes (another version, another file). Reading all mods of a large instance takes some
 * seconds; with the cache the next scan is almost instant.
 */
public final class EvidenceCache {
    /** Bumped whenever {@link BytecodeEvidence} learns something new, so old results are not reused. */
    private static final int VERSION = 5;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path file;
    private final Map<String, BytecodeEvidence.Evidence> entries = new HashMap<>();
    private final Map<String, BytecodeEvidence.Evidence> used = new HashMap<>();
    private int misses;

    public EvidenceCache(Path file) {
        this.file = file;
        load();
    }

    /** The evidence for a mod, from the cache when the key is known, else by reading its classes. */
    public BytecodeEvidence.Evidence get(String key, Supplier<BytecodeEvidence.Evidence> compute) {
        BytecodeEvidence.Evidence evidence = entries.get(key);
        if (evidence == null) {
            evidence = compute.get();
            misses++;
        }
        used.put(key, evidence);
        return evidence;
    }

    /** Mods that had to be read this time. */
    public int misses() {
        return misses;
    }

    /** Saves what was used this time (entries of removed or updated mods are dropped). */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject mods = new JsonObject();
        used.forEach((key, evidence) -> {
            JsonObject entry = new JsonObject();
            entry.add("strong", array(evidence.strong()));
            entry.add("medium", array(evidence.medium()));
            entry.add("weak", array(evidence.weak()));
            entry.add("patterns", array(evidence.patterns()));
            entry.add("parents", array(evidence.parents()));
            mods.add(key, entry);
        });
        root.add("mods", mods);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Only costs time next scan.
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != VERSION) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("mods").entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                entries.put(entry.getKey(), new BytecodeEvidence.Evidence(set(value, "strong"), set(value, "medium"), set(value, "weak"),
                        set(value, "patterns"), set(value, "parents")));
            }
        } catch (IOException | RuntimeException e) {
            entries.clear();
        }
    }

    private static JsonArray array(Set<String> values) {
        JsonArray array = new JsonArray();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    private static Set<String> set(JsonObject object, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (object.has(key)) {
            object.getAsJsonArray(key).forEach(element -> out.add(element.getAsString()));
        }
        return Set.copyOf(out);
    }
}
