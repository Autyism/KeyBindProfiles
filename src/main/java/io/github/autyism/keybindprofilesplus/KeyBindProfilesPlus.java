package io.github.autyism.keybindprofilesplus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import io.github.autyism.keybindprofilesplus.gui.ConflictSummaryOverlay;
import io.github.autyism.keybindprofilesplus.gui.ControlsScreenProfileButton;
import io.github.autyism.keybindprofilesplus.gui.KeyBindProfileScreen;
import io.github.autyism.keybindprofilesplus.input.ComboRecorder;
import io.github.autyism.keybindprofilesplus.input.ProfileHotkeyController;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.notification.ProfileNoticeHud;
import io.github.autyism.keybindprofilesplus.notification.ProfileNotification;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.selftest.SelfTest;
import io.github.autyism.keybindprofilesplus.server.ServerAutoSwitchController;
import io.github.autyism.keybindprofilesplus.storage.LegacyOptions;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import io.github.autyism.keybindprofilesplus.storage.ProfileFileStore;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class KeyBindProfilesPlus implements ClientModInitializer {
    public static final String MOD_ID = "keybindprofilesplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ProfileNotification NOTIFICATION = new ProfileNotification();
    private static final ProfileNoticeHud NOTICE_HUD = new ProfileNoticeHud(NOTIFICATION);
    private static final ProfileService PROFILE_SERVICE = new ProfileService(new ProfileFileStore());
    private static final ModSettings SETTINGS = new ModSettings(PROFILE_SERVICE::profilesDirectory);
    private static final ProfileHotkeyController HOTKEY_CONTROLLER = new ProfileHotkeyController(PROFILE_SERVICE, NOTIFICATION);
    private static final ServerAutoSwitchController AUTO_SWITCH_CONTROLLER = new ServerAutoSwitchController(PROFILE_SERVICE, NOTIFICATION, SETTINGS);

    public static final Map<String, Map<String, String>> PROFILES = PROFILE_SERVICE.profiles();
    public static final Map<String, List<String>> PROFILE_HOTKEYS = PROFILE_SERVICE.profileHotkeys();
    public static final Map<String, List<String>> PROFILE_AUTO_SWITCH_SERVERS = PROFILE_SERVICE.profileAutoSwitchServers();

    private static KeyBinding openProfileScreenKey;
    private static String legacyOpenKey;

    @Override
    public void onInitializeClient() {
        // Must be read now: Minecraft drops unknown entries the next time it writes options.txt.
        legacyOpenKey = LegacyOptions.readLegacyOpenKey(FabricLoader.getInstance().getGameDir().toFile());
        PROFILE_SERVICE.setAutoSwitchResetCallback(AUTO_SWITCH_CONTROLLER::reset);
        registerOpenScreenKey();
        registerClientEvents();
        registerConnectionEvents();
        registerControlsScreenButton();
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "profile_notice"), NOTICE_HUD::render);
        loadProfilesOnClientStart();
        if (SelfTest.isRequested()) {
            SelfTest.install(PROFILE_SERVICE);
        }
    }

    public static ProfileService profileService() {
        return PROFILE_SERVICE;
    }

    public static ModSettings settings() {
        return SETTINGS;
    }

    public static ServerAutoSwitchController autoSwitchController() {
        return AUTO_SWITCH_CONTROLLER;
    }

    public static ProfileHotkeyController hotkeyController() {
        return HOTKEY_CONTROLLER;
    }

    public static void openConfigScreen(Screen parent) {
        MinecraftClient.getInstance().setScreen(new KeyBindProfileScreen(parent));
    }

    public static void reloadProfilesFromDirectory() {
        PROFILE_SERVICE.reloadProfiles();
    }

    public static void saveProfile(String name, KeyBinding[] bindings) {
        PROFILE_SERVICE.saveProfile(name, bindings);
    }

    public static void applyProfile(String name) {
        PROFILE_SERVICE.applyProfile(name);
    }

    public static void deleteProfile(String name) {
        PROFILE_SERVICE.deleteProfile(name);
        if (name.equals(SETTINGS.defaultProfile())) {
            SETTINGS.setDefaultProfile(null);
        }
    }

    public static boolean renameProfile(String oldName, String newName) {
        if (!PROFILE_SERVICE.renameProfile(oldName, newName)) {
            return false;
        }
        if (oldName.equals(SETTINGS.defaultProfile())) {
            SETTINGS.setDefaultProfile(newName);
        }
        return true;
    }

    public static void exportProfile(String name) {
        PROFILE_SERVICE.exportProfile(name);
    }

    public static void setProfileHotkey(String profileName, List<String> keys) {
        PROFILE_SERVICE.setProfileHotkey(profileName, keys);
    }

    public static List<String> getProfileHotkey(String profileName) {
        return PROFILE_SERVICE.getProfileHotkey(profileName);
    }

    public static void setProfileAutoSwitchServers(String profileName, List<String> servers) {
        PROFILE_SERVICE.setProfileAutoSwitchServers(profileName, servers);
    }

    public static List<String> getProfileAutoSwitchServers(String profileName) {
        return PROFILE_SERVICE.getProfileAutoSwitchServers(profileName);
    }

    public static void loadProfiles() {
        PROFILE_SERVICE.loadProfiles();
    }

    public static void saveCurrentProfile(String profile) {
        PROFILE_SERVICE.saveCurrentProfile(profile);
    }

    public static String getCurrentProfile() {
        return PROFILE_SERVICE.getCurrentProfile();
    }

    public static boolean openProfilesFolder() {
        return PROFILE_SERVICE.openProfilesFolder();
    }

    public static void showNotification(String profileName) {
        NOTIFICATION.show(profileName);
    }

    public static String getNotificationText() {
        return NOTIFICATION.getVisibleProfileName();
    }

    private static void registerOpenScreenKey() {
        openProfileScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.keybindprofilesplus.open",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_O,
                KeyBinding.Category.MISC
        ));
    }

    private static void registerClientEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            openProfileScreenWhenKeyPressed();
            HOTKEY_CONTROLLER.tick(client);
            AUTO_SWITCH_CONTROLLER.tick(client);
        });
    }

    private static void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            AUTO_SWITCH_CONTROLLER.reset();
            AUTO_SWITCH_CONTROLLER.tick(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            AUTO_SWITCH_CONTROLLER.reset();
            AUTO_SWITCH_CONTROLLER.onLeave();
        }));
    }

    private static void registerControlsScreenButton() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof KeybindsScreen) {
                ControlsScreenProfileButton.addOrReplace(screen, scaledWidth, scaledHeight);
                // The vanilla screen moves its own buttons back on a window resize without
                // re-initialising, so lay the row out again whenever the size has changed.
                ComboRecorder.install((KeybindsScreen) screen);
                ConflictSummaryOverlay conflictSummary = new ConflictSummaryOverlay();
                ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) -> conflictSummary.render(current, context));
                int[] lastSize = {scaledWidth, scaledHeight};
                ScreenEvents.beforeRender(screen).register((current, context, mouseX, mouseY, tickDelta) -> {
                    if (current.width != lastSize[0] || current.height != lastSize[1]) {
                        lastSize[0] = current.width;
                        lastSize[1] = current.height;
                        ControlsScreenProfileButton.addOrReplace(current, current.width, current.height);
                    }
                });
            }
        });
    }

    private static void loadProfilesOnClientStart() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            carryOverLegacyOpenKey(client);
            KeyCombos.load(PROFILE_SERVICE.profilesDirectory());
            PROFILE_SERVICE.loadProfiles();
            PROFILE_SERVICE.loadCurrentProfile();
        });
    }

    private static void carryOverLegacyOpenKey(MinecraftClient client) {
        if (legacyOpenKey == null || openProfileScreenKey == null || !openProfileScreenKey.isDefault()) {
            return;
        }

        try {
            openProfileScreenKey.setBoundKey(InputUtil.fromTranslationKey(legacyOpenKey));
            KeyBinding.updateKeysByCode();
            client.options.write();
            LOGGER.info("Carried over the 'open profiles' key from KeyBindProfiles: {}", legacyOpenKey);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not carry over the old 'open profiles' key '{}'", legacyOpenKey, e);
        }
        legacyOpenKey = null;
    }

    private static void openProfileScreenWhenKeyPressed() {
        while (openProfileScreenKey.wasPressed()) {
            openConfigScreen(null);
        }
    }
}
