package io.github.autyi6969.keybindprofilesplus.selftest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.gui.ApplyConfirmScreen;
import io.github.autyi6969.keybindprofilesplus.gui.KeyBindProfileScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileCompareScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileContentsScreen;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileComparison;
import io.github.autyi6969.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyi6969.keybindprofilesplus.options.OptionCatalog;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileChange;
import io.github.autyi6969.keybindprofilesplus.gui.KeyOverviewScreen;
import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
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
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
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
    private static final String PROFILE_A_KEY = "key.keyboard.k";
    private static final String PROFILE_FOV = "0.5";
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
    private String originalAutoJump;
    private String originalFov;
    private String profileAutoJump;
    private Map<String, String> savedOptions = Map.of();
    private boolean savedConfirmApply = true;
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
        step("conflicts: layered detection", 0, () -> checkConflicts(client));

        step("profile: create", 0, () -> profileCreate(client));
        step("profile: apply", 0, () -> profileApply(client));
        step("profile: rename", 0, this::profileRename);
        step("profile: reload from disk", 0, this::profileReload);
        step("contents: game options and partial profiles", 0, () -> checkProfileContents(client));
        step("compare: model", 0, () -> checkComparison(client));
        step("conflicts: set up visible examples", 0, () -> setUpVisibleConflicts(client));

        screenTour(client, "en");
        applyAndContentsFlow(client);
        step("switch language to zh_cn", SCREEN_SETTLE_TICKS, () -> setLanguage(client, "zh_cn"));
        step("chinese texts", 0, this::checkChineseTexts);
        screenTour(client, "zh");
        step("switch language back", 4, () -> setLanguage(client, savedLanguage));

        step("world: prepare auto-switch", 0, () -> {
            service.applyProfile(PROFILE_C);
            service.setProfileAutoSwitchServers(PROFILE_A, List.of("singleplayer"));
        });
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
        step("overview: conflicts only", 4, () -> {
            KeyOverviewScreen overview = (KeyOverviewScreen) client.currentScreen;
            overview.setConflictsOnly(true);
            check("overview: conflicts-only shows at least the 4 bindings set up to conflict, got " + overview.visibleBindingCount(),
                    overview.visibleBindingCount() >= 4 && overview.visibleBindingCount() < client.options.allKeys.length);
        });
        shot(client, tag + "_overview_conflicts_only");
        step("overview: all again", 2, () -> ((KeyOverviewScreen) client.currentScreen).setConflictsOnly(false));
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

        step("make the game differ from " + PROFILE_A, 0, () -> makeGameDifferFromProfileA(client));
        step("click apply (expect the confirm screen)", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.apply").getString());
            check("apply with pending changes opens the confirm screen", client.currentScreen instanceof ApplyConfirmScreen);
        });
        shot(client, tag + "_apply_confirm");
        step("confirm: cancel", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("gui.cancel").getString());
            check("confirm: cancel returns to the profile screen and applies nothing",
                    client.currentScreen instanceof KeyBindProfileScreen && TEST_KEY.equals(requireBinding().getBoundKeyTranslationKey()));
        });

        step("click saved contents", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.contents.open").getString());
            check("contents button opens the contents screen", client.currentScreen instanceof ProfileContentsScreen);
        });
        if (tag.equals("en")) {
            shot(client, tag + "_contents_collapsed");
        }
        step("contents: expand movement keys and video settings", 4, () -> {
            ProfileContentsScreen contents = (ProfileContentsScreen) client.currentScreen;
            check("contents: movement group can be expanded", contents.setExpanded("keys/minecraft:movement", true));
            check("contents: video group can be expanded", contents.setExpanded("options/video", true));
        });
        shot(client, tag + "_contents_expanded");
        step("contents: cancel", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("gui.cancel").getString());
            check("contents: cancel returns to the profile screen", client.currentScreen instanceof KeyBindProfileScreen);
        });

        step("click compare", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.compare.open").getString());
            check("compare button opens the compare screen with the selected profile on the left",
                    client.currentScreen instanceof ProfileCompareScreen compare && PROFILE_A.equals(compare.leftSide()) && compare.rightSide() == null);
        });
        step("compare: two profiles", 4, () -> ((ProfileCompareScreen) client.currentScreen).setSides(PROFILE_A, PROFILE_C));
        shot(client, tag + "_compare_all");
        if (tag.equals("en")) {
            step("compare: only differences", 4, () -> {
                ProfileCompareScreen compare = (ProfileCompareScreen) client.currentScreen;
                compare.setOnlyDifferences(true);
                check("compare: only-differences shows exactly the differing rows (" + compare.visibleRowCount() + " of " + compare.differenceCount() + ")",
                        compare.differenceCount() >= 1 && compare.visibleRowCount() == compare.differenceCount());
            });
            shot(client, tag + "_compare_only_differences");
            step("compare: identical sides", 4, () -> {
                ProfileCompareScreen compare = (ProfileCompareScreen) client.currentScreen;
                compare.setSides(PROFILE_C, PROFILE_C);
                check("compare: a profile against itself has no differences", compare.differenceCount() == 0 && compare.visibleRowCount() == 0);
            });
            shot(client, tag + "_compare_no_differences");
        }
        step("compare: done", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("gui.done").getString());
            check("compare: done returns to the profile screen", client.currentScreen instanceof KeyBindProfileScreen);
        });

        open(client, "vanilla key binds screen", () -> new KeybindsScreen(homeScreen, client.options));
        step("manage button present", 0, () -> check("vanilla Key Binds screen has the '" + manageLabel() + "' button",
                findWidget(client.currentScreen, manageLabel()) != null));
        step("vanilla list uses the layered conflict check", 0, () -> {
            String hardKey = keyLabel("key.inventory");
            String softKey = keyLabel("key.loadToolbarActivator");
            String debugKey = keyLabel("key.advancements");
            check("vanilla list: the real conflict on " + hardKey + " is marked", findWidget(client.currentScreen, "[ " + hardKey + " ]") != null);
            check("vanilla list: the possible conflict on " + softKey + " is marked", findWidget(client.currentScreen, "[ " + softKey + " ]") != null);
            check("vanilla list: sharing " + debugKey + " with an F3 combination is not marked",
                    findWidget(client.currentScreen, "[ " + debugKey + " ]") == null && findWidget(client.currentScreen, debugKey) != null);
            KeyConflicts.Summary summary = KeyConflicts.summarize(client.options);
            check("vanilla list: summary counts them (" + summary.hard() + " conflicts, " + summary.soft() + " possible)",
                    summary.hard() >= 2 && summary.soft() >= 2);
        });
        shot(client, tag + "_vanilla_keybinds_with_manage_button");
        step("compare button on the vanilla screen", SCREEN_SETTLE_TICKS, () -> {
            String compareLabel = Text.translatable("keybindprofilesplus.compare.open_short").getString();
            check("vanilla Key Binds screen has the compare button", findWidget(client.currentScreen, compareLabel) != null);
            click(client, compareLabel);
            check("it opens the compare screen against the current settings",
                    client.currentScreen instanceof ProfileCompareScreen compare && compare.rightSide() == null);
        });
        if (tag.equals("en")) {
            shot(client, tag + "_compare_from_keybinds");
        }
        step("compare: done (back to vanilla)", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("gui.done").getString());
            check("compare: done returns to the vanilla Key Binds screen", client.currentScreen instanceof KeybindsScreen);
        });
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
        savedConfirmApply = KeyBindProfilesPlus.settings().confirmApply();
        Map<String, String> optionValues = new LinkedHashMap<>();
        GameOptionsBridge.readAll(client.options).forEach((key, entry) -> {
            if (OptionCatalog.isOffered(key)) {
                optionValues.put(key, entry.rawValue());
            }
        });
        savedOptions = optionValues;
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

    /**
     * Turns selftest_a into a partial profile: it saves only the jump key plus two game settings.
     * Everything else must be left alone when it is applied.
     */
    private void checkProfileContents(MinecraftClient client) {
        Map<String, GameOptionsBridge.Entry> options = GameOptionsBridge.readAll(client.options);
        check("options: the game settings can be listed (" + options.size() + " entries)", options.size() > 60);
        check("options: fov, mouse sensitivity and auto-jump are listed",
                options.containsKey("fov") && options.containsKey("mouseSensitivity") && options.containsKey("autoJump"));
        check("options: key bindings are not listed as settings", options.keySet().stream().noneMatch(key -> key.startsWith("key_")));
        check("options: language and resource packs are never offered",
                !OptionCatalog.isOffered("lang") && !OptionCatalog.isOffered("resourcePacks") && OptionCatalog.isOffered("fov"));
        int uncategorized = 0;
        for (String key : options.keySet()) {
            if (OptionCatalog.isOffered(key) && OptionCatalog.categoryOf(key) == OptionCatalog.Category.OTHER) {
                uncategorized++;
                log("options: not in a named group: " + key);
            }
        }
        check("options: every offered setting has a named group (" + uncategorized + " in Other)", uncategorized == 0);

        originalAutoJump = options.get("autoJump").rawValue();
        originalFov = options.get("fov").rawValue();
        profileAutoJump = "true".equals(originalAutoJump) ? "false" : "true";
        log("options: fov raw=" + originalFov + " shown as [" + options.get("fov").describe(originalFov).getString()
                + "], 0.5 shown as [" + options.get("fov").describe(PROFILE_FOV).getString()
                + "], autoJump " + originalAutoJump + " shown as [" + options.get("autoJump").describe(originalAutoJump).getString() + "]");

        check("options: values are shown without repeating the name (fov 0.5 -> 90)", "90".equals(options.get("fov").describe(PROFILE_FOV).getString()));
        GameOptionsBridge.Entry chunkFade = options.get("chunkSectionFadeInTime");
        if (chunkFade != null) {
            String shown = chunkFade.describe(chunkFade.rawValue()).getString();
            check("options: shortened labels are stripped too (chunk fade shown as [" + shown + "])", !shown.contains(":"));
        }

        service.setProfileContents(PROFILE_A, Map.of(TEST_BINDING_ID, PROFILE_A_KEY), Map.of("autoJump", profileAutoJump, "fov", PROFILE_FOV));
        check("contents: profile now saves 1 key binding", service.profiles().get(PROFILE_A).size() == 1);
        check("contents: profile now saves 2 settings", service.getProfileOptions(PROFILE_A).size() == 2);
        service.reloadProfiles();
        check("contents: settings survive a reload from disk",
                PROFILE_FOV.equals(service.getProfileOptions(PROFILE_A).get("fov")) && profileAutoJump.equals(service.getProfileOptions(PROFILE_A).get("autoJump")));

        List<ProfileChange> changes = service.previewApply(PROFILE_A);
        for (ProfileChange change : changes) {
            log("preview: " + change.kind() + " " + change.name().getString() + ": " + change.from().getString() + " -> " + change.to().getString());
        }
        check("preview: exactly the 3 saved items would change, got " + changes.size(), changes.size() == 3);
        check("preview: 1 key binding and 2 settings",
                changes.stream().filter(change -> change.kind() == ProfileChange.Kind.KEY_BINDING).count() == 1
                        && changes.stream().filter(change -> change.kind() == ProfileChange.Kind.OPTION).count() == 2);
    }

    private void checkConflicts(MinecraftClient client) {
        Map<String, String> before = new LinkedHashMap<>();
        for (KeyBinding binding : client.options.allKeys) {
            before.put(binding.getId(), binding.getBoundKeyTranslationKey());
        }

        try {
            for (KeyBinding binding : client.options.allKeys) {
                binding.setBoundKey(binding.getDefaultKey());
            }
            KeyBinding.updateKeysByCode();
            KeyConflicts.Summary defaults = KeyConflicts.summarize(client.options);
            check("conflicts: the default key bindings have none (" + defaults.hard() + " / " + defaults.soft() + ")", defaults.isEmpty());

            bind("key.jump", "key.keyboard.b");
            checkConflict(client, "a normal key on B and F3+B (show hitboxes) do not conflict", "key.jump", KeyConflicts.Level.NONE, 0, null);
            bind("key.jump", "key.keyboard.1");
            checkConflict(client, "jump on 1 conflicts with hotbar slot 1 but not with F3+1", "key.jump", KeyConflicts.Level.HARD, 1, "general");
            bind("key.jump", "key.keyboard.space");

            bind("key.drop", "key.keyboard.e");
            checkConflict(client, "drop and inventory on E is a real conflict", "key.drop", KeyConflicts.Level.HARD, 1, "general");
            checkConflict(client, "... seen from the other binding too", "key.inventory", KeyConflicts.Level.HARD, 1, "general");
            KeyConflicts.Summary oneClash = KeyConflicts.summarize(client.options);
            check("conflicts: summary counts both bindings of the pair", oneClash.hard() == 2 && oneClash.soft() == 0);
            bind("key.drop", "key.keyboard.q");

            bind("key.sprint", "key.keyboard.x");
            checkConflict(client, "sprint on X and the Creative-only load-hotbar key is a possible conflict", "key.sprint", KeyConflicts.Level.SOFT, 1, "creative");
            bind("key.sprint", "key.keyboard.left.control");

            bind("key.spectatorOutlines", "key.mouse.middle");
            checkConflict(client, "two Spectator-only keys clash, pick block (never in Spectator) does not", "key.spectatorOutlines", KeyConflicts.Level.HARD, 1, "spectator_both");
            bind("key.spectatorOutlines", "key.keyboard.space");
            checkConflict(client, "a Spectator-only key and jump is a possible conflict", "key.spectatorOutlines", KeyConflicts.Level.SOFT, 1, "spectator");
            bind("key.spectatorOutlines", "key.keyboard.unknown");

            bind("key.debug.reloadChunk", "key.keyboard.b");
            checkConflict(client, "two F3 combinations on the same key conflict", "key.debug.reloadChunk", KeyConflicts.Level.HARD, 1, "debug");
            bind("key.debug.reloadChunk", "key.keyboard.a");

            bind("key.jump", "key.keyboard.f3");
            checkConflict(client, "a normal key on F3 itself conflicts with the debug keys", "key.jump", KeyConflicts.Level.HARD, 2, "general");
            bind("key.jump", "key.keyboard.space");

            bind(DEMO_MOD_BINDING, "key.keyboard.q");
            checkConflict(client, "a mod key on the same key as a vanilla one conflicts", DEMO_MOD_BINDING, KeyConflicts.Level.HARD, 1, "general");
        } finally {
            before.forEach(this::bind);
        }
    }

    private void checkConflict(MinecraftClient client, String what, String bindingId, KeyConflicts.Level level, int count, String reason) {
        List<KeyConflicts.Conflict> conflicts = KeyConflicts.conflictsOf(Objects.requireNonNull(KeyBinding.byId(bindingId)), client.options);
        boolean ok = KeyConflicts.worst(conflicts) == level && conflicts.size() == count
                && (reason == null || conflicts.stream().anyMatch(conflict -> conflict.reason().equals(reason)));
        StringBuilder found = new StringBuilder();
        for (KeyConflicts.Conflict conflict : conflicts) {
            found.append(" [").append(conflict.level()).append(" ").append(conflict.other().getId()).append(" ").append(conflict.reason()).append("]");
        }
        check("conflicts: " + what + (ok ? "" : " - got" + found), ok);
    }

    private void bind(String bindingId, String translationKey) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        if (binding != null) {
            binding.setBoundKey(InputUtil.fromTranslationKey(translationKey));
            KeyBinding.updateKeysByCode();
        }
    }

    private static String keyLabel(String bindingId) {
        return Objects.requireNonNull(KeyBinding.byId(bindingId)).getBoundKeyLocalizedText().getString();
    }

    /**
     * Leaves three situations in the live key bindings for the screenshots: a real conflict
     * (drop on the inventory key), a possible one (sprint on the Creative load-hotbar key) and a
     * harmless overlap with an F3 combination (advancements on a key only F3 combinations use).
     */
    private void setUpVisibleConflicts(MinecraftClient client) {
        KeySourceResolver sources = KeyConflicts.sources(client.options);
        // F13 / F14 are used so the examples do not depend on the key layout the dev client happens to have.
        bind("key.inventory", "key.keyboard.f13");
        bind("key.drop", "key.keyboard.f13");
        bind("key.loadToolbarActivator", "key.keyboard.f14");
        bind("key.sprint", "key.keyboard.f14");
        for (KeyBinding debug : client.options.allKeys) {
            if (KeyConflicts.scopeOf(debug, sources) != KeyConflicts.Scope.DEBUG_COMBO || debug.isUnbound()) {
                continue;
            }
            boolean usedElsewhere = false;
            for (KeyBinding other : client.options.allKeys) {
                if (other != debug && other.equals(debug) && KeyConflicts.scopeOf(other, sources) != KeyConflicts.Scope.DEBUG_COMBO) {
                    usedElsewhere = true;
                    break;
                }
            }
            if (!usedElsewhere) {
                bind("key.advancements", debug.getBoundKeyTranslationKey());
                log("conflicts: advancements now shares " + keyLabel("key.advancements") + " with F3 combination " + debug.getId());
                break;
            }
        }
        check("conflicts: advancements shares its key only with an F3 combination",
                KeyConflicts.conflictsOf(Objects.requireNonNull(KeyBinding.byId("key.advancements")), client.options).isEmpty());
    }

    private void checkComparison(MinecraftClient client) {
        int savedByC = service.profiles().get(PROFILE_C).size();
        ProfileComparison.Result partial = ProfileComparison.compare(service, client.options, PROFILE_A, PROFILE_C);
        check("compare: " + PROFILE_A + " vs " + PROFILE_C + " differ only in the jump key, got " + partial.different(), partial.different() == 1);
        check("compare: items saved by one side only are counted separately (" + partial.oneSided() + ")",
                partial.oneSided() == savedByC - 1 + 2);

        ProfileComparison.Result live = ProfileComparison.compare(service, client.options, PROFILE_C, null);
        check("compare: the applied profile matches the current settings", live.different() == 0 && live.oneSided() == 0);
        setLiveKey(requireBinding(), PROFILE_A_KEY);
        check("compare: changing a key in the game shows up as 1 difference",
                ProfileComparison.compare(service, client.options, PROFILE_C, null).different() == 1);
        setLiveKey(requireBinding(), TEST_KEY);
    }

    private void mouseClick(MinecraftClient client, int[] point) {
        if (point == null || client.currentScreen == null) {
            fail("nothing to click at");
            return;
        }
        client.currentScreen.mouseClicked(new Click(point[0], point[1], new MouseInput(0, 0)), false);
    }

    /** Puts the live game back into a state where applying selftest_a changes exactly its 3 items. */
    private void makeGameDifferFromProfileA(MinecraftClient client) {
        setLiveKey(requireBinding(), TEST_KEY);
        GameOptionsBridge.apply(client.options, Map.of("autoJump", originalAutoJump, "fov", originalFov));
    }

    private String liveOption(MinecraftClient client, String key) {
        return GameOptionsBridge.readAll(client.options).get(key).rawValue();
    }

    /** Clicks through Apply (with and without the confirm screen) and the contents screen for real. */
    private void applyAndContentsFlow(MinecraftClient client) {
        KeyBinding drop = Objects.requireNonNull(KeyBinding.byId("key.drop"));
        String[] dropBefore = new String[1];

        open(client, "profile screen", () -> new KeyBindProfileScreen(null));
        step("flow: select " + PROFILE_A, 4, () -> click(client, PROFILE_A));
        step("flow: prepare", 0, () -> {
            makeGameDifferFromProfileA(client);
            dropBefore[0] = drop.getBoundKeyTranslationKey();
            setLiveKey(drop, "key.keyboard.x");
            KeyBindProfilesPlus.settings().setConfirmApply(true);
        });
        step("flow: apply -> confirm screen", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.apply").getString());
            check("flow: confirm screen is shown", client.currentScreen instanceof ApplyConfirmScreen);
        });
        step("flow: confirm", SCREEN_SETTLE_TICKS, () -> {
            if (client.currentScreen instanceof ApplyConfirmScreen confirm) {
                confirm.confirm(false);
            }
            check("flow: back on the profile screen", client.currentScreen instanceof KeyBindProfileScreen);
            check("flow: saved key applied (" + TEST_BINDING_ID + " = " + PROFILE_A_KEY + ")", PROFILE_A_KEY.equals(requireBinding().getBoundKeyTranslationKey()));
            check("flow: saved auto-jump applied", profileAutoJump.equals(liveOption(client, "autoJump")));
            check("flow: saved fov applied", PROFILE_FOV.equals(liveOption(client, "fov")));
            check("flow: fov really changed in the game", Math.abs(client.options.getFov().getValue() - 90) <= 1);
            check("flow: a key the profile does not save is left alone", "key.keyboard.x".equals(drop.getBoundKeyTranslationKey()));
            check("flow: profile became current", PROFILE_A.equals(service.getCurrentProfile()));
            check("flow: nothing left to change", service.previewApply(PROFILE_A).isEmpty());
            check("flow: confirm setting untouched", KeyBindProfilesPlus.settings().confirmApply());
            check("flow: options.txt has the new fov", optionsFileContains(client, "fov:" + PROFILE_FOV));
        });
        step("flow: apply again (nothing to change, no dialog)", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.apply").getString());
            check("flow: no confirm screen when nothing would change", client.currentScreen instanceof KeyBindProfileScreen);
        });
        step("flow: apply with do-not-ask-again", SCREEN_SETTLE_TICKS, () -> {
            makeGameDifferFromProfileA(client);
            click(client, Text.translatable("keybindprofilesplus.apply").getString());
            if (client.currentScreen instanceof ApplyConfirmScreen confirm) {
                confirm.confirm(true);
            } else {
                fail("flow: expected the confirm screen");
            }
            check("flow: do-not-ask-again is remembered", !KeyBindProfilesPlus.settings().confirmApply());
        });
        step("flow: apply without asking", SCREEN_SETTLE_TICKS, () -> {
            makeGameDifferFromProfileA(client);
            click(client, Text.translatable("keybindprofilesplus.apply").getString());
            check("flow: applied directly, no confirm screen", client.currentScreen instanceof KeyBindProfileScreen
                    && PROFILE_A_KEY.equals(requireBinding().getBoundKeyTranslationKey()));
            KeyBindProfilesPlus.settings().reload();
            check("flow: the choice is stored on disk", !KeyBindProfilesPlus.settings().confirmApply());
            KeyBindProfilesPlus.settings().setConfirmApply(true);
            setLiveKey(drop, dropBefore[0]);
        });

        step("flow: open contents", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.contents.open").getString());
            check("flow: contents screen is shown", client.currentScreen instanceof ProfileContentsScreen);
        });
        // Real mouse clicks, one per step so the rows are laid out again in between.
        step("flow: click a group label", 4, () -> {
            mouseClick(client, ((ProfileContentsScreen) client.currentScreen).hitPoint("options/video", false));
            check("flow: clicking a group label expands it", ((ProfileContentsScreen) client.currentScreen).isExpanded("options/video"));
        });
        step("flow: click a check box", 4, () -> {
            mouseClick(client, ((ProfileContentsScreen) client.currentScreen).hitPoint("opt:ao", true));
            check("flow: clicking a check box ticks the item", ((ProfileContentsScreen) client.currentScreen).isChecked("opt:ao"));
        });
        step("flow: click an item label", 4, () -> {
            mouseClick(client, ((ProfileContentsScreen) client.currentScreen).hitPoint("opt:ao", false));
            check("flow: clicking the item again unticks it", !((ProfileContentsScreen) client.currentScreen).isChecked("opt:ao"));
        });
        step("flow: click a group check box", 4, () -> {
            ProfileContentsScreen contents = (ProfileContentsScreen) client.currentScreen;
            mouseClick(client, contents.hitPoint("keys/minecraft:multiplayer", true));
            check("flow: clicking a group check box ticks the whole group and does not expand it",
                    contents.checkedCount(true) == 5 && !contents.isExpanded("keys/minecraft:multiplayer"));
        });
        step("flow: click the group check box again", 4, () -> {
            ProfileContentsScreen contents = (ProfileContentsScreen) client.currentScreen;
            mouseClick(client, contents.hitPoint("keys/minecraft:multiplayer", true));
            check("flow: clicking it again unticks the group", contents.checkedCount(true) == 1);
            contents.setExpanded("options/video", false);
        });
        step("flow: tick more items", 4, () -> {
            ProfileContentsScreen contents = (ProfileContentsScreen) client.currentScreen;
            check("flow: contents starts with 1 key and 2 settings", contents.checkedCount(true) == 1 && contents.checkedCount(false) == 2);
            check("flow: tick brightness", contents.setChecked("opt:gamma", true));
            check("flow: tick the whole movement group", contents.setChecked("keys/minecraft:movement", true));
            check("flow: untick auto-jump", contents.setChecked("opt:autoJump", false));
            check("flow: 7 movement keys and 2 settings ticked, got " + contents.checkedCount(true) + " and " + contents.checkedCount(false),
                    contents.checkedCount(true) == 7 && contents.checkedCount(false) == 2);
            contents.setQuery("sensitivity");
            check("flow: search narrows the tree, rows=" + contents.visibleRowCount(), contents.visibleRowCount() > 0 && contents.visibleRowCount() < 10);
        });
        shot(client, "en_contents_search");
        step("flow: save contents", SCREEN_SETTLE_TICKS, () -> {
            ProfileContentsScreen contents = (ProfileContentsScreen) client.currentScreen;
            contents.setQuery("");
            contents.save();
            check("flow: back on the profile screen", client.currentScreen instanceof KeyBindProfileScreen);
            Map<String, String> keys = service.profiles().get(PROFILE_A);
            Map<String, String> saved = service.getProfileOptions(PROFILE_A);
            check("flow: profile saves the 7 movement keys", keys.size() == 7 && keys.containsKey("key.sneak"));
            check("flow: the previously saved jump key is kept", PROFILE_A_KEY.equals(keys.get(TEST_BINDING_ID)));
            check("flow: profile saves fov and brightness, not auto-jump",
                    saved.size() == 2 && saved.containsKey("fov") && saved.containsKey("gamma") && !saved.containsKey("autoJump"));
            setLiveKey(requireBinding(), TEST_KEY);
        });
        step("flow: reopen contents", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("keybindprofilesplus.contents.open").getString());
            if (client.currentScreen instanceof ProfileContentsScreen contents) {
                contents.setExpanded("keys/minecraft:movement", true);
            } else {
                fail("flow: contents screen did not open");
            }
        });
        shot(client, "en_contents_saved_differs_from_now");
        step("flow: use current values", SCREEN_SETTLE_TICKS, () -> {
            ProfileContentsScreen contents = (ProfileContentsScreen) client.currentScreen;
            contents.recapture();
            contents.save();
            check("flow: use-current-values stored the live jump key", TEST_KEY.equals(service.profiles().get(PROFILE_A).get(TEST_BINDING_ID)));
        });
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
            GameOptionsBridge.apply(client.options, savedOptions);
            KeyBindProfilesPlus.settings().setConfirmApply(savedConfirmApply);
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
