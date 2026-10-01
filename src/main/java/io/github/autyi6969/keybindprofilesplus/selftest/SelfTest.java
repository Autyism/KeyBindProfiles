package io.github.autyi6969.keybindprofilesplus.selftest;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.text.Text;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.gui.KeyBindProfileScreen;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.storage.LegacyOptions;
import io.github.autyi6969.keybindprofilesplus.storage.ProfileFileStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Development-only automated check. It only runs when the dev client is started with
 * {@code -Dkbp.selftest=true} (see the {@code runSelfTest} Gradle task and tools/selftest.ps1).
 * Without that property nothing here is ever registered, so release jars are unaffected.
 *
 * <p>Once the client reaches the main menu it opens every screen of the mod, saves a screenshot of
 * each into run/screenshots/selftest_*.png, runs a create / apply / rename / delete round trip on
 * throwaway profiles named selftest_*, logs every result with the prefix [SelfTest], restores the
 * previous key bindings and quits the game.
 */
public final class SelfTest {
    public static final String PROPERTY = "kbp.selftest";
    private static final String LOG_PREFIX = "[SelfTest] ";
    private static final String PROFILE_PREFIX = "selftest_";
    private static final String PROFILE_A = PROFILE_PREFIX + "a";
    private static final String PROFILE_B = PROFILE_PREFIX + "b";
    private static final String PROFILE_C = PROFILE_PREFIX + "c";
    private static final String TEST_BINDING_ID = "key.jump";
    private static final String TEST_KEY = "key.keyboard.j";
    private static final int READY_TICKS = 40;
    private static final int SCREEN_SETTLE_TICKS = 12;
    private static final int MAX_RUN_TICKS = 20 * 180;

    private final ProfileService service;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final Map<String, String> savedBindings = new LinkedHashMap<>();
    private final AtomicInteger pendingScreenshots = new AtomicInteger();

    private String savedCurrentProfile;
    private String originalTestKey;
    private Screen homeScreen;
    private boolean started;
    private boolean finished;
    private boolean cleanedUp;
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
        ClientTickEvents.END_CLIENT_TICK.register(selfTest::tick);
        log("installed, waiting for the main menu");
    }

    // ------------------------------------------------------------------ driver

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
            log("START steps=" + steps.size());
            return;
        }

        if (++runTicks > MAX_RUN_TICKS) {
            fail("self-test exceeded " + MAX_RUN_TICKS + " ticks, aborting");
            steps.clear();
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

        try {
            log("STEP " + step.name());
            step.action().run();
        } catch (Throwable t) {
            fail(step.name() + " threw " + t);
            KeyBindProfilesPlus.LOGGER.error(LOG_PREFIX + "exception in step '" + step.name() + "'", t);
        }
        waitTicks = step.waitAfter();
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
        step("snapshot current key bindings", 0, () -> snapshot(client));
        step("identity", 0, this::checkIdentity);
        step("migration from upstream KeyBindProfiles", 0, this::checkMigration);

        step("profile: create", 0, () -> profileCreate(client));
        step("profile: apply", 0, () -> profileApply(client));
        step("profile: rename", 0, this::profileRename);
        step("profile: reload from disk", 0, this::profileReload);

        open(client, "profile screen", () -> new KeyBindProfileScreen(null));
        shot(client, "profiles_nothing_selected");
        step("click profile " + PROFILE_A, 4, () -> click(client, PROFILE_A));
        shot(client, "profiles_profile_selected");

        open(client, "vanilla key binds screen", () -> new KeybindsScreen(homeScreen, client.options));
        step("manage button present", 0, () -> check("vanilla Key Binds screen has the '" + manageLabel() + "' button",
                findWidget(client.currentScreen, manageLabel()) != null));
        shot(client, "vanilla_keybinds_with_manage_button");
        step("click manage button", SCREEN_SETTLE_TICKS, () -> {
            click(client, manageLabel());
            check("manage button opens the profile screen", client.currentScreen instanceof KeyBindProfileScreen);
        });
        shot(client, "profiles_opened_from_keybinds");
        step("click done", SCREEN_SETTLE_TICKS, () -> {
            click(client, Text.translatable("gui.done").getString());
            check("done returns to the vanilla Key Binds screen", client.currentScreen instanceof KeybindsScreen);
        });

        step("profile: delete", 0, this::profileDelete);
    }

    private void logEnvironment(MinecraftClient client) {
        var window = client.getWindow();
        log("ENV minecraft=" + net.minecraft.SharedConstants.getGameVersion().name()
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

            for (File dir : List.of(legacy, target)) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        Files.deleteIfExists(file.toPath());
                    }
                }
                Files.deleteIfExists(dir.toPath());
            }
            Files.deleteIfExists(root.toPath());
        } catch (IOException e) {
            fail("migration check could not use a temp folder: " + e);
        }

        check("old 'open' key is carried over", "key.keyboard.p".equals(LegacyOptions.findLegacyOpenKey(
                List.of("fov:0.0", "key_key.keybindprofiles.open:key.keyboard.p", "key_key.jump:key.keyboard.space"))));
        check("old 'open' key is ignored once the new entry exists", LegacyOptions.findLegacyOpenKey(
                List.of("key_key.keybindprofiles.open:key.keyboard.p", "key_key.keybindprofilesplus.open:key.keyboard.o")) == null);
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
        service.setProfileHotkey(PROFILE_B, List.of("key.keyboard.f6"));
        service.setProfileAutoSwitchServers(PROFILE_B, List.of("example.org", "*.selftest.example"));

        check("rename " + PROFILE_B + " -> " + PROFILE_C + ": accepted", service.renameProfile(PROFILE_B, PROFILE_C));
        check("rename: old name gone", !service.profiles().containsKey(PROFILE_B) && !profileFile(PROFILE_B).exists());
        check("rename: new name present", service.profiles().containsKey(PROFILE_C) && profileFile(PROFILE_C).isFile());
        check("rename: current profile follows", PROFILE_C.equals(service.getCurrentProfile()));
        check("rename: hotkey kept", List.of("key.keyboard.f6").equals(service.getProfileHotkey(PROFILE_C)));
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
        check("reload: hotkey read back", List.of("key.keyboard.f6").equals(service.getProfileHotkey(PROFILE_C)));
        check("reload: servers read back", List.of("example.org", "*.selftest.example").equals(service.getProfileAutoSwitchServers(PROFILE_C)));
    }

    private void profileDelete() {
        service.deleteProfile(PROFILE_A);
        service.deleteProfile(PROFILE_C);
        check("delete: profiles removed from memory", !service.profiles().containsKey(PROFILE_A) && !service.profiles().containsKey(PROFILE_C));
        check("delete: files removed", !profileFile(PROFILE_A).exists() && !profileFile(PROFILE_C).exists());
        check("delete: current profile cleared", service.getCurrentProfile() == null);
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
        client.setScreen(homeScreen);
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
        steps.add(new Step(name, waitAfter, action));
    }

    private void open(MinecraftClient client, String name, java.util.function.Supplier<Screen> screen) {
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

    private record Step(String name, int waitAfter, Runnable action) {
    }
}
