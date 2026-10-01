package io.github.autyi6969.keybindprofilesplus.selftest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.gui.KeyBindProfileScreen;
import io.github.autyi6969.keybindprofilesplus.gui.KeyOverviewScreen;
import io.github.autyi6969.keybindprofilesplus.keys.KeySource;
import io.github.autyi6969.keybindprofilesplus.keys.KeySourceResolver;
import io.github.autyi6969.keybindprofilesplus.notification.ProfileNoticeHud;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.storage.LegacyOptions;
import io.github.autyi6969.keybindprofilesplus.storage.ProfileFileStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Development-only automated check. It only runs when the dev client is started with
 * {@code -Dkbp.selftest=true} (see the {@code runSelfTest} Gradle task and tools/selftest.ps1).
 * Without that property nothing here is ever registered, so release jars are unaffected.
 *
 * <p>Once the client reaches the main menu it opens every screen of the mod (in English and in
 * Chinese), saves a screenshot of each into run/screenshots/selftest_*.png, runs a
 * create / apply / rename / delete round trip on throwaway profiles named selftest_*, enters a
 * throwaway flat world to check the HUD notice, logs every result with the prefix [SelfTest],
 * restores the previous key bindings and quits the game.
 */
public final class SelfTest {
    public static final String PROPERTY = "kbp.selftest";
    private static final String LOG_PREFIX = "[SelfTest] ";
    private static final String PROFILE_PREFIX = "selftest_";
    private static final String PROFILE_A = PROFILE_PREFIX + "a";
    private static final String PROFILE_B = PROFILE_PREFIX + "b";
    private static final String PROFILE_C = PROFILE_PREFIX + "c";
    private static final String WORLD_NAME = "selftest_world";
    private static final String TEST_BINDING_ID = "key.jump";
    private static final String TEST_KEY = "key.keyboard.j";
    private static final String DEMO_MOD_BINDING = "key.fabric-api.selftest_demo";
    private static final String DEMO_UNKNOWN_BINDING = "key.selftest.unknown_demo";
    private static final String LANG_PATH = "assets/" + KeyBindProfilesPlus.MOD_ID + "/lang/";
    private static final int READY_TICKS = 40;
    private static final int SCREEN_SETTLE_TICKS = 12;
    private static final int MAX_RUN_TICKS = 20 * 240;

    private final ProfileService service;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final Map<String, String> savedBindings = new LinkedHashMap<>();
    private final AtomicInteger pendingScreenshots = new AtomicInteger();

    private String savedCurrentProfile;
    private String savedLanguage;
    private boolean savedPauseOnLostFocus;
    private String originalTestKey;
    private Screen homeScreen;
    private boolean started;
    private boolean finished;
    private boolean busy;
    private boolean cleanedUp;
    private BooleanSupplier waitCondition;
    private String waitName;
    private int waitDeadline;
    private int readyTicks;
    private int waitTicks;
    private int runTicks;
    private int passed;
    private int failed;
    private int screenshotIndex;

