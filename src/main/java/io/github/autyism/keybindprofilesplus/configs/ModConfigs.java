package io.github.autyism.keybindprofilesplus.configs;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The mod config export / import as the game uses it: where the files are, which mods are loaded,
 * and the scan running in the background so the screens stay responsive.
 */
public final class ModConfigs {
    /** Everything a screen needs: the mods (installed and switched off), and the result of the scan. */
    public record Context(Map<String, ModInfo> mods, ConfigScan scan, ConfigScan.Result result) {
    }

    private static final AtomicInteger PROGRESS = new AtomicInteger();
    private static final AtomicInteger TOTAL = new AtomicInteger();
    private static volatile CompletableFuture<Context> running;

    private ModConfigs() {
    }

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
    }

    public static Path ownDir() {
        return ConfigImportApplier.ownDir(gameDir(), configDir());
    }

    public static Path exportsDir() {
        return ownDir().resolve(ConfigImportApplier.EXPORTS_DIR);
    }

    public static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    /** {done, total} mods read by the running scan. */
    public static int[] progress() {
        return new int[]{PROGRESS.get(), TOTAL.get()};
    }

    /** Scans again (the files may have changed since the last time); mods whose code was read before come from the cache. */
    public static synchronized CompletableFuture<Context> scan() {
        CompletableFuture<Context> current = running;
        if (current != null && !current.isDone()) {
            return current;
        }
        current = CompletableFuture.supplyAsync(ModConfigs::scanNow, runnable -> {
            Thread thread = new Thread(runnable, "KeyBind Profiles+ config scan");
            thread.setDaemon(true);
            thread.start();
        });
        running = current;
        return current;
    }

    private static Context scanNow() {
        long start = System.currentTimeMillis();
        EvidenceCache cache = new EvidenceCache(ownDir().resolve(ConfigRules.SCAN_CACHE_FILE));
        List<ModInfo> mods = new ArrayList<>(loadedMods(cache));
        // Mods lying in the mods folder switched off: to say whose leftovers a file is.
        for (ModInfo off : ModJars.read(gameDir().resolve("mods"), cache, false)) {
            if (mods.stream().noneMatch(m -> m.id().equals(off.id()))) {
                mods.add(off);
            }
        }
        cache.save();
        Path game = gameDir();
        ConfigScan scan = new ConfigScan(game, configDir(), mods, WorldNames.of(game), KeyBindProfilesPlus.MOD_ID);
        ConfigScan.Result result = scan.scan();
        Map<String, ModInfo> byId = new LinkedHashMap<>();
        mods.forEach(mod -> byId.putIfAbsent(mod.id(), mod));
        KeyBindProfilesPlus.LOGGER.info("Mod config scan: {} mods ({} read now), {} files offered, {} not exported, {} ms",
                mods.size(), cache.misses(), result.files().size(), result.skipped().size(), System.currentTimeMillis() - start);
        return new Context(Map.copyOf(byId), scan, result);
    }

    /** Every loaded mod with what its code says about files. */
    private static List<ModInfo> loadedMods(EvidenceCache cache) {
        Collection<ModContainer> containers = FabricLoader.getInstance().getAllMods();
        TOTAL.set(containers.size());
        PROGRESS.set(0);
        List<ModInfo> mods = new ArrayList<>();
        for (ModContainer container : containers) {
            PROGRESS.incrementAndGet();
            var metadata = container.getMetadata();
            String id = metadata.getId();
            if (id.equals("java") || id.equals("minecraft")) {
                continue;
            }
            String parent = container.getContainingMod().map(c -> c.getMetadata().getId()).orElse(null);
            String version = metadata.getVersion().getFriendlyString();
            BytecodeEvidence.Evidence evidence = cache.get(id + "@" + version + "|" + originKey(container),
                    () -> ModJars.evidenceOfRoots(container.getRootPaths()));
            Set<String> depends = metadata.getDependencies().stream().filter(dependency -> dependency.getKind() == ModDependency.Kind.DEPENDS)
                    .map(ModDependency::getModId).collect(Collectors.toSet());
            Set<String> packages = container.findPath("fabric.mod.json").map(ModConfigs::entrypointPackages).orElse(Set.of());
            mods.add(new ModInfo(id, metadata.getName(), version, metadata.getProvides().stream().collect(Collectors.toSet()), true, evidence, parent,
                    depends, packages));
        }
        return mods;
    }

    private static Set<String> entrypointPackages(Path modJson) {
        try {
            return ModJars.entrypointPackages(ModJars.parse(Files.readAllBytes(modJson)));
        } catch (IOException | RuntimeException e) {
            return Set.of();
        }
    }

    /** Where a mod comes from, precise enough to notice an updated jar. */
    private static String originKey(ModContainer container) {
        try {
            ModOrigin origin = container.getOrigin();
            if (origin.getKind() == ModOrigin.Kind.PATH) {
                StringBuilder key = new StringBuilder();
                for (Path path : origin.getPaths()) {
                    key.append(path.getFileName());
                    if (Files.isRegularFile(path)) {
                        key.append('|').append(Files.size(path)).append('|').append(Files.getLastModifiedTime(path).toMillis());
                    } else {
                        // A folder (the development environment): never cached, it changes all the time.
                        key.append('|').append(System.nanoTime());
                    }
                }
                return key.toString();
            }
            if (origin.getKind() == ModOrigin.Kind.NESTED) {
                String parentKey = FabricLoader.getInstance().getModContainer(origin.getParentModId()).map(ModConfigs::originKey).orElse("");
                return parentKey + "#" + origin.getParentSubLocation();
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through
        }
        return "unknown|" + System.nanoTime();
    }

    // ------------------------------------------------------------------ export / import

    public static Path export(Context context, Collection<ConfigScan.Found> selected) throws IOException {
        String version = FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        return ConfigArchive.export(exportsDir(), context.result(), selected, gameDir(), configDir(), minecraftVersion(), version, LocalDateTime.now());
    }

    /** The export files in the exports folder, newest first. */
    public static List<Path> archives() {
        List<Path> out = new ArrayList<>();
        Path dir = exportsDir();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.zip")) {
            stream.forEach(out::add);
        } catch (IOException ignored) {
            // nothing to list
        }
        out.sort(Comparator.comparing((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).reversed());
        return out;
    }

    public static ConfigImport.Plan planImport(Path archive, Context context) {
        return ConfigImport.plan(ConfigArchive.read(archive), gameDir(), configDir(), context.scan(), context.mods(), minecraftVersion());
    }

    public static void stage(ConfigImport.Plan plan, Collection<ConfigImport.Item> chosen) throws IOException {
        ConfigImport.stage(plan, chosen, ownDir());
    }

    public static String pendingSource() {
        return ConfigImport.pendingSource(ownDir());
    }

    public static int pendingCount() {
        return ConfigImport.pendingCount(ownDir());
    }

    public static boolean cancelPending() {
        return ConfigImport.cancelPending(ownDir());
    }

    public static ConfigImport.LastResult lastResult() {
        return ConfigImport.lastResult(ownDir());
    }
}
