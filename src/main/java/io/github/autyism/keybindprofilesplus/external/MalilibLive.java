package io.github.autyism.keybindprofilesplus.external;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The hotkeys of every malilib mod (Litematica, Tweakeroo, MiniHUD, Item Scroller...), taken from
 * malilib's own list of them - the one behind its "all hotkeys" overview - while the game runs.
 *
 * <p>malilib is reached by reflection on its own class and method names: they are not renamed
 * between environments, and this mod builds and runs without malilib. A change sets the keys of the
 * mod's own hotkey object, makes malilib rebuild its key lookup, and has the mod save its config
 * the way malilib's config screens do when they close ({@code ConfigManager.onConfigsChanged}).
 *
 * <p>Value form (what profiles store): malilib's own key text, e.g. {@code LEFT_CONTROL,X}; empty for no key.
 */
final class MalilibLive implements LiveSource {
    static final String PREFIX = "ext:malilib:";
    private static final int MALILIB_MOUSE_OFFSET = -100;

    private boolean reflected;
    private boolean broken;
    private Method getKeybindManager;
    private Method getKeybindCategories;
    private Method updateUsedKeys;
    private Method categoryModName;
    private Method categoryHotkeys;
    private Method hotkeyName;
    private Method hotkeyPrettyName;
    private Method hotkeyKeybind;
    private Method keybindValue;
    private Method keybindDefault;
    private Method keybindSetValue;
    private Method keybindClear;
    private Method keybindAdd;
    private Method keybindSettings;
    private Method settingsContext;
    private Method configManagerInstance;
    private Method configsChanged;
    private Method saveAllConfigs;
    private Method modIdSet;

    /** Hotkey id -> malilib keybind object and the id of the mod it belongs to, from the last {@link #list()}. */
    private final Map<String, Object> keybinds = new HashMap<>();
    private final Map<String, String> modOf = new HashMap<>();
    /** Mods with changes not saved yet (null = a mod whose id could not be told: save them all). */
    private final Set<String> unsaved = new LinkedHashSet<>();

    @Override
    public boolean available() {
        return FabricLoader.getInstance().isModLoaded(MalilibKeys.LIBRARY_ID) && reflect();
    }

    @Override
    public boolean coversMalilib() {
        return true;
    }

    @Override
    public boolean owns(String hotkeyId) {
        return hotkeyId.startsWith(PREFIX);
    }

