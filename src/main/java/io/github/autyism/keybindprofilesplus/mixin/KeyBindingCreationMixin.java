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
    // The constructor every other one ends up in
    //? if >=1.21.11 {
    @Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V",
            at = @At("RETURN"))
    private void keybindprofilesplus$recordCreator(String id, InputConstants.Type type, int code, KeyMapping.Category category, int order, CallbackInfo ci) {
    //?} else if >=1.21.9 {
    /*@Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)V",
            at = @At("RETURN"))
    private void keybindprofilesplus$recordCreator(String id, InputConstants.Type type, int code, KeyMapping.Category category, CallbackInfo ci) {
    *///?} else {
    /*@Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
            at = @At("RETURN"))
    private void keybindprofilesplus$recordCreator(String id, InputConstants.Type type, int code, String category, CallbackInfo ci) {
    *///?}
        KeyOrigins.record((KeyMapping) (Object) this);
    }
}
