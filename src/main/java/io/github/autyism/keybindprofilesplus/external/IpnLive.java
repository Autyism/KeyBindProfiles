package io.github.autyism.keybindprofilesplus.external;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hotkeys of Inventory Profiles Next (sort, move all, throw all, profiles...), which it keeps in
 * its own library libIPN rather than in the game's key bindings. Taken from IPN's config screen
 * settings while the game runs, changed in IPN's own hotkey objects and saved by IPN's own config
 * saver.
 *
 * <p>Reached by reflection on IPN's / libIPN's own names (not renamed between environments). Only
 * the main key of a hotkey is handled; IPN's optional "alternative" keys are left as they are.
 *
 * <p>Value form (what profiles store): libIPN's key text, which uses the same names as malilib
 * ({@code LEFT_CONTROL,R}); empty for no key.
 */
final class IpnLive implements LiveSource {
    static final String MOD_ID = "inventoryprofilesnext";
    static final String PREFIX = "ext:ipn:";
    private static final String FILE = "config/inventoryprofilesnext/inventoryprofiles.json";
    //? if >=26.3 {
    /*// libIPN 26.3+ counts from the SDL button numbers: its left button (-100) is SDL button 1
    private static final int LIBIPN_MOUSE_OFFSET = -100 - InputConstants.MOUSE_BUTTON_LEFT;
    *///?} else
    private static final int LIBIPN_MOUSE_OFFSET = -100;

    private boolean reflected;
    private boolean broken;
    private Object screenSettings;
    private Class<?> hotkeyClass;
    private Method declarations;
    private Method declarationBuilder;
    private Method builderInnerConfig;
    private Method innerCategories;
    private Method pairSecond;
    private Method optionKey;
    private Method mainKeybind;
    private Method keyCodes;
    private Method setKeyCodes;
    private Method defaultKeyCodes;
    private Method keybindSettings;
    private Method settingsContext;
    private Object companion;
    private Method storageString;
    private Method parseStorage;
    private Method saveLoadManager;
    private Method save;
    private String optionsPrefix = "inventoryprofiles.config.";

    private final Map<String, Object> keybinds = new HashMap<>();
    private boolean changed;