    @Override
    public List<ExternalBinding> list() {
        List<ExternalBinding> out = new ArrayList<>();
        if (!available()) {
            return out;
        }
        keybinds.clear();
        modOf.clear();
        Map<String, String> modIds = modIdsByName();
        try {
            Set<?> registered = registeredConfigs();
            Object manager = getKeybindManager.invoke(null);
            for (Object category : (Collection<?>) getKeybindCategories.invoke(manager)) {
                String modName = String.valueOf(categoryModName.invoke(category));
                String modId = modIds.getOrDefault(normalize(modName), normalize(modName));
                Component group = Component.literal(modName);
                for (Object hotkey : (Collection<?>) categoryHotkeys.invoke(category)) {
                    add(hotkey, modId, registered.contains(modId), group, out);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("list the malilib hotkeys", e);
        }
        return out;
    }

    /**
     * @param knownMod whether the mod registered its config under this id, so that exactly its config can be saved
     */
    private void add(Object hotkey, String modId, boolean knownMod, Component group, List<ExternalBinding> out)
            throws ReflectiveOperationException {
        String rawName = String.valueOf(hotkeyName.invoke(hotkey));
        Object keybind = hotkeyKeybind.invoke(hotkey);
        if (keybind == null) {
            return;
        }
        String id = PREFIX + modId + "/" + rawName;
        for (int n = 2; keybinds.containsKey(id); n++) {
            id = PREFIX + modId + "/" + rawName + "#" + n;
        }
        keybinds.put(id, keybind);
        modOf.put(id, knownMod ? modId : null);

        String value = String.valueOf(keybindValue.invoke(keybind));
        String defaultValue = String.valueOf(keybindDefault.invoke(keybind));
        String context = null;
        Object settings = keybindSettings.invoke(keybind);
        if (settings != null) {
            Object contextValue = settingsContext.invoke(settings);
            context = contextValue instanceof Enum<?> e ? e.name() : null;
        }

        MalilibKeys.Trigger trigger = MalilibKeys.parse(value);
        boolean bareModifier = trigger != null && trigger.bareModifier();
        boolean bareMouseClick = trigger != null && trigger.bareMouseClick();
        ExternalBinding.When when = MalilibKeys.when(modId, rawName, context, bareModifier, bareMouseClick);
        String name = MalilibKeys.readableName(rawName);
        Component title = Component.literal(prettyName(hotkey, name));
        Component keyText = trigger == null ? Component.translatable("key.keyboard.unknown") : trigger.text();
        out.add(new ExternalBinding(modId, group, name, title, trigger == null ? 0 : trigger.modifiers(), trigger == null ? null : trigger.key(),
                keyText, when, true, "config/" + modId + ".json", id, value, defaultValue));
    }

    private String prettyName(Object hotkey, String fallback) {
        if (hotkeyPrettyName == null) {
            return fallback;
        }
        try {
            String pretty = String.valueOf(hotkeyPrettyName.invoke(hotkey));
            return pretty.isBlank() ? fallback : pretty;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    @Override
    public boolean bind(String hotkeyId, InputConstants.Key key, int modifiers) {
        Object keybind = keybinds.get(hotkeyId);
        if (keybind == null || !available()) {
            return false;
        }
        try {
            keybindClear.invoke(keybind);
            if (key != null && !key.equals(InputConstants.UNKNOWN)) {
                boolean isModifierKey = key.getType() == InputConstants.Type.KEYSYM && KeyCombo.modifierOfKeyCode(key.getValue()) != 0;
                if (!isModifierKey) {
                    // Written the way malilib's own key recorder writes a combination: modifiers first.
                    if ((modifiers & KeyCombo.CTRL) != 0) {
                        keybindAdd.invoke(keybind, InputConstants.KEY_LCONTROL);
                    }
                    if ((modifiers & KeyCombo.SHIFT) != 0) {
                        keybindAdd.invoke(keybind, InputConstants.KEY_LSHIFT);
                    }
                    if ((modifiers & KeyCombo.ALT) != 0) {
                        keybindAdd.invoke(keybind, InputConstants.KEY_LALT);
                    }
                }
                int code = key.getType() == InputConstants.Type.MOUSE ? key.getValue() + MALILIB_MOUSE_OFFSET : key.getValue();
                keybindAdd.invoke(keybind, code);
            }
            changed(hotkeyId);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("change a malilib hotkey", e);
            return false;
        }
    }

    @Override
    public boolean setValue(String hotkeyId, String value) {
        Object keybind = keybinds.get(hotkeyId);
        if (keybind == null || value == null || !available()) {
            return false;
        }
        try {
            keybindSetValue.invoke(keybind, value);
            changed(hotkeyId);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("change a malilib hotkey", e);
            return false;
        }
    }

    private void changed(String hotkeyId) throws ReflectiveOperationException {
        // malilib finds the hotkeys of a key press through a lookup built from all keys; it has to be rebuilt.
        updateUsedKeys.invoke(getKeybindManager.invoke(null));
        unsaved.add(modOf.get(hotkeyId));
    }

    @Override
    public void save() {
        if (unsaved.isEmpty() || !available()) {
            return;
        }
        try {
            Object configManager = configManagerInstance.invoke(null);
            if (unsaved.contains(null)) {
                saveAllConfigs.invoke(configManager);
            } else {
                for (String modId : unsaved) {
                    configsChanged.invoke(configManager, modId);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("save the malilib configs", e);
        }
        unsaved.clear();
    }

    /** The ids of the mods whose config malilib saves (empty if that cannot be told: then everything is saved). */
    private Set<?> registeredConfigs() {
        try {
            return modIdSet == null ? Set.of() : (Set<?>) modIdSet.invoke(configManagerInstance.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Set.of();
        }
    }

    /** Every loaded mod: normalized display name (and id) -> mod id. */
    private Map<String, String> modIdsByName() {
        Map<String, String> ids = new LinkedHashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            ids.putIfAbsent(normalize(mod.getMetadata().getName()), id);
            ids.putIfAbsent(normalize(id), id);
        }
        return ids;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean reflect() {
        if (reflected) {
            return !broken;
        }
        reflected = true;
        try {
            Class<?> inputHandler = Class.forName("fi.dy.masa.malilib.event.InputEventHandler");
            Class<?> keybindManager = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybindManager");
            Class<?> category = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindCategory");
            Class<?> hotkey = Class.forName("fi.dy.masa.malilib.hotkeys.IHotkey");
            Class<?> keybind = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybind");
            Class<?> settings = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindSettings");
            Class<?> configManager = Class.forName("fi.dy.masa.malilib.config.ConfigManager");
            Class<?> configManagerApi = Class.forName("fi.dy.masa.malilib.config.IConfigManager");
            getKeybindManager = inputHandler.getMethod("getKeybindManager");
            getKeybindCategories = keybindManager.getMethod("getKeybindCategories");
            updateUsedKeys = keybindManager.getMethod("updateUsedKeys");
            categoryModName = category.getMethod("getModName");
            categoryHotkeys = category.getMethod("getHotkeys");
            hotkeyName = hotkey.getMethod("getName");
            hotkeyPrettyName = optional(hotkey, "getPrettyName");
            hotkeyKeybind = hotkey.getMethod("getKeybind");
            keybindValue = keybind.getMethod("getStringValue");
            keybindDefault = keybind.getMethod("getDefaultStringValue");
            keybindSetValue = keybind.getMethod("setValueFromString", String.class);
            keybindClear = keybind.getMethod("clearKeys");
            keybindAdd = keybind.getMethod("addKey", int.class);
            keybindSettings = keybind.getMethod("getSettings");
            settingsContext = settings.getMethod("getContext");
            configManagerInstance = configManager.getMethod("getInstance");
            configsChanged = configManagerApi.getMethod("onConfigsChanged", String.class);
            saveAllConfigs = configManager.getMethod("saveAllConfigs");
            modIdSet = optional(configManager, "modIdSet");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            KeyBindProfilesPlus.LOGGER.warn("malilib's hotkeys cannot be reached (unexpected malilib version?); they are read from its files instead: {}", e.toString());
        }
        return !broken;
    }

    private static Method optional(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private void fail(String what, Exception e) {
        Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : e;
        KeyBindProfilesPlus.LOGGER.error("Could not {}", what, cause);
    }
}
