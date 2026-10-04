package io.github.autyism.keybindprofilesplus.mixin;

import io.github.autyism.keybindprofilesplus.gui.KeyOverviewScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shows the mod's Key Binds screen wherever the game would open the vanilla one (Options ->
 * Controls -> Key Binds, or another mod opening it). Fabric's screen events can only add to a
 * screen, not put another one in its place, hence the mixin. Only the exact vanilla class is
 * replaced, so a different key screen supplied by another mod is left alone, and the setting
 * "Replace the vanilla Key Binds screen" switches this off.
 */
@Mixin(MinecraftClient.class)
public abstract class KeyBindsScreenReplaceMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen keybindprofilesplus$replaceKeyBindsScreen(Screen screen) {
        return KeyOverviewScreen.replacementFor(screen);
    }
}