    @Override
    public boolean available() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID) && reflect();
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
        Component group = Component.literal(FabricLoader.getInstance().getModContainer(MOD_ID).map(mod -> mod.getMetadata().getName()).orElse("Inventory Profiles Next"));
        try {
            for (Object declaration : (Collection<?>) declarations.invoke(screenSettings)) {
                Object inner = builderInnerConfig.invoke(declarationBuilder.invoke(declaration));
                for (Object category : (Collection<?>) innerCategories.invoke(inner)) {
                    for (Object option : (Collection<?>) pairSecond.invoke(category)) {
                        if (hotkeyClass.isInstance(option)) {
                            add(option, group, out);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("list the Inventory Profiles Next hotkeys", e);
        }
        return out;
    }

    private void add(Object hotkey, Component group, List<ExternalBinding> out) throws ReflectiveOperationException {
        String key = String.valueOf(optionKey.invoke(hotkey));
        Object keybind = mainKeybind.invoke(hotkey);
        String id = PREFIX + key;
        if (keybind == null || keybinds.containsKey(id)) {
            return;
        }
        keybinds.put(id, keybind);
        String value = String.valueOf(storageString.invoke(companion, keyCodes.invoke(keybind)));
        String defaultValue = String.valueOf(storageString.invoke(companion, defaultKeyCodes.invoke(keybind)));
        String context = null;
        Object settings = keybindSettings.invoke(keybind);
        if (settings != null) {
            Object contextValue = settingsContext.invoke(settings);
            context = contextValue instanceof Enum<?> e ? e.name() : null;
        }
        MalilibKeys.Trigger trigger = MalilibKeys.parse(value);
        ExternalBinding.When when = "GUI".equals(context) ? ExternalBinding.When.SCREEN_ONLY
                : "ANY".equals(context) ? ExternalBinding.When.ANYWHERE : ExternalBinding.When.IN_GAME;
        if (trigger != null && trigger.bareModifier()) {
            when = ExternalBinding.When.SITUATIONAL;
        }
        String name = MalilibKeys.readableName(key);
        String translationKey = optionsPrefix + "name." + key;
        //? if >=26.2 {
        /*Component title = net.minecraft.locale.Language.getInstance().has(translationKey) ? Component.translatable(translationKey) : Component.literal(name);
        *///?} else
        Component title = I18n.exists(translationKey) ? Component.translatable(translationKey) : Component.literal(name);
        Component keyText = trigger == null ? Component.translatable("key.keyboard.unknown") : trigger.text();
        out.add(new ExternalBinding(MOD_ID, group, name, title, trigger == null ? 0 : trigger.modifiers(), trigger == null ? null : trigger.key(),
                keyText, when, true, FILE, id, value, defaultValue));
    }

    @Override
    public boolean bind(String hotkeyId, InputConstants.Key key, int modifiers) {
        Object keybind = keybinds.get(hotkeyId);
        if (keybind == null || !available()) {
            return false;
        }
        List<Integer> codes = new ArrayList<>();
        if (key != null && !key.equals(InputConstants.UNKNOWN)) {
            boolean isModifierKey = key.getType() == InputConstants.Type.KEYSYM && KeyCombo.modifierOfKeyCode(key.getValue()) != 0;
            if (!isModifierKey) {
                if ((modifiers & KeyCombo.CTRL) != 0) {
                    codes.add(InputConstants.KEY_LCONTROL);
                }
                if ((modifiers & KeyCombo.SHIFT) != 0) {
                    codes.add(InputConstants.KEY_LSHIFT);
                }
                if ((modifiers & KeyCombo.ALT) != 0) {
                    codes.add(InputConstants.KEY_LALT);
                }
            }
            codes.add(key.getType() == InputConstants.Type.MOUSE ? key.getValue() + LIBIPN_MOUSE_OFFSET : key.getValue());
        }
        return setCodes(keybind, codes);
    }

    @Override
    public boolean setValue(String hotkeyId, String value) {
        Object keybind = keybinds.get(hotkeyId);
        if (keybind == null || value == null || !available()) {
            return false;
        }
        try {
            List<?> codes = (List<?>) parseStorage.invoke(companion, value);
            List<Integer> known = new ArrayList<>();
            for (Object code : codes) {
                // Names libIPN does not know come back as -1.
                if (code instanceof Integer number && number != -1) {
                    known.add(number);
                }
            }
            return setCodes(keybind, known);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("change an Inventory Profiles Next hotkey", e);
            return false;
        }
    }

    private boolean setCodes(Object keybind, List<Integer> codes) {
        try {
            setKeyCodes.invoke(keybind, codes);
            changed = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("change an Inventory Profiles Next hotkey", e);
            return false;
        }
    }

    @Override
    public void save() {
        if (!changed || !available()) {
            return;
        }
        try {
            save.invoke(saveLoadManager.invoke(screenSettings));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("save the Inventory Profiles Next config", e);
        }
        changed = false;
    }

    private boolean reflect() {
        if (reflected) {
            return !broken;
        }
        reflected = true;
        try {
            Class<?> settingsClass = Class.forName("org.anti_ad.mc.ipnext.config.ConfigScreenSettings");
            screenSettings = settingsClass.getField("INSTANCE").get(null);
            Class<?> declarationClass = Class.forName("org.anti_ad.mc.common.config.builder.ConfigDeclaration");
            Class<?> builderClass = Class.forName("org.anti_ad.mc.common.config.builder.ConfigDeclarationBuilder");
            Class<?> innerClass = Class.forName("org.anti_ad.mc.common.config.CategorizedMultiConfig");
            Class<?> optionClass = Class.forName("org.anti_ad.mc.common.config.IConfigOption");
            Class<?> keybindClass = Class.forName("org.anti_ad.mc.common.input.IKeybind");
            Class<?> settingsDataClass = Class.forName("org.anti_ad.mc.common.input.KeybindSettings");
            Class<?> companionClass = Class.forName("org.anti_ad.mc.common.input.IKeybind$Companion");
            Class<?> managerClass = Class.forName("org.anti_ad.mc.common.config.builder.ConfigSaveLoadManager");
            hotkeyClass = Class.forName("org.anti_ad.mc.common.config.options.ConfigHotkey");
            declarations = settingsClass.getMethod("getConfigDeclarations");
            declarationBuilder = declarationClass.getMethod("getBuilder");
            builderInnerConfig = builderClass.getMethod("getInnerConfig");
            innerCategories = innerClass.getMethod("getCategories");
            pairSecond = Class.forName("kotlin.Pair").getMethod("getSecond");
            optionKey = optionClass.getMethod("getKey");
            mainKeybind = hotkeyClass.getMethod("getMainKeybind");
            keyCodes = keybindClass.getMethod("getKeyCodes");
            setKeyCodes = keybindClass.getMethod("setKeyCodes", List.class);
            defaultKeyCodes = keybindClass.getMethod("getDefaultKeyCodes");
            keybindSettings = keybindClass.getMethod("getSettings");
            settingsContext = settingsDataClass.getMethod("getContext");
            companion = keybindClass.getField("Companion").get(null);
            storageString = companionClass.getMethod("getStorageString", List.class);
            parseStorage = companionClass.getMethod("getKeyCodes", String.class);
            saveLoadManager = settingsClass.getMethod("getSaveLoadManager");
            save = managerClass.getMethod("save");
            Object prefix = settingsClass.getMethod("getConfigOptionsPrefix").invoke(screenSettings);
            if (prefix instanceof String text && !text.isEmpty()) {
                optionsPrefix = text;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            KeyBindProfilesPlus.LOGGER.warn("Inventory Profiles Next's hotkeys cannot be reached (unexpected version?); they are not listed: {}", e.toString());
        }
        return !broken;
    }

    private void fail(String what, Exception e) {
        Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null ? invocation.getCause() : e;
        KeyBindProfilesPlus.LOGGER.error("Could not {}", what, cause);
    }
}
