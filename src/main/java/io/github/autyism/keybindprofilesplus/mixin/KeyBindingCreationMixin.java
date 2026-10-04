package io.github.autyism.keybindprofilesplus.mixin;

import io.github.autyism.keybindprofilesplus.keys.KeyOrigins;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes who creates each key binding so the key overview can say which mod it comes from.
 * Display-only bookkeeping; there is no event for "a key binding was created".
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingCreationMixin {
    @Inject(method = "<init>(Ljava/lang/String;Lnet/minecraft/client/util/InputUtil$Type;ILnet/minecraft/client/option/KeyBinding$Category;I)V",
            at = @At("RETURN"))
    private void keybindprofilesplus$recordCreator(String id, InputUtil.Type type, int code, KeyBinding.Category category, int order, CallbackInfo ci) {
        KeyOrigins.record((KeyBinding) (Object) this);
    }
}
