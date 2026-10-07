package io.github.autyism.keybindprofilesplus.keys;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Works out which mod registered a key binding. Minecraft does not record this, so it is deduced:
 * <ol>
 *   <li>bindings held in a field of {@link Options} are vanilla;</li>
 *   <li>otherwise the mod whose code created the binding (see {@link KeyOrigins});</li>
 *   <li>otherwise the namespace of the binding's category names the mod, if such a mod is loaded;</li>
 *   <li>otherwise a part of the binding's id (e.g. {@code key.<modid>.open}) that is a loaded mod id;</li>
 *   <li>otherwise the source is unknown.</li>
 * </ol>
 */
public final class KeySourceResolver {
    private static final Set<String> IGNORED_MOD_IDS = Set.of("minecraft", "java");

    private final Set<String> vanillaIds = new HashSet<>();
    private final Map<String, ModContainer> modsByNormalizedId = new HashMap<>();
    private final Map<String, KeySource> cache = new HashMap<>();

    public KeySourceResolver(Options options) {
        collectVanillaIds(options);
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (!IGNORED_MOD_IDS.contains(id)) {
                modsByNormalizedId.put(normalize(id), mod);
            }
        }
    }

    public KeySource resolve(KeyMapping binding) {
        return cache.computeIfAbsent(binding.getName(), id -> resolveUncached(binding));
    }

    public boolean isVanilla(String bindingId) {
        return vanillaIds.contains(bindingId);
    }

    private KeySource resolveUncached(KeyMapping binding) {
        if (vanillaIds.contains(binding.getName())) {
            return KeySource.VANILLA;
        }

        Optional<ModContainer> creator = KeyOrigins.modOf(binding);
        if (creator.isPresent()) {
            return toSource(creator.get());
        }

        // Fallbacks for bindings whose creator could not be traced: go by how they are named.
        String namespace = binding.getCategory().id().getNamespace();
        ModContainer byNamespace = modsByNormalizedId.get(normalize(namespace));
        if (byNamespace != null) {
            return toSource(byNamespace);
        }

        for (String part : binding.getName().split("[.:/]")) {
            ModContainer byIdPart = modsByNormalizedId.get(normalize(part));
            if (byIdPart != null) {
                return toSource(byIdPart);
            }
        }

        return KeySource.unknown("minecraft".equals(namespace) ? null : namespace);
    }

    private static KeySource toSource(ModContainer mod) {
        return KeySource.mod(mod.getMetadata().getId(), mod.getMetadata().getName());
    }

    /** Every KeyMapping stored in a field of Options (single or in an array) is a vanilla one. */
    private void collectVanillaIds(Options options) {
        for (Field field : Options.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(options);
                if (value instanceof KeyMapping binding) {
                    vanillaIds.add(binding.getName());
                } else if (value instanceof KeyMapping[] bindings && bindings != options.keyMappings) {
                    // allKeys is skipped: Fabric appends the mods' bindings to it.
                    for (KeyMapping binding : bindings) {
                        if (binding != null) {
                            vanillaIds.add(binding.getName());
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A field we cannot read just means fewer bindings recognised as vanilla.
            }
        }
    }

    private static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
