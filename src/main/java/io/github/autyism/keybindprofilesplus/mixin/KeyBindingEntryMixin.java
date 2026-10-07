package io.github.autyism.keybindprofilesplus.mixin;

import io.github.autyism.keybindprofilesplus.input.ComboRecorder;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Replaces the vanilla "this key is also used for..." marker in the Key Binds list with the
 * layered conflict check: red for a real conflict, yellow for a possible one, nothing for keys
 * that merely share a key without ever being active together (F3+G and G). Vanilla recomputes the
 * marker after every key change, so the result updates the moment a key is rebound. There is no
 * Fabric event for this list, hence the mixin; it only changes what is displayed.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindingEntryMixin {
    @Shadow
    @Final
    private KeyMapping key;

    @Shadow
    @Final
    private Button changeButton;

    @Shadow
    private boolean hasCollision;

    @Unique
    private int keybindprofilesplus$markerColor = KeyConflicts.Level.SOFT.color();

    @Inject(method = "refreshEntry", at = @At("TAIL"))
    private void keybindprofilesplus$smartConflicts(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        List<KeyConflicts.Conflict> conflicts = KeyConflicts.conflictsOf(key, client.options);
        KeyConflicts.Level level = KeyConflicts.worst(conflicts);

        Component keyName = key.getTranslatedKeyMessage();
        hasCollision = level != KeyConflicts.Level.NONE;
        if (hasCollision) {
            keybindprofilesplus$markerColor = level.color();
            changeButton.setMessage(Component.literal("[ ").append(keyName.copy().withStyle(ChatFormatting.WHITE)).append(" ]").withStyle(level.formatting()));
            changeButton.setTooltip(Tooltip.create(lines(KeyConflicts.describe(conflicts))));
        } else {
            changeButton.setMessage(keyName);
            // Not marked, but worth a word: something of another mod is on this key on purpose.
            List<Component> shared = KeyConflicts.describeShared(KeyConflicts.sharedWithoutConflict(key, client.options));
            changeButton.setTooltip(shared.isEmpty() ? null : Tooltip.create(lines(shared)));
        }

        // Same "waiting for a key" decoration as vanilla, which the lines above just overwrote.
        if (client.screen instanceof KeyBindsScreen screen && screen.selectedKey == key) {
            int pending = ComboRecorder.pendingModifiers(key);
            Component waiting = pending == 0 ? changeButton.getMessage() : KeyCombo.withModifiers(pending, Component.literal("..."));
            changeButton.setMessage(Component.literal("> ")
                    .append(waiting.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                    .append(" <")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    @Unique
    private static Component lines(List<Component> lines) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append(lines.get(i));
        }
        return text;
    }

    /** The little bar left of the key button: vanilla always paints it yellow. */
    //? if >=1.21.9 {
    @ModifyArg(method = "renderContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4)
    //?} else
    /*@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4)*/
    private int keybindprofilesplus$markerColor(int color) {
        return keybindprofilesplus$markerColor;
    }
}
