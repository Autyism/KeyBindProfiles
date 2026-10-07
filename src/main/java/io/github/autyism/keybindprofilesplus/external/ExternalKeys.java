package io.github.autyism.keybindprofilesplus.external;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hotkeys that other mods manage themselves: Meteor Client (with its addons and macros), the
 * malilib family and Inventory Profiles Next. While such a mod runs, its hotkeys are taken from the mod itself and can be
 * changed here ({@link #bind}, {@link #setValue}, {@link #applyValues}); the mod then saves them with
 * its own save code. What cannot be reached that way (Meteor profiles that are not loaded, or a mod
 * version whose insides are unexpected) is read from the config files and stays read-only. Nothing
 * is read for a mod that is not installed.
 *
 * <p>In profiles these hotkeys are stored next to the game's key bindings under their
 * {@link ExternalBinding#hotkeyId() id}, which always starts with {@value #ID_PREFIX}.
 */
public final class ExternalKeys {
    public static final String ID_PREFIX = "ext:";
    /** How long a scan is reused before the mods (and files) are looked at again. */
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

        /** The running mods whose hotkeys can be changed (none for fixture files). */
        default List<LiveSource> liveSources() {
            return List.of();
        }
    }

    public record MalilibMod(String id, String name) {
    }

    /** Every external hotkey: all editable ones (also unbound), and the read-only ones that have a key. */
    public static synchronized List<ExternalBinding> all() {
        long now = System.nanoTime();
        if (!cached || now - cacheTime > CACHE_NANOS) {
            cache = List.copyOf(scan());
            cacheTime = now;
            cached = true;
        }
        return cache;
    }

    /** Only the hotkeys that are in effect and have a key (leaves out Meteor profiles that are not loaded). */
    public static List<ExternalBinding> active() {
        List<ExternalBinding> active = new ArrayList<>();
        for (ExternalBinding binding : all()) {
            if (binding.active() && !binding.unbound()) {
                active.add(binding);
            }
        }
        return active;
    }

    /** The editable hotkeys: id -> current value, in list order. What a profile saves of them. */
    public static Map<String, String> currentValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (ExternalBinding binding : all()) {
            if (binding.editable()) {
                values.put(binding.hotkeyId(), binding.value());
            }
        }
        return values;
    }

    /** The editable hotkey with this id, or null (mod not installed / not running). */
    public static ExternalBinding find(String hotkeyId) {
        for (ExternalBinding binding : all()) {
            if (hotkeyId.equals(binding.hotkeyId())) {
                return binding;
            }
        }
        return null;
    }

    /** Looks at the mods and files again on the next call, e.g. when a screen that shows them opens. */
    public static synchronized void refresh() {
        cached = false;
    }

    // ------------------------------------------------------------------ changes

    /**
     * Binds an editable hotkey to a key plus Ctrl / Shift / Alt ({@link InputConstants#UNKNOWN} = no
     * key) and lets its mod save. False when that mod does not take that key for it (Meteor modules
     * on the left / right mouse button) or the hotkey is not editable.
     */
    public static boolean bind(ExternalBinding binding, InputConstants.Key key, int modifiers) {
        LiveSource source = sourceOf(binding.hotkeyId());
        if (source == null || !source.bind(binding.hotkeyId(), key, modifiers & KeyCombo.ALL)) {
            return false;
        }
        source.save();
        refresh();
        return true;
    }

    /** Sets an editable hotkey to a value as {@link ExternalBinding#value()} gives it, and lets its mod save. */
    public static boolean setValue(ExternalBinding binding, String value) {
        return applyValues(Map.of(binding.hotkeyId(), value)) == 1;
    }

    /** Puts an editable hotkey back on its mod's default. */
    public static boolean reset(ExternalBinding binding) {
        return binding.defaultValue() != null && setValue(binding, binding.defaultValue());
    }

    /**
     * Sets every hotkey in {@code values} (id -> value; ids that are not external are ignored) whose
     * mod is running, then lets each mod save once. Returns how many were set.
     */
    public static int applyValues(Map<String, String> values) {
        int applied = 0;
        Set<LiveSource> touched = new LinkedHashSet<>();
        // Ids are only known to a source after it listed its hotkeys.
        all();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!isExternalId(entry.getKey())) {
                continue;
            }
            LiveSource source = sourceOf(entry.getKey());
            if (source != null && source.setValue(entry.getKey(), entry.getValue())) {
                touched.add(source);
                applied++;
            }
        }
        for (LiveSource source : touched) {
            source.save();
        }
        if (!touched.isEmpty()) {
            refresh();
        }
        return applied;
    }

    private static LiveSource sourceOf(String hotkeyId) {
        if (hotkeyId == null) {
            return null;
        }
        for (LiveSource source : environment.liveSources()) {
            if (source.owns(hotkeyId) && source.available()) {
                return source;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ values in profiles

    public static boolean isExternalId(String id) {
        return id != null && id.startsWith(ID_PREFIX);
    }

    /** Whether a stored value means "no key". */
    public static boolean isUnboundValue(String value) {
        return value == null || value.isBlank() || value.equals(InputConstants.UNKNOWN.getName());
    }

    /** A stored value of the hotkey with this id, formatted for display. Works without the mod running. */
    public static Component describeValue(String hotkeyId, String value) {
        if (isUnboundValue(value)) {
            return Component.translatable("key.keyboard.unknown");
        }
        if (hotkeyId.startsWith(MalilibLive.PREFIX) || hotkeyId.startsWith(IpnLive.PREFIX)) {
            // libIPN writes keys with the same names as malilib.
            MalilibKeys.Trigger trigger = MalilibKeys.parse(value);
            return trigger == null ? Component.translatable("key.keyboard.unknown") : trigger.text();
        }
        return KeyCombo.describe(value);
    }

    /** What the hotkey with this id is called: its mod's name for it when running, else worked out from the id. */
    public static Component nameOf(String hotkeyId) {
        ExternalBinding binding = find(hotkeyId);
        if (binding != null) {
            return binding.title();
        }
        String rest = hotkeyId.substring(hotkeyId.indexOf(':', ID_PREFIX.length()) + 1);
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            rest = rest.substring(0, hash);
        }
        StringBuilder name = new StringBuilder();
        for (String part : rest.split("/")) {
            if (!name.isEmpty()) {
                name.append(" / ");
            }
            name.append(hotkeyId.startsWith(MeteorLive.PREFIX) || hotkeyId.startsWith(MeteorLive.MACRO_PREFIX)
                    ? MeteorKeys.title(part) : MalilibKeys.readableName(part));
        }
        return Component.literal(name.toString());
    }

    /** The heading the hotkey with this id is listed under ("Meteor", "Litematica"), also when its mod is not running. */
    public static Component groupOf(String hotkeyId) {
        ExternalBinding binding = find(hotkeyId);
        if (binding != null) {
            return binding.group();
        }
        if (hotkeyId.startsWith(MeteorLive.MACRO_PREFIX)) {
            return Component.translatable("keybindprofilesplus.external.meteor_macros");
        }
        if (hotkeyId.startsWith(MeteorLive.PREFIX)) {
            return Component.translatable("keybindprofilesplus.external.meteor");
        }
        if (hotkeyId.startsWith(IpnLive.PREFIX)) {
            return Component.literal(FabricLoader.getInstance().getModContainer(IpnLive.MOD_ID).map(mod -> mod.getMetadata().getName()).orElse("Inventory Profiles Next"));
        }
        if (hotkeyId.startsWith(MalilibLive.PREFIX)) {
            String rest = hotkeyId.substring(MalilibLive.PREFIX.length());
            int slash = rest.indexOf('/');
            String modId = slash < 0 ? rest : rest.substring(0, slash);
            return Component.literal(FabricLoader.getInstance().getModContainer(modId).map(mod -> mod.getMetadata().getName()).orElse(modId));
        }
        return Component.literal(hotkeyId);
    }

    // ------------------------------------------------------------------ self-test hooks

    /** For the self-test: what the malilib reader makes of a hotkey whose file says nothing about its context. */
    public static ExternalBinding.When malilibWhen(String modId, String name, boolean bareModifier, boolean bareMouseClick) {
        return MalilibKeys.when(modId, name, null, bareModifier, bareMouseClick);
    }

    /** For the self-test: use this environment instead of the real one (null restores it). */
    public static synchronized void setEnvironmentForTesting(Environment testEnvironment) {
        environment = testEnvironment == null ? new LoadedMods() : testEnvironment;
        cached = false;
    }

    /** For the self-test: whether hotkeys of this kind ("malilib" / "meteor" / "ipn") are read from the running mod. */
    public static boolean isLive(String kind) {
        for (LiveSource source : environment.liveSources()) {
            boolean matches = switch (kind) {
                case "malilib" -> source.coversMalilib();
                case "meteor" -> source.coversMeteor();
                default -> source instanceof IpnLive;
            };
            if (source.available() && matches) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ scanning

    private static List<ExternalBinding> scan() {
        List<ExternalBinding> bindings = new ArrayList<>();
        boolean liveMeteor = false;
        boolean liveMalilib = false;
        for (LiveSource source : environment.liveSources()) {
            if (source.available()) {
                bindings.addAll(source.list());
                liveMeteor |= source.coversMeteor();
                liveMalilib |= source.coversMalilib();
            }
        }

        Path gameDirectory = environment.gameDirectory();
        if (gameDirectory == null) {
            return bindings;
        }
        if (environment.hasMeteor()) {
            // Meteor profiles that are not loaded are only in their files; so is everything when Meteor could not be reached.
            bindings.addAll(MeteorKeys.read(gameDirectory, !liveMeteor));
        }
        if (!liveMalilib) {
            for (MalilibMod mod : environment.malilibMods()) {
                bindings.addAll(MalilibKeys.read(gameDirectory, mod.id(), mod.name()));
            }
        }
        return bindings;
    }

    private static final class LoadedMods implements Environment {
        private final List<LiveSource> live = createLiveSources();

        private static List<LiveSource> createLiveSources() {
            List<LiveSource> sources = new ArrayList<>();
            if (FabricLoader.getInstance().isModLoaded(MeteorKeys.MOD_ID)) {
                sources.add(new MeteorLive());
            }
            if (FabricLoader.getInstance().isModLoaded(MalilibKeys.LIBRARY_ID)) {
                sources.add(new MalilibLive());
            }
            if (FabricLoader.getInstance().isModLoaded(IpnLive.MOD_ID)) {
                sources.add(new IpnLive());
            }
            return List.copyOf(sources);
        }

        @Override
        public Path gameDirectory() {
            return FabricLoader.getInstance().getGameDir();
        }

        @Override
        public boolean hasMeteor() {
            return FabricLoader.getInstance().isModLoaded(MeteorKeys.MOD_ID);
        }

        @Override
        public List<LiveSource> liveSources() {
            return live;
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
