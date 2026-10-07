//? if <1.21.9 {
/*package io.github.autyism.keybindprofilesplus.legacy;

/^* A click as Minecraft 1.21.9 and later pass it: where, which button, which modifier keys. ^/
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) {
    public int button() {
        return buttonInfo.button();
    }

    public int modifiers() {
        return buttonInfo.modifiers();
    }
}
*///?}
