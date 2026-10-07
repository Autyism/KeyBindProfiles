package io.github.autyism.keybindprofilesplus.selftest;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.gui.KeyBindProfileScreen;
import io.github.autyism.keybindprofilesplus.gui.KeyOverviewScreen;
import io.github.autyism.keybindprofilesplus.gui.ProfileEditScreen;
import io.github.autyism.keybindprofilesplus.input.ProfileHotkeyController;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.keys.KeyOrigins;
import io.github.autyism.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyism.keybindprofilesplus.options.OptionCatalog;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.server.ServerAutoSwitchController;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.autyism.keybindprofilesplus.selftest.LogicChecks.DEMO_DEBUG_BINDING;
import static io.github.autyism.keybindprofilesplus.selftest.LogicChecks.DEMO_MOD_BINDING;
import static io.github.autyism.keybindprofilesplus.selftest.LogicChecks.DEMO_SCREEN_BINDING;
import static io.github.autyism.keybindprofilesplus.selftest.LogicChecks.DEMO_UNKNOWN_BINDING;
import static io.github.autyism.keybindprofilesplus.selftest.LogicChecks.PROFILE_A;
import static io.github.autyism.keybindprofilesplus.selftest.LogicChecks.PROFILE_C;
import static io.github.autyism.keybindprofilesplus.selftest.LogicChecks.PROFILE_PREFIX;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Development-only automated check. It only runs when the dev client is started with
 * {@code -Dkbp.selftest=true} (see the {@code runSelfTest} Gradle task and tools/selftest.ps1).
 * Without that property nothing here is ever registered, so release jars are unaffected.
 *
 * <p>Once the client reaches the main menu it checks every rule of the mod with known inputs
 * ({@link LogicChecks}), walks through every screen in English and Chinese taking screenshots
 * into run/screenshots/selftest_*.png ({@link ScreenChecks}), enters a throwaway flat world for
 * the in-game parts, logs every result with the prefix [SelfTest], puts the key bindings and
 * settings back as they were and quits the game. It only ever touches profiles named selftest_*.
 */
public final class SelfTest extends SelfTestRunner {
    public static final String PROPERTY = "kbp.selftest";
    private static final String WORLD_NAME = "selftest_world";
    private static final int READY_TICKS = 40;

    private final LogicChecks logic;
    private final ScreenChecks screens;
    private final ConfigChecks configs;
    private final LiveExternalChecks live;
    private final Map<String, String> savedBindings = new LinkedHashMap<>();

    private Map<String, String> savedOptions = Map.of();
    private Map<Path, String> fixtureHashes = Map.of();
    private String savedCurrentProfile;
    private String savedLanguage;
    private String savedDefaultProfile;
    private boolean savedPauseOnLostFocus;
    private boolean savedConfirmApply = true;
    private boolean savedAutoSwitch = true;
    private boolean savedReturnToDefault;
    private boolean savedReplaceKeyBinds = true;
    private int savedScaleFactor;
    private boolean started;
    private boolean finished;
    private int readyTicks;

    private SelfTest(ProfileService service) {
        super(service);
        this.logic = new LogicChecks(this, service);
        this.screens = new ScreenChecks(this, logic, service);
        this.configs = new ConfigChecks(this);
        this.screens.setConfigChecks(configs);
        this.live = new LiveExternalChecks(this, logic, service);
    }

