package io.github.autyism.keybindprofilesplus.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A scrolling column of rows for form-like screens: headings, lines of text, and rows of buttons
 * or text fields laid out side by side. Because it scrolls, such a screen stays usable in a small
 * window, and the rows adapt their width to the window.
 */
final class WidgetRowList extends ElementListWidget<WidgetRowList.Row> {
    private static final int ROW_HEIGHT = 24;
    private static final int GAP = 4;

    WidgetRowList(MinecraftClient client, int width, ThreePartsLayoutWidget layout) {
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
    void addHeading(Text text) {
        addEntry(new TextRow(() -> text, () -> GuiUtil.WHITE, true));
    }

    /** A line of explanatory text, re-read every frame so it can change. */
    void addText(Supplier<Text> text, int color) {
        addEntry(new TextRow(text, () -> color, false));
    }

    /** A line of text whose colour can change as well (for messages that are sometimes errors). */
    void addText(Supplier<Text> text, IntSupplier color) {
        addEntry(new TextRow(text, color, false));
    }

    /** Widgets side by side, all the same width. */
    void addWidgets(ClickableWidget... widgets) {
        int[] weights = new int[widgets.length];
        java.util.Arrays.fill(weights, 1);
        addWidgets(weights, widgets);
    }

    /** Widgets side by side, sharing the row width in proportion to their weights. */
    void addWidgets(int[] weights, ClickableWidget... widgets) {
        addEntry(new WidgetRow(weights, widgets));
    }

    abstract static class Row extends ElementListWidget.Entry<Row> {
    }

    private final class TextRow extends Row {
        private final Supplier<Text> text;
        private final IntSupplier color;
        private final boolean heading;

        TextRow(Supplier<Text> text, IntSupplier color, boolean heading) {
            this.text = text;
            this.color = color;
            this.heading = heading;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            Text current = text.get();
            if (current == null) {
                return;
            }
            String line = GuiUtil.ellipsize(client.textRenderer, current.getString(), getContentWidth());
            int y = heading ? getContentBottomEnd() - client.textRenderer.fontHeight - 2 : getContentMiddleY() - client.textRenderer.fontHeight / 2;
            context.drawTextWithShadow(client.textRenderer, line, getContentX(), y, color.getAsInt());
            if (heading) {
                context.fill(getContentX(), getContentBottomEnd() - 1, getContentRightEnd(), getContentBottomEnd(), 0x40FFFFFF);
            }
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of();
        }
    }

    private final class WidgetRow extends Row {
        private final int[] weights;
        private final List<ClickableWidget> widgets;

        WidgetRow(int[] weights, ClickableWidget[] widgets) {
            this.weights = weights;
            this.widgets = List.of(widgets);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int totalWeight = 0;
            for (int weight : weights) {
                totalWeight += weight;
            }
            int available = getContentWidth() - GAP * (widgets.size() - 1);
            int x = getContentX();
            int y = getContentMiddleY() - 10;
            for (int i = 0; i < widgets.size(); i++) {
                ClickableWidget widget = widgets.get(i);
                // The last widget takes whatever is left so the row ends exactly at the right edge.
                int widgetWidth = i == widgets.size() - 1 ? getContentRightEnd() - x : available * weights[i] / totalWeight;
                widget.setPosition(x, y);
                widget.setWidth(widgetWidth);
                widget.render(context, mouseX, mouseY, deltaTicks);
                x += widgetWidth + GAP;
            }
        }

        @Override
        public List<? extends Element> children() {
            return widgets;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return widgets;
        }
    }
}
