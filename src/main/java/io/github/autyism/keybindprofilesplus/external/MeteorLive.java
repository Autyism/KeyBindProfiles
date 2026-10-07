package io.github.autyism.keybindprofilesplus.external;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The key binds of Meteor Client as Meteor has them while the game runs: every module's own bind
 * (also of Meteor addons, which register their modules with Meteor), every key bind setting inside
 * a module, and every macro.
 *
 * <p>Meteor is reached by reflection on its own class, field and method names, which are the same
 * in every environment; this mod builds and runs without Meteor. A change sets Meteor's own key bind
 * object and lets Meteor save its "modules" / "macros" system the way Meteor itself does.
 *
 * <p>Value form (what profiles store): the same text as for game key bindings, {@code ctrl+key.keyboard.x}
 * or {@code key.mouse.4}; {@code key.keyboard.unknown} for no key.
 */
final class MeteorLive implements LiveSource {
    static final String PREFIX = "ext:meteor:";
    static final String MACRO_PREFIX = "ext:meteor-macro:";
    private static final String MODULES_FILE = "meteor-client/modules.nbt";
    private static final String MACROS_FILE = "meteor-client/macros.nbt";
    private static final int GLFW_MOD_SUPER = 8;
    private static final int UNBOUND = -1;

    private boolean reflected;
    private boolean broken;
    private Class<?> keybindSettingClass;
    private Method modulesGet;
    private Method modulesGetAll;
    private Method macrosGet;
    private Method macrosGetAll;
    private Method systemSave;
    private Field moduleName;
    private Field moduleTitle;
    private Field moduleKeybind;
    private Field moduleSettings;
    private Field moduleAddon;
    private Field addonName;
    private Field settingName;
    private Field settingTitle;
    private Method settingGet;
    private Method settingDefault;
    private Method settingOnChanged;
    private Field macroName;
    private Field macroKeybind;
    private Method keybindIsKey;
    private Method keybindValue;
    private Field keybindModifiers;
    private Method keybindSet;
    private Object coreAddon;

    /** Hotkey id -> what it is, from the last {@link #list()}. */
    private final Map<String, Target> targets = new HashMap<>();
    private boolean modulesChanged;
    private boolean macrosChanged;

    /**
     * @param keybind  Meteor's key bind object
     * @param setting  the key bind setting holding it (to tell Meteor it changed), or null for a module's own bind
     * @param macro    whether it belongs to a macro (saved with the macros, not the modules)
     */
    private record Target(Object keybind, Object setting, boolean macro, boolean moduleBind) {
    }

