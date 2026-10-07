//? if <1.21.9 {
/*package io.github.autyism.keybindprofilesplus.legacy;

import com.mojang.blaze3d.platform.InputConstants;

/^*
 * Minecraft 1.21.9 hands key presses to screens as a record; before, they came as three numbers.
 * This is that record for the older versions, so the mod's screens read the same on all of them.
 ^/
public record KeyEvent(int key, int scancode, int modifiers) {
    public boolean isSelection() {
        return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_NUMPADENTER;
    }

    public boolean isConfirmation() {
        return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
    }

    public boolean isEscape() {
        return key == InputConstants.KEY_ESCAPE;
    }
}
*///?}
