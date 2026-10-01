package io.github.autyi6969.keybindprofilesplus.mixin;

import io.github.autyi6969.keybindprofilesplus.input.ComboRecorder;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Replaces the vanilla "this key is also used for..." marker in the Key Binds list with the
 * layered conflict check: red for a real conflict, yellow for a possible one, nothing for keys
 * that merely share a key without ever being active together (F3+G and G). Vanilla recomputes the
 * marker after every key change, so the result updates the moment a key is rebound. There is no
 * Fabric event for this list, hence the mixin; it only changes what is displayed.
 */
@Mixin(ControlsListWidget.KeyBindingEntry.class)
public abstract class KeyBindingEntryMixin {
    @Shadow
    @Final
    private KeyBinding binding;

    @Shadow
    @Final
    private ButtonWidget editButton;

    @Shadow
    private boolean duplicate;

    @Unique
    private int keybindprofilesplus$markerColor = KeyConflicts.Level.SOFT.color();

    @Inject(method = "update", at = @At("TAIL"))
    private void keybindprofilesplus$smartConflicts(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<KeyConflicts.Conflict> conflicts = KeyConflicts.conflictsOf(binding, client.options);
        KeyConflicts.Level level = KeyConflicts.worst(conflicts);

        Text key = binding.getBoundKeyLocalizedText();
        duplicate = level != KeyConflicts.Level.NONE;
        if (duplicate) {
            keybindprofilesplus$markerColor = level.color();
            editButton.setMessage(Text.literal("[ ").append(key.copy().formatted(Formatting.WHITE)).append(" ]").formatted(level.formatting()));
            MutableText tooltip = Text.empty();
            List<Text> lines = KeyConflicts.describe(conflicts);
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    tooltip.append("\n");
                }
                tooltip.append(lines.get(i));
            }
            editButton.setTooltip(Tooltip.of(tooltip));
        } else {
            editButton.setMessage(key);
            editButton.setTooltip(null);
        }

        // Same "waiting for a key" decoration as vanilla, which the lines above just overwrote.
        if (client.currentScreen instanceof KeybindsScreen screen && screen.selectedKeyBinding == binding) {
            int pending = ComboRecorder.pendingModifiers(binding);
            Text waiting = pending == 0 ? editButton.getMessage() : KeyCombo.withModifiers(pending, Text.literal("..."));
            editButton.setMessage(Text.literal("> ")
                    .append(waiting.copy().formatted(Formatting.WHITE, Formatting.UNDERLINE))
                    .append(" <")
                    .formatted(Formatting.YELLOW));
        }
    }

    /** The little bar left of the key button: vanilla always paints it yellow. */
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), index = 4)
    private int keybindprofilesplus$markerColor(int color) {
        return keybindprofilesplus$markerColor;
    }
}
