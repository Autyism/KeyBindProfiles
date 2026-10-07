package io.github.autyism.keybindprofilesplus.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.keys.KeyOrigins;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes who creates each key binding so the key overview can say which mod it comes from.
 * Display-only bookkeeping; there is no event for "a key binding was created".
 */
@Mixin(KeyMapping.class)
public abstract class KeyBindingCreationMixin {
    @Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V",
            at = @At("RETURN"))
    private void keybindprofilesplus$recordCreator(String id, InputConstants.Type type, int code, KeyMapping.Category category, int order, CallbackInfo ci) {
        KeyOrigins.record((KeyMapping) (Object) this);
    }
}