    @Override
    public boolean available() {
        if (!FabricLoader.getInstance().isModLoaded(MeteorKeys.MOD_ID) || !reflect()) {
            return false;
        }
        try {
            // Null until Meteor has set up its systems.
            return modulesGet.invoke(null) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean coversMeteor() {
        return true;
    }

    @Override
    public boolean owns(String hotkeyId) {
        return hotkeyId.startsWith(PREFIX) || hotkeyId.startsWith(MACRO_PREFIX);
    }

    @Override
    public List<ExternalBinding> list() {
        List<ExternalBinding> out = new ArrayList<>();
        if (!available()) {
            return out;
        }
        targets.clear();
        try {
            Object modules = modulesGet.invoke(null);
            List<Object> all = new ArrayList<>((Collection<?>) modulesGetAll.invoke(modules));
            all.sort((first, second) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(get(moduleName, first)), String.valueOf(get(moduleName, second))));
            for (Object module : all) {
                addModule(module, out);
            }
            if (macrosGet != null) {
                Object macros = macrosGet.invoke(null);
                if (macros != null) {
                    for (Object macro : (Collection<?>) macrosGetAll.invoke(macros)) {
                        addMacro(macro, out);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("list the Meteor key binds", e);
        }
        return out;
    }

    private void addModule(Object module, List<ExternalBinding> out) throws ReflectiveOperationException {
        String rawName = String.valueOf(get(moduleName, module));
        String title = String.valueOf(get(moduleTitle, module));
        Component group = groupOf(module);
        String readable = MeteorKeys.title(rawName);

        Object keybind = get(moduleKeybind, module);
        if (keybind != null) {
            String id = unique(PREFIX + rawName);
            targets.put(id, new Target(keybind, null, false, true));
            out.add(binding(id, readable, Component.literal(title), group, keybind, KeyCombo.encode(0, InputConstants.UNKNOWN.getName()),
                    ExternalBinding.When.IN_GAME, MODULES_FILE));
        }

        Object settings = get(moduleSettings, module);
        if (!(settings instanceof Iterable<?> groups)) {
            return;
        }
        for (Object settingGroup : groups) {
            if (!(settingGroup instanceof Iterable<?> members)) {
                continue;
            }
            for (Object setting : members) {
                if (!keybindSettingClass.isInstance(setting)) {
                    continue;
                }
                Object value = settingGet.invoke(setting);
                if (value == null) {
                    continue;
                }
                String settingRaw = String.valueOf(get(settingName, setting));
                // Two key bind settings of one module may share a name (in different setting groups).
                String id = unique(PREFIX + rawName + "/" + settingRaw);
                targets.put(id, new Target(value, setting, false, false));
                Object defaultValue = settingDefault.invoke(setting);
                Component settingTitleText = Component.literal(title + " / " + get(settingTitle, setting));
                // A key inside a module's settings only does something within that module's own feature.
                out.add(binding(id, readable + " / " + MeteorKeys.title(settingRaw), settingTitleText, group, value,
                        defaultValue == null ? null : encode(defaultValue), ExternalBinding.When.SITUATIONAL, MODULES_FILE));
            }
        }
    }

    private void addMacro(Object macro, List<ExternalBinding> out) throws ReflectiveOperationException {
        Object nameSetting = get(macroName, macro);
        Object keybindSetting = get(macroKeybind, macro);
        if (nameSetting == null || keybindSetting == null) {
            return;
        }
        String name = String.valueOf(settingGet.invoke(nameSetting));
        Object keybind = settingGet.invoke(keybindSetting);
        if (keybind == null) {
            return;
        }
        String id = unique(MACRO_PREFIX + name);
        targets.put(id, new Target(keybind, keybindSetting, true, false));
        out.add(binding(id, name, Component.literal(name), Component.translatable("keybindprofilesplus.external.meteor_macros"), keybind,
                KeyCombo.encode(0, InputConstants.UNKNOWN.getName()), ExternalBinding.When.IN_GAME, MACROS_FILE));
    }

    /** The id itself, or with "#2", "#3"... when it is taken already in this listing. */
    private String unique(String id) {
        String unique = id;
        for (int n = 2; targets.containsKey(unique); n++) {
            unique = id + "#" + n;
        }
        return unique;
    }

    private ExternalBinding binding(String id, String name, Component title, Component group, Object keybind, String defaultValue,
                                    ExternalBinding.When when, String file) throws ReflectiveOperationException {
        String value = encode(keybind);
        KeyCombo combo = KeyCombo.parse(value);
        InputConstants.Key key = ExternalKeys.isUnboundValue(value) ? null : combo.inputKey();
        Component keyText = key == null ? Component.translatable("key.keyboard.unknown") : combo.displayText();
        int modifiers = key == null ? 0 : combo.modifiers();
        if (key != null && (modifiersOf(keybind) & GLFW_MOD_SUPER) != 0) {
            // The Windows / Command key is not something a game key binding can ask for: shown, but not compared.
            keyText = Component.literal("Super + ").append(keyText);
            key = null;
            modifiers = 0;
        }
        return new ExternalBinding("meteor", group, name, title, modifiers, key, keyText, when, true, file, id, value, defaultValue);
    }

    private Component groupOf(Object module) {
        Object addon = get(moduleAddon, module);
        if (addon == null || addon == coreAddon || addonName == null) {
            return Component.translatable("keybindprofilesplus.external.meteor");
        }
        Object name = get(addonName, addon);
        return name == null ? Component.translatable("keybindprofilesplus.external.meteor") : Component.translatable("keybindprofilesplus.external.meteor_addon", String.valueOf(name));
    }

    /** Meteor's key bind object -> the value form ("ctrl+key.keyboard.x"). */
    private String encode(Object keybind) throws ReflectiveOperationException {
        boolean isKey = (Boolean) keybindIsKey.invoke(keybind);
        int value = (Integer) keybindValue.invoke(keybind);
        if (value == UNBOUND || (isKey && value == 0)) {
            return InputConstants.UNKNOWN.getName();
        }
        InputConstants.Key key = (isKey ? InputConstants.Type.KEYSYM : InputConstants.Type.MOUSE).getOrCreate(value);
        int modifiers = isKey ? modifiersOf(keybind) & KeyCombo.ALL : 0;
        if (key.getType() == InputConstants.Type.KEYSYM) {
            // A modifier key as the key itself reports its own bit.
            modifiers &= ~KeyCombo.modifierOfKeyCode(key.getValue());
        }
        //? if >=26.3 {
        /*return KeyCombo.encode(modifiers, io.github.autyism.keybindprofilesplus.input.SdlKeys.toStoredName(key.getName()));
        *///?} else
        return KeyCombo.encode(modifiers, key.getName());
    }

    private int modifiersOf(Object keybind) {
        Object modifiers = get(keybindModifiers, keybind);
        return modifiers instanceof Integer bits ? bits : 0;
    }

    @Override
    public boolean bind(String hotkeyId, InputConstants.Key key, int modifiers) {
        Target target = targets.get(hotkeyId);
        if (target == null || !available()) {
            return false;
        }
        boolean none = key == null || key.equals(InputConstants.UNKNOWN);
        boolean isKey = none || key.getType() != InputConstants.Type.MOUSE;
        int value = none ? UNBOUND : key.getValue();
        // Meteor's own key recorder never binds a module to the left or right mouse button (that would fire on every attack / use).
        if (!none && !isKey && value <= 1 && target.moduleBind()) {
            return false;
        }
        int bits = none || !isKey ? 0 : modifiers & KeyCombo.ALL;
        if (!none && isKey) {
            bits &= ~KeyCombo.modifierOfKeyCode(value);
        }
        try {
            keybindSet.invoke(target.keybind(), isKey, value, bits);
            if (target.setting() != null && settingOnChanged != null) {
                settingOnChanged.invoke(target.setting());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("change a Meteor key bind", e);
            return false;
        }
        if (target.macro()) {
            macrosChanged = true;
        } else {
            modulesChanged = true;
        }
        return true;
    }

    @Override
    public boolean setValue(String hotkeyId, String value) {
        if (value == null) {
            return false;
        }
        if (ExternalKeys.isUnboundValue(value)) {
            return bind(hotkeyId, InputConstants.UNKNOWN, 0);
        }
        KeyCombo combo = KeyCombo.parse(value);
        InputConstants.Key key = combo.inputKey();
        return key != null && bind(hotkeyId, key, combo.modifiers());
    }

    @Override
    public void save() {
        if (!available()) {
            return;
        }
        try {
            if (modulesChanged) {
                systemSave.invoke(modulesGet.invoke(null));
            }
            if (macrosChanged && macrosGet != null) {
                systemSave.invoke(macrosGet.invoke(null));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("save Meteor's key binds", e);
        }
        modulesChanged = false;
        macrosChanged = false;
    }

    private boolean reflect() {
        if (reflected) {
            return !broken;
        }
        reflected = true;
        try {
            Class<?> modulesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
            Class<?> moduleClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Module");
            Class<?> systemClass = Class.forName("meteordevelopment.meteorclient.systems.System");
            Class<?> settingClass = Class.forName("meteordevelopment.meteorclient.settings.Setting");
            Class<?> keybindClass = Class.forName("meteordevelopment.meteorclient.utils.misc.Keybind");
            keybindSettingClass = Class.forName("meteordevelopment.meteorclient.settings.KeybindSetting");
            modulesGet = modulesClass.getMethod("get");
            modulesGetAll = modulesClass.getMethod("getAll");
            systemSave = systemClass.getMethod("save");
            moduleName = moduleClass.getField("name");
            moduleTitle = moduleClass.getField("title");
            moduleKeybind = moduleClass.getField("keybind");
            moduleSettings = moduleClass.getField("settings");
            moduleAddon = optionalField(moduleClass, "addon");
            settingName = settingClass.getField("name");
            settingTitle = settingClass.getField("title");
            settingGet = settingClass.getMethod("get");
            settingDefault = settingClass.getMethod("getDefaultValue");
            settingOnChanged = optionalMethod(settingClass, "onChanged");
            keybindIsKey = keybindClass.getMethod("isKey");
            keybindValue = keybindClass.getMethod("getValue");
            keybindModifiers = keybindClass.getDeclaredField("modifiers");
            keybindModifiers.setAccessible(true);
            keybindSet = keybindClass.getMethod("set", boolean.class, int.class, int.class);
            try {
                Class<?> addonClass = Class.forName("meteordevelopment.meteorclient.addons.MeteorAddon");
                addonName = optionalField(addonClass, "name");
                Field core = optionalField(Class.forName("meteordevelopment.meteorclient.MeteorClient"), "ADDON");
                coreAddon = core == null ? null : core.get(null);
            } catch (ReflectiveOperationException | LinkageError e) {
                addonName = null;
            }
            try {
                Class<?> macrosClass = Class.forName("meteordevelopment.meteorclient.systems.macros.Macros");
                Class<?> macroClass = Class.forName("meteordevelopment.meteorclient.systems.macros.Macro");
                macrosGet = macrosClass.getMethod("get");
                macrosGetAll = macrosClass.getMethod("getAll");
                macroName = macroClass.getField("name");
                macroKeybind = macroClass.getField("keybind");
            } catch (ReflectiveOperationException | LinkageError e) {
                macrosGet = null;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            KeyBindProfilesPlus.LOGGER.warn("Meteor's key binds cannot be reached (unexpected Meteor version?); they are read from its files instead: {}", e.toString());
        }
        return !broken;
    }

    private static Field optionalField(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Method optionalMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object get(Field field, Object owner) {
        if (field == null || owner == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private void fail(String what, Exception e) {
        Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : e;
        KeyBindProfilesPlus.LOGGER.error("Could not {}", what, cause);
    }
}
