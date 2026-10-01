package io.github.autyi6969.keybindprofilesplus.external;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Hotkeys that other mods manage themselves: Meteor Client and the malilib family. They are read
 * from those mods' config files so they can be listed and checked for conflicts. Nothing is ever
 * written to those files, and nothing is read for a mod that is not installed.
 */
public final class ExternalKeys {
    /** How long a scan of the config files is reused before the files are looked at again. */
    private static final long CACHE_NANOS = 2_000_000_000L;

    private static Environment environment = new LoadedMods();
    private static List<ExternalBinding> cache = List.of();
    private static long cacheTime;
    private static boolean cached;

    private ExternalKeys() {
    }

    /** What is installed and where the game directory is. Replaceable so the self-test can use fixture files. */
    public interface Environment {
        Path gameDirectory();

        boolean hasMeteor();

        /** Installed mods built on malilib (including malilib itself). */
        List<MalilibMod> malilibMods();
    }

    public record MalilibMod(String id, String name) {
    }

    /** Every external hotkey that currently has a key, from all installed sources. */
    public static synchronized List<ExternalBinding> all() {
        long now = System.nanoTime();
        if (!cached || now - cacheTime > CACHE_NANOS) {
            cache = List.copyOf(scan());
            cacheTime = now;
            cached = true;
        }
        return cache;
    }

    /** Only the hotkeys that are in effect (leaves out Meteor profiles that are not loaded). */
    public static List<ExternalBinding> active() {
        List<ExternalBinding> active = new ArrayList<>();
        for (ExternalBinding binding : all()) {
            if (binding.active()) {
                active.add(binding);
            }
        }
        return active;
    }

    /** Looks at the files again on the next call, e.g. when a screen that shows them opens. */
    public static synchronized void refresh() {
        cached = false;
    }

    /** For the self-test: what the malilib reader makes of a hotkey whose file says nothing about its context. */
    public static ExternalBinding.When malilibWhen(String modId, String name, boolean bareModifier, boolean bareMouseClick) {
        return MalilibKeys.when(modId, name, null, bareModifier, bareMouseClick);
    }

    /** For the self-test: use this environment instead of the real one (null restores it). */
    public static synchronized void setEnvironmentForTesting(Environment testEnvironment) {
        environment = testEnvironment == null ? new LoadedMods() : testEnvironment;
        cached = false;
    }

    private static List<ExternalBinding> scan() {
        List<ExternalBinding> bindings = new ArrayList<>();
        Path gameDirectory = environment.gameDirectory();
        if (gameDirectory == null) {
            return bindings;
        }
        if (environment.hasMeteor()) {
            bindings.addAll(MeteorKeys.read(gameDirectory));
        }
        for (MalilibMod mod : environment.malilibMods()) {
            bindings.addAll(MalilibKeys.read(gameDirectory, mod.id(), mod.name()));
        }
        return bindings;
    }

    private static final class LoadedMods implements Environment {
        @Override
        public Path gameDirectory() {
            return FabricLoader.getInstance().getGameDir();
        }

        @Override
        public boolean hasMeteor() {
            return FabricLoader.getInstance().isModLoaded(MeteorKeys.MOD_ID);
        }

        @Override
        public List<MalilibMod> malilibMods() {
            List<MalilibMod> mods = new ArrayList<>();
            if (!FabricLoader.getInstance().isModLoaded(MalilibKeys.LIBRARY_ID)) {
                return mods;
            }
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                boolean usesMalilib = id.equals(MalilibKeys.LIBRARY_ID);
                for (ModDependency dependency : mod.getMetadata().getDependencies()) {
                    if (dependency.getModId().equals(MalilibKeys.LIBRARY_ID)) {
                        usesMalilib = true;
                        break;
                    }
                }
                if (usesMalilib) {
                    mods.add(new MalilibMod(id, mod.getMetadata().getName()));
                }
            }
            mods.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
            return mods;
        }
    }
}
