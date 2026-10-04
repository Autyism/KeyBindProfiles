package io.github.autyism.keybindprofilesplus.input;

import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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
    private static KeyBinding pendingFor;

    private ComboRecorder() {
    }

    public static void install(KeybindsScreen screen) {
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, input) -> onKeyPress(screen, input));
        ScreenKeyboardEvents.allowKeyRelease(screen).register((current, input) -> onKeyRelease(screen, input));
        ScreenMouseEvents.allowMouseClick(screen).register((current, click) -> onMouseClick(screen, click));
    }

    /** The modifiers held back for the binding that is waiting for its key; 0 when there are none. */
    public static int pendingModifiers(KeyBinding binding) {
        return binding != null && binding == pendingFor ? pendingModifiers : 0;
    }

    /** Returns false when the key press was handled here and vanilla must not see it. */
    static boolean onKeyPress(KeybindsScreen screen, KeyInput input) {
        KeyBinding binding = screen.selectedKeyBinding;
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

        finish(screen, binding, InputUtil.fromKeyCode(input), input.modifiers());
        return false;
    }

    static boolean onKeyRelease(KeybindsScreen screen, KeyInput input) {
        KeyBinding binding = screen.selectedKeyBinding;
        int modifier = KeyCombo.modifierOfKeyCode(input.key());
        if (binding == null || binding != pendingFor || modifier == 0 || (pendingModifiers & modifier) == 0) {
            return true;
        }

        // The modifier came back up without another key: it is the key the player wants.
        int others = pendingModifiers & ~modifier;
        finish(screen, binding, InputUtil.fromKeyCode(input), others);
        return false;
    }

    static boolean onMouseClick(KeybindsScreen screen, Click click) {
        KeyBinding binding = screen.selectedKeyBinding;
        if (binding == null) {
            return true;
        }
        finish(screen, binding, InputUtil.Type.MOUSE.createFromCode(click.button()), click.modifiers());
        return false;
    }

    private static void finish(KeybindsScreen screen, KeyBinding binding, InputUtil.Key key, int modifiers) {
        KeyCombos.bind(binding, key, modifiers & KeyCombo.ALL);
        screen.selectedKeyBinding = null;
        screen.lastKeyCodeUpdateTime = Util.getMeasuringTimeMs();
        reset();
        refresh(screen);
    }

    private static void refresh(KeybindsScreen screen) {
        if (screen.controlsList != null) {
            screen.controlsList.update();
        }
    }

    private static void reset() {
        pendingModifiers = 0;
        pendingFor = null;
    }
}
