package io.github.autyism.keybindprofilesplus.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.gui.SettingsScreen;

/**
 * Gives the mod a configure button in Mod Menu's mod list: it opens the settings screen, which in
 * turn leads to the profiles and the key binds screen. Only loaded when Mod Menu is installed
 * (it is named as the "modmenu" entry point in fabric.mod.json and referenced nowhere else).
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SettingsScreen(parent, KeyBindProfilesPlus.profileService());
    }
}
