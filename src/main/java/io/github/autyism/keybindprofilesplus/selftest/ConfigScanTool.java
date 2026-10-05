package io.github.autyism.keybindprofilesplus.selftest;

import io.github.autyism.keybindprofilesplus.configs.ConfigScan;
import io.github.autyism.keybindprofilesplus.configs.EvidenceCache;
import io.github.autyism.keybindprofilesplus.configs.ModInfo;
import io.github.autyism.keybindprofilesplus.configs.ModJars;
import io.github.autyism.keybindprofilesplus.configs.WorldNames;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Development tool, not part of the game: runs the config scan on any game folder from the command
 * line (Gradle task {@code scanConfigs}) and writes a report of what belongs to which mod, what would
 * be exported and what not, and why. Used to check the scan against real instances with many mods.
 *
 * <p>Arguments: game folder, report file, cache file.</p>
 */
public final class ConfigScanTool {
    private ConfigScanTool() {
    }

    public static void main(String[] args) throws IOException {
        Path gameDir = Path.of(args[0]);
        Path report = Path.of(args[1]);
        EvidenceCache cache = new EvidenceCache(Path.of(args[2]));
        long start = System.currentTimeMillis();
        List<ModInfo> mods = ModJars.read(gameDir.resolve("mods"), cache, true);
        long read = System.currentTimeMillis();
        cache.save();
        ConfigScan.Result result = new ConfigScan(gameDir, gameDir.resolve("config"), mods, WorldNames.of(gameDir), "keybindprofilesplus").scan();
        long done = System.currentTimeMillis();

        Files.createDirectories(report.toAbsolutePath().getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(report, StandardCharsets.UTF_8))) {
            out.printf("mods: %d (%d installed), read in %d ms (%d not cached), scan %d ms%n", mods.size(),
                    mods.stream().filter(ModInfo::installed).count(), read - start, cache.misses(), done - read);
            Map<String, List<ConfigScan.Found>> byOwner = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (ConfigScan.Found f : result.files()) {
                String owner = f.kind() == ConfigScan.Kind.ORPHAN ? "~ not installed: " + (f.detail().isEmpty() ? "?" : f.detail())
                        : f.owners().stream().map(id -> result.mod(id).rootId()).distinct().collect(java.util.stream.Collectors.joining("+"))
                        + "   " + f.owners();
                byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(f);
            }
            for (Map.Entry<String, List<ConfigScan.Found>> group : byOwner.entrySet()) {
                out.println();
                out.println("== " + group.getKey());
                for (ConfigScan.Found f : group.getValue()) {
                    out.printf("   %-9s %s %s %s%s%n", f.kind(), f.byCode() ? "code" : "name", f.sure() ? "sure " : "MAYBE", f.path(), f.detail().isEmpty() ? "" : "   [" + f.detail() + "]");
                }
            }
            out.println();
            out.println("== NOT EXPORTED");
            for (ConfigScan.Skipped s : result.skipped()) {
                out.printf("   %-12s %s  (%d files, %d KB) %s %s%n", s.reason(), s.path(), s.files(), s.size() / 1024,
                        s.detail(), s.owners().isEmpty() ? "" : s.owners());
            }
        }
        System.out.println("report written to " + report.toAbsolutePath());
    }
}