    public static boolean isRequested() {
        return Boolean.getBoolean(PROPERTY) && FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public static void install(ProfileService service) {
        SelfTest selfTest = new SelfTest(service);
        //? if >=26.1
        /*countFrames();*/
        registerDemoBindings();
        ClientTickEvents.END_CLIENT_TICK.register(selfTest::tick);
        log("installed, waiting for the main menu");
    }

    /**
     * Four fake key bindings so the dev client (which has hardly any other mods) can exercise the
     * rules for mod keys: one that looks like it belongs to Fabric API, one whose name says it is
     * for an inventory screen, one from a mod that cannot be identified, and one a mod put into
     * the Debug category (an F3 combination of its own). They only exist while the self-test is running. Their creator is forgotten on purpose so the naming rules are used.
     */
    private static void registerDemoBindings() {
        List<KeyMapping> demo = List.of(
                new KeyMapping(DEMO_MOD_BINDING, InputConstants.Type.KEYSYM, InputConstants.KEY_NUMPAD5, KeyMapping.Category.MISC),
                new KeyMapping(DEMO_SCREEN_BINDING, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC),
                new KeyMapping(DEMO_UNKNOWN_BINDING, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
                        //? if >=1.21.9 {
                        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("selftestmod", "demo")))
                        //?} else
                        /*"key.categories.selftestmod.demo")*/
                // Before 1.21.11 the debug keys were no key bindings, so there is no Debug category to put one into
                //? if >=1.21.11 {
                , new KeyMapping(DEMO_DEBUG_BINDING, InputConstants.Type.KEYSYM, InputConstants.KEY_J, KeyMapping.Category.DEBUG)
                //?}
        );
        for (KeyMapping binding : demo) {
            KeyBindingHelper.registerKeyBinding(binding);
            KeyOrigins.forget(binding);
        }
    }

    private void tick(Minecraft client) {
        if (finished) {
            return;
        }
        if (!started) {
            boolean ready = client.getOverlay() == null && client.screen != null && client.level == null;
            readyTicks = ready ? readyTicks + 1 : 0;
            if (readyTicks < READY_TICKS) {
                return;
            }
            started = true;
            homeScreen = client.screen;
            buildSteps(client);
            log("START steps=" + stepCount());
            return;
        }
        if (advance()) {
            finish(client);
        }
    }

