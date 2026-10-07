package io.github.autyism.keybindprofilesplus.gui;

import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if <1.21.9 {
/*import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.legacy.CharacterEvent;
import io.github.autyism.keybindprofilesplus.legacy.KeyEvent;
import io.github.autyism.keybindprofilesplus.legacy.MouseButtonEvent;
import io.github.autyism.keybindprofilesplus.legacy.MouseButtonInfo;
*///?}

/**
 * Base of the mod's screens. Their button and field widths depend on the window size, so when the
 * window is resized while one of them is open, the screen is put together again for the new size
 * (what was typed or selected lives in the screen's fields and survives that).
 */
abstract class ResizingScreen extends Screen {
    private int builtWidth = -1;
    private int builtHeight = -1;

    protected ResizingScreen(Component title) {
        super(title);
    }

    /** First thing in init(): a fresh layout, and the size the widgets are about to be made for. */
    protected final HeaderAndFooterLayout startLayout(int headerHeight, int footerHeight) {
        builtWidth = width;
        builtHeight = height;
        return new HeaderAndFooterLayout(this, headerHeight, footerHeight);
    }

    /**
     * First thing in repositionElements(): when the window size changed since init(), builds
     * the screen again and returns true - there is then nothing left to lay out.
     */
    protected final boolean rebuiltAfterResize() {
        if (width == builtWidth && height == builtHeight) {
            return false;
        }
        rebuildWidgets();
        return true;
    }
    //? if <1.21.9 {
    /*
    // Before 1.21.9 screens got key presses and clicks as plain numbers. The screens of this mod are
    // written against the newer form: these pass the old calls on to it, and the defaults back to the game.

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public boolean keyPressed(KeyEvent input) {
        return super.keyPressed(input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return keyReleased(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public boolean keyReleased(KeyEvent input) {
        return super.keyReleased(input.key(), input.scancode(), input.modifiers());
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return charTyped(new CharacterEvent(codePoint, modifiers));
    }

    public boolean charTyped(CharacterEvent input) {
        return super.charTyped((char) input.codepoint(), input.modifiers());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicks carried no modifier keys and no double-click flag yet: the keys held right now, and the timing
        boolean doubled = io.github.autyism.keybindprofilesplus.legacy.ListEntry.noteClick(button);
        return mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, KeyCombos.heldModifiers())), doubled);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(click.x(), click.y(), click.button());
    }
    *///?}
}
