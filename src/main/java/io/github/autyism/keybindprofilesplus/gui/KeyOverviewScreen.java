package io.github.autyism.keybindprofilesplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.keys.KeyLabels;
import io.github.autyism.keybindprofilesplus.keys.KeySource;
import io.github.autyism.keybindprofilesplus.keys.KeySourceResolver;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The mod's Key Binds screen, shown instead of the vanilla one (unless that is switched off in the
 * settings). Every key binding registered with the game can be rebound here - including
 * Ctrl / Shift / Alt combinations - with the mod each one comes from next to it and conflicts
 * marked as they arise. The hotkeys Meteor and malilib mods manage on their own are listed too and
 * can be rebound the same way while those mods run (only what is known from a file alone, such as
 * Meteor profiles that are not loaded, stays read-only). Searchable, filterable by source,
 * groupable by category or by source.
 */
public class KeyOverviewScreen extends ResizingScreen {
    /** Filter values that are not the name of a particular source. */
    public static final String FILTER_ALL = "";
    public static final String FILTER_VANILLA = "#vanilla";
    public static final String FILTER_MODS = "#mods";

    private static final Component RESET_TEXT = Component.translatable("controls.reset");
    private static final int HEADER_HEIGHT = 82;
    private static final int ROW_HEIGHT = 20;
    private static final int KEY_BUTTON_WIDTH = 86;
    private static final int MAX_SOURCE_WIDTH = 110;

    private final Screen parent;
    private HeaderAndFooterLayout layout;
    private final List<Row> rows = new ArrayList<>();

    private EditBox searchField;
    private CycleButton<String> filterButton;
    private CycleButton<Boolean> groupButton;
    private CycleButton<Boolean> conflictsButton;
    private Button resetAllButton;
    private KeyList list;
    private List<String> filterValues = List.of(FILTER_ALL);
    private String filter = FILTER_ALL;
    private boolean groupBySource;
    private boolean conflictsOnly;
    private String query = "";
    private Component summary = Component.empty();
    private KeyConflicts.Summary conflictSummary = new KeyConflicts.Summary(0, 0);
    private Row hoveredRow;
    private boolean hoveredOverKey;
    private int resetWidth = 40;

    /** The binding that is waiting for its new key, or null. */
    private KeyMapping waiting;
    /** The hotkey of another mod that is waiting for its new key, or null (at most one of the two is set). */
    private ExternalBinding waitingExternal;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();
    /** Ctrl / Shift / Alt pressed since the binding started waiting. */
    private int pendingModifiers;
    /** The key that was just bound also arrives as a typed character; it must not end up in the search box. */
    private boolean swallowTypedCharacter;

    public KeyOverviewScreen(Screen parent) {
        super(Component.translatable("keybindprofilesplus.overview.title"));
        this.parent = parent;
    }

    /**
     * What to open when the game asks for {@code screen}: this screen in place of the vanilla Key
     * Binds screen (leading back to the same place), anything else unchanged.
     */
    public static Screen replacementFor(Screen screen) {
        if (screen != null && screen.getClass() == KeyBindsScreen.class && KeyBindProfilesPlus.settings().replaceKeyBinds()) {
            return new KeyOverviewScreen(((KeyBindsScreen) screen).lastScreen);
        }
        return screen;
    }

