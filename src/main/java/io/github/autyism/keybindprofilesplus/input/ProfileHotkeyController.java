package io.github.autyism.keybindprofilesplus.input;

//? if <26.3
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.notification.ProfileNotification;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;

/**
 * Switches to a profile when its hotkey (one or two keys held together) goes down during play.
 * Nothing happens while a screen is open, so typing in chat cannot switch profiles by accident.
 */
public final class ProfileHotkeyController {
    private final ProfileService profileService;
    private final ProfileNotification notification;
    private final Set<String> pressedHotkeys = new HashSet<>();
    private Predicate<InputConstants.Key> keyDown = ProfileHotkeyController::isPhysicallyDown;

    public ProfileHotkeyController(ProfileService profileService, ProfileNotification notification) {
        this.profileService = profileService;
        this.notification = notification;
    }

    public void tick(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }
        if (client.screen != null) {
            return;
        }

        for (Map.Entry<String, List<String>> entry : profileService.profileHotkeys().entrySet()) {
            checkProfileHotkey(entry.getKey(), entry.getValue());
        }
    }

    /** For the self-test only: pretend these keys are held (null restores the real keyboard and mouse). */
    public void setKeyStateForTesting(Predicate<InputConstants.Key> keyState) {
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

        InputConstants.Key key = parseInputKey(translationKey);
        return key != null && keyDown.test(key);
    }

    private static boolean isPhysicallyDown(InputConstants.Key key) {
        //? if >=26.3 {
        /*if (key.getType() == InputConstants.Type.MOUSE) {
            return SdlKeys.isMouseButtonDown(key.getValue());
        }
        return InputConstants.isKeyDown(key.getValue());
        *///?} else {
        Minecraft client = Minecraft.getInstance();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(client.getWindow(), key.getValue());
        //?}
    }

    private InputConstants.Key parseInputKey(String translationKey) {
        try {
            //? if >=26.3
            /*translationKey = SdlKeys.toGameName(translationKey);*/
            return InputConstants.getKey(translationKey);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