    private SelfTest(ProfileService service) {
        this.service = service;
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
     * Two fake key bindings so the dev client (which has no other mods) can exercise the
     * "which mod is this from" logic: one that looks like it belongs to Fabric API and one from
     * a mod that cannot be identified. They only exist while the self-test is running.
     */
    private static void registerDemoBindings() {
        KeyBindingHelper.registerKeyBinding(new KeyBinding(DEMO_MOD_BINDING, InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_KP_5, KeyBinding.Category.MISC));
        KeyBindingHelper.registerKeyBinding(new KeyBinding(DEMO_UNKNOWN_BINDING, InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(), KeyBinding.Category.create(Identifier.of("selftestmod", "demo"))));
    }

    // ------------------------------------------------------------------ driver

    private void tick(MinecraftClient client) {
        if (finished || busy) {
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
            log("START steps=" + steps.size());
            return;
        }

        if (++runTicks > MAX_RUN_TICKS) {
            fail("self-test exceeded " + MAX_RUN_TICKS + " ticks, aborting");
            steps.clear();
            waitCondition = null;
        }

        if (waitCondition != null) {
            if (waitCondition.getAsBoolean()) {
                waitCondition = null;
            } else if (runTicks > waitDeadline) {
                fail("timed out waiting for: " + waitName);
                waitCondition = null;
            } else {
                return;
            }
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        Step step = steps.poll();
        if (step == null) {
            if (pendingScreenshots.get() > 0 && runTicks <= MAX_RUN_TICKS) {
                return;
            }
            finish(client);
            return;
        }

        busy = true;
        try {
            log("STEP " + step.name());
            step.action().run();
        } catch (Throwable t) {
            fail(step.name() + " threw " + t);
            KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "exception in step '" + step.name() + "'", t);
        } finally {
            busy = false;
        }
        waitTicks = step.waitAfter();
        if (step.until() != null) {
            waitCondition = step.until();
            waitName = step.name();
            waitDeadline = runTicks + step.timeoutTicks();
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
        log("SUMMARY passed=" + passed + " failed=" + failed + " screenshots=" + screenshotIndex);
        log("DONE");
        client.scheduleStop();
    }

    // ------------------------------------------------------------------ scenario

    private void buildSteps(MinecraftClient client) {
        step("environment", 0, () -> logEnvironment(client));
        step("snapshot current settings", 0, () -> snapshot(client));
        step("identity", 0, this::checkIdentity);
        step("migration from upstream KeyBindProfiles", 0, this::checkMigration);
        step("language files", 0, this::checkLanguageFiles);
        step("key display names", 0, this::checkKeyNames);
        step("key sources", 0, () -> checkKeySources(client));

        step("profile: create", 0, () -> profileCreate(client));
        step("profile: apply", 0, () -> profileApply(client));
        step("profile: rename", 0, this::profileRename);
        step("profile: reload from disk", 0, this::profileReload);

        screenTour(client, "en");
        step("switch language to zh_cn", SCREEN_SETTLE_TICKS, () -> setLanguage(client, "zh_cn"));
        step("chinese texts", 0, this::checkChineseTexts);
        screenTour(client, "zh");
        step("switch language back", 4, () -> setLanguage(client, savedLanguage));

        step("world: prepare auto-switch", 0, () -> service.setProfileAutoSwitchServers(PROFILE_A, List.of("singleplayer")));
        stepUntil("world: create and enter " + WORLD_NAME, () -> enterWorld(client),
                () -> client.player != null && client.world != null && client.currentScreen == null, 20 * 90);
        step("world: settle", 30, () -> {
        });
        step("world: auto-switch on join", 2, () -> {
            check("joining singleplayer auto-switched to " + PROFILE_A, PROFILE_A.equals(service.getCurrentProfile()));
            // Shown again so the notice is guaranteed to still be on screen for the screenshot.
            KeyBindProfilesPlus.showNotification(PROFILE_A);
        });
        shot(client, "ingame_hud_profile_notice");

        step("profile: delete", 0, this::profileDelete);
    }

    /** Opens each screen of the mod once and takes a screenshot; {@code tag} is the language. */
    private void screenTour(MinecraftClient client, String tag) {
        open(client, "profile screen", () -> new KeyBindProfileScreen(null));
        if (tag.equals("en")) {
            shot(client, tag + "_profiles_nothing_selected");
        }
        step("click profile " + PROFILE_A, 4, () -> click(client, PROFILE_A));
        shot(client, tag + "_profiles_profile_selected");

        step("click overview button", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.overview.open").getString());
            check("overview button opens the key overview", client.currentScreen instanceof KeyOverviewScreen);
        });
        shot(client, tag + "_overview_all");
        if (tag.equals("en")) {
            step("overview: mods only", 4, () -> {
                KeyOverviewScreen overview = (KeyOverviewScreen) client.currentScreen;
                overview.setSourceFilter(KeyOverviewScreen.SourceFilter.MODS);
                check("overview: mods filter shows the 3 non-vanilla bindings, got " + overview.visibleBindingCount(), overview.visibleBindingCount() == 3);
            });
            shot(client, tag + "_overview_mods_only");
            step("overview: search", 4, () -> {
                KeyOverviewScreen overview = (KeyOverviewScreen) client.currentScreen;
                overview.setSourceFilter(KeyOverviewScreen.SourceFilter.ALL);
                overview.setQuery("num 5");
                check("overview: searching 'num 5' finds the binding on numpad 5, got " + overview.visibleBindingCount(), overview.visibleBindingCount() == 1);
                overview.setQuery("zzzz no such key");
                check("overview: a search without hits shows nothing", overview.visibleBindingCount() == 0);
            });
            shot(client, tag + "_overview_no_match");
        }
        step("overview: done", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("gui.done").getString());
            check("overview: done returns to the profile screen", client.currentScreen instanceof KeyBindProfileScreen);
        });

