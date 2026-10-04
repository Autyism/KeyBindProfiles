package io.github.autyism.keybindprofilesplus.mixin;

import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Makes key bindings understand "Ctrl + X". Vanilla has no notion of modifier combinations and
 * Fabric has no event for key binding dispatch, hence the mixin. Keys that no binding uses in a
 * combination are left to the vanilla code untouched.
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingComboMixin {
    @Shadow
    @Final
    private static Map<InputUtil.Key, List<KeyBinding>> KEY_TO_BINDINGS;

    @Shadow
    private int timesPressed;

    /** A key press: only the bindings whose modifiers fit get the press counted. */
    @Inject(method = "onKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void keybindprofilesplus$onKeyPressed(InputUtil.Key key, CallbackInfo ci) {
        List<KeyBinding> onKey = KEY_TO_BINDINGS.get(key);
        if (!KeyCombos.anyCombination(onKey)) {
            return;
        }
        for (KeyBinding binding : KeyCombos.eligible(onKey, KeyCombos.heldModifiers())) {
            ((KeyBindingComboMixin) (Object) binding).timesPressed++;
        }
        ci.cancel();
    }

    /** Held state: pressing only reaches the fitting bindings; releasing always reaches all of them. */
    @Inject(method = "setKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void keybindprofilesplus$setKeyPressed(InputUtil.Key key, boolean pressed, CallbackInfo ci) {
        if (!pressed) {
            return;
        }
        List<KeyBinding> onKey = KEY_TO_BINDINGS.get(key);
        if (!KeyCombos.anyCombination(onKey)) {
            return;
        }
        for (KeyBinding binding : KeyCombos.eligible(onKey, KeyCombos.heldModifiers())) {
            binding.setPressed(true);
        }
        ci.cancel();
    }

    /** After the game re-reads which keys are down (window regained focus), drop combinations whose modifiers are not held. */
    @Inject(method = "updatePressedStates", at = @At("TAIL"))
    private static void keybindprofilesplus$updatePressedStates(CallbackInfo ci) {
        if (!KeyCombos.hasAny()) {
            return;
        }
        int held = KeyCombos.heldModifiers();
        for (List<KeyBinding> onKey : KEY_TO_BINDINGS.values()) {
            if (!KeyCombos.anyCombination(onKey)) {
                continue;
            }
            List<KeyBinding> eligible = KeyCombos.eligible(onKey, held);
            for (KeyBinding binding : onKey) {
                if (binding.isPressed() && !eligible.contains(binding)) {
                    binding.setPressed(false);
                }
            }
        }
    }

    /** Any other code changing the key (reset buttons, vanilla rebinding) ends the old combination. */
    @Inject(method = "setBoundKey", at = @At("HEAD"))
    private void keybindprofilesplus$setBoundKey(InputUtil.Key key, CallbackInfo ci) {
        KeyCombos.onBoundKeyChanged((KeyBinding) (Object) this);
    }

    @Inject(method = "getBoundKeyLocalizedText", at = @At("RETURN"), cancellable = true)
    private void keybindprofilesplus$comboName(CallbackInfoReturnable<Text> cir) {
        int modifiers = KeyCombos.modifiersOf((KeyBinding) (Object) this);
        if (modifiers != 0) {
            cir.setReturnValue(KeyCombo.withModifiers(modifiers, cir.getReturnValue()));
        }
    }

    /** A combination is never "the default", so the vanilla reset button stays usable for it. */
    @Inject(method = "isDefault", at = @At("RETURN"), cancellable = true)
    private void keybindprofilesplus$isDefault(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KeyCombos.modifiersOf((KeyBinding) (Object) this) != 0) {
            cir.setReturnValue(false);
        }
    }

    /** Key checks made by screens ("is this the inventory key?") respect combinations too. */
    @Inject(method = "matchesKey", at = @At("RETURN"), cancellable = true)
    private void keybindprofilesplus$matchesKey(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KeyCombos.hasAny() && !KeyCombos.reactsWith((KeyBinding) (Object) this, input.modifiers())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "matchesMouse", at = @At("RETURN"), cancellable = true)
    private void keybindprofilesplus$matchesMouse(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && KeyCombos.hasAny() && !KeyCombos.reactsWith((KeyBinding) (Object) this, click.modifiers())) {
            cir.setReturnValue(false);
        }
    }
}
