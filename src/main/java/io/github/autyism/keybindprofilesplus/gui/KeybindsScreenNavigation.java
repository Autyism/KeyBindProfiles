package io.github.autyism.keybindprofilesplus.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;

/**
 * Going back and forth between the vanilla Key Binds screen and the mod's screens. The two
 * vanilla fields used here are opened up by the access widener.
 */
final class KeybindsScreenNavigation {
    private KeybindsScreenNavigation() {
    }

    /** Makes the vanilla list show the keys as they are now (after a profile was applied). */
    static void refreshControlsList(KeybindsScreen keybindsScreen) {
        if (keybindsScreen.controlsList != null) {
            keybindsScreen.controlsList.update();
        }
    }

    /** A new Key Binds screen that leads back to wherever the original one came from. */
    static Screen createFreshKeybindsScreen(KeybindsScreen originalKeybindsScreen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return null;
        }
        return new KeybindsScreen(originalKeybindsScreen.parent, client.options);
    }
}
