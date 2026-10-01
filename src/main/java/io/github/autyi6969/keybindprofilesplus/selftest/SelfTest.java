package io.github.autyi6969.keybindprofilesplus.selftest;

import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.external.ExternalKeys;
import io.github.autyi6969.keybindprofilesplus.gui.KeyBindProfileScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileEditScreen;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombos;
import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyi6969.keybindprofilesplus.keys.KeyOrigins;
import io.github.autyi6969.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyi6969.keybindprofilesplus.options.OptionCatalog;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.server.ServerAutoSwitchController;
import io.github.autyi6969.keybindprofilesplus.storage.ModSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.DEMO_MOD_BINDING;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.DEMO_SCREEN_BINDING;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.DEMO_UNKNOWN_BINDING;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_A;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_C;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_PREFIX;

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
    private int savedScaleFactor;
    private boolean started;
    private boolean finished;
    private int readyTicks;

    private SelfTest(ProfileService service) {
        super(service);
        this.logic = new LogicChecks(this, service);
        this.screens = new ScreenChecks(this, logic, service);
    }

    public static boolean isRequested() {
        return Boolean.getBoolean(PROPERTY) && FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    public static void install(ProfileService service) {
        SelfTest selfTest = new SelfTest(service);
        registerDemoBindings();
        ClientTickEvents.END_CLIENT_TICK.register(selfTest::tick);
        log("installed, waiting for the main menu");
    }

    /**
     * Three fake key bindings so the dev client (which has no other mods) can exercise the rules
     * for mod keys: one that looks like it belongs to Fabric API, one whose name says it is for an
     * inventory screen, and one from a mod that cannot be identified. They only exist while the
     * self-test is running. Their creator is forgotten on purpose so the naming rules are used.
     */
    private static void registerDemoBindings() {
        List<KeyBinding> demo = List.of(
                new KeyBinding(DEMO_MOD_BINDING, InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_KP_5, KeyBinding.Category.MISC),
                new KeyBinding(DEMO_SCREEN_BINDING, InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), KeyBinding.Category.MISC),
                new KeyBinding(DEMO_UNKNOWN_BINDING, InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(),
                        KeyBinding.Category.create(Identifier.of("selftestmod", "demo"))));
        for (KeyBinding binding : demo) {
            KeyBindingHelper.registerKeyBinding(binding);
            KeyOrigins.forget(binding);
        }
    }

    private void tick(MinecraftClient client) {
        if (finished) {
            return;
        }
        if (!started) {
            boolean ready = client.getOverlay() == null && client.currentScreen != null && client.world == null;
            readyTicks = ready ? readyTicks + 1 : 0;
            if (readyTicks < READY_TICKS) {
                return;
            }
            started = true;
            homeScreen = client.currentScreen;
            buildSteps(client);
            log("START steps=" + stepCount());
            return;
        }
        if (advance()) {
            finish(client);
        }
    }

    private void finish(MinecraftClient client) {
        finished = true;
        try {
            cleanup(client);
        } catch (Throwable t) {
            fail("cleanup threw " + t);
            KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "exception in cleanup", t);
        }
        log("SUMMARY " + summary());
        log("DONE");
        client.scheduleStop();
    }

    // ------------------------------------------------------------------ scenario

    private void buildSteps(MinecraftClient client) {
        step("environment", () -> logEnvironment(client));
        step("snapshot current settings", () -> snapshot(client));

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

        // Screens.
        screens.recordCombinations();
        step("examples for the screenshots", screens::setUpVisibleExamples);
        screens.tour("en");
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
                () -> client.player != null && client.world != null && client.currentScreen == null, 20 * 90);
        step("world: settle", 30, () -> {
        });
        step("world: auto-switch rules", 2, () -> worldAutoSwitch(client));
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
    private void smallWindowTour(MinecraftClient client) {
        step("small: largest interface scale", SCREEN_SETTLE_TICKS, () -> {
            var window = client.getWindow();
            savedScaleFactor = window.getScaleFactor();
            window.setScaleFactor(window.calculateScaleFactor(0, client.forcesUnicodeFont()));
            log("small: interface is now " + window.getScaledWidth() + "x" + window.getScaledHeight() + " (scale " + window.getScaleFactor() + ")");
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
        open("vanilla key binds screen (small)", () -> new net.minecraft.client.gui.screen.option.KeybindsScreen(homeScreen, client.options));
        step("small: the four buttons fit side by side", () -> {
            var manage = widget(translated("keybindprofilesplus.open"));
            var done = widget(translated("gui.done"));
            check("small: vanilla footer buttons are inside the screen (" + manage.getX() + " .. " + (done.getX() + done.getWidth()) + " of " + client.currentScreen.width + ")",
                    manage.getX() >= 0 && done.getX() + done.getWidth() <= client.currentScreen.width && manage.getX() + manage.getWidth() <= widget(translated("keybindprofilesplus.compare.open_short")).getX());
        });
        shot("small_vanilla_keybinds");
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

    private void restoreScale(MinecraftClient client) {
        if (savedScaleFactor > 0) {
            client.getWindow().setScaleFactor(savedScaleFactor);
            savedScaleFactor = 0;
            if (client.currentScreen != null) {
                client.currentScreen.resize(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            }
        }
    }

    private void worldAutoSwitch(MinecraftClient client) {
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

    private void logEnvironment(MinecraftClient client) {
        var window = client.getWindow();
        log("ENV minecraft=" + SharedConstants.getGameVersion().name()
                + " mod=" + FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + " framebuffer=" + window.getFramebufferWidth() + "x" + window.getFramebufferHeight()
                + " scaled=" + window.getScaledWidth() + "x" + window.getScaledHeight()
                + " guiScale=" + window.getScaleFactor()
                + " fullscreen=" + window.isFullscreen()
                + " lang=" + client.getLanguageManager().getLanguage()
                + " keyBindings=" + client.options.allKeys.length);
    }

    private void snapshot(MinecraftClient client) {
        savedBindings.putAll(currentKeyValues());
        savedCurrentProfile = service.getCurrentProfile();
        savedLanguage = client.getLanguageManager().getLanguage();
        savedPauseOnLostFocus = client.options.pauseOnLostFocus;
        ModSettings settings = KeyBindProfilesPlus.settings();
        savedConfirmApply = settings.confirmApply();
        savedAutoSwitch = settings.autoSwitch();
        savedDefaultProfile = settings.defaultProfile();
        savedReturnToDefault = settings.returnToDefault();
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
        for (KeyBinding binding : client.options.allKeys) {
            for (KeyConflicts.Conflict conflict : KeyConflicts.conflictsOf(binding, client.options)) {
                log("conflict in the current key layout: " + binding.getId() + " (" + binding.getBoundKeyLocalizedText().getString() + ") vs "
                        + conflict.otherId() + " -> " + conflict.level() + " " + conflict.reason());
            }
        }
        deleteTestProfiles();
    }

    private void enterWorld(MinecraftClient client) {
        // The dev client is usually not the focused window; without this the pause menu would cover the HUD.
        client.options.pauseOnLostFocus = false;
        Path worldDir = client.getLevelStorage().getSavesDirectory().resolve(WORLD_NAME);
        try {
            LogicChecks.deleteRecursively(worldDir);
        } catch (IOException e) {
            fail("could not remove the old " + WORLD_NAME + " folder: " + e);
        }

        LevelInfo levelInfo = new LevelInfo(WORLD_NAME, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart(WORLD_NAME, levelInfo, GeneratorOptions.createTestWorld(),
                WorldPresets::createTestOptions, homeScreen);
    }

    // ------------------------------------------------------------------ cleanup

    private void cleanup(MinecraftClient client) {
        restoreScale(client);
        deleteTestProfiles();
        logic.removeFixtures();
        ExternalKeys.refresh();
        KeyCombos.setHeldModifiersForTesting(null);

        ModSettings settings = KeyBindProfilesPlus.settings();
        for (String demo : List.of(DEMO_MOD_BINDING, DEMO_SCREEN_BINDING, DEMO_UNKNOWN_BINDING)) {
            settings.setScopeOverride(demo, null);
        }
        if (!savedBindings.isEmpty()) {
            KeyCombos.batch(() -> {
                for (KeyBinding binding : client.options.allKeys) {
                    String key = savedBindings.get(binding.getId());
                    if (key != null) {
                        KeyCombos.applyValue(binding, key);
                    }
                }
            });
            KeyBinding.updateKeysByCode();
            GameOptionsBridge.apply(client.options, savedOptions);
            settings.setConfirmApply(savedConfirmApply);
            settings.setAutoSwitch(savedAutoSwitch);
            settings.setDefaultProfile(savedDefaultProfile);
            settings.setReturnToDefault(savedReturnToDefault);
            client.options.pauseOnLostFocus = savedPauseOnLostFocus;
            if (savedLanguage != null && !savedLanguage.equals(client.getLanguageManager().getLanguage())) {
                setLanguage(client, savedLanguage);
            }
            client.options.write();
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
        if (client.world == null) {
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

    private static void setLanguage(MinecraftClient client, String code) {
        // Only the in-memory language is switched; options.txt keeps the user's choice.
        client.getLanguageManager().setLanguage(code);
        client.getLanguageManager().reload(client.getResourceManager());
    }
}
