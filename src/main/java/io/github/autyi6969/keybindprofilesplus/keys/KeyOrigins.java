package io.github.autyi6969.keybindprofilesplus.keys;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.option.KeyBinding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Remembers which class created each key binding, and from that which mod it belongs to.
 *
 * <p>The game does not keep track of who registers a key binding. But every binding is created by
 * some code, and that code sits in some mod's jar. So when a binding is constructed, the first
 * class on the call stack that is not the game, Java or Fabric itself is noted; later that class
 * is looked up in the loaded mods' files. This works no matter how a mod names its bindings.
 */
public final class KeyOrigins {
    private static final String[] SKIPPED_PREFIXES = {
            "net.minecraft.", "com.mojang.", "java.", "javax.", "jdk.", "sun.",
            "net.fabricmc.fabric.", "net.fabricmc.loader.", "org.spongepowered.",
            "io.github.autyi6969.keybindprofilesplus.mixin.", "io.github.autyi6969.keybindprofilesplus.keys.KeyOrigins"
    };
    private static final StackWalker WALKER = StackWalker.getInstance();

    private static final Map<KeyBinding, String> CREATOR_CLASS = new WeakHashMap<>();
    private static final Map<String, Optional<ModContainer>> MOD_BY_CLASS = new HashMap<>();

    private KeyOrigins() {
    }

    /** Called from the KeyBinding constructor. */
    public static void record(KeyBinding binding) {
        try {
            String creator = WALKER.walk(frames -> frames
                    .map(StackWalker.StackFrame::getClassName)
                    .filter(KeyOrigins::isForeign)
                    .findFirst()
                    .orElse(null));
            if (creator != null) {
                synchronized (CREATOR_CLASS) {
                    CREATOR_CLASS.put(binding, creator);
                }
            }
        } catch (RuntimeException ignored) {
            // Not knowing the creator only means falling back to the naming rules.
        }
    }

    /** The mod whose code created the binding, if that could be worked out. */
    public static Optional<ModContainer> modOf(KeyBinding binding) {
        String creator;
        synchronized (CREATOR_CLASS) {
            creator = CREATOR_CLASS.get(binding);
        }
        if (creator == null) {
            return Optional.empty();
        }
        synchronized (MOD_BY_CLASS) {
            return MOD_BY_CLASS.computeIfAbsent(creator, KeyOrigins::findModContaining);
        }
    }

    /** The class that created the binding, as noted when it was constructed; null if unknown. */
    public static String creatorClassOf(KeyBinding binding) {
        synchronized (CREATOR_CLASS) {
            return CREATOR_CLASS.get(binding);
        }
    }

    /** For the self-test: makes a binding look as if nobody knows who created it. */
    public static void forget(KeyBinding binding) {
        synchronized (CREATOR_CLASS) {
            CREATOR_CLASS.remove(binding);
        }
    }

    private static boolean isForeign(String className) {
        for (String prefix : SKIPPED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static Optional<ModContainer> findModContaining(String className) {
        String path = className.replace('.', '/') + ".class";
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (id.equals("minecraft") || id.equals("java")) {
                continue;
            }
            try {
                if (mod.findPath(path).isPresent()) {
                    return Optional.of(mod);
                }
            } catch (RuntimeException ignored) {
                // A mod whose files cannot be searched is simply not a match.
            }
        }
        return findModByEntrypointPackage(className);
    }

    /**
     * Second way of finding the mod, for setups where a mod's classes are not inside the files the
     * loader knows it by (a development environment keeps classes and resources apart): the mod
     * whose entry point class shares the longest package with the creating class.
     */
    private static Optional<ModContainer> findModByEntrypointPackage(String className) {
        ModContainer best = null;
        int bestLength = 0;
        for (String key : List.of("client", "main")) {
            List<EntrypointContainer<Object>> entrypoints;
            try {
                entrypoints = FabricLoader.getInstance().getEntrypointContainers(key, Object.class);
            } catch (RuntimeException e) {
                continue;
            }
            for (EntrypointContainer<Object> entrypoint : entrypoints) {
                String entryClass;
                try {
                    entryClass = entrypoint.getEntrypoint().getClass().getName();
                } catch (RuntimeException e) {
                    continue;
                }
                int shared = sharedPackageSegments(className, entryClass);
                // At least three shared segments ("io.github.someone"), so "net.x" and "net.y" never match.
                if (shared >= 3 && shared > bestLength) {
                    bestLength = shared;
                    best = entrypoint.getProvider();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static int sharedPackageSegments(String firstClass, String secondClass) {
        String[] first = firstClass.split("\\.");
        String[] second = secondClass.split("\\.");
        int shared = 0;
        // The last segment is the class name itself, not part of the package.
        while (shared < first.length - 1 && shared < second.length - 1 && first[shared].equals(second[shared])) {
            shared++;
        }
        return shared;
    }
}
