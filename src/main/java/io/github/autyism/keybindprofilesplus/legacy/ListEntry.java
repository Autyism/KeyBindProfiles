//? if <1.21.9 {
/*package io.github.autyism.keybindprofilesplus.legacy;

import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/^*
 * Before 1.21.9 a list row was drawn with its position passed in, and clicks reached it as plain
 * numbers without a double-click flag. The rows of this mod are written against the newer form
 * (renderContent, getContentX() and so on, mouseClicked with a click record); this base class
 * provides that form on the older versions. The area comes from the list the row is in
 * ({@link SelectionList}), so it is right at any time, not only while the row is drawn.
 ^/
public abstract class ListEntry<E extends ListEntry<E>> extends net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry<E> {
    // The newer game's rule for a double click: the same button again within 250 ms
    private static final long DOUBLE_CLICK_MILLIS = 250L;
    private static long lastClickTime;
    private static int lastClickButton = -1;
    private static boolean lastClickDoubled;

    // The list this row was added to; null until then
    SelectionList<E> list;
    // Where the row was drawn last, for a row in no such list
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    /^* Called for every click on a screen of this mod, before it reaches a row: whether it completes a double click. ^/
    public static boolean noteClick(int button) {
        long now = Util.getMillis();
        lastClickDoubled = button == lastClickButton && now - lastClickTime < DOUBLE_CLICK_MILLIS;
        lastClickTime = now;
        lastClickButton = button;
        return lastClickDoubled;
    }

    @Override
    public void render(GuiGraphics context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
        // Same area as the newer versions give a row: 2 pixels in from the row on each side
        contentX = left;
        contentY = top;
        contentWidth = width - 4;
        contentHeight = height;
        renderContent(context, mouseX, mouseY, hovered, deltaTicks);
    }

    public abstract void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, KeyCombos.heldModifiers())), lastClickDoubled);
    }

    /^* The newer form, which rows of this mod override; by default the click goes to the row's widgets. ^/
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(click.x(), click.y(), click.button());
    }

    // The same area the list passes to render(): the row's left and top, its width less 4, its height less the gap of 4

    public int getContentX() {
        return list != null ? list.getRowLeft() : contentX;
    }

    public int getContentY() {
        int index = list != null ? list.children().indexOf(this) : -1;
        return index >= 0 ? list.getRowTop(index) : contentY;
    }

    public int getContentWidth() {
        return list != null ? list.getRowWidth() - 4 : contentWidth;
    }

    public int getContentHeight() {
        return list != null ? list.rowHeight() - 4 : contentHeight;
    }

    public int getContentRight() {
        return getContentX() + getContentWidth();
    }

    public int getContentBottom() {
        return getContentY() + getContentHeight();
    }

    public int getContentYMiddle() {
        return getContentY() + getContentHeight() / 2;
    }
}
*///?}
