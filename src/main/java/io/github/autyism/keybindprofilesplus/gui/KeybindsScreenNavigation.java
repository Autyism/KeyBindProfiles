package io.github.autyism.keybindprofilesplus.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/**
 * Going back and forth between the vanilla Key Binds screen and the mod's screens. The two
 * vanilla fields used here are opened up by the access widener.
 */
final class KeybindsScreenNavigation {
    private KeybindsScreenNavigation() {
    }

    /** Makes the vanilla list show the keys as they are now (after a profile was applied). */
    static void refreshControlsList(KeyBindsScreen keybindsScreen) {
        if (keybindsScreen.keyBindsList != null) {
            keybindsScreen.keyBindsList.resetMappingAndUpdateButtons();
        }
    }

    /** A new Key Binds screen that leads back to wherever the original one came from. */
    static Screen createFreshKeybindsScreen(KeyBindsScreen originalKeybindsScreen) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return null;
        }
        return new KeyBindsScreen(originalKeybindsScreen.lastScreen, client.options);
    }
}
