//? if <1.21.9 {
/*package io.github.autyism.keybindprofilesplus.legacy;

import net.minecraft.client.Minecraft;

/^*
 * Before 1.21.9 a list row did not know where it is: the list only passed the position in while
 * drawing it. The rows of this mod ask for their area at any time, also right after the list
 * scrolled and before it is drawn again (as they can on the newer versions); this list answers.
 ^/
// (the full name keeps the build from turning this into a subclass of itself)
public abstract class SelectionList<E extends ListEntry<E>> extends net.minecraft.client.gui.components.ContainerObjectSelectionList<E> {
    protected SelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @Override
    protected int addEntry(E entry) {
        entry.list = this;
        return super.addEntry(entry);
    }

    /^* The height of a row, gaps included. ^/
    int rowHeight() {
        return itemHeight;
    }
}
*///?}
