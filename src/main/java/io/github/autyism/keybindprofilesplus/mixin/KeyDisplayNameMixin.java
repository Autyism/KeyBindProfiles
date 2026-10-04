package io.github.autyism.keybindprofilesplus.mixin;

import io.github.autyism.keybindprofilesplus.input.KeyNames;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
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
@Mixin(InputUtil.Key.class)
public abstract class KeyDisplayNameMixin {
    @Shadow
    @Final
    private InputUtil.Type type;

    @Shadow
    @Final
    private int code;

    @Inject(method = "getLocalizedText", at = @At("HEAD"), cancellable = true)
    private void keybindprofilesplus$keypadName(CallbackInfoReturnable<Text> cir) {
        if (type == InputUtil.Type.KEYSYM) {
            Text name = KeyNames.keypadName(code);
            if (name != null) {
                cir.setReturnValue(name);
            }
        }
    }
}
