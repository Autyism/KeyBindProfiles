package io.github.autyism.keybindprofilesplus.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;

/**
 * Lets the vanilla Key Binds screen record combinations. Vanilla binds whatever key goes down
 * first, so holding Ctrl to press Ctrl+X would bind "Left Control". Here a modifier key that goes
 * down while a binding waits for its key is held back: if another key follows, the two become a
 * combination; if the modifier is released on its own, it is bound as a plain key after all.
 */
public final class ComboRecorder {
    /** Modifiers pressed since the binding started waiting, as a {@link KeyCombo} bit mask. */
    private static int pendingModifiers;
    private static KeyMapping pendingFor;

    private ComboRecorder() {
    }

    public static void install(KeyBindsScreen screen) {
        //? if >=1.21.9 {
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, input) -> onKeyPress(screen, input));
        ScreenKeyboardEvents.allowKeyRelease(screen).register((current, input) -> onKeyRelease(screen, input));
        ScreenMouseEvents.allowMouseClick(screen).register((current, click) -> onMouseClick(screen, click));
        //?} else {
        /*// Before 1.21.9 the events came as plain numbers, clicks without modifier keys (the ones held right now)
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, key, scancode, modifiers) -> onKeyPress(screen, new KeyEvent(key, scancode, modifiers)));
        ScreenKeyboardEvents.allowKeyRelease(screen).register((current, key, scancode, modifiers) -> onKeyRelease(screen, new KeyEvent(key, scancode, modifiers)));
        ScreenMouseEvents.allowMouseClick(screen).register((current, mouseX, mouseY, button) -> onMouseClick(screen,
                new MouseButtonEvent(mouseX, mouseY, new io.github.autyism.keybindprofilesplus.legacy.MouseButtonInfo(button, KeyCombos.heldModifiers()))));
        *///?}
    }

    /** The modifiers held back for the binding that is waiting for its key; 0 when there are none. */
    public static int pendingModifiers(KeyMapping binding) {
        return binding != null && binding == pendingFor ? pendingModifiers : 0;
    }

    /** Returns false when the key press was handled here and vanilla must not see it. */
    static boolean onKeyPress(KeyBindsScreen screen, KeyEvent input) {
        KeyMapping binding = screen.selectedKey;
        if (binding == null) {
            reset();
            return true;
        }
        if (input.isEscape()) {
            // Vanilla unbinds on Escape; changing the key drops the combination as well.
            reset();
            return true;
        }

        int modifier = KeyCombo.modifierOfKeyCode(input.key());
        if (modifier != 0) {
            if (pendingFor != binding) {
                pendingModifiers = 0;
            }
            pendingFor = binding;
            pendingModifiers |= modifier;
            refresh(screen);
            return false;
        }

        finish(screen, binding, InputConstants.getKey(input), input.modifiers());
        return false;
    }

    static boolean onKeyRelease(KeyBindsScreen screen, KeyEvent input) {
        KeyMapping binding = screen.selectedKey;
        int modifier = KeyCombo.modifierOfKeyCode(input.key());
        if (binding == null || binding != pendingFor || modifier == 0 || (pendingModifiers & modifier) == 0) {
            return true;
        }

        // The modifier came back up without another key: it is the key the player wants.
        int others = pendingModifiers & ~modifier;
        finish(screen, binding, InputConstants.getKey(input), others);
        return false;
    }

    static boolean onMouseClick(KeyBindsScreen screen, MouseButtonEvent click) {
        KeyMapping binding = screen.selectedKey;
        if (binding == null) {
            return true;
        }
        finish(screen, binding, InputConstants.Type.MOUSE.getOrCreate(click.button()), click.modifiers());
        return false;
    }

    private static void finish(KeyBindsScreen screen, KeyMapping binding, InputConstants.Key key, int modifiers) {
        KeyCombos.bind(binding, key, modifiers & KeyCombo.ALL);
        screen.selectedKey = null;
        screen.lastKeySelection = Util.getMillis();
        reset();
        refresh(screen);
    }

    private static void refresh(KeyBindsScreen screen) {
        if (screen.keyBindsList != null) {
            screen.keyBindsList.resetMappingAndUpdateButtons();
        }
    }

    private static void reset() {
        pendingModifiers = 0;
        pendingFor = null;
    }
}