        open(client, "vanilla key binds screen", () -> new KeybindsScreen(homeScreen, client.options));
        step("manage button present", 0, () -> check("vanilla Key Binds screen has the '" + manageLabel() + "' button",
                findWidget(client.currentScreen, manageLabel()) != null));
        shot(client, tag + "_vanilla_keybinds_with_manage_button");
        step("click manage button", SCREEN_SETTLE_TICKS, () -> {
            click(client, manageLabel());
            check("manage button opens the profile screen", client.currentScreen instanceof KeyBindProfileScreen);
        });
        if (tag.equals("en")) {
            shot(client, tag + "_profiles_opened_from_keybinds");
        }
        step("click done", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("gui.done").getString());
            check("done returns to the vanilla Key Binds screen", client.currentScreen instanceof KeybindsScreen);
        });
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
        for (KeyBinding binding : client.options.allKeys) {
            savedBindings.put(binding.getId(), binding.getBoundKeyTranslationKey());
        }
        savedCurrentProfile = service.getCurrentProfile();
        savedLanguage = client.getLanguageManager().getLanguage();
        savedPauseOnLostFocus = client.options.pauseOnLostFocus;
        log("current profile before test: " + savedCurrentProfile + ", existing profiles: " + sortedProfileNames());
        deleteTestProfiles();
    }

    private void checkIdentity() {
        var mod = FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID);
        check("mod is loaded as '" + KeyBindProfilesPlus.MOD_ID + "'", mod.isPresent());
        mod.ifPresent(container -> {
            check("mod name is 'KeyBind Profiles+'", "KeyBind Profiles+".equals(container.getMetadata().getName()));
            check("mod license is GPL", container.getMetadata().getLicense().stream().anyMatch(license -> license.startsWith("GPL-3.0")));
        });
        check("upstream mod id is not loaded alongside", !FabricLoader.getInstance().isModLoaded("keybindprofiles"));
        check("profiles live in config/keybindprofilesplus",
                service.profilesDirectory().getPath().replace('\\', '/').endsWith("config/keybindprofilesplus"));
        check("title is translated", "KeyBind Profiles+".equals(Text.translatable("keybindprofilesplus.title").getString()));
        check("open key binding uses the new id", KeyBinding.byId("key.keybindprofilesplus.open") != null);
    }

    private void checkMigration() {
        try {
            File root = Files.createTempDirectory("kbp_selftest_migration").toFile();
            File legacy = new File(root, "keybindprofiles");
            File target = new File(root, "keybindprofilesplus");
            check("migration: nothing to do without an old folder", ProfileFileStore.migrateLegacyDirectory(legacy, target) == 0 && !target.exists());

            Files.createDirectories(legacy.toPath());
            Files.writeString(new File(legacy, "Old.kbp").toPath(), "{\"name\":\"Old\",\"keybindings\":{}}");
            Files.writeString(new File(legacy, "current_profile.txt").toPath(), "Old");
            check("migration: copies the old folder", ProfileFileStore.migrateLegacyDirectory(legacy, target) == 2
                    && new File(target, "Old.kbp").isFile() && new File(target, "current_profile.txt").isFile());
            check("migration: leaves the old folder in place", new File(legacy, "Old.kbp").isFile());

            Files.writeString(new File(legacy, "Later.kbp").toPath(), "{}");
            check("migration: runs only once", ProfileFileStore.migrateLegacyDirectory(legacy, target) == 0 && !new File(target, "Later.kbp").exists());
            deleteRecursively(root.toPath());
        } catch (IOException e) {
            fail("migration check could not use a temp folder: " + e);
        }

        check("old 'open' key is carried over", "key.keyboard.p".equals(LegacyOptions.findLegacyOpenKey(
                List.of("fov:0.0", "key_key.keybindprofiles.open:key.keyboard.p", "key_key.jump:key.keyboard.space"))));
        check("old 'open' key is ignored once the new entry exists", LegacyOptions.findLegacyOpenKey(
                List.of("key_key.keybindprofiles.open:key.keyboard.p", "key_key.keybindprofilesplus.open:key.keyboard.o")) == null);
    }

    private void checkLanguageFiles() {
        Map<String, String> english = readLanguage("en_us");
        check("en_us.json is readable (" + english.size() + " entries)", !english.isEmpty());

        for (String code : List.of("zh_cn", "ru_ru")) {
            Map<String, String> other = readLanguage(code);
            Set<String> missing = new TreeSet<>(english.keySet());
            missing.removeAll(other.keySet());
            Set<String> unknown = new TreeSet<>(other.keySet());
            unknown.removeAll(english.keySet());
            Set<String> placeholderMismatch = new TreeSet<>();
            for (Map.Entry<String, String> entry : other.entrySet()) {
                String source = english.get(entry.getKey());
                if (source != null && countPlaceholders(source) != countPlaceholders(entry.getValue())) {
                    placeholderMismatch.add(entry.getKey());
                }
            }

            if (code.equals("zh_cn")) {
                check("zh_cn.json has every en_us entry" + (missing.isEmpty() ? "" : ", missing " + missing), missing.isEmpty());
            } else {
                // Russian is kept from upstream; entries it lacks fall back to English in game.
                log(code + ".json lacks " + missing.size() + " entries (they fall back to English)" + (missing.isEmpty() ? "" : ": " + missing));
            }
            check(code + ".json has no entries unknown to en_us" + (unknown.isEmpty() ? "" : ": " + unknown), unknown.isEmpty());
            check(code + ".json keeps the %s placeholders" + (placeholderMismatch.isEmpty() ? "" : ", wrong in " + placeholderMismatch), placeholderMismatch.isEmpty());
        }

        String notice = ProfileNoticeHud.messageFor("X").getString();
        check("HUD notice is translated, not hardcoded Russian: '" + notice + "'",
                "Profile \"X\" applied".equals(notice) && notice.chars().noneMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC));
    }

    private void checkKeyNames() {
        checkKeyName("key.keyboard.keypad.5", "Num 5");
        checkKeyName("key.keyboard.keypad.0", "Num 0");
        checkKeyName("key.keyboard.keypad.add", "Num +");
        checkKeyName("key.keyboard.keypad.divide", "Num /");
        checkKeyName("key.keyboard.keypad.enter", "Num Enter");
        checkKeyName("key.keyboard.5", "5");
        checkKeyName("key.keyboard.enter", "Enter");
    }

    private void checkKeySources(MinecraftClient client) {
        KeySourceResolver resolver = new KeySourceResolver(client.options);
        checkSource(resolver, "key.jump", KeySource.Kind.VANILLA, "minecraft");
        checkSource(resolver, "key.hotbar.1", KeySource.Kind.VANILLA, "minecraft");
        checkSource(resolver, "key.debug.reloadChunk", KeySource.Kind.VANILLA, "minecraft");
        checkSource(resolver, "key.keybindprofilesplus.open", KeySource.Kind.MOD, KeyBindProfilesPlus.MOD_ID);
        checkSource(resolver, DEMO_MOD_BINDING, KeySource.Kind.MOD, "fabric-api");
        checkSource(resolver, DEMO_UNKNOWN_BINDING, KeySource.Kind.UNKNOWN, "selftestmod");

        int vanilla = 0;
        for (KeyBinding binding : client.options.allKeys) {
            if (resolver.resolve(binding).isVanilla()) {
                vanilla++;
            }
        }
        check("key sources: every binding except the 3 mod ones is vanilla (" + vanilla + " of " + client.options.allKeys.length + ")",
                vanilla == client.options.allKeys.length - 3);
    }

    private void checkSource(KeySourceResolver resolver, String bindingId, KeySource.Kind kind, String modId) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        if (binding == null) {
            fail("key source: binding " + bindingId + " does not exist");
            return;
        }
        KeySource source = resolver.resolve(binding);
        check("key source: " + bindingId + " -> " + kind + " " + modId + " (label '" + source.label().getString() + "')",
                source.kind() == kind && modId.equals(source.modId()));
    }

    private void checkChineseTexts() {
        checkKeyName("key.keyboard.keypad.5", "小键盘 5");
        check("zh_cn: manage button reads 管理档案", "管理档案".equals(manageLabel()));
        check("zh_cn: HUD notice reads 已应用档案“X”", "已应用档案“X”".equals(ProfileNoticeHud.messageFor("X").getString()));
    }

    private void checkKeyName(String translationKey, String expected) {
        String actual = InputUtil.fromTranslationKey(translationKey).getLocalizedText().getString();
        check("key name " + translationKey + " -> '" + expected + "'" + (expected.equals(actual) ? "" : " but was '" + actual + "'"), expected.equals(actual));
    }

    private void profileCreate(MinecraftClient client) {
        KeyBinding jump = requireBinding();
        originalTestKey = jump.getBoundKeyTranslationKey();

        service.saveProfile(PROFILE_A, client.options.allKeys);
        check("create " + PROFILE_A + ": in memory", service.profiles().containsKey(PROFILE_A));
        check("create " + PROFILE_A + ": file written", profileFile(PROFILE_A).isFile());
        check("create " + PROFILE_A + ": stores " + TEST_BINDING_ID + "=" + originalTestKey,
                originalTestKey.equals(service.profiles().get(PROFILE_A).get(TEST_BINDING_ID)));

        setLiveKey(jump, TEST_KEY);
        service.saveProfile(PROFILE_B, client.options.allKeys);
        check("create " + PROFILE_B + ": stores " + TEST_BINDING_ID + "=" + TEST_KEY,
                TEST_KEY.equals(service.profiles().get(PROFILE_B).get(TEST_BINDING_ID)));
        check("create " + PROFILE_B + ": file written", profileFile(PROFILE_B).isFile());
    }

    private void profileApply(MinecraftClient client) {
        KeyBinding jump = requireBinding();

        service.applyProfile(PROFILE_A);
        check("apply " + PROFILE_A + ": " + TEST_BINDING_ID + " back to " + originalTestKey,
                originalTestKey.equals(jump.getBoundKeyTranslationKey()));
        check("apply " + PROFILE_A + ": becomes current profile", PROFILE_A.equals(service.getCurrentProfile()));

        service.applyProfile(PROFILE_B);
        check("apply " + PROFILE_B + ": " + TEST_BINDING_ID + " is " + TEST_KEY, TEST_KEY.equals(jump.getBoundKeyTranslationKey()));
        check("apply " + PROFILE_B + ": becomes current profile", PROFILE_B.equals(service.getCurrentProfile()));
        check("apply " + PROFILE_B + ": options.txt updated", optionsFileContains(client, "key_" + TEST_BINDING_ID + ":" + TEST_KEY));
    }

    private void profileRename() {
        KeyBinding jump = requireBinding();
        service.setProfileHotkey(PROFILE_B, List.of("key.keyboard.keypad.5", "key.keyboard.f6"));
        service.setProfileAutoSwitchServers(PROFILE_B, List.of("example.org", "*.selftest.example"));

        check("rename " + PROFILE_B + " -> " + PROFILE_C + ": accepted", service.renameProfile(PROFILE_B, PROFILE_C));
        check("rename: old name gone", !service.profiles().containsKey(PROFILE_B) && !profileFile(PROFILE_B).exists());
        check("rename: new name present", service.profiles().containsKey(PROFILE_C) && profileFile(PROFILE_C).isFile());
        check("rename: current profile follows", PROFILE_C.equals(service.getCurrentProfile()));
        check("rename: hotkey kept", List.of("key.keyboard.keypad.5", "key.keyboard.f6").equals(service.getProfileHotkey(PROFILE_C)));
        check("rename: servers kept", List.of("example.org", "*.selftest.example").equals(service.getProfileAutoSwitchServers(PROFILE_C)));
        check("rename: live key bindings untouched", TEST_KEY.equals(jump.getBoundKeyTranslationKey()));
        check("rename onto an existing name is refused", !service.renameProfile(PROFILE_C, PROFILE_A));
    }

    private void profileReload() {
        service.reloadProfiles();
        check("reload: " + PROFILE_A + " read back", service.profiles().containsKey(PROFILE_A)
                && originalTestKey.equals(service.profiles().get(PROFILE_A).get(TEST_BINDING_ID)));
        check("reload: " + PROFILE_C + " read back", service.profiles().containsKey(PROFILE_C)
                && TEST_KEY.equals(service.profiles().get(PROFILE_C).get(TEST_BINDING_ID)));
        check("reload: hotkey read back", List.of("key.keyboard.keypad.5", "key.keyboard.f6").equals(service.getProfileHotkey(PROFILE_C)));
        check("reload: servers read back", List.of("example.org", "*.selftest.example").equals(service.getProfileAutoSwitchServers(PROFILE_C)));
    }

    private void profileDelete() {
        service.deleteProfile(PROFILE_A);
        service.deleteProfile(PROFILE_C);
        check("delete: profiles removed from memory", !service.profiles().containsKey(PROFILE_A) && !service.profiles().containsKey(PROFILE_C));
        check("delete: files removed", !profileFile(PROFILE_A).exists() && !profileFile(PROFILE_C).exists());
        check("delete: current profile cleared", service.getCurrentProfile() == null);
    }

    private void enterWorld(MinecraftClient client) {
        // The dev client is usually not the focused window; without this the pause menu would cover the HUD.
        client.options.pauseOnLostFocus = false;
        Path worldDir = client.getLevelStorage().getSavesDirectory().resolve(WORLD_NAME);
        try {
            deleteRecursively(worldDir);
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
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;

        deleteTestProfiles();
        if (!savedBindings.isEmpty()) {
            for (KeyBinding binding : client.options.allKeys) {
                String key = savedBindings.get(binding.getId());
                if (key != null) {
                    binding.setBoundKey(InputUtil.fromTranslationKey(key));
                }
            }
            KeyBinding.updateKeysByCode();
            client.options.pauseOnLostFocus = savedPauseOnLostFocus;
            if (savedLanguage != null && !savedLanguage.equals(client.getLanguageManager().getLanguage())) {
                setLanguage(client, savedLanguage);
            }
            client.options.write();
            service.saveCurrentProfile(savedCurrentProfile != null && service.profiles().containsKey(savedCurrentProfile) ? savedCurrentProfile : null);
            log("restored " + savedBindings.size() + " key bindings and current profile '" + service.getCurrentProfile() + "'");
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

    // ------------------------------------------------------------------ helpers

    private void step(String name, int waitAfter, Runnable action) {
        steps.add(new Step(name, waitAfter, action, null, 0));
    }

    private void stepUntil(String name, Runnable action, BooleanSupplier until, int timeoutTicks) {
        steps.add(new Step(name, 0, action, until, timeoutTicks));
    }

    private void open(MinecraftClient client, String name, Supplier<Screen> screen) {
        step("open " + name, SCREEN_SETTLE_TICKS, () -> client.setScreen(screen.get()));
    }

    private void shot(MinecraftClient client, String name) {
        step("screenshot " + name, 6, () -> screenshot(client, name));
    }

    private void screenshot(MinecraftClient client, String name) {
        String fileName = String.format("selftest_%02d_%s.png", ++screenshotIndex, name);
        Framebuffer framebuffer = client.getFramebuffer();
        // 4K frames are halved so the files stay small; GUI pixels are still at least 2 px wide.
        int downscale = framebuffer.textureWidth >= 3000 && framebuffer.textureWidth % 2 == 0 && framebuffer.textureHeight % 2 == 0 ? 2 : 1;
        String screenName = client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName();
        pendingScreenshots.incrementAndGet();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, fileName, framebuffer, downscale, message -> {
            pendingScreenshots.decrementAndGet();
            log("SCREENSHOT " + fileName + " screen=" + screenName + " result=" + message.getString());
        });
    }

    private void click(MinecraftClient client, String label) {
        ClickableWidget widget = findWidget(client.currentScreen, label);
        if (!(widget instanceof PressableWidget pressable)) {
            fail("no clickable widget labelled '" + label + "' on " + (client.currentScreen == null ? "no screen" : client.currentScreen.getClass().getSimpleName()));
            return;
        }
        if (!pressable.active) {
            fail("widget '" + label + "' is disabled");
            return;
        }
        pressable.onPress(new KeyInput(InputUtil.GLFW_KEY_ENTER, 0, 0));
        pass("clicked '" + label + "'");
    }

    private static ClickableWidget findWidget(ParentElement parent, String label) {
        if (parent == null) {
            return null;
        }
        for (Element element : parent.children()) {
            if (element instanceof ClickableWidget widget && label.equals(widget.getMessage().getString())) {
                return widget;
            }
            if (element instanceof ParentElement nested) {
                ClickableWidget found = findWidget(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String manageLabel() {
        return Text.translatable("keybindprofilesplus.open").getString();
    }

    private static void setLanguage(MinecraftClient client, String code) {
        // Only the in-memory language is switched; options.txt keeps the user's choice.
        client.getLanguageManager().setLanguage(code);
        client.getLanguageManager().reload(client.getResourceManager());
    }

    private static Map<String, String> readLanguage(String code) {
        Map<String, String> entries = new LinkedHashMap<>();
        Path path = FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID)
                .flatMap(mod -> mod.findPath(LANG_PATH + code + ".json")).orElse(null);
        if (path == null) {
            return entries;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
        } catch (IOException | RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "could not read language file " + code, e);
        }
        return entries;
    }

    private static int countPlaceholders(String text) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf("%s", index)) >= 0) {
            count++;
            index += 2;
        }
        return count;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private KeyBinding requireBinding() {
        return Objects.requireNonNull(KeyBinding.byId(TEST_BINDING_ID), "missing key binding " + TEST_BINDING_ID);
    }

    private void setLiveKey(KeyBinding binding, String translationKey) {
        binding.setBoundKey(InputUtil.fromTranslationKey(translationKey));
        KeyBinding.updateKeysByCode();
    }

    private File profileFile(String name) {
        return new File(service.profilesDirectory(), name + ".kbp");
    }

    private boolean optionsFileContains(MinecraftClient client, String line) {
        try {
            return Files.readAllLines(new File(client.runDirectory, "options.txt").toPath(), StandardCharsets.UTF_8).contains(line);
        } catch (IOException e) {
            return false;
        }
    }

    private List<String> sortedProfileNames() {
        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void check(String what, boolean ok) {
        if (ok) {
            pass(what);
        } else {
            fail(what);
        }
    }

    private void pass(String what) {
        passed++;
        log("PASS " + what);
    }

    private void fail(String what) {
        failed++;
        KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "FAIL " + what);
    }

    private static void log(String message) {
        KeyBindProfilesPlus.LOGGER.info(LOG_PREFIX + message);
    }

    private record Step(String name, int waitAfter, Runnable action, BooleanSupplier until, int timeoutTicks) {
    }
}
