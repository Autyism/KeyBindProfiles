package io.github.autyi6969.keybindprofilesplus.gui;

import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.external.ExternalBinding;
import io.github.autyi6969.keybindprofilesplus.external.ExternalKeys;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombos;
import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyi6969.keybindprofilesplus.keys.KeyLabels;
import io.github.autyi6969.keybindprofilesplus.keys.KeySource;
import io.github.autyi6969.keybindprofilesplus.keys.KeySourceResolver;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.EmptyWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
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
 * The mod's Key Binds screen, shown instead of the vanilla one (unless that is switched off in the
 * settings). Every key binding registered with the game can be rebound here - including
 * Ctrl / Shift / Alt combinations - with the mod each one comes from next to it and conflicts
 * marked as they arise. The hotkeys Meteor and malilib mods manage on their own are listed too,
 * read-only. Searchable, filterable by source, groupable by category or by source.
 */
public class KeyOverviewScreen extends ResizingScreen {
    /** Filter values that are not the name of a particular source. */
    public static final String FILTER_ALL = "";
    public static final String FILTER_VANILLA = "#vanilla";
    public static final String FILTER_MODS = "#mods";

    private static final Text RESET_TEXT = Text.translatable("controls.reset");
    private static final int HEADER_HEIGHT = 82;
    private static final int ROW_HEIGHT = 20;
    private static final int KEY_BUTTON_WIDTH = 86;
    private static final int MAX_SOURCE_WIDTH = 110;

    private final Screen parent;
    private ThreePartsLayoutWidget layout;
    private final List<Row> rows = new ArrayList<>();

    private TextFieldWidget searchField;
    private CyclingButtonWidget<String> filterButton;
    private CyclingButtonWidget<Boolean> groupButton;
    private CyclingButtonWidget<Boolean> conflictsButton;
    private ButtonWidget resetAllButton;
    private KeyList list;
    private List<String> filterValues = List.of(FILTER_ALL);
    private String filter = FILTER_ALL;
    private boolean groupBySource;
    private boolean conflictsOnly;
    private String query = "";
    private Text summary = Text.empty();
    private KeyConflicts.Summary conflictSummary = new KeyConflicts.Summary(0, 0);
    private Row hoveredRow;
    private boolean hoveredOverKey;
    private int resetWidth = 40;

    /** The binding that is waiting for its new key, or null. */
    private KeyBinding waiting;
    /** Ctrl / Shift / Alt pressed since the binding started waiting. */
    private int pendingModifiers;
    /** The key that was just bound also arrives as a typed character; it must not end up in the search box. */
    private boolean swallowTypedCharacter;

    public KeyOverviewScreen(Screen parent) {
        super(Text.translatable("keybindprofilesplus.overview.title"));
        this.parent = parent;
    }

    /**
     * What to open when the game asks for {@code screen}: this screen in place of the vanilla Key
     * Binds screen (leading back to the same place), anything else unchanged.
     */
    public static Screen replacementFor(Screen screen) {
        if (screen != null && screen.getClass() == KeybindsScreen.class && KeyBindProfilesPlus.settings().replaceKeyBinds()) {
            return new KeyOverviewScreen(((KeybindsScreen) screen).parent);
        }
        return screen;
    }

