package io.github.autyi6969.keybindprofilesplus.gui;

import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyi6969.keybindprofilesplus.keys.KeySource;
import io.github.autyi6969.keybindprofilesplus.keys.KeySourceResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.EmptyWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Read-only overview of every key binding registered with the game (vanilla and mods alike),
 * grouped by category, with the mod each one comes from. Searchable and filterable by source.
 */
public class KeyOverviewScreen extends Screen {
    private static final int HEADER_HEIGHT = 58;
    private static final int ROW_HEIGHT = 20;
    private static final int KEY_BOX_WIDTH = 86;
    private static final int MAX_SOURCE_WIDTH = 110;

    private final Screen parent;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, HEADER_HEIGHT, 33);
    private final List<Row> rows = new ArrayList<>();

    private TextFieldWidget searchField;
    private CyclingButtonWidget<SourceFilter> filterButton;
    private CyclingButtonWidget<Boolean> conflictsButton;
    private boolean conflictsOnly;
    private KeyConflicts.Summary conflictSummary = new KeyConflicts.Summary(0, 0);
    private KeyList list;
    private SourceFilter filter = SourceFilter.ALL;
    private String query = "";
    private Text summary = Text.empty();
    private Row hoveredRow;

    public KeyOverviewScreen(Screen parent) {
        super(Text.translatable("keybindprofilesplus.overview.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        collectRows();

        DirectionalLayoutWidget header = layout.addHeader(DirectionalLayoutWidget.vertical().spacing(4));
        header.getMainPositioner().alignHorizontalCenter();
        header.add(new TextWidget(title, textRenderer));

        DirectionalLayoutWidget controls = header.add(DirectionalLayoutWidget.horizontal().spacing(4));
        int filterWidth = Math.max(70, Math.min(130, (width - 24) / 4));
        int conflictsWidth = Math.max(80, Math.min(150, (width - 24) / 3));
        int searchWidth = Math.max(60, Math.min(220, width - filterWidth - conflictsWidth - 28));
        searchField = controls.add(new TextFieldWidget(textRenderer, searchWidth, 20, Text.translatable("keybindprofilesplus.overview.search")));
        searchField.setMaxLength(64);
        searchField.setText(query);
        searchField.setPlaceholder(Text.translatable("keybindprofilesplus.overview.search").setStyle(TextFieldWidget.SEARCH_STYLE));
        searchField.setChangedListener(value -> {
            query = value;
            refreshList();
        });
        filterButton = controls.add(CyclingButtonWidget.<SourceFilter>builder(SourceFilter::label, filter)
                .values(SourceFilter.values())
                .build(0, 0, filterWidth, 20, Text.translatable("keybindprofilesplus.overview.filter"), (button, value) -> {
                    filter = value;
                    refreshList();
                }));
        conflictsButton = controls.add(CyclingButtonWidget.onOffBuilder(conflictsOnly)
                .build(0, 0, conflictsWidth, 20, Text.translatable("keybindprofilesplus.overview.conflicts_only"), (button, value) -> {
                    conflictsOnly = value;
                    refreshList();
                }));
        // Placeholder line: the summary text is drawn here by render().
        header.add(new EmptyWidget(1, textRenderer.fontHeight));

        list = layout.addBody(new KeyList(client));
        layout.addFooter(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).width(200).build());

        layout.forEachChild(this::addDrawableChild);
        refreshList();
        refreshWidgetPositions();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(searchField);
    }

    @Override
    protected void refreshWidgetPositions() {
        layout.refreshPositions();
        if (list != null) {
            list.position(width, layout);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        hoveredRow = null;
        super.render(context, mouseX, mouseY, deltaTicks);

        if (conflictSummary.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, summary, width / 2, searchField.getY() + 24, 0xFFA0A0A0);
        } else {
            Text conflicts = ConflictSummaryOverlay.text(conflictSummary);
            int total = textRenderer.getWidth(summary) + 10 + textRenderer.getWidth(conflicts);
            int x = (width - total) / 2;
            context.drawTextWithShadow(textRenderer, summary, x, searchField.getY() + 24, 0xFFA0A0A0);
            context.drawTextWithShadow(textRenderer, conflicts, x + textRenderer.getWidth(summary) + 10, searchField.getY() + 24,
                    (conflictSummary.hard() > 0 ? KeyConflicts.Level.HARD : KeyConflicts.Level.SOFT).color());
        }
        if (list.children().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("keybindprofilesplus.overview.empty"),
                    width / 2, layout.getHeaderHeight() + layout.getContentHeight() / 2 - 4, 0xFFA0A0A0);
        }
        if (hoveredRow != null) {
            context.drawTooltip(textRenderer, hoveredRow.tooltip(), mouseX, mouseY);
        }
    }

    /** For the self-test and other screens: the text typed into the search box. */
    public void setQuery(String query) {
        searchField.setText(query);
    }

    public void setSourceFilter(SourceFilter filter) {
        this.filter = filter;
        filterButton.setValue(filter);
        refreshList();
    }

    public void setConflictsOnly(boolean conflictsOnly) {
        this.conflictsOnly = conflictsOnly;
        conflictsButton.setValue(conflictsOnly);
        refreshList();
    }

    public int visibleBindingCount() {
        return (int) list.children().stream().filter(entry -> entry instanceof KeyList.BindingEntry).count();
    }

    private void collectRows() {
        rows.clear();
        KeySourceResolver resolver = new KeySourceResolver(client.options);
        KeyBinding[] bindings = client.options.allKeys.clone();
        Arrays.sort(bindings);
        for (KeyBinding binding : bindings) {
            rows.add(new Row(binding, Text.translatable(binding.getId()), resolver.resolve(binding), KeyConflicts.conflictsOf(binding, client.options)));
        }
    }

    private void refreshList() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Row> visible = new ArrayList<>();
        int fromMods = 0;
        int unbound = 0;
        int hard = 0;
        int soft = 0;
        for (Row row : rows) {
            if (!row.source().isVanilla()) {
                fromMods++;
            }
            if (row.binding().isUnbound()) {
                unbound++;
            }
            if (row.level() == KeyConflicts.Level.HARD) {
                hard++;
            } else if (row.level() == KeyConflicts.Level.SOFT) {
                soft++;
            }
            if (filter.accepts(row.source()) && row.matches(needle) && (!conflictsOnly || row.level() != KeyConflicts.Level.NONE)) {
                visible.add(row);
            }
        }

        list.setRows(visible);
        summary = Text.translatable("keybindprofilesplus.overview.summary", visible.size(), rows.size(), fromMods, unbound);
        conflictSummary = new KeyConflicts.Summary(hard, soft);
    }

    public enum SourceFilter {
        ALL("all"),
        VANILLA("vanilla"),
        MODS("mods");

        private final String key;

        SourceFilter(String key) {
            this.key = key;
        }

        Text label() {
            return Text.translatable("keybindprofilesplus.overview.filter." + key);
        }

        boolean accepts(KeySource source) {
            return switch (this) {
                case ALL -> true;
                case VANILLA -> source.isVanilla();
                case MODS -> !source.isVanilla();
            };
        }
    }

    private record Row(KeyBinding binding, Text name, KeySource source, List<KeyConflicts.Conflict> conflicts) {
        KeyConflicts.Level level() {
            return KeyConflicts.worst(conflicts);
        }

        boolean matches(String needle) {
            if (needle.isEmpty()) {
                return true;
            }
            return contains(name.getString(), needle)
                    || contains(binding.getBoundKeyLocalizedText().getString(), needle)
                    || contains(source.description().getString(), needle)
                    || contains(binding.getCategory().getLabel().getString(), needle)
                    || contains(binding.getId(), needle);
        }

        List<Text> tooltip() {
            List<Text> lines = new ArrayList<>();
            lines.add(name);
            lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.source", source.description()).formatted(Formatting.GRAY));
            lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.category", binding.getCategory().getLabel()).formatted(Formatting.GRAY));
            lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.default", binding.getDefaultKey().getLocalizedText()).formatted(Formatting.GRAY));
            if (!binding.isDefault()) {
                lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.changed").formatted(Formatting.GRAY));
            }
            lines.addAll(KeyConflicts.describe(conflicts));
            lines.add(Text.literal(binding.getId()).formatted(Formatting.DARK_GRAY));
            return lines;
        }

        private static boolean contains(String text, String needle) {
            return text.toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    private final class KeyList extends ElementListWidget<KeyList.Entry> {
        KeyList(MinecraftClient client) {
            super(client, KeyOverviewScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
        }

        void setRows(List<Row> visible) {
            clearEntries();
            KeyBinding.Category category = null;
            for (Row row : visible) {
                if (row.binding().getCategory() != category) {
                    category = row.binding().getCategory();
                    KeyBinding.Category current = category;
                    long count = visible.stream().filter(other -> other.binding().getCategory() == current).count();
                    addEntry(new CategoryEntry(category.getLabel(), (int) count));
                }
                addEntry(new BindingEntry(row));
            }
            setScrollY(0);
        }

        @Override
        public int getRowWidth() {
            return Math.max(200, Math.min(420, width - 40));
        }

        private abstract static class Entry extends ElementListWidget.Entry<Entry> {
            @Override
            public List<? extends Element> children() {
                return List.of();
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return List.of();
            }
        }

        private final class CategoryEntry extends Entry {
            private final Text label;

            CategoryEntry(Text categoryLabel, int count) {
                this.label = Text.empty().append(categoryLabel).append(Text.literal(" (" + count + ")").formatted(Formatting.GRAY));
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                context.drawCenteredTextWithShadow(textRenderer, label, KeyList.this.width / 2, getContentBottomEnd() - textRenderer.fontHeight - 1, 0xFFFFFFFF);
            }
        }

        private final class BindingEntry extends Entry {
            private final Row row;

            BindingEntry(Row row) {
                this.row = row;
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                TextRenderer font = textRenderer;
                int left = getContentX();
                int right = getContentRightEnd();
                int top = getContentY();
                int bottom = getContentBottomEnd();
                int textY = getContentMiddleY() - font.fontHeight / 2;

                if (hovered) {
                    context.fill(left - 2, top - 1, right + 2, bottom + 1, 0x30FFFFFF);
                    hoveredRow = row;
                }

                int keyLeft = right - KEY_BOX_WIDTH;
                context.fill(keyLeft, top, right, bottom, 0x80000000);
                boolean unbound = row.binding().isUnbound();
                String keyText = font.trimToWidth(row.binding().getBoundKeyLocalizedText().getString(), KEY_BOX_WIDTH - 6);
                KeyConflicts.Level level = row.level();
                if (level != KeyConflicts.Level.NONE) {
                    context.fill(keyLeft - 4, top, keyLeft - 1, bottom, level.color());
                }
                context.drawCenteredTextWithShadow(font, keyText, keyLeft + KEY_BOX_WIDTH / 2, textY,
                        unbound ? 0xFF808080 : level != KeyConflicts.Level.NONE ? level.color() : row.binding().isDefault() ? 0xFFFFFFFF : 0xFFB8E0FF);

                int sourceRight = keyLeft - 9;
                int available = sourceRight - left;
                int sourceBudget = Math.min(MAX_SOURCE_WIDTH, available / 2);
                String sourceText = ellipsize(font, row.source().label().getString(), sourceBudget);
                int sourceWidth = font.getWidth(sourceText);
                context.drawTextWithShadow(font, sourceText, sourceRight - sourceWidth, textY, row.source().color());

                String nameText = ellipsize(font, row.name().getString(), available - sourceWidth - 8);
                context.drawTextWithShadow(font, nameText, left, textY, 0xFFFFFFFF);
            }
        }
    }

    private static String ellipsize(TextRenderer font, String text, int maxWidth) {
        return GuiUtil.ellipsize(font, text, maxWidth);
    }
}
