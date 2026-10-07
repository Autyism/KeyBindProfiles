package io.github.autyism.keybindprofilesplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Records the hotkey that switches to a profile: one key, or two keys held together.
 * While recording, Enter saves, Backspace removes the hotkey and Escape cancels.
 */
final class ProfileHotkeyCapture {
    private static final int MAX_KEYS = 2;

    private final ProfileService service;
    private final List<String> capturedKeys = new ArrayList<>();
    private String profileName;

    ProfileHotkeyCapture(ProfileService service) {
        this.service = service;
    }

    boolean isCapturing() {
        return profileName != null;
    }

    Component getButtonText(String profile) {
        if (isCapturing()) {
            return capturedKeys.isEmpty() ? Component.translatable("keybindprofilesplus.hotkey.press") : Component.literal(formatKeys(capturedKeys) + " ...");
        }
        List<String> keys = service.getProfileHotkey(profile);
        return keys == null || keys.isEmpty() ? Component.translatable("keybindprofilesplus.hotkey.none") : Component.literal(formatKeys(keys));
    }

    /** Starts recording for the profile, or saves what was recorded when already recording. */
    void toggle(String profile) {
        if (isCapturing()) {
            saveCapturedKeys();
            clear();
            return;
        }
        profileName = profile;
        capturedKeys.clear();
    }

    /** Tells the capture that the profile it records for now has a different name. */
    void cancel() {
        clear();
    }

    boolean handleKeyPressed(KeyEvent input) {
        if (!isCapturing()) {
            return false;
        }

        int keyCode = input.key();
        if (keyCode == InputConstants.KEY_ESCAPE) {
            clear();
            return true;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE) {
            service.setProfileHotkey(profileName, null);
            clear();
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN) {
            saveCapturedKeys();
            clear();
            return true;
        }

        addCapturedKey(InputConstants.getKey(input).getName());
        return true;
    }

    boolean handleMouseClicked(int button) {
        if (!isCapturing()) {
            return false;
        }
        addCapturedKey(InputConstants.Type.MOUSE.getOrCreate(button).getName());
        return true;
    }

    static String formatKeys(List<String> keys) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                text.append(" + ");
            }
            try {
                text.append(InputConstants.getKey(keys.get(i)).getDisplayName().getString());
            } catch (IllegalArgumentException e) {
                text.append(keys.get(i));
            }
        }
        return text.toString();
    }

    private void addCapturedKey(String translationKey) {
        if (!capturedKeys.contains(translationKey) && capturedKeys.size() < MAX_KEYS) {
            capturedKeys.add(translationKey);
        }
    }

    private void saveCapturedKeys() {
        if (!capturedKeys.isEmpty()) {
            service.setProfileHotkey(profileName, new ArrayList<>(capturedKeys));
        }
    }

    private void clear() {
        profileName = null;
        capturedKeys.clear();
    }
}
