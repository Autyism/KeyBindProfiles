package io.github.autyism.keybindprofilesplus.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Makes key bindings understand "Ctrl + X". Vanilla has no notion of modifier combinations and
 * Fabric has no event for key binding dispatch, hence the mixin. Keys that no binding uses in a
 * combination are left to the vanilla code untouched.
 */
@Mixin(KeyMapping.class)
public abstract class KeyBindingComboMixin {
    @Shadow
    @Final
    private static Map<InputConstants.Key, List<KeyMapping>> MAP;

    @Shadow
    private int clickCount;

    /** A key press: only the bindings whose modifiers fit get the press counted. */
    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void keybindprofilesplus$onKeyPressed(InputConstants.Key key, CallbackInfo ci) {
        //? if >=1.21.9 {
        List<KeyMapping> onKey = MAP.get(key);
        //?} else
        /*List<KeyMapping> onKey = KeyCombos.bindingsOn(key);*/
        if (!KeyCombos.anyCombination(onKey)) {
            return;
        }
        for (KeyMapping binding : KeyCombos.eligible(onKey, KeyCombos.heldModifiers())) {
            ((KeyBindingComboMixin) (Object) binding).clickCount++;
        }
        ci.cancel();
    }

    /** Held state: pressing only reaches the fitting bindings; releasing always reaches all of them. */
    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void keybindprofilesplus$setKeyPressed(InputConstants.Key key, boolean pressed, CallbackInfo ci) {
        //? if <1.21.9 {
        /*// Before 1.21.9 the game releases only one binding per key; with combinations there are several
        if (!pressed) {
            List<KeyMapping> held = KeyCombos.bindingsOn(key);
            if (KeyCombos.anyCombination(held)) {
                for (KeyMapping binding : held) {
                    binding.setDown(false);
                }
                ci.cancel();
            }
            return;
        }
        *///?}
        if (!pressed) {
            return;
        }
        //? if >=1.21.9 {
        List<KeyMapping> onKey = MAP.get(key);
        //?} else
        /*List<KeyMapping> onKey = KeyCombos.bindingsOn(key);*/
        if (!KeyCombos.anyCombination(onKey)) {
            return;
        }
        for (KeyMapping binding : KeyCombos.eligible(onKey, KeyCombos.heldModifiers())) {
            binding.setDown(true);
        }
        ci.cancel();
    }

    /** After the game re-reads which keys are down (window regained focus), drop combinations whose modifiers are not held. */
    @Inject(method = "setAll", at = @At("TAIL"))
    private static void keybindprofilesplus$updatePressedStates(CallbackInfo ci) {
        if (!KeyCombos.hasAny()) {
            return;
        }
        int held = KeyCombos.heldModifiers();
        //? if >=1.21.9 {
        for (List<KeyMapping> onKey : MAP.values()) {
        //?} else
        /*for (List<KeyMapping> onKey : KeyCombos.bindingsByKey()) {*/
            if (!KeyCombos.anyCombination(onKey)) {
                continue;
            }
            List<KeyMapping> eligible = KeyCombos.eligible(onKey, held);
            for (KeyMapping binding : onKey) {
                if (binding.isDown() && !eligible.contains(binding)) {
                    binding.setDown(false);
                }
            }
        }
    }

    /** Any other code changing the key (reset buttons, vanilla rebinding) ends the old combination. */
    @Inject(method = "setKey", at = @At("HEAD"))
    private void keybindprofilesplus$setBoundKey(InputConstants.Key key, CallbackInfo ci) {
        KeyCombos.onBoundKeyChanged((KeyMapping) (Object) this);
    }

    @Inject(method = "getTranslatedKeyMessage", at = @At("RETURN"), cancellable = true)
    private void keybindprofilesplus$comboName(CallbackInfoReturnable<Component> cir) {
        int modifiers = KeyCombos.modifiersOf((KeyMapping) (Object) this);
        if (modifiers != 0) {
            cir.setReturnValue(KeyCombo.withModifiers(modifiers, cir.getReturnValue()));
        }
    }

    /** A combination is never "the default", so the vanilla reset button stays usable for it. */
    @Inject(method = "isDefault", at = @At("RETURN"), cancellable = true)
    private void keybindprofilesplus$isDefault(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KeyCombos.modifiersOf((KeyMapping) (Object) this) != 0) {
            cir.setReturnValue(false);
        }
    }

    /** Key checks made by screens ("is this the inventory key?") respect combinations too. */
    //? if >=26.2 {
    /*@Inject(method = "matches(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("RETURN"), cancellable = true)
    *///?} else
    @Inject(method = "matches", at = @At("RETURN"), cancellable = true)
    //? if >=1.21.9 {
    private void keybindprofilesplus$matchesKey(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void keybindprofilesplus$matchesKey(int keyCode, int scanCode, CallbackInfoReturnable<Boolean> cir) {
        // Before 1.21.9 the check got no modifier keys: the ones held right now
        KeyEvent input = new KeyEvent(keyCode, scanCode, KeyCombos.heldModifiers());
    *///?}
        if (cir.getReturnValueZ() && KeyCombos.hasAny() && !KeyCombos.reactsWith((KeyMapping) (Object) this, input.modifiers())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "matchesMouse", at = @At("RETURN"), cancellable = true)
    //? if >=1.21.9 {
    private void keybindprofilesplus$matchesMouse(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void keybindprofilesplus$matchesMouse(int button, CallbackInfoReturnable<Boolean> cir) {
        MouseButtonEvent click = new MouseButtonEvent(0, 0, new io.github.autyism.keybindprofilesplus.legacy.MouseButtonInfo(button, KeyCombos.heldModifiers()));
    *///?}
        if (cir.getReturnValueZ() && KeyCombos.hasAny() && !KeyCombos.reactsWith((KeyMapping) (Object) this, click.modifiers())) {
            cir.setReturnValue(false);
        }
    }

    //? if >=26.2 {
    /*// Since 26.2 the game checks its global keys (fullscreen, screenshot, friends) by the key alone.
    @Inject(method = "matches(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Z", at = @At("RETURN"), cancellable = true)
    private void keybindprofilesplus$matchesBareKey(InputConstants.Key key, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KeyCombos.hasAny() && !KeyCombos.reactsWith((KeyMapping) (Object) this, KeyCombos.heldModifiers())) {
            cir.setReturnValue(false);
        }
    }
    *///?}
}