    @Override
    protected void init() {
        layout = startLayout(HEADER_HEIGHT, 33);
        ExternalKeys.refresh();
        collectRows();
        resetWidth = Math.max(40, font.width(RESET_TEXT) + 12);

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));

        int half = Math.max(90, Math.min(190, (width - 24) / 2));
        LinearLayout first = header.addChild(LinearLayout.horizontal().spacing(4));
        searchField = first.addChild(new EditBox(font, half, 20, Component.translatable("keybindprofilesplus.overview.search")));
        searchField.setMaxLength(64);
        searchField.setValue(query);
        searchField.setHint(Component.translatable("keybindprofilesplus.overview.search").setStyle(EditBox.SEARCH_HINT_STYLE));
        searchField.setResponder(value -> {
            query = value;
            refreshList(false);
        });
        conflictsButton = first.addChild(CycleButton.onOffBuilder(conflictsOnly)
                .create(0, 0, half, 20, Component.translatable("keybindprofilesplus.overview.conflicts_only"), (button, value) -> {
                    conflictsOnly = value;
                    refreshList(false);
                }));

        LinearLayout second = header.addChild(LinearLayout.horizontal().spacing(4));
        if (!filterValues.contains(filter)) {
            filter = FILTER_ALL;
        }
        filterButton = second.addChild(CycleButton.<String>builder(KeyOverviewScreen::filterLabel, filter)
                .withValues(filterValues)
                .create(0, 0, half, 20, Component.translatable("keybindprofilesplus.overview.filter"), (button, value) -> {
                    filter = value;
                    refreshList(false);
                }));
        groupButton = second.addChild(CycleButton.<Boolean>builder(bySource -> Component.translatable(
                        //? if >=1.21.11 {
                        bySource ? "keybindprofilesplus.overview.group.source" : "keybindprofilesplus.overview.group.category"), groupBySource)
                        //?} else
                        /*bySource ? "keybindprofilesplus.overview.group.source" : "keybindprofilesplus.overview.group.category")).withInitialValue(groupBySource)*/
                .withValues(Boolean.FALSE, Boolean.TRUE)
                .create(0, 0, half, 20, Component.translatable("keybindprofilesplus.overview.group"), (button, value) -> {
                    groupBySource = value;
                    refreshList(false);
                }));
        // Placeholder line: the summary text is drawn here by render().
        header.addChild(new SpacerElement(1, font.lineHeight));

        list = layout.addToContents(new KeyList(minecraft));

        int buttonWidth = Math.max(60, Math.min(150, (width - 16 - 3 * 8) / 4));
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("keybindprofilesplus.open"), button -> openProfiles()).width(buttonWidth).build());
        footer.addChild(Button.builder(Component.translatable("keybindprofilesplus.compare.open_short"), button -> openCompare()).width(buttonWidth).build());
        resetAllButton = footer.addChild(Button.builder(Component.translatable("controls.resetAll"), button -> askResetAll()).width(buttonWidth).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(buttonWidth).build());

        layout.visitWidgets(this::addRenderableWidget);
        refreshList(false);
        repositionElements();
    }

    @Override
    protected void setInitialFocus() {
        // Nothing is focused to begin with: a focused search box would swallow the first key typed.
    }

    @Override
    protected void repositionElements() {
        if (rebuiltAfterResize()) {
            return;
        }
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
            // Also reached when coming back from another screen (a profile may have been applied there).
            reloadKeys();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        cancelRebind();
        // Like the vanilla screen: key changes are written to options.txt when the screen is left.
        minecraft.options.save();
    }

    @Override
    public void tick() {
        swallowTypedCharacter = false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !isWaiting();
    }

    private boolean isWaiting() {
        return waiting != null || waitingExternal != null;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        hoveredRow = null;
        super.render(context, mouseX, mouseY, deltaTicks);

        int summaryY = filterButton.getY() + 24;
        Component status = statusMessage.getVisibleText();
        if (isWaiting()) {
            Component waitingName = waiting != null ? KeyLabels.name(waiting) : waitingExternal.label();
            context.drawCenteredString(font, Component.translatable("keybindprofilesplus.overview.waiting", waitingName),
                    width / 2, summaryY, GuiUtil.YELLOW);
        } else if (status != null) {
            context.drawCenteredString(font, status, width / 2, summaryY, GuiUtil.YELLOW);
        } else if (conflictSummary.isEmpty()) {
            context.drawCenteredString(font, summary, width / 2, summaryY, GuiUtil.GRAY);
        } else {
            Component conflicts = ConflictSummaryOverlay.text(conflictSummary);
            int total = font.width(summary) + 10 + font.width(conflicts);
            int x = (width - total) / 2;
            context.drawString(font, summary, x, summaryY, GuiUtil.GRAY);
            context.drawString(font, conflicts, x + font.width(summary) + 10, summaryY,
                    (conflictSummary.hard() > 0 ? KeyConflicts.Level.HARD : KeyConflicts.Level.SOFT).color());
        }
        if (visibleBindingCount() == 0) {
            context.drawCenteredString(font, Component.translatable("keybindprofilesplus.overview.empty"),
                    width / 2, layout.getHeaderHeight() + layout.getContentHeight() / 2 - 4, GuiUtil.GRAY);
        }
        if (hoveredRow != null && !isWaiting()) {
            // Over the key: only what matters while rebinding (conflicts). Over the name: everything about the binding.
            List<Component> lines = hoveredOverKey ? hoveredRow.keyTooltip() : hoveredRow.tooltip();
            if (!lines.isEmpty()) {
                //? if >=1.21.6 {
                context.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                //?} else
                /*setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());*/
            }
        }
    }

    // ------------------------------------------------------------------ recording a new key

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (isWaiting()) {
            finishRebind(InputConstants.Type.MOUSE.getOrCreate(click.button()), click.modifiers());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (!isWaiting()) {
            return super.keyPressed(input);
        }
        if (input.isEscape()) {
            // As in vanilla: Escape leaves the binding without a key.
            finishRebind(InputConstants.UNKNOWN, 0);
            return true;
        }
        int modifier = KeyCombo.modifierOfKeyCode(input.key());
        if (modifier != 0) {
            // Held back: if another key follows, the two become a combination.
            pendingModifiers |= modifier;
            list.updateLabels();
            return true;
        }
        finishRebind(InputConstants.getKey(input), input.modifiers());
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        int modifier = KeyCombo.modifierOfKeyCode(input.key());
        if (isWaiting() && modifier != 0 && (pendingModifiers & modifier) != 0) {
            // The modifier came back up without another key: it is the key the player wants.
            finishRebind(InputConstants.getKey(input), pendingModifiers & ~modifier);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (isWaiting() || swallowTypedCharacter) {
            return true;
        }
        return super.charTyped(input);
    }

    private void finishRebind(InputConstants.Key key, int modifiers) {
        KeyMapping binding = waiting;
        ExternalBinding external = waitingExternal;
        waiting = null;
        waitingExternal = null;
        pendingModifiers = 0;
        swallowTypedCharacter = true;
        if (external != null) {
            // The other mod has the last word (Meteor never binds a module to the left / right mouse button).
            if (!ExternalKeys.bind(external, key, modifiers & KeyCombo.ALL)) {
                statusMessage.show("keybindprofilesplus.overview.external_refused", external.label(), key.getDisplayName());
            }
        } else {
            KeyCombos.bind(binding, key, modifiers & KeyCombo.ALL);
        }
        reloadKeys();
    }

    private void cancelRebind() {
        waiting = null;
        waitingExternal = null;
        pendingModifiers = 0;
    }

    /** After any key change: lets the game know and shows the list (and its conflicts) as it is now. */
    private void reloadKeys() {
        KeyMapping.resetMapping();
        ExternalKeys.refresh();
        collectRows();
        refreshList(true);
    }

    // ------------------------------------------------------------------ also used by the self-test

    /** Makes a binding wait for its new key, as a click on its key button does. */
    public boolean startRebind(String bindingId) {
        KeyMapping binding = KeyMapping.get(bindingId);
        if (binding == null) {
            return false;
        }
        cancelRebind();
        waiting = binding;
        setFocused(null);
        list.updateLabels();
        return true;
    }

    /** The same for a hotkey of another mod, by its {@link ExternalBinding#hotkeyId()}. False if it is not listed as editable. */
    public boolean startExternalRebind(String hotkeyId) {
        Row row = externalRow(hotkeyId);
        if (row == null) {
            return false;
        }
        cancelRebind();
        waitingExternal = row.external();
        setFocused(null);
        list.updateLabels();
        return true;
    }

    /** The id of the binding (or {@link ExternalBinding#hotkeyId()} of the hotkey) that is waiting for its new key, or null. */
    public String waitingFor() {
        return waiting != null ? waiting.getName() : waitingExternal != null ? waitingExternal.hotkeyId() : null;
    }

    /** The message shown above the list right now (e.g. a key another mod refused), or null. */
    public String statusText() {
        Component status = statusMessage.getVisibleText();
        return status == null ? null : status.getString();
    }

    /** Puts a hotkey of another mod back on that mod's default, as its Reset button does. */
    public boolean resetExternal(String hotkeyId) {
        Row row = externalRow(hotkeyId);
        if (row == null) {
            return false;
        }
        cancelRebind();
        boolean done = ExternalKeys.reset(row.external());
        reloadKeys();
        return done;
    }

    /** What the list shows for a hotkey of another mod right now: its key text, or null when it is not listed. */
    public String externalKeyText(String hotkeyId) {
        Row row = externalRow(hotkeyId);
        return row == null ? null : row.keyText().getString();
    }

    private Row externalRow(String hotkeyId) {
        for (Row row : rows) {
            if (row.external() != null && hotkeyId.equals(row.external().hotkeyId())) {
                return row;
            }
        }
        return null;
    }

    /** Puts one binding back on its default key, as its Reset button does. */
    public void reset(String bindingId) {
        KeyMapping binding = KeyMapping.get(bindingId);
        if (binding != null) {
            cancelRebind();
            binding.setKey(binding.getDefaultKey());
            reloadKeys();
        }
    }

    /** Puts every binding back on its default key (the button asks first). */
    public void resetAll() {
        cancelRebind();
        KeyCombos.batch(() -> {
            for (KeyMapping binding : minecraft.options.keyMappings) {
                binding.setKey(binding.getDefaultKey());
            }
        });
        reloadKeys();
    }

    /** Screen coordinates {x, y} of a binding's (or another mod's hotkey's) key button, or null when its row is not listed. */
    public int[] keyButtonPoint(String bindingId) {
        return list.keyButtonPoint(bindingId);
    }

    public void setQuery(String query) {
        searchField.setValue(query);
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
        KeyMapping binding = KeyMapping.get(bindingId);
        if (binding == null || !KeyConflicts.canOverrideScope(binding, KeyConflicts.sources(minecraft.options))) {
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
            onClose();
        } else {
            minecraft.setScreen(new KeyBindProfileScreen(this));
        }
    }

    /** Compares the applied profile (left) with the keys as they are set right now (right). */
    private void openCompare() {
        ProfileService service = KeyBindProfilesPlus.profileService();
        minecraft.setScreen(new ProfileCompareScreen(this, service, appliedOrFirstProfile(service), null));
    }

    static String appliedOrFirstProfile(ProfileService service) {
        String applied = service.getCurrentProfile();
        if (applied != null && service.profiles().containsKey(applied)) {
            return applied;
        }
        return service.profiles().keySet().stream().min(String.CASE_INSENSITIVE_ORDER).orElse(null);
    }

    private void askResetAll() {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                resetAll();
            }
        }, Component.translatable("keybindprofilesplus.overview.reset_all.title"), Component.translatable("keybindprofilesplus.overview.reset_all.message")));
    }

    // ------------------------------------------------------------------ data

    private void collectRows() {
        rows.clear();
        KeySourceResolver resolver = KeyConflicts.sources(minecraft.options);
        KeyMapping[] bindings = minecraft.options.keyMappings.clone();
        Arrays.sort(bindings);
        for (KeyMapping binding : bindings) {
            rows.add(Row.of(binding, resolver, KeyConflicts.conflictsOf(binding, minecraft.options),
                    KeyConflicts.sharedWithoutConflict(binding, minecraft.options)));
        }
        for (ExternalBinding external : ExternalKeys.all()) {
            rows.add(Row.of(external, KeyConflicts.conflictsOf(external, minecraft.options),
                    KeyConflicts.sharedWithoutConflict(external, minecraft.options)));
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
            }
            if (row.binding() != null || row.external().active()) {
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

        double scroll = keepScroll ? list.scrollAmount() : 0;
        list.setRows(visible);
        list.setScrollAmount(scroll);
        summary = Component.translatable("keybindprofilesplus.overview.summary", visible.size(), rows.size(), fromMods, unbound);
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

    private static Component filterLabel(String value) {
        return switch (value) {
            case FILTER_ALL -> Component.translatable("keybindprofilesplus.overview.filter.all");
            case FILTER_VANILLA -> Component.translatable("keybindprofilesplus.overview.filter.vanilla");
            case FILTER_MODS -> Component.translatable("keybindprofilesplus.overview.filter.mods");
            default -> Component.literal(value);
        };
    }

    /**
     * One line of the list: a game key binding ({@code binding}) or a hotkey another mod manages
     * itself ({@code external}).
     *
     * @param shared what sits on the same key without being a conflict (see {@link KeyConflicts#sharedWithoutConflict})
     */
    private record Row(KeyMapping binding, ExternalBinding external, Component name, Component category, KeySource source, Component keyText,
                       boolean unbound, boolean changed, KeyConflicts.Scope scope, boolean scopeAdjustable, boolean scopeOverridden,
                       List<KeyConflicts.Conflict> conflicts, List<Component> shared) {
        static Row of(KeyMapping binding, KeySourceResolver resolver, List<KeyConflicts.Conflict> conflicts, List<Component> shared) {
            boolean adjustable = KeyConflicts.canOverrideScope(binding, resolver);
            return new Row(binding, null, KeyLabels.name(binding), KeyLabels.category(binding.getCategory()), resolver.resolve(binding),
                    binding.getTranslatedKeyMessage(), binding.isUnbound(), !binding.isDefault(), KeyConflicts.scopeOf(binding, resolver),
                    adjustable, adjustable && KeyBindProfilesPlus.settings().scopeOverride(binding.getName()) != null, conflicts, shared);
        }

        static Row of(ExternalBinding external, List<KeyConflicts.Conflict> conflicts, List<Component> shared) {
            return new Row(null, external, external.title(), external.group(),
                    KeySource.external(external.sourceId(), external.group().getString()), external.keyText(), external.unbound(), external.changed(),
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
                    || (binding != null && contains(binding.getName(), needle))
                    || (external != null && contains(external.name(), needle));
        }

        List<Component> tooltip() {
            List<Component> lines = new ArrayList<>();
            lines.add(name);
            lines.add(Component.translatable("keybindprofilesplus.overview.tooltip.source", source.description()).withStyle(ChatFormatting.GRAY));
            if (binding != null) {
                lines.add(Component.translatable("keybindprofilesplus.overview.tooltip.category", category).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("keybindprofilesplus.overview.tooltip.default", binding.getDefaultKey().getDisplayName()).withStyle(ChatFormatting.GRAY));
                if (changed) {
                    lines.add(Component.translatable("keybindprofilesplus.overview.tooltip.changed").withStyle(ChatFormatting.GRAY));
                }
            } else if (external.editable()) {
                lines.add(Component.translatable("keybindprofilesplus.external.editable", external.file()).withStyle(ChatFormatting.GRAY));
                if (external.defaultValue() != null) {
                    lines.add(Component.translatable("keybindprofilesplus.overview.tooltip.default",
                            ExternalKeys.describeValue(external.hotkeyId(), external.defaultValue())).withStyle(ChatFormatting.GRAY));
                }
                if (changed) {
                    lines.add(Component.translatable("keybindprofilesplus.overview.tooltip.changed").withStyle(ChatFormatting.GRAY));
                }
            } else {
                lines.add(Component.translatable("keybindprofilesplus.external.readonly", external.file()).withStyle(ChatFormatting.GOLD));
                if (!external.active()) {
                    lines.add(Component.translatable("keybindprofilesplus.external.inactive").withStyle(ChatFormatting.GRAY));
                }
            }
            if (scopeAdjustable) {
                lines.add(Component.translatable(scopeOverridden ? "keybindprofilesplus.overview.tooltip.scope_set" : "keybindprofilesplus.overview.tooltip.scope_guessed",
                        scope.label()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("keybindprofilesplus.overview.tooltip.scope_click").withStyle(ChatFormatting.DARK_AQUA));
            }
            lines.addAll(KeyConflicts.describe(conflicts));
            lines.addAll(KeyConflicts.describeShared(shared));
            if (binding != null) {
                lines.add(Component.literal(binding.getName()).withStyle(ChatFormatting.DARK_GRAY));
            } else if (external.editable()) {
                lines.add(Component.literal(external.hotkeyId()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return lines;
        }

        /** What the key button says when pointed at: the conflicts, and what shares the key without conflicting. */
        List<Component> keyTooltip() {
            List<Component> lines = new ArrayList<>(KeyConflicts.describe(conflicts));
            lines.addAll(KeyConflicts.describeShared(shared));
            return lines;
        }

        private static boolean contains(String text, String needle) {
            return text.toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    private final class KeyList extends ContainerObjectSelectionList<KeyList.Entry> {
        KeyList(Minecraft client) {
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
                if (entry instanceof BindingEntry binding && binding.keyButton != null && (binding.row.binding() != null
                        ? binding.row.binding().getName().equals(bindingId) : bindingId.equals(binding.row.external().hotkeyId()))) {
                    return new int[]{binding.keyLeft() + KEY_BUTTON_WIDTH / 2, binding.getContentYMiddle()};
                }
            }
            return null;
        }

        @Override
        public int getRowWidth() {
            return Math.max(240, Math.min(480, width - 40));
        }

        private abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }
        }

        private final class GroupEntry extends Entry {
            private final String heading;
            private final Component label;

            GroupEntry(String heading, int count) {
                this.heading = heading;
                this.label = Component.literal(heading).append(Component.literal(" (" + count + ")").withStyle(ChatFormatting.GRAY));
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                context.drawCenteredString(font, label, KeyList.this.width / 2, getContentBottom() - font.lineHeight - 1, GuiUtil.WHITE);
            }
        }

        private final class BindingEntry extends Entry {
            private final Row row;
            /** Null for the read-only hotkeys of other mods. */
            private final Button keyButton;
            private final Button resetButton;
            private final List<Button> buttons;

            BindingEntry(Row row) {
                this.row = row;
                KeyMapping binding = row.binding();
                ExternalBinding external = row.external();
                if (binding == null && !external.editable()) {
                    keyButton = null;
                    resetButton = null;
                    buttons = List.of();
                    return;
                }
                keyButton = Button.builder(row.keyText(), button -> {
                            cancelRebind();
                            if (binding != null) {
                                waiting = binding;
                            } else {
                                waitingExternal = external;
                            }
                            updateLabels();
                        })
                        .bounds(0, 0, KEY_BUTTON_WIDTH, 20)
                        .createNarration(text -> row.unbound()
                                ? Component.translatable("narrator.controls.unbound", row.name())
                                : Component.translatable("narrator.controls.bound", row.name(), text.get()))
                        .build();
                resetButton = Button.builder(RESET_TEXT, button -> {
                            if (binding != null) {
                                reset(binding.getName());
                            } else {
                                resetExternal(external.hotkeyId());
                            }
                        })
                        .bounds(0, 0, resetWidth, 20)
                        .createNarration(text -> Component.translatable("narrator.controls.reset", row.name()))
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
                Component key = row.keyText();
                KeyConflicts.Level level = row.level();
                MutableComponent label;
                boolean rowWaiting = row.binding() != null ? waiting == row.binding()
                        : waitingExternal != null && waitingExternal.hotkeyId().equals(row.external().hotkeyId());
                if (rowWaiting) {
                    Component shown = pendingModifiers == 0 ? key : KeyCombo.withModifiers(pendingModifiers, Component.literal("..."));
                    label = Component.literal("> ").append(shown.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)).append(" <").withStyle(ChatFormatting.YELLOW);
                } else if (level != KeyConflicts.Level.NONE) {
                    label = Component.literal("[ ").append(key.copy().withStyle(ChatFormatting.WHITE)).append(" ]").withStyle(level.formatting());
                } else {
                    label = row.unbound() ? key.copy().withStyle(ChatFormatting.GRAY) : key.copy();
                }
                keyButton.setMessage(label);
            }

            int keyLeft() {
                return getContentRight() - resetWidth - 4 - KEY_BUTTON_WIDTH;
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return buttons;
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return buttons;
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                if (super.mouseClicked(click, doubled)) {
                    return true;
                }
                // A click beside the buttons changes when a mod's key counts as being in use.
                if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && row.scopeAdjustable()) {
                    cycleOverride(row.binding() != null ? row.binding().getName() : KeyConflicts.overrideKey(row.external()));
                    return true;
                }
                return false;
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                Font font = KeyOverviewScreen.this.font;
                int left = getContentX();
                int right = getContentRight();
                int top = getContentY() - 2;
                int bottom = top + ROW_HEIGHT;
                int textY = getContentYMiddle() - font.lineHeight / 2;

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
                    context.drawCenteredString(font, keyText, (keyLeft + right) / 2, textY, keyColor);
                }
                if (level != KeyConflicts.Level.NONE) {
                    context.fill(keyLeft - 5, top + 1, keyLeft - 2, bottom - 1, level.color());
                }

                int sourceRight = keyLeft - 9;
                int available = sourceRight - left;
                // Grouped by source, the heading already says where the keys come from.
                String sourceText = groupBySource ? "" : GuiUtil.ellipsize(font, row.source().label().getString(), Math.min(MAX_SOURCE_WIDTH, available / 2));
                int sourceWidth = font.width(sourceText);
                context.drawString(font, sourceText, sourceRight - sourceWidth, textY, row.source().color());

                String nameText = GuiUtil.ellipsize(font, row.name().getString(), available - sourceWidth - 8);
                context.drawString(font, nameText, left, textY, GuiUtil.WHITE);
            }
        }
    }
}