    private void finish(Minecraft client) {
        finished = true;
        try {
            cleanup(client);
        } catch (Throwable t) {
            fail("cleanup threw " + t);
            KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "exception in cleanup", t);
        }
        log("SUMMARY " + summary());
        log("DONE");
        client.stop();
    }

    // ------------------------------------------------------------------ scenario

    private void buildSteps(Minecraft client) {
        step("environment", () -> logEnvironment(client));
        step("snapshot current settings", () -> snapshot(client));
        // Staged by the harness before this start, written before any mod read its settings.
        step("configs: import staged before this start", configs::earlyImport);

        // Rules and calculations, no screens involved.
        step("identity", logic::identity);
        step("migration from upstream KeyBindProfiles", logic::migration);
        step("language files", logic::languageFiles);
        step("key display names", logic::keyNames);
        step("profile names", logic::profileNames);
        step("key sources", logic::keySources);
        step("conflicts: layered detection", logic::conflicts);
        step("server rules: matching", logic::serverRules);
        step("combinations: dispatch, display, storage", logic::combos);
        //? if >=26.3
        /*step("keys saved on other versions", logic::crossVersionKeys);*/
        step("share codes", logic::shareCodes);
        step("external keys: Meteor and malilib (fixture files)", () -> {
            logic.externalKeys();
            fixtureHashes = logic.fixtureHashes();
        });
        step("profile: create", logic::profileCreate);
        step("profile: apply", logic::profileApply);
        step("profile: rename", logic::profileRename);
        step("profile: reload from disk", logic::profileReload);
        step("contents: game options and partial profiles", logic::profileContents);
        step("compare: model", logic::comparison);
        step("leave: back to the default profile", logic::leaveDefault);
        step("configs: rules and login data", configs::rules);
        step("configs: reading mod code", configs::bytecode);
        step("configs: sorting a made-up game folder", configs::classification);
        step("configs: export, import and undo", configs::roundTrip);
        step("configs: scan this game folder", configs::startRealScan);
        stepUntil("configs: scan finished", () -> {
        }, configs::realScanDone, 20 * 180);
        step("configs: scan results", configs::realScan);

        // Screens.
        screens.rebindOnKeyBindsScreen();
        screens.recordCombinationsOnVanillaScreen();
        // Meteor's and malilib's own hotkeys, changed for real in the running mods.
        live.register();
        step("examples for the screenshots", screens::setUpVisibleExamples);
        screens.tour("en");
        screens.modMenu();
        screens.applyFlow();
        step("switch language to zh_cn", SCREEN_SETTLE_TICKS, () -> setLanguage(client, "zh_cn"));
        step("chinese texts", logic::chineseTexts);
        screens.tour("zh");
        step("switch language back", 3, () -> setLanguage(client, savedLanguage));
        smallWindowTour(client);

        // In a world.
        step("world: prepare auto-switch", () -> {
            service.applyProfile(PROFILE_C);
            // Both match a singleplayer world; the specific rule must beat the catch-all.
            service.setProfileAutoSwitchServers(PROFILE_A, List.of("singleplayer"));
            service.setProfileAutoSwitchServers(PROFILE_C, List.of("*"));
            KeyBindProfilesPlus.settings().setAutoSwitch(true);
        });
        stepUntil("world: create and enter " + WORLD_NAME, () -> enterWorld(client),
                () -> client.player != null && client.level != null && client.screen == null, 20 * 90);
        step("world: settle", 30, () -> {
        });
        step("world: auto-switch rules", 2, () -> worldAutoSwitch(client));
        step("world: profile hotkey", 2, () -> worldHotkey(client));
        open("main screen in a world", () -> new KeyBindProfileScreen(null));
        step("world: open the edit screen of " + PROFILE_A, SCREEN_SETTLE_TICKS, () -> {
            screen(KeyBindProfileScreen.class).select(PROFILE_A);
            click(translated("keybindprofilesplus.edit"));
            check("world: edit screen opened", isScreen(ProfileEditScreen.class));
        });
        shot("ingame_edit_profile_where_am_i");
        step("world: close the screens", 6, () -> client.setScreen(null));
        step("world: show the notice", 2, () -> KeyBindProfilesPlus.showNotification(PROFILE_A));
        shot("ingame_hud_profile_notice");

        step("profile: delete", logic::profileDelete);
        step("external: files untouched", () -> logic.checkFixturesUntouched(fixtureHashes));
    }

    /**
     * Every screen once more at the smallest interface size the game allows (what "GUI Scale: Auto"
     * gives, about 427 x 240), to see that nothing is pushed off the screen there.
     */
    private void smallWindowTour(Minecraft client) {
        // The interface shrinks while a screen is open, as when the window is dragged smaller.
        open("key binds screen before the interface shrinks", () -> new KeyOverviewScreen(homeScreen));
        step("small: largest interface scale", SCREEN_SETTLE_TICKS, () -> {
            var window = client.getWindow();
            //? if >=1.21.6 {
            savedScaleFactor = window.getGuiScale();
            //?} else
            /*savedScaleFactor = (int) window.getGuiScale();*/
            window.setGuiScale(window.calculateScale(0, client.isEnforceUnicode()));
            //? if >=1.21.11 {
            client.screen.resize(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            //?} else
            /*client.screen.resize(client, window.getGuiScaledWidth(), window.getGuiScaledHeight());*/
            log("small: interface is now " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " (scale " + window.getGuiScale() + ")");
        });
        step("small: the open screen was laid out again", () -> {
            var manage = widget(translated("keybindprofilesplus.open"));
            var done = widget(translated("gui.done"));
            check("small: a screen that was open while the interface shrank fits the new size (" + manage.getX() + " .. " + (done.getX() + done.getWidth()) + " of " + client.screen.width + ")",
                    isScreen(KeyOverviewScreen.class) && manage.getX() >= 0 && done.getX() + done.getWidth() <= client.screen.width);
        });
        shot("small_keybinds_after_resize");
        step("small: main screen", SCREEN_SETTLE_TICKS, () -> {
            logic.makeGameDifferFromProfileA();
            client.setScreen(new KeyBindProfileScreen(null));
        });
        step("small: select " + PROFILE_A, 3, () -> {
            KeyBindProfileScreen main = screen(KeyBindProfileScreen.class);
            main.select(PROFILE_A);
            main.toggleCompareMark(PROFILE_C);
        });
        shot("small_main");
        smallVisit("keybindprofilesplus.compare.open_two", "small_compare", "gui.done");
        smallVisit("keybindprofilesplus.apply", "small_apply_confirm", "gui.cancel");
        smallVisit("keybindprofilesplus.edit", "small_edit_profile", "gui.done");
        smallVisit("keybindprofilesplus.new", "small_new_profile", "gui.cancel");
        smallVisit("keybindprofilesplus.import", "small_import", "gui.cancel");
        smallVisit("keybindprofilesplus.overview.open", "small_overview", "gui.done");
        smallVisit("keybindprofilesplus.rules.open", "small_server_rules", "gui.done");
        smallVisit("keybindprofilesplus.settings.open", "small_settings", "gui.done");
        open("key binds screen from the options (small)", () -> new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(homeScreen, client.options));
        step("small: the four buttons fit side by side", () -> {
            var manage = widget(translated("keybindprofilesplus.open"));
            var done = widget(translated("gui.done"));
            check("small: the key binds screen's footer buttons are inside the screen (" + manage.getX() + " .. " + (done.getX() + done.getWidth()) + " of " + client.screen.width + ")",
                    manage.getX() >= 0 && done.getX() + done.getWidth() <= client.screen.width && manage.getX() + manage.getWidth() <= widget(translated("keybindprofilesplus.compare.open_short")).getX());
        });
        shot("small_keybinds_from_options");
        step("small: the vanilla screen with the mod's buttons", SCREEN_SETTLE_TICKS, () -> {
            KeyBindProfilesPlus.settings().setReplaceKeyBinds(false);
            client.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(homeScreen, client.options));
        });
        step("small: the four buttons fit side by side there too", () -> {
            var manage = widget(translated("keybindprofilesplus.open"));
            var done = widget(translated("gui.done"));
            check("small: vanilla footer buttons are inside the screen (" + manage.getX() + " .. " + (done.getX() + done.getWidth()) + " of " + client.screen.width + ")",
                    manage.getX() >= 0 && done.getX() + done.getWidth() <= client.screen.width && manage.getX() + manage.getWidth() <= widget(translated("keybindprofilesplus.compare.open_short")).getX());
            KeyBindProfilesPlus.settings().setReplaceKeyBinds(true);
        });
        shot("small_vanilla_keybinds");
        configs.smallScreens();
        step("small: back to the normal interface scale", SCREEN_SETTLE_TICKS, () -> {
            restoreScale(client);
            client.setScreen(homeScreen);
        });
    }

    /** From the main screen: opens a screen by its button, takes a screenshot, and leaves it again. */
    private void smallVisit(String buttonKey, String shotName, String leaveKey) {
        step("small: " + buttonKey, SCREEN_SETTLE_TICKS, () -> click(translated(buttonKey)));
        shot(shotName);
        step("small: leave " + shotName, SCREEN_SETTLE_TICKS, () -> {
            click(translated(leaveKey));
            check("small: back on the main screen after " + shotName, isScreen(KeyBindProfileScreen.class));
        });
    }

    private void restoreScale(Minecraft client) {
        if (savedScaleFactor > 0) {
            client.getWindow().setGuiScale(savedScaleFactor);
            savedScaleFactor = 0;
            if (client.screen != null) {
                //? if >=1.21.11 {
                client.screen.resize(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                //?} else
                /*client.screen.resize(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());*/
            }
        }
    }

    private void worldAutoSwitch(Minecraft client) {
        ModSettings settings = KeyBindProfilesPlus.settings();
        ServerAutoSwitchController controller = KeyBindProfilesPlus.autoSwitchController();
        check("joining singleplayer auto-switched to " + PROFILE_A + " (specific rule beats *)", PROFILE_A.equals(service.getCurrentProfile()));

        settings.setAutoSwitch(false);
        service.applyProfile(PROFILE_C);
        controller.reset();
        controller.tick(client);
        check("world: with auto-switch off nothing is applied", PROFILE_C.equals(service.getCurrentProfile()));

        settings.setAutoSwitch(true);
        controller.reset();
        controller.tick(client);
        check("world: switching it back on applies the matching profile", PROFILE_A.equals(service.getCurrentProfile()));

        service.setProfileAutoSwitchServers(PROFILE_A, List.of("play.example.org"));
        controller.tick(client);
        check("world: with the specific rule gone the catch-all profile takes over", PROFILE_C.equals(service.getCurrentProfile()));

        service.setProfileAutoSwitchServers(PROFILE_A, List.of("singleplayer"));
        controller.tick(client);
        check("world: adding the rule back switches again without rejoining", PROFILE_A.equals(service.getCurrentProfile()));
    }

    /** A profile's hotkey during play. The keys are pretended to be held: real key state cannot be faked. */
    private void worldHotkey(Minecraft client) {
        ModSettings settings = KeyBindProfilesPlus.settings();
        ProfileHotkeyController hotkeys = KeyBindProfilesPlus.hotkeyController();
        List<String> keys = List.of("key.keyboard.keypad.5", "key.keyboard.f6");
        java.util.Set<String> down = new java.util.HashSet<>();
        boolean autoSwitch = settings.autoSwitch();
        try {
            settings.setAutoSwitch(false);
            service.setProfileHotkey(PROFILE_C, keys);
            service.applyProfile(PROFILE_A);
            hotkeys.setKeyStateForTesting(key -> down.contains(key.getName()));

            down.add(keys.get(0));
            hotkeys.tick(client);
            check("hotkey: one of its two keys alone does not switch", PROFILE_A.equals(service.getCurrentProfile()));

            down.addAll(keys);
            hotkeys.tick(client);
            check("hotkey: both keys held switch to " + PROFILE_C, PROFILE_C.equals(service.getCurrentProfile()));
            check("hotkey: ... and the notice names the profile", PROFILE_C.equals(KeyBindProfilesPlus.getNotificationText()));

            service.applyProfile(PROFILE_A);
            hotkeys.tick(client);
            check("hotkey: keeping the keys held does not switch again", PROFILE_A.equals(service.getCurrentProfile()));

            down.clear();
            hotkeys.tick(client);
            client.setScreen(new KeyBindProfileScreen(null));
            down.addAll(keys);
            hotkeys.tick(client);
            check("hotkey: nothing happens while a screen is open", PROFILE_A.equals(service.getCurrentProfile()));
            client.setScreen(null);
            hotkeys.tick(client);
            check("hotkey: back in the game the same keys switch", PROFILE_C.equals(service.getCurrentProfile()));
        } finally {
            hotkeys.setKeyStateForTesting(null);
            settings.setAutoSwitch(autoSwitch);
        }
    }

    private void logEnvironment(Minecraft client) {
        var window = client.getWindow();
        //? if >=1.21.6 {
        log("ENV minecraft=" + SharedConstants.getCurrentVersion().name()
        //?} else
        /*log("ENV minecraft=" + SharedConstants.getCurrentVersion().getName()*/
                + " mod=" + FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + " framebuffer=" + window.getWidth() + "x" + window.getHeight()
                + " scaled=" + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight()
                + " guiScale=" + window.getGuiScale()
                //? if >=26.3 {
                /*+ " fullscreen=" + client.options.fullscreen().get()
                *///?} else
                + " fullscreen=" + window.isFullscreen()
                + " lang=" + client.getLanguageManager().getSelected()
                + " keyBindings=" + client.options.keyMappings.length);
    }

    private void snapshot(Minecraft client) {
        savedBindings.putAll(currentKeyValues());
        savedCurrentProfile = service.getCurrentProfile();
        savedLanguage = client.getLanguageManager().getSelected();
        savedPauseOnLostFocus = client.options.pauseOnLostFocus;
        ModSettings settings = KeyBindProfilesPlus.settings();
        savedConfirmApply = settings.confirmApply();
        savedAutoSwitch = settings.autoSwitch();
        savedDefaultProfile = settings.defaultProfile();
        savedReturnToDefault = settings.returnToDefault();
        savedReplaceKeyBinds = settings.replaceKeyBinds();
        // The run starts from the setting a fresh install has.
        settings.setReplaceKeyBinds(true);
        Map<String, String> optionValues = new LinkedHashMap<>();
        GameOptionsBridge.readAll(client.options).forEach((key, entry) -> {
            if (OptionCatalog.isOffered(key)) {
                optionValues.put(key, entry.rawValue());
            }
        });
        savedOptions = optionValues;

        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        log("current profile before test: " + savedCurrentProfile + ", existing profiles: " + names);
        for (KeyMapping binding : client.options.keyMappings) {
            for (KeyConflicts.Conflict conflict : KeyConflicts.conflictsOf(binding, client.options)) {
                log("conflict in the current key layout: " + binding.getName() + " (" + binding.getTranslatedKeyMessage().getString() + ") vs "
                        + conflict.otherId() + " -> " + conflict.level() + " " + conflict.reason());
            }
        }
        deleteTestProfiles();
    }

    private void enterWorld(Minecraft client) {
        // The dev client is usually not the focused window; without this the pause menu would cover the HUD.
        client.options.pauseOnLostFocus = false;
        Path worldDir = client.getLevelSource().getBaseDir().resolve(WORLD_NAME);
        try {
            LogicChecks.deleteRecursively(worldDir);
        } catch (IOException e) {
            fail("could not remove the old " + WORLD_NAME + " folder: " + e);
        }

        //? if >=26.1 {
        /*LevelSettings levelInfo = new LevelSettings(WORLD_NAME, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        *///?} else {
        LevelSettings levelInfo = new LevelSettings(WORLD_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
        //?}
        //? if >=26.2 {
        /*client.createWorldOpenFlows().createFreshLevel(WORLD_NAME, levelInfo, WorldOptions.testWorldWithRandomSeed(),
                registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                homeScreen);
        *///?} else {
        client.createWorldOpenFlows().createFreshLevel(WORLD_NAME, levelInfo, WorldOptions.testWorldWithRandomSeed(),
                WorldPresets::createFlatWorldDimensions, homeScreen);
        //?}
    }

    // ------------------------------------------------------------------ cleanup

    private void cleanup(Minecraft client) {
        restoreScale(client);
        deleteTestProfiles();
        logic.removeFixtures();
        configs.cleanup();
        ExternalKeys.refresh();
        KeyCombos.setHeldModifiersForTesting(null);

        ModSettings settings = KeyBindProfilesPlus.settings();
        for (String demo : List.of(DEMO_MOD_BINDING, DEMO_SCREEN_BINDING, DEMO_UNKNOWN_BINDING)) {
            settings.setScopeOverride(demo, null);
        }
        if (!savedBindings.isEmpty()) {
            KeyCombos.batch(() -> {
                for (KeyMapping binding : client.options.keyMappings) {
                    String key = savedBindings.get(binding.getName());
                    if (key != null) {
                        KeyCombos.applyValue(binding, key);
                    }
                }
            });
            KeyMapping.resetMapping();
            GameOptionsBridge.apply(client.options, savedOptions);
            settings.setConfirmApply(savedConfirmApply);
            settings.setAutoSwitch(savedAutoSwitch);
            settings.setDefaultProfile(savedDefaultProfile);
            settings.setReturnToDefault(savedReturnToDefault);
            settings.setReplaceKeyBinds(savedReplaceKeyBinds);
            client.options.pauseOnLostFocus = savedPauseOnLostFocus;
            if (savedLanguage != null && !savedLanguage.equals(client.getLanguageManager().getSelected())) {
                setLanguage(client, savedLanguage);
            }
            client.options.save();
            service.saveCurrentProfile(savedCurrentProfile != null && service.profiles().containsKey(savedCurrentProfile) ? savedCurrentProfile : null);
            log("restored " + savedBindings.size() + " key bindings and current profile '" + service.getCurrentProfile() + "'");
            check("cleanup: key bindings are back as they were", currentKeyValues().equals(savedBindings));
        }

        List<String> leftovers = new ArrayList<>();
        for (String name : service.profiles().keySet()) {
            if (name.startsWith(PROFILE_PREFIX)) {
                leftovers.add(name);
            }
        }
        check("cleanup: no " + PROFILE_PREFIX + "* profiles left", leftovers.isEmpty());
        if (client.level == null) {
            client.setScreen(homeScreen);
        }
    }

    private void deleteTestProfiles() {
        service.reloadProfiles();
        for (String name : new ArrayList<>(service.profiles().keySet())) {
            if (name.startsWith(PROFILE_PREFIX)) {
                service.deleteProfile(name);
            }
        }
    }

    private static void setLanguage(Minecraft client, String code) {
        // Only the in-memory language is switched; options.txt keeps the user's choice.
        client.getLanguageManager().setSelected(code);
        client.getLanguageManager().onResourceManagerReload(client.getResourceManager());
    }
}