    @Override
    protected void init() {
        layout = startLayout(HEADER_HEIGHT, 33);
        ExternalKeys.refresh();
        collectRows();
        resetWidth = Math.max(40, textRenderer.getWidth(RESET_TEXT) + 12);

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
            refreshList(false);
        });
        conflictsButton = first.add(CyclingButtonWidget.onOffBuilder(conflictsOnly)
                .build(0, 0, half, 20, Text.translatable("keybindprofilesplus.overview.conflicts_only"), (button, value) -> {
                    conflictsOnly = value;
                    refreshList(false);
                }));

        DirectionalLayoutWidget second = header.add(DirectionalLayoutWidget.horizontal().spacing(4));
        if (!filterValues.contains(filter)) {
            filter = FILTER_ALL;
        }
        filterButton = second.add(CyclingButtonWidget.<String>builder(KeyOverviewScreen::filterLabel, filter)
                .values(filterValues)
                .build(0, 0, half, 20, Text.translatable("keybindprofilesplus.overview.filter"), (button, value) -> {
                    filter = value;
                    refreshList(false);
                }));
        groupButton = second.add(CyclingButtonWidget.<Boolean>builder(bySource -> Text.translatable(
                        bySource ? "keybindprofilesplus.overview.group.source" : "keybindprofilesplus.overview.group.category"), groupBySource)
                .values(Boolean.FALSE, Boolean.TRUE)
                .build(0, 0, half, 20, Text.translatable("keybindprofilesplus.overview.group"), (button, value) -> {
                    groupBySource = value;
                    refreshList(false);
                }));
        // Placeholder line: the summary text is drawn here by render().
        header.add(new EmptyWidget(1, textRenderer.fontHeight));

        list = layout.addBody(new KeyList(client));

        int buttonWidth = Math.max(60, Math.min(150, (width - 16 - 3 * 8) / 4));
        DirectionalLayoutWidget footer = layout.addFooter(DirectionalLayoutWidget.horizontal().spacing(8));
        footer.add(ButtonWidget.builder(Text.translatable("keybindprofilesplus.open"), button -> openProfiles()).width(buttonWidth).build());
        footer.add(ButtonWidget.builder(Text.translatable("keybindprofilesplus.compare.open_short"), button -> openCompare()).width(buttonWidth).build());
        resetAllButton = footer.add(ButtonWidget.builder(Text.translatable("controls.resetAll"), button -> askResetAll()).width(buttonWidth).build());
        footer.add(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).width(buttonWidth).build());

        layout.forEachChild(this::addDrawableChild);
        refreshList(false);
        refreshWidgetPositions();
    }

    @Override
    protected void setInitialFocus() {
        // Nothing is focused to begin with: a focused search box would swallow the first key typed.
    }

    @Override
    protected void refreshWidgetPositions() {
        if (rebuiltAfterResize()) {
            return;
        }
        layout.refreshPositions();
        if (list != null) {
            list.position(width, layout);
            // Also reached when coming back from another screen (a profile may have been applied there).
            reloadKeys();
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        cancelRebind();
        // Like the vanilla screen: key changes are written to options.txt when the screen is left.
        client.options.write();
    }

    @Override
    public void tick() {
        swallowTypedCharacter = false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return waiting == null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        hoveredRow = null;
        super.render(context, mouseX, mouseY, deltaTicks);

        int summaryY = filterButton.getY() + 24;
        if (waiting != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("keybindprofilesplus.overview.waiting", KeyLabels.name(waiting)),
                    width / 2, summaryY, GuiUtil.YELLOW);
        } else if (conflictSummary.isEmpty()) {
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
        if (hoveredRow != null && waiting == null) {
            // Over the key: only what matters while rebinding (conflicts). Over the name: everything about the binding.
            List<Text> lines = hoveredOverKey ? hoveredRow.keyTooltip() : hoveredRow.tooltip();
            if (!lines.isEmpty()) {
                context.drawTooltip(textRenderer, lines, mouseX, mouseY);
            }
        }
    }

    // ------------------------------------------------------------------ recording a new key

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (waiting != null) {
            finishRebind(InputUtil.Type.MOUSE.createFromCode(click.button()), click.modifiers());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (waiting == null) {
            return super.keyPressed(input);
        }
        if (input.isEscape()) {
            // As in vanilla: Escape leaves the binding without a key.
            finishRebind(InputUtil.UNKNOWN_KEY, 0);
            return true;
        }
        int modifier = KeyCombo.modifierOfKeyCode(input.key());
        if (modifier != 0) {
            // Held back: if another key follows, the two become a combination.
            pendingModifiers |= modifier;
            list.updateLabels();
            return true;
        }
        finishRebind(InputUtil.fromKeyCode(input), input.modifiers());
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        int modifier = KeyCombo.modifierOfKeyCode(input.key());
        if (waiting != null && modifier != 0 && (pendingModifiers & modifier) != 0) {
            // The modifier came back up without another key: it is the key the player wants.
            finishRebind(InputUtil.fromKeyCode(input), pendingModifiers & ~modifier);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (waiting != null || swallowTypedCharacter) {
            return true;
        }
        return super.charTyped(input);
    }

    private void finishRebind(InputUtil.Key key, int modifiers) {
        KeyBinding binding = waiting;
        waiting = null;
        pendingModifiers = 0;
        swallowTypedCharacter = true;
        KeyCombos.bind(binding, key, modifiers & KeyCombo.ALL);
        reloadKeys();
    }

    private void cancelRebind() {
        waiting = null;
        pendingModifiers = 0;
    }

    /** After any key change: lets the game know and shows the list (and its conflicts) as it is now. */
    private void reloadKeys() {
        KeyBinding.updateKeysByCode();
        collectRows();
        refreshList(true);
    }

    // ------------------------------------------------------------------ also used by the self-test

    /** Makes a binding wait for its new key, as a click on its key button does. */
    public boolean startRebind(String bindingId) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        if (binding == null) {
            return false;
        }
        waiting = binding;
        pendingModifiers = 0;
        setFocused(null);
        list.updateLabels();
        return true;
    }

    /** The id of the binding that is waiting for its new key, or null. */
    public String waitingFor() {
        return waiting == null ? null : waiting.getId();
    }

    /** Puts one binding back on its default key, as its Reset button does. */
    public void reset(String bindingId) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        if (binding != null) {
            cancelRebind();
            binding.setBoundKey(binding.getDefaultKey());
            reloadKeys();
        }
    }

    /** Puts every binding back on its default key (the button asks first). */
    public void resetAll() {
        cancelRebind();
        KeyCombos.batch(() -> {
            for (KeyBinding binding : client.options.allKeys) {
                binding.setBoundKey(binding.getDefaultKey());
            }
        });
        reloadKeys();
    }

    /** Screen coordinates {x, y} of a binding's key button, or null when its row is not listed. */
    public int[] keyButtonPoint(String bindingId) {
        return list.keyButtonPoint(bindingId);
    }

    public void setQuery(String query) {
        searchField.setText(query);
    }

    /** {@link #FILTER_ALL}, {@link #FILTER_VANILLA}, {@link #FILTER_MODS}, or the name of one source. */
    public void setSourceFilter(String filter) {
        if (filterValues.contains(filter)) {
            this.filter = filter;
            filterButton.setValue(filter);
            refreshList(false);
        }
    }

    public List<String> sourceFilterValues() {
        return filterValues;
    }

    public void setGroupBySource(boolean groupBySource) {
        this.groupBySource = groupBySource;
        groupButton.setValue(groupBySource);
        refreshList(false);
    }

    public void setConflictsOnly(boolean conflictsOnly) {
        this.conflictsOnly = conflictsOnly;
        conflictsButton.setValue(conflictsOnly);
        refreshList(false);
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
     * Changes when a mod's key binding counts as being in use, the way a click on its row does:
     * automatic, during play, only in screens, only in a special situation, and round again.
     * Returns the new setting ("general", "screen", "situational") or null for "automatic".
     */
    public String cycleScope(String bindingId) {
        KeyBinding binding = KeyBinding.byId(bindingId);
        if (binding == null || !KeyConflicts.canOverrideScope(binding, KeyConflicts.sources(client.options))) {
            return null;
        }
        return cycleOverride(bindingId);
    }

    /** The same for a hotkey of another mod, by its name and group as listed ("Auto Totem", "Meteor"). */
    public String cycleExternalScope(String group, String name) {
        for (Row row : rows) {
            if (row.external() != null && row.external().name().equals(name) && row.external().group().getString().equals(group)) {
                return cycleOverride(KeyConflicts.overrideKey(row.external()));
            }
        }
        return null;
    }

    private String cycleOverride(String settingName) {
        String next = KeyConflicts.nextOverride(KeyBindProfilesPlus.settings().scopeOverride(settingName));
        KeyBindProfilesPlus.settings().setScopeOverride(settingName, next);
        collectRows();
        refreshList(true);
        return next;
    }

    // ------------------------------------------------------------------ footer actions

    private void openProfiles() {
        if (parent instanceof KeyBindProfileScreen) {
            close();
        } else {
            client.setScreen(new KeyBindProfileScreen(this));
        }
    }

    /** Compares the applied profile (left) with the keys as they are set right now (right). */
    private void openCompare() {
        ProfileService service = KeyBindProfilesPlus.profileService();
        client.setScreen(new ProfileCompareScreen(this, service, appliedOrFirstProfile(service), null));
    }

    static String appliedOrFirstProfile(ProfileService service) {
        String applied = service.getCurrentProfile();
        if (applied != null && service.profiles().containsKey(applied)) {
            return applied;
        }
        return service.profiles().keySet().stream().min(String.CASE_INSENSITIVE_ORDER).orElse(null);
    }

    private void askResetAll() {
        client.setScreen(new ConfirmScreen(confirmed -> {
            client.setScreen(this);
            if (confirmed) {
                resetAll();
            }
        }, Text.translatable("keybindprofilesplus.overview.reset_all.title"), Text.translatable("keybindprofilesplus.overview.reset_all.message")));
    }

    // ------------------------------------------------------------------ data

    private void collectRows() {
        rows.clear();
        KeySourceResolver resolver = KeyConflicts.sources(client.options);
        KeyBinding[] bindings = client.options.allKeys.clone();
        Arrays.sort(bindings);
        for (KeyBinding binding : bindings) {
            rows.add(Row.of(binding, resolver, KeyConflicts.conflictsOf(binding, client.options),
                    KeyConflicts.sharedWithoutConflict(binding, client.options)));
        }
        for (ExternalBinding external : ExternalKeys.all()) {
            rows.add(Row.of(external, KeyConflicts.conflictsOf(external, client.options),
                    KeyConflicts.sharedWithoutConflict(external, client.options)));
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

    private void refreshList(boolean keepScroll) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Row> visible = new ArrayList<>();
        int fromMods = 0;
        int unbound = 0;
        int hard = 0;
        int soft = 0;
        boolean anyChanged = false;
        for (Row row : rows) {
            if (!row.source().isVanilla()) {
                fromMods++;
            }
            if (row.unbound()) {
                unbound++;
            }
            if (row.binding() != null) {
                anyChanged |= row.changed();
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

        double scroll = keepScroll ? list.getScrollY() : 0;
        list.setRows(visible);
        list.setScrollY(scroll);
        summary = Text.translatable("keybindprofilesplus.overview.summary", visible.size(), rows.size(), fromMods, unbound);
        conflictSummary = new KeyConflicts.Summary(hard, soft);
        resetAllButton.active = anyChanged;
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
     * One line of the list: a game key binding ({@code binding}) or a hotkey another mod manages
     * itself ({@code external}).
     *
     * @param shared what sits on the same key without being a conflict (see {@link KeyConflicts#sharedWithoutConflict})
     */
    private record Row(KeyBinding binding, ExternalBinding external, Text name, Text category, KeySource source, Text keyText,
                       boolean unbound, boolean changed, KeyConflicts.Scope scope, boolean scopeAdjustable, boolean scopeOverridden,
                       List<KeyConflicts.Conflict> conflicts, List<Text> shared) {
        static Row of(KeyBinding binding, KeySourceResolver resolver, List<KeyConflicts.Conflict> conflicts, List<Text> shared) {
            boolean adjustable = KeyConflicts.canOverrideScope(binding, resolver);
            return new Row(binding, null, KeyLabels.name(binding), KeyLabels.category(binding.getCategory()), resolver.resolve(binding),
                    binding.getBoundKeyLocalizedText(), binding.isUnbound(), !binding.isDefault(), KeyConflicts.scopeOf(binding, resolver),
                    adjustable, adjustable && KeyBindProfilesPlus.settings().scopeOverride(binding.getId()) != null, conflicts, shared);
        }

        static Row of(ExternalBinding external, List<KeyConflicts.Conflict> conflicts, List<Text> shared) {
            return new Row(null, external, Text.literal(external.name()), external.group(),
                    KeySource.external(external.sourceId(), external.group().getString()), external.keyText(), false, false,
                    KeyConflicts.scopeOf(external), true,
                    KeyBindProfilesPlus.settings().scopeOverride(KeyConflicts.overrideKey(external)) != null, conflicts, shared);
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
            } else {
                lines.add(Text.translatable("keybindprofilesplus.external.readonly", external.file()).formatted(Formatting.GOLD));
                if (!external.active()) {
                    lines.add(Text.translatable("keybindprofilesplus.external.inactive").formatted(Formatting.GRAY));
                }
            }
            if (scopeAdjustable) {
                lines.add(Text.translatable(scopeOverridden ? "keybindprofilesplus.overview.tooltip.scope_set" : "keybindprofilesplus.overview.tooltip.scope_guessed",
                        scope.label()).formatted(Formatting.GRAY));
                lines.add(Text.translatable("keybindprofilesplus.overview.tooltip.scope_click").formatted(Formatting.DARK_AQUA));
            }
            lines.addAll(KeyConflicts.describe(conflicts));
            lines.addAll(KeyConflicts.describeShared(shared));
            if (binding != null) {
                lines.add(Text.literal(binding.getId()).formatted(Formatting.DARK_GRAY));
            }
            return lines;
        }

        /** What the key button says when pointed at: the conflicts, and what shares the key without conflicting. */
        List<Text> keyTooltip() {
            List<Text> lines = new ArrayList<>(KeyConflicts.describe(conflicts));
            lines.addAll(KeyConflicts.describeShared(shared));
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
        }

        void updateLabels() {
            for (Entry entry : children()) {
                if (entry instanceof BindingEntry binding) {
                    binding.updateLabel();
                }
            }
        }

        int[] keyButtonPoint(String bindingId) {
            for (Entry entry : children()) {
                if (entry instanceof BindingEntry binding && binding.row.binding() != null && binding.row.binding().getId().equals(bindingId)) {
                    return new int[]{binding.keyLeft() + KEY_BUTTON_WIDTH / 2, binding.getContentMiddleY()};
                }
            }
            return null;
        }

        @Override
        public int getRowWidth() {
            return Math.max(240, Math.min(480, width - 40));
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
            /** Null for the read-only hotkeys of other mods. */
            private final ButtonWidget keyButton;
            private final ButtonWidget resetButton;
            private final List<ButtonWidget> buttons;

            BindingEntry(Row row) {
                this.row = row;
                KeyBinding binding = row.binding();
                if (binding == null) {
                    keyButton = null;
                    resetButton = null;
                    buttons = List.of();
                    return;
                }
                keyButton = ButtonWidget.builder(row.keyText(), button -> {
                            waiting = binding;
                            pendingModifiers = 0;
                            updateLabels();
                        })
                        .dimensions(0, 0, KEY_BUTTON_WIDTH, 20)
                        .narrationSupplier(text -> row.unbound()
                                ? Text.translatable("narrator.controls.unbound", row.name())
                                : Text.translatable("narrator.controls.bound", row.name(), text.get()))
                        .build();
                resetButton = ButtonWidget.builder(RESET_TEXT, button -> reset(binding.getId()))
                        .dimensions(0, 0, resetWidth, 20)
                        .narrationSupplier(text -> Text.translatable("narrator.controls.reset", row.name()))
                        .build();
                resetButton.active = row.changed();
                buttons = List.of(keyButton, resetButton);
                updateLabel();
            }

            /** The key button's text: the key, marked when it conflicts, or the "waiting for a key" decoration. */
            void updateLabel() {
                if (keyButton == null) {
                    return;
                }
                Text key = row.keyText();
                KeyConflicts.Level level = row.level();
                MutableText label;
                if (waiting == row.binding()) {
                    Text shown = pendingModifiers == 0 ? key : KeyCombo.withModifiers(pendingModifiers, Text.literal("..."));
                    label = Text.literal("> ").append(shown.copy().formatted(Formatting.WHITE, Formatting.UNDERLINE)).append(" <").formatted(Formatting.YELLOW);
                } else if (level != KeyConflicts.Level.NONE) {
                    label = Text.literal("[ ").append(key.copy().formatted(Formatting.WHITE)).append(" ]").formatted(level.formatting());
                } else {
                    label = row.unbound() ? key.copy().formatted(Formatting.GRAY) : key.copy();
                }
                keyButton.setMessage(label);
            }

            int keyLeft() {
                return getContentRightEnd() - resetWidth - 4 - KEY_BUTTON_WIDTH;
            }

            @Override
            public List<? extends Element> children() {
                return buttons;
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return buttons;
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                if (super.mouseClicked(click, doubled)) {
                    return true;
                }
                // A click beside the buttons changes when a mod's key counts as being in use.
                if (click.button() == 0 && row.scopeAdjustable()) {
                    cycleOverride(row.binding() != null ? row.binding().getId() : KeyConflicts.overrideKey(row.external()));
                    return true;
                }
                return false;
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                TextRenderer font = textRenderer;
                int left = getContentX();
                int right = getContentRightEnd();
                int top = getContentY() - 2;
                int bottom = top + ROW_HEIGHT;
                int textY = getContentMiddleY() - font.fontHeight / 2;

                int keyLeft = keyLeft();
                if (hovered) {
                    context.fill(left - 2, top, right + 2, bottom, GuiUtil.ROW_HOVER);
                    hoveredRow = row;
                    hoveredOverKey = mouseX >= keyLeft - 6;
                }

                KeyConflicts.Level level = row.level();
                if (keyButton != null) {
                    resetButton.setPosition(right - resetWidth, top);
                    resetButton.render(context, mouseX, mouseY, deltaTicks);
                    keyButton.setPosition(keyLeft, top);
                    keyButton.render(context, mouseX, mouseY, deltaTicks);
                } else {
                    // Read-only: a flat box where the two buttons would be.
                    context.fill(keyLeft, top + 1, right, bottom - 1, GuiUtil.VALUE_BOX);
                    String keyText = GuiUtil.ellipsize(font, row.keyText().getString(), right - keyLeft - 6);
                    int keyColor = level != KeyConflicts.Level.NONE ? level.color() : row.external().active() ? GuiUtil.WHITE : GuiUtil.GRAY;
                    context.drawCenteredTextWithShadow(font, keyText, (keyLeft + right) / 2, textY, keyColor);
                }
                if (level != KeyConflicts.Level.NONE) {
                    context.fill(keyLeft - 5, top + 1, keyLeft - 2, bottom - 1, level.color());
                }

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
