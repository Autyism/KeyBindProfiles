package io.github.autyi6969.keybindprofilesplus.gui;

import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.external.ExternalBinding;
import io.github.autyi6969.keybindprofilesplus.external.ExternalKeys;
import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyi6969.keybindprofilesplus.keys.KeyLabels;
import io.github.autyi6969.keybindprofilesplus.keys.KeySource;
import io.github.autyi6969.keybindprofilesplus.keys.KeySourceResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only overview of every key in play: all key bindings registered with the game (vanilla and
 * mods alike) with the mod each one comes from, plus the hotkeys Meteor and malilib mods manage
 * on their own when those mods are installed. Searchable, filterable by source, groupable by
 * category or by source, with conflicts marked.
 */
public class KeyOverviewScreen extends Screen {
    /** Filter values that are not the name of a particular source. */
    public static final String FILTER_ALL = "";
    public static final String FILTER_VANILLA = "#vanilla";
    public static final String FILTER_MODS = "#mods";

    private static final int HEADER_HEIGHT = 82;
    private static final int ROW_HEIGHT = 20;
    private static final int KEY_BOX_WIDTH = 96;
    private static final int MAX_SOURCE_WIDTH = 110;

    private final Screen parent;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, HEADER_HEIGHT, 33);
    private final List<Row> rows = new ArrayList<>();

    private TextFieldWidget searchField;
    private CyclingButtonWidget<String> filterButton;
    private CyclingButtonWidget<Boolean> groupButton;
    private CyclingButtonWidget<Boolean> conflictsButton;
    private KeyList list;
    private List<String> filterValues = List.of(FILTER_ALL);
    private String filter = FILTER_ALL;
    private boolean groupBySource;
    private boolean conflictsOnly;
    private String query = "";
    private Text summary = Text.empty();
    private KeyConflicts.Summary conflictSummary = new KeyConflicts.Summary(0, 0);
    private Row hoveredRow;

    public KeyOverviewScreen(Screen parent) {
        super(Text.translatable("keybindprofilesplus.overview.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ExternalKeys.refresh();
        collectRows();

        DirectionalLayoutWidget header = layout.addHeader(DirectionalLayoutWidget.vertical().spacing(4));
        header.getMainPositioner().alignHorizontalCenter();
        header.add(new TextWidget(title, textRenderer));

        int half = Math.max(90, Math.min(190, (width - 24) / 2));
        DirectionalLayoutWidget first = header.add(DirectionalLayoutWidget.horizontal().spacing(4));
        searchField = first.add(new TextFieldWidget(textRenderer, half, 20, Text.translatable("keybindprofilesplus.overview.search")));
        searchField.setMaxLength(64);
        searchField.setText(query);
        searchField.setPlaceholder(Text.translatable("keybindprofilesplus.overview.search").setStyle(TextFieldWidget.SEARCH_STYLE));
        searchField.setChangedListener(value -> {
            query = value;
            refreshList();
        });
        conflictsButton = first.add(CyclingButtonWidget.onOffBuilder(conflictsOnly)
                .build(0, 0, half, 20, Text.translatable("keybindprofilesplus.overview.conflicts_only"), (button, value) -> {
                    conflictsOnly = value;
                    refreshList();
                }));

        DirectionalLayoutWidget second = header.add(DirectionalLayoutWidget.horizontal().spacing(4));
        if (!filterValues.contains(filter)) {
            filter = FILTER_ALL;
        }
        filterButton = second.add(CyclingButtonWidget.<String>builder(KeyOverviewScreen::filterLabel, filter)
                .values(filterValues)
                .build(0, 0, half, 20, Text.translatable("keybindprofilesplus.overview.filter"), (button, value) -> {
                    filter = value;
                    refreshList();
                }));
        groupButton = second.add(CyclingButtonWidget.<Boolean>builder(bySource -> Text.translatable(
                        bySource ? "keybindprofilesplus.overview.group.source" : "keybindprofilesplus.overview.group.category"), groupBySource)
                .values(Boolean.FALSE, Boolean.TRUE)
                .build(0, 0, half, 20, Text.translatable("keybindprofilesplus.overview.group"), (button, value) -> {
                    groupBySource = value;
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

        int summaryY = filterButton.getY() + 24;
        if (conflictSummary.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, summary, width / 2, summaryY, GuiUtil.GRAY);
        } else {
            Text conflicts = ConflictSummaryOverlay.text(conflictSummary);
            int total = textRenderer.getWidth(summary) + 10 + textRenderer.getWidth(conflicts);
            int x = (width - total) / 2;
            context.drawTextWithShadow(textRenderer, summary, x, summaryY, GuiUtil.GRAY);
            context.drawTextWithShadow(textRenderer, conflicts, x + textRenderer.getWidth(summary) + 10, summaryY,
                    (conflictSummary.hard() > 0 ? KeyConflicts.Level.HARD : KeyConflicts.Level.SOFT).color());
        }
        if (visibleBindingCount() == 0) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("keybindprofilesplus.overview.empty"),
                    width / 2, layout.getHeaderHeight() + layout.getContentHeight() / 2 - 4, GuiUtil.GRAY);
        }
        if (hoveredRow != null) {
            context.drawTooltip(textRenderer, hoveredRow.tooltip(), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ also used by the self-test

    public void setQuery(String query) {
        searchField.setText(query);
    }

    /** {@link #FILTER_ALL}, {@link #FILTER_VANILLA}, {@link #FILTER_MODS}, or the name of one source. */
    public void setSourceFilter(String filter) {
        if (filterValues.contains(filter)) {
            this.filter = filter;
            filterButton.setValue(filter);
            refreshList();
        }
    }

    public List<String> sourceFilterValues() {
        return filterValues;
    }

    public void setGroupBySource(boolean groupBySource) {
        this.groupBySource = groupBySource;
        groupButton.setValue(groupBySource);
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

    /** The group headings currently shown, in order. */
    public List<String> groupHeadings() {
        List<String> headings = new ArrayList<>();
        for (KeyList.Entry entry : list.children()) {
            if (entry instanceof KeyList.GroupEntry group) {
                headings.add(group.heading);
            }
        }
        return headings;
    }

    /**
     * Switches a mod key binding between "works during play" and "only works in screens", the way
     * a click on its row does. Returns the new setting ("general", "screen") or null for "automatic".
     */
    public String cycleScope(String bindingId) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        KeySourceResolver sources = KeyConflicts.sources(client.options);
        if (binding == null || !KeyConflicts.canOverrideScope(binding, sources)) {
            return null;
        }
        String current = KeyBindProfilesPlus.settings().scopeOverride(bindingId);
        String next = current == null ? KeyConflicts.OVERRIDE_GENERAL
                : current.equals(KeyConflicts.OVERRIDE_GENERAL) ? KeyConflicts.OVERRIDE_SCREEN : null;
        KeyBindProfilesPlus.settings().setScopeOverride(bindingId, next);
        collectRows();
        refreshList();
        return next;
    }

    // ------------------------------------------------------------------ data

    private void collectRows() {
        rows.clear();
        KeySourceResolver resolver = KeyConflicts.sources(client.options);
        KeyBinding[] bindings = client.options.allKeys.clone();
        Arrays.sort(bindings);
        for (KeyBinding binding : bindings) {
            rows.add(Row.of(binding, resolver, KeyConflicts.conflictsOf(binding, client.options)));
        }
        for (ExternalBinding external : ExternalKeys.all()) {
            rows.add(Row.of(external, KeyConflicts.conflictsOf(external, client.options)));
        }

        Set<String> sources = new LinkedHashSet<>();
        for (Row row : rows) {
            if (!row.source().isVanilla()) {
                sources.add(row.source().label().getString());
            }
        }
        List<String> values = new ArrayList<>(List.of(FILTER_ALL, FILTER_VANILLA));
        if (!sources.isEmpty()) {
            values.add(FILTER_MODS);
            values.addAll(sources);
        }
        filterValues = values;
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
            if (row.unbound()) {
                unbound++;
            }
            if (row.binding() != null) {
                if (row.level() == KeyConflicts.Level.HARD) {
                    hard++;
                } else if (row.level() == KeyConflicts.Level.SOFT) {
                    soft++;
                }
            }
            if (accepts(row) && row.matches(needle) && (!conflictsOnly || row.level() != KeyConflicts.Level.NONE)) {
                visible.add(row);
            }
        }

        list.setRows(visible);
        summary = Text.translatable("keybindprofilesplus.overview.summary", visible.size(), rows.size(), fromMods, unbound);
        conflictSummary = new KeyConflicts.Summary(hard, soft);
    }

    private boolean accepts(Row row) {
        return switch (filter) {
            case FILTER_ALL -> true;
            case FILTER_VANILLA -> row.source().isVanilla();
            case FILTER_MODS -> !row.source().isVanilla();
            default -> row.source().label().getString().equals(filter);
        };
    }

    private static Text filterLabel(String value) {
        return switch (value) {
            case FILTER_ALL -> Text.translatable("keybindprofilesplus.overview.filter.all");
            case FILTER_VANILLA -> Text.translatable("keybindprofilesplus.overview.filter.vanilla");
            case FILTER_MODS -> Text.translatable("keybindprofilesplus.overview.filter.mods");
            default -> Text.literal(value);
        };
    }

    /**
     * One line of the overview: a game key binding ({@code binding}) or a hotkey another mod
     * manages itself ({@code external}).
     */
    private record Row(KeyBinding binding, ExternalBinding external, Text name, Text category, KeySource source, Text keyText,
                       boolean unbound, boolean changed, KeyConflicts.Scope scope, boolean scopeAdjustable, boolean scopeOverridden,
                       List<KeyConflicts.Conflict> conflicts) {
        static Row of(KeyBinding binding, KeySourceResolver resolver, List<KeyConflicts.Conflict> conflicts) {
            boolean adjustable = KeyConflicts.canOverrideScope(binding, resolver);
            return new Row(binding, null, KeyLabels.name(binding), KeyLabels.category(binding.getCategory()), resolver.resolve(binding),
                    binding.getBoundKeyLocalizedText(), binding.isUnbound(), !binding.isDefault(), KeyConflicts.scopeOf(binding, resolver),
                    adjustable, adjustable && KeyBindProfilesPlus.settings().scopeOverride(binding.getId()) != null, conflicts);
        }

        static Row of(ExternalBinding external, List<KeyConflicts.Conflict> conflicts) {
            return new Row(null, external, Text.literal(external.name()), external.group(),
                    KeySource.external(external.sourceId(), external.group().getString()), external.keyText(), false, false,
                    external.screenOnly() ? KeyConflicts.Scope.SCREEN_ONLY : KeyConflicts.Scope.GENERAL, false, false, conflicts);
        }

        KeyConflicts.Level level() {
            return KeyConflicts.worst(conflicts);
        }

        boolean matches(String needle) {
            if (needle.isEmpty()) {
                return true;
            }
            return contains(name.getString(), needle)
                    || contains(keyText.getString(), needle)
                    || contains(source.description().getString(), needle)
                    || contains(category.getString(), needle)
                    || (binding != null && contains(binding.getId(), needle));
        }

        List<Text> tooltip() {
            List<Text> lines = new ArrayList<>();
            lines.add(name);
            lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.source", source.description()).formatted(Formatting.GRAY));
            if (binding != null) {
                lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.category", category).formatted(Formatting.GRAY));
                lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.default", binding.getDefaultKey().getLocalizedText()).formatted(Formatting.GRAY));
                if (changed) {
                    lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.changed").formatted(Formatting.GRAY));
                }
                if (scopeAdjustable) {
                    lines.add(Text.translatable(scopeOverridden ? "keybindprofilesplus.overview.tooltip.scope_set" : "keybindprofilesplus.overview.tooltip.scope_guessed",
                            scope.label()).formatted(Formatting.GRAY));
                    lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.scope_click").formatted(Formatting.DARK_AQUA));
                }
            } else {
                if (external.screenOnly()) {
                    lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.scope_set", scope.label()).formatted(Formatting.GRAY));
                }
                lines.add(Text.translatable("keybindprofilesplus.external.readonly", external.file()).formatted(Formatting.GOLD));
                if (!external.active()) {
                    lines.add(Text.translatable("keybindprofilesplus.external.inactive").formatted(Formatting.GRAY));
                }
            }
            lines.addAll(KeyConflicts.describe(conflicts));
            if (binding != null) {
                lines.add(Text.literal(binding.getId()).formatted(Formatting.DARK_GRAY));
            }
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
            // Insertion order keeps the game's category order (or, by source: Minecraft, mods, external).
            Map<String, List<Row>> groups = new LinkedHashMap<>();
            for (Row row : visible) {
                String heading = (groupBySource ? row.source().label() : row.category()).getString();
                groups.computeIfAbsent(heading, key -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<String, List<Row>> group : groups.entrySet()) {
                List<Row> members = group.getValue();
                if (groupBySource) {
                    members.sort(Comparator.comparing(row -> row.name().getString(), String.CASE_INSENSITIVE_ORDER));
                }
                addEntry(new GroupEntry(group.getKey(), members.size()));
                for (Row row : members) {
                    addEntry(new BindingEntry(row));
                }
            }
            setScrollY(0);
        }

        @Override
        public int getRowWidth() {
            return Math.max(220, Math.min(440, width - 40));
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

        private final class GroupEntry extends Entry {
            private final String heading;
            private final Text label;

            GroupEntry(String heading, int count) {
                this.heading = heading;
                this.label = Text.literal(heading).append(Text.literal(" (" + count + ")").formatted(Formatting.GRAY));
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                context.drawCenteredTextWithShadow(textRenderer, label, KeyList.this.width / 2, getContentBottomEnd() - textRenderer.fontHeight - 1, GuiUtil.WHITE);
            }
        }

        private final class BindingEntry extends Entry {
            private final Row row;

            BindingEntry(Row row) {
                this.row = row;
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                if (click.button() == 0 && row.scopeAdjustable()) {
                    cycleScope(row.binding().getId());
                    return true;
                }
                return false;
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
                    context.fill(left - 2, top - 1, right + 2, bottom + 1, GuiUtil.ROW_HOVER);
                    hoveredRow = row;
                }

                int keyLeft = right - KEY_BOX_WIDTH;
                context.fill(keyLeft, top, right, bottom, GuiUtil.VALUE_BOX);
                KeyConflicts.Level level = row.level();
                if (level != KeyConflicts.Level.NONE) {
                    context.fill(keyLeft - 4, top, keyLeft - 1, bottom, level.color());
                }
                String keyText = GuiUtil.ellipsize(font, row.keyText().getString(), KEY_BOX_WIDTH - 6);
                int keyColor = row.unbound() ? 0xFF808080
                        : level != KeyConflicts.Level.NONE ? level.color()
                        : row.external() != null && !row.external().active() ? GuiUtil.GRAY
                        : row.changed() ? 0xFFB8E0FF : GuiUtil.WHITE;
                context.drawCenteredTextWithShadow(font, keyText, keyLeft + KEY_BOX_WIDTH / 2, textY, keyColor);

                int sourceRight = keyLeft - 9;
                int available = sourceRight - left;
                // Grouped by source, the heading already says where the keys come from.
                String sourceText = groupBySource ? "" : GuiUtil.ellipsize(font, row.source().label().getString(), Math.min(MAX_SOURCE_WIDTH, available / 2));
                int sourceWidth = font.getWidth(sourceText);
                context.drawTextWithShadow(font, sourceText, sourceRight - sourceWidth, textY, row.source().color());

                String nameText = GuiUtil.ellipsize(font, row.name().getString(), available - sourceWidth - 8);
                context.drawTextWithShadow(font, nameText, left, textY, GuiUtil.WHITE);
            }
        }
    }
}
