package io.github.autyi6969.keybindprofilesplus.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;

/**
 * Base of the mod's screens. Their button and field widths depend on the window size, so when the
 * window is resized while one of them is open, the screen is put together again for the new size
 * (what was typed or selected lives in the screen's fields and survives that).
 */
abstract class ResizingScreen extends Screen {
    private int builtWidth = -1;
    private int builtHeight = -1;

    protected ResizingScreen(Text title) {
        super(title);
    }

    /** First thing in init(): a fresh layout, and the size the widgets are about to be made for. */
    protected final ThreePartsLayoutWidget startLayout(int headerHeight, int footerHeight) {
        builtWidth = width;
        builtHeight = height;
        return new ThreePartsLayoutWidget(this, headerHeight, footerHeight);
    }

    /**
     * First thing in refreshWidgetPositions(): when the window size changed since init(), builds
     * the screen again and returns true - there is then nothing left to lay out.
     */
    protected final boolean rebuiltAfterResize() {
        if (width == builtWidth && height == builtHeight) {
            return false;
        }
        clearAndInit();
        return true;
    }
}
