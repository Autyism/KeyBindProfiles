package io.github.autyism.keybindprofilesplus.gui;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/**
 * A scrolling column of rows for form-like screens: headings, lines of text, and rows of buttons
 * or text fields laid out side by side. Because it scrolls, such a screen stays usable in a small
 * window, and the rows adapt their width to the window.
 */
final class WidgetRowList extends ContainerObjectSelectionList<WidgetRowList.Row> {
    private static final int ROW_HEIGHT = 24;
    private static final int GAP = 4;

    WidgetRowList(Minecraft client, int width, HeaderAndFooterLayout layout) {
        super(client, width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
    }

    @Override
    public int getRowWidth() {
        return Math.max(200, Math.min(380, width - 40));
    }

    void clear() {
        clearEntries();
    }

    /** A section title. */
    void addHeading(Component text) {
        addEntry(new TextRow(() -> text, () -> GuiUtil.WHITE, true));
    }

    /** A line of explanatory text, re-read every frame so it can change. */
    void addText(Supplier<Component> text, int color) {
        addEntry(new TextRow(text, () -> color, false));
    }

    /** A line of text whose colour can change as well (for messages that are sometimes errors). */
    void addText(Supplier<Component> text, IntSupplier color) {
        addEntry(new TextRow(text, color, false));
    }

    /** Widgets side by side, all the same width. */
    void addWidgets(AbstractWidget... widgets) {
        int[] weights = new int[widgets.length];
        java.util.Arrays.fill(weights, 1);
        addWidgets(weights, widgets);
    }

    /** Widgets side by side, sharing the row width in proportion to their weights. */
    void addWidgets(int[] weights, AbstractWidget... widgets) {
        addEntry(new WidgetRow(weights, widgets));
    }

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    private final class TextRow extends Row {
        private final Supplier<Component> text;
        private final IntSupplier color;
        private final boolean heading;

        TextRow(Supplier<Component> text, IntSupplier color, boolean heading) {
            this.text = text;
            this.color = color;
            this.heading = heading;
        }

        @Override
        public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            Component current = text.get();
            if (current == null) {
                return;
            }
            String line = GuiUtil.ellipsize(minecraft.font, current.getString(), getContentWidth());
            int y = heading ? getContentBottom() - minecraft.font.lineHeight - 2 : getContentYMiddle() - minecraft.font.lineHeight / 2;
            context.drawString(minecraft.font, line, getContentX(), y, color.getAsInt());
            if (heading) {
                context.fill(getContentX(), getContentBottom() - 1, getContentRight(), getContentBottom(), 0x40FFFFFF);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private final class WidgetRow extends Row {
        private final int[] weights;
        private final List<AbstractWidget> widgets;

        WidgetRow(int[] weights, AbstractWidget[] widgets) {
            this.weights = weights;
            this.widgets = List.of(widgets);
        }

        @Override
        public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int totalWeight = 0;
            for (int weight : weights) {
                totalWeight += weight;
            }
            int available = getContentWidth() - GAP * (widgets.size() - 1);
            int x = getContentX();
            int y = getContentYMiddle() - 10;
            for (int i = 0; i < widgets.size(); i++) {
                AbstractWidget widget = widgets.get(i);
                // The last widget takes whatever is left so the row ends exactly at the right edge.
                int widgetWidth = i == widgets.size() - 1 ? getContentRight() - x : available * weights[i] / totalWeight;
                widget.setPosition(x, y);
                widget.setWidth(widgetWidth);
                widget.render(context, mouseX, mouseY, deltaTicks);
                x += widgetWidth + GAP;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }
}
