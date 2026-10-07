package io.github.autyism.keybindprofilesplus.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.input.KeyNames;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Display-only: makes numpad keys read "Num 5" instead of "5" everywhere a key name is shown
 * (vanilla Key Binds screen included). There is no Fabric event for key names, hence the mixin.
 */
@Mixin(InputConstants.Key.class)
public abstract class KeyDisplayNameMixin {
    @Shadow
    @Final
    private InputConstants.Type type;

    @Shadow
    @Final
    private int value;

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void keybindprofilesplus$keypadName(CallbackInfoReturnable<Component> cir) {
        if (type == InputConstants.Type.KEYSYM) {
            Component name = KeyNames.keypadName(value);
            if (name != null) {
                cir.setReturnValue(name);
            }
        }
    }
}
