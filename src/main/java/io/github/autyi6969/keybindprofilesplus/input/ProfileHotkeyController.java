package io.github.autyi6969.keybindprofilesplus.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import io.github.autyi6969.keybindprofilesplus.notification.ProfileNotification;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Switches to a profile when its hotkey (one or two keys held together) goes down during play.
 * Nothing happens while a screen is open, so typing in chat cannot switch profiles by accident.
 */
public final class ProfileHotkeyController {
    private final ProfileService profileService;
    private final ProfileNotification notification;
    private final Set<String> pressedHotkeys = new HashSet<>();
    private Predicate<InputUtil.Key> keyDown = ProfileHotkeyController::isPhysicallyDown;

    public ProfileHotkeyController(ProfileService profileService, ProfileNotification notification) {
        this.profileService = profileService;
        this.notification = notification;
    }

    public void tick(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }

        for (Map.Entry<String, List<String>> entry : profileService.profileHotkeys().entrySet()) {
            checkProfileHotkey(entry.getKey(), entry.getValue());
        }
    }

    /** For the self-test only: pretend these keys are held (null restores the real keyboard and mouse). */
    public void setKeyStateForTesting(Predicate<InputUtil.Key> keyState) {
        keyDown = keyState == null ? ProfileHotkeyController::isPhysicallyDown : keyState;
        pressedHotkeys.clear();
    }

    private void checkProfileHotkey(String profileName, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        String hotkeyId = profileName + "_" + keys;
        if (areAllKeysPressed(keys)) {
            applyProfile(profileName, hotkeyId);
        } else {
            pressedHotkeys.remove(hotkeyId);
        }
    }

    private boolean areAllKeysPressed(List<String> keys) {
        for (String translationKey : keys) {
            if (!isHotkeyPressed(translationKey)) {
                return false;
            }
        }
        return true;
    }

    private void applyProfile(String profileName, String hotkeyId) {
        if (pressedHotkeys.contains(hotkeyId) || profileName.equals(profileService.getCurrentProfile())) {
            return;
        }

        profileService.applyProfile(profileName);
        notification.show(profileName);
        pressedHotkeys.add(hotkeyId);
    }

    private boolean isHotkeyPressed(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return false;
        }

        InputUtil.Key key = parseInputKey(translationKey);
        return key != null && keyDown.test(key);
    }

    private static boolean isPhysicallyDown(InputUtil.Key key) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(client.getWindow(), key.getCode());
    }

    private InputUtil.Key parseInputKey(String translationKey) {
        try {
            return InputUtil.fromTranslationKey(translationKey);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
