package io.github.autyism.keybindprofilesplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.keys.KeyContext;
import io.github.autyism.keybindprofilesplus.keys.KeyLabels;
import io.github.autyism.keybindprofilesplus.keys.KeySource;
import io.github.autyism.keybindprofilesplus.keys.KeySourceResolver;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
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
import net.minecraft.util.FormattedCharSequence;

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
    /** Opens the list of sources below it (a click picks one) instead of stepping through them one by one. */
    private Button filterButton;
    private boolean filterOpen;
    private int filterScroll;
    private CycleButton<Boolean> groupButton;
    private CycleButton<Boolean> conflictsButton;
    private Button resetAllButton;
    private KeyList list;
    private List<String> filterValues = List.of(FILTER_ALL);
    private String filter = FILTER_ALL;
    private boolean groupBySource;
    private boolean conflictsOnly;
    /**
     * Rows shown since "conflicts only" was turned on. One whose conflict is solved meanwhile (a click on its row,
     * a new key) stays in the list, in green, until "conflicts only" is switched again or the screen is opened anew.
     */
    private final Set<String> conflictRowsShown = new HashSet<>();
    private String query = "";
    private Component summary = Component.empty();
    private KeyConflicts.Summary conflictSummary = new KeyConflicts.Summary(0, 0);
    private Row hoveredRow;
    private boolean hoveredOverKey;
    /** For the self-test: the row whose tooltip is drawn whatever the mouse does, or null. */
    private String tooltipForTesting;
    private boolean tooltipForTestingOverKey;
    /** The tooltip last drawn, already wrapped, so pointing at a row does not rebuild it every frame. */
    private Row cachedTooltipRow;
    private boolean cachedTooltipOverKey;
    private int cachedTooltipWidth;
    private List<FormattedCharSequence> cachedTooltip = List.of();
    private int resetWidth = 40;
    private int visibleCount;

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
                    conflictRowsShown.clear();
                    refreshList(false);
                }));

        LinearLayout second = header.addChild(LinearLayout.horizontal().spacing(4));
        if (!filterValues.contains(filter)) {
            filter = FILTER_ALL;
        }
        filterButton = second.addChild(Button.builder(filterMessage(), button -> {
                    filterOpen = !filterOpen;
                    filterScroll = Math.max(0, filterValues.indexOf(filter) - 2);
                }).width(half).build());
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
        Row tooltipRow = hoveredRow;
        boolean overKey = hoveredOverKey;
        int tooltipX = mouseX;
        int tooltipY = mouseY;
        if (tooltipForTesting != null) {
            int[] point = list.keyButtonPoint(tooltipForTesting);
            tooltipRow = point == null ? null : rowById(tooltipForTesting);
            overKey = tooltipForTestingOverKey;
            tooltipX = point == null ? 0 : overKey ? point[0] : list.getRowLeft() + 20;
            tooltipY = point == null ? 0 : point[1];
        }
        if (filterOpen) {
            // Drawn last, over the list; no row tooltip on top of it.
            renderFilterList(context, mouseX, mouseY);
            tooltipRow = null;
        }
        if (tooltipRow != null && !isWaiting()) {
            List<FormattedCharSequence> wrapped = wrappedTooltip(tooltipRow, overKey);
            if (!wrapped.isEmpty()) {
                //? if >=1.21.6 {
                context.setTooltipForNextFrame(font, wrapped, tooltipX, tooltipY);
                //?} else
                /*setTooltipForNextRenderPass(wrapped);*/
            }
        }
    }

    /**
     * The tooltip of a row, over its key button (only what matters while rebinding: conflicts) or over its name
     * (everything about it). Long lines (where a key acts, why two keys clash) are wrapped so the tooltip stays on
     * the screen. Built once per row and kept while the pointer stays there; rows are new after every change.
     */
    private List<FormattedCharSequence> wrappedTooltip(Row row, boolean overKey) {
        int maxWidth = Math.max(120, Math.min(width - 24, 360));
        if (row != cachedTooltipRow || overKey != cachedTooltipOverKey || maxWidth != cachedTooltipWidth) {
            List<FormattedCharSequence> wrapped = new ArrayList<>();
            for (Component line : overKey ? row.keyTooltip() : row.tooltip()) {
                wrapped.addAll(font.split(line, maxWidth));
            }
            cachedTooltipRow = row;
            cachedTooltipOverKey = overKey;
            cachedTooltipWidth = maxWidth;
            cachedTooltip = wrapped;
        }
        return cachedTooltip;
    }

    /**
     * For the self-test: draws the tooltip of one row (a binding id or a hotkey id) as if the mouse pointed at
     * its key button ({@code overKey}) or its name, so screenshots can show it; null stops it.
     */
    public void showTooltipForTesting(String id, boolean overKey) {
        tooltipForTesting = id;
        tooltipForTestingOverKey = overKey;
    }

    private Row rowById(String id) {
        for (Row row : rows) {
            if (row.binding() != null ? row.binding().getName().equals(id) : id.equals(row.external().hotkeyId())) {
                return row;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ recording a new key

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (filterOpen && !isWaiting()) {
            if (overFilterList(click.x(), click.y())) {
                int index = filterScroll + (int) ((click.y() - filterListTop()) / FILTER_ROW);
                if (index >= 0 && index < filterValues.size()) {
                    setSourceFilter(filterValues.get(index));
                }
            }
            // Any click closes the list; one beside it (also on the source button) does nothing else.
            filterOpen = false;
            return true;
        }
        if (isWaiting()) {
            finishRebind(InputConstants.Type.MOUSE.getOrCreate(click.button()), click.modifiers());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (filterOpen) {
            if (overFilterList(mouseX, mouseY)) {
                filterScroll = Math.max(0, Math.min(filterScroll - (int) Math.signum(verticalAmount), filterValues.size() - filterVisibleRows()));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (filterOpen && !isWaiting() && input.isEscape()) {
            filterOpen = false;
            return true;
        }
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

    /** Shows the keys and their conflicts as they are now, after they were changed somewhere else (also used by the self-test). */
    public void refreshKeys() {
        reloadKeys();
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
            filterOpen = false;
            filterButton.setMessage(filterMessage());
            refreshList(false);
        }
    }

    /** For the self-test: opens or closes the list of sources under the source button. */
    public void setSourceListOpen(boolean open) {
        filterOpen = open;
        filterScroll = 0;
    }

    public boolean sourceListOpen() {
        return filterOpen;
    }

    /** For the self-test: the screen point of an entry in the open source list, or null when it is not shown. */
    public int[] sourceListPoint(String value) {
        int index = filterValues.indexOf(value) - filterScroll;
        if (!filterOpen || index < 0 || index >= filterVisibleRows()) {
            return null;
        }
        return new int[]{filterButton.getX() + filterButton.getWidth() / 2, filterListTop() + index * FILTER_ROW + FILTER_ROW / 2};
    }

    /** Ids of the rows shown in green: their conflict was solved while "conflicts only" was on. */
    public List<String> solvedConflictRows() {
        List<String> solved = new ArrayList<>();
        for (KeyList.Entry entry : list.children()) {
            if (entry instanceof KeyList.BindingEntry binding && binding.solved) {
                solved.add(rowId(binding.row));
            }
        }
        return solved;
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
        conflictRowsShown.clear();
        conflictsButton.setValue(conflictsOnly);
        refreshList(false);
    }

    /** How many keys the list shows right now (counted when the list is filled, not every frame). */
    public int visibleBindingCount() {
        return visibleCount;
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
     * Changes where a mod's key binding counts as acting, the way a click on its row does: automatic,
     * in the world, in inventory screens, in any screen, everywhere, only in a special situation, and
     * round again. Returns the new setting (see {@link KeyConflicts#nextOverride}) or null for "automatic".
     */
    public String cycleScope(String bindingId) {
        KeyMapping binding = KeyMapping.get(bindingId);
        if (binding == null || !KeyConflicts.canOverrideContext(binding, KeyConflicts.sources(minecraft.options))) {
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
        List<ExternalBinding> externals = ExternalKeys.all();
        KeyConflicts.Verdicts verdicts = KeyConflicts.verdicts(minecraft.options);
        KeyMapping[] bindings = minecraft.options.keyMappings.clone();
        Arrays.sort(bindings);
        for (KeyMapping binding : bindings) {
            rows.add(Row.of(binding, resolver, verdicts.conflictsOf(binding), verdicts.sharedWithoutConflict(binding)));
        }
        for (ExternalBinding external : externals) {
            rows.add(Row.of(external, verdicts.conflictsOf(external), verdicts.sharedWithoutConflict(external)));
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
            if (accepts(row) && row.matches(needle)) {
                if (!conflictsOnly) {
                    visible.add(row);
                } else if (row.level() != KeyConflicts.Level.NONE) {
                    conflictRowsShown.add(rowId(row));
                    visible.add(row);
                } else if (conflictRowsShown.contains(rowId(row))) {
                    visible.add(row);
                }
            }
        }

        double scroll = keepScroll ? list.scrollAmount() : 0;
        list.setRows(visible);
        visibleCount = visible.size();
        list.setScrollAmount(scroll);
        summary = Component.translatable("keybindprofilesplus.overview.summary", visible.size(), rows.size(), fromMods, unbound);
        conflictSummary = new KeyConflicts.Summary(hard, soft);
        resetAllButton.active = anyChanged;
    }

    /** Stable id of a row: "key:" + the binding's name, or "ext:" + the other mod's hotkey name. */
    private static String rowId(Row row) {
        // the same hotkey can be listed twice (the loaded Meteor profile and a saved one), so the group is part of it
        return row.binding() != null ? "key:" + row.binding().getName() : "ext:" + row.external().group().getString() + "/" + KeyConflicts.overrideKey(row.external());
    }

    private boolean accepts(Row row) {
        return switch (filter) {
            case FILTER_ALL -> true;
            case FILTER_VANILLA -> row.source().isVanilla();
            case FILTER_MODS -> !row.source().isVanilla();
            default -> row.source().label().getString().equals(filter);
        };
    }

    private Component filterMessage() {
        return Component.translatable("keybindprofilesplus.overview.filter.button", filterLabel(filter));
    }

    // ------------------------------------------------------------------ the source list under the source button

    private static final int FILTER_ROW = 14;

    private int filterListTop() {
        return filterButton.getY() + filterButton.getHeight() + 1;
    }

    /** Rows of the open list: as many sources as fit down to the bottom of the screen (at least 3). */
    private int filterVisibleRows() {
        int fit = filterButton == null ? 10 : (height - 6 - filterListTop()) / FILTER_ROW;
        return Math.max(1, Math.min(filterValues.size(), Math.max(3, fit)));
    }

    private boolean overFilterList(double mouseX, double mouseY) {
        int top = filterListTop();
        return mouseX >= filterButton.getX() && mouseX < filterButton.getX() + filterButton.getWidth()
                && mouseY >= top && mouseY < top + filterVisibleRows() * FILTER_ROW;
    }

    private void renderFilterList(GuiGraphics context, int mouseX, int mouseY) {
        int rowsShown = filterVisibleRows();
        filterScroll = Math.max(0, Math.min(filterScroll, filterValues.size() - rowsShown));
        int left = filterButton.getX();
        int right = left + filterButton.getWidth();
        int top = filterListTop();
        int bottom = top + rowsShown * FILTER_ROW;
        context.fill(left - 1, top - 1, right + 1, bottom + 1, GuiUtil.GRAY);
        context.fill(left, top, right, bottom, 0xF0101010);
        boolean scrolls = filterValues.size() > rowsShown;
        int textRight = scrolls ? right - 6 : right - 4;
        for (int i = 0; i < rowsShown; i++) {
            String value = filterValues.get(filterScroll + i);
            int y = top + i * FILTER_ROW;
            if (mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + FILTER_ROW) {
                context.fill(left, y, right, y + FILTER_ROW, 0x40FFFFFF);
            }
            String text = GuiUtil.ellipsize(font, filterLabel(value).getString(), textRight - left - 6);
            context.drawString(font, text, left + 5, y + (FILTER_ROW - font.lineHeight) / 2 + 1, value.equals(filter) ? GuiUtil.YELLOW : GuiUtil.WHITE);
        }
        if (scrolls) {
            int barHeight = Math.max(8, (bottom - top) * rowsShown / filterValues.size());
            int barTop = top + (bottom - top - barHeight) * filterScroll / (filterValues.size() - rowsShown);
            context.fill(right - 4, barTop, right - 1, barTop + barHeight, 0xFFC0C0C0);
        }
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
                       boolean unbound, boolean changed, KeyContext context, Component contextLine, boolean scopeAdjustable, boolean scopeOverridden,
                       List<KeyConflicts.Conflict> conflicts, List<KeyConflicts.Shared> shared) {
        static Row of(KeyMapping binding, KeySourceResolver resolver, List<KeyConflicts.Conflict> conflicts, List<KeyConflicts.Shared> shared) {
            boolean adjustable = KeyConflicts.canOverrideContext(binding, resolver);
            boolean overridden = adjustable && KeyBindProfilesPlus.settings().scopeOverride(binding.getName()) != null;
            KeyContext context = KeyConflicts.contextOf(binding, resolver);
            return new Row(binding, null, KeyLabels.name(binding), KeyLabels.category(binding.getCategory()), resolver.resolve(binding),
                    binding.getTranslatedKeyMessage(), binding.isUnbound(), !binding.isDefault(), context, contextLine(context, adjustable, overridden),
                    adjustable, overridden, conflicts, shared);
        }

        static Row of(ExternalBinding external, List<KeyConflicts.Conflict> conflicts, List<KeyConflicts.Shared> shared) {
            boolean overridden = KeyBindProfilesPlus.settings().scopeOverride(KeyConflicts.overrideKey(external)) != null;
            KeyContext context = KeyConflicts.contextOf(external);
            return new Row(null, external, external.title(), external.group(),
                    KeySource.external(external.sourceId(), external.group().getString()), external.keyText(), external.unbound(), external.changed(),
                    context, contextLine(context, true, overridden), true, overridden, conflicts, shared);
        }

        private static Component contextLine(KeyContext context, boolean adjustable, boolean overridden) {
            return KeyConflicts.contextLine(context, adjustable, overridden);
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
            lines.add(contextLine);
            if (scopeAdjustable) {
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

        /** What the key button says when pointed at: the conflicts and what shares the key without conflicting, after where this key acts. */
        List<Component> keyTooltip() {
            List<Component> lines = new ArrayList<>(KeyConflicts.describe(conflicts));
            lines.addAll(KeyConflicts.describeShared(shared));
            if (!lines.isEmpty()) {
                lines.add(0, contextLine);
            }
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
            /** Shown only because it was in the "conflicts only" list before its conflict was solved: drawn green. */
            private final boolean solved;
            /** Null for the read-only hotkeys of other mods. */
            private final Button keyButton;
            private final Button resetButton;
            private final List<Button> buttons;

            BindingEntry(Row row) {
                this.row = row;
                this.solved = conflictsOnly && row.level() == KeyConflicts.Level.NONE;
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
                } else if (solved) {
                    label = key.copy().withStyle(ChatFormatting.GREEN);
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
                    int keyColor = level != KeyConflicts.Level.NONE ? level.color() : solved ? GuiUtil.GREEN : row.external().active() ? GuiUtil.WHITE : GuiUtil.GRAY;
                    context.drawCenteredString(font, keyText, (keyLeft + right) / 2, textY, keyColor);
                }
                if (level != KeyConflicts.Level.NONE) {
                    context.fill(keyLeft - 5, top + 1, keyLeft - 2, bottom - 1, level.color());
                } else if (solved) {
                    context.fill(keyLeft - 5, top + 1, keyLeft - 2, bottom - 1, GuiUtil.GREEN);
                }

                int sourceRight = keyLeft - 9;
                int available = sourceRight - left;
                // Grouped by source, the heading already says where the keys come from.
                String sourceText = groupBySource ? "" : GuiUtil.ellipsize(font, row.source().label().getString(), Math.min(MAX_SOURCE_WIDTH, available / 2));
                int sourceWidth = font.width(sourceText);
                context.drawString(font, sourceText, sourceRight - sourceWidth, textY, row.source().color());

                String nameText = GuiUtil.ellipsize(font, row.name().getString(), available - sourceWidth - 8);
                context.drawString(font, nameText, left, textY, solved ? GuiUtil.GREEN : GuiUtil.WHITE);
            }
        }
    }
}
