package io.github.autyi6969.keybindprofilesplus.gui;

import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombos;
import io.github.autyi6969.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyi6969.keybindprofilesplus.options.OptionCatalog;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.EmptyWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * "What does this profile save?" - a tree with a check box on every branch and leaf.
 * Key bindings (by category, down to the single binding) and the game's other settings (by group,
 * down to the single setting). Ticking something stores its current value in the profile;
 * unticking removes it, so applying the profile leaves that setting alone.
 */
public class ProfileContentsScreen extends Screen {
    private static final int HEADER_HEIGHT = 58;
    private static final int ROW_HEIGHT = 18;
    private static final int INDENT = 12;
    private static final int ARROW_WIDTH = 10;

    private final Screen parent;
    private final ProfileService service;
    private final String profileName;
    private final Consumer<String> onSaved;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, HEADER_HEIGHT, 33);
    private final Group root = new Group("root", Text.empty());
    private final Map<String, Node> nodesById = new HashMap<>();

    private TextFieldWidget searchField;
    private TreeList list;
    private String query = "";
    private Text summary = Text.empty();
    private boolean built;

    /**
     * @param onSaved called with the profile name after Done stored the new contents
     */
    public ProfileContentsScreen(Screen parent, ProfileService service, String profileName, Consumer<String> onSaved) {
        super(Text.translatable("keybindprofilesplus.contents.title", profileName));
        this.parent = parent;
        this.service = service;
        this.profileName = profileName;
        this.onSaved = onSaved;
    }

    @Override
    protected void init() {
        if (!built) {
            buildTree();
            built = true;
        }

        DirectionalLayoutWidget header = layout.addHeader(DirectionalLayoutWidget.vertical().spacing(4));
        header.getMainPositioner().alignHorizontalCenter();
        header.add(new TextWidget(title, textRenderer));
        searchField = header.add(new TextFieldWidget(textRenderer, Math.max(100, Math.min(260, width - 40)), 20,
                Text.translatable("keybindprofilesplus.contents.search")));
        searchField.setMaxLength(64);
        searchField.setText(query);
        searchField.setPlaceholder(Text.translatable("keybindprofilesplus.contents.search").setStyle(TextFieldWidget.SEARCH_STYLE));
        searchField.setChangedListener(value -> {
            query = value;
            refreshRows(false);
        });
        // Placeholder line: the summary text is drawn here by render().
        header.add(new EmptyWidget(1, textRenderer.fontHeight));

        list = layout.addBody(new TreeList(client));

        DirectionalLayoutWidget footer = layout.addFooter(DirectionalLayoutWidget.horizontal().spacing(8));
        int buttonWidth = Math.max(70, Math.min(150, (width - 40) / 3));
        footer.add(ButtonWidget.builder(Text.translatable("keybindprofilesplus.contents.recapture"), button -> recapture())
                .width(buttonWidth)
                .tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.contents.recapture.tooltip")))
                .build());
        footer.add(ButtonWidget.builder(ScreenTexts.DONE, button -> save()).width(buttonWidth).build());
        footer.add(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close()).width(buttonWidth).build());

        layout.forEachChild(this::addDrawableChild);
        refreshRows(false);
        refreshWidgetPositions();
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
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, summary, width / 2, searchField.getY() + 24, GuiUtil.GRAY);
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    /** Stores the ticked items in the profile and goes back. */
    public void save() {
        Map<String, String> keyBindings = new LinkedHashMap<>();
        Map<String, String> options = new LinkedHashMap<>();
        forEachItem(root, item -> {
            String value = item.valueToStore();
            if (item.checked && value != null) {
                (item.keyBinding ? keyBindings : options).put(item.key, value);
            }
        });
        service.setProfileContents(profileName, keyBindings, options);
        client.setScreen(parent);
        onSaved.accept(profileName);
    }

    /** Makes every ticked item take the value the game has right now. */
    public void recapture() {
        forEachItem(root, item -> {
            if (item.checked && item.current != null) {
                item.stored = item.current;
            }
        });
        refreshRows(true);
    }

    /** Ticks or unticks a node (a group applies to everything below it). Returns false for an unknown id. */
    public boolean setChecked(String nodeId, boolean checked) {
        Node node = nodesById.get(nodeId);
        if (node == null) {
            return false;
        }
        node.setChecked(checked);
        refreshRows(true);
        return true;
    }

    public boolean setExpanded(String nodeId, boolean expanded) {
        if (!(nodesById.get(nodeId) instanceof Group group)) {
            return false;
        }
        group.expanded = expanded;
        refreshRows(true);
        return true;
    }

    public void setQuery(String query) {
        searchField.setText(query);
    }

    public int checkedCount(boolean keyBindings) {
        int[] count = new int[1];
        forEachItem(root, item -> {
            if (item.checked && item.keyBinding == keyBindings) {
                count[0]++;
            }
        });
        return count[0];
    }

    public int visibleRowCount() {
        return list.children().size();
    }

    /**
     * Screen coordinates {x, y} of a visible row, either on its check box or on its label.
     * Null when the row is not shown. Lets the self-test click rows the way a mouse would.
     */
    public int[] hitPoint(String nodeId, boolean onCheckbox) {
        return list.hitPoint(nodeId, onCheckbox);
    }

    public boolean isChecked(String nodeId) {
        Node node = nodesById.get(nodeId);
        return node != null && node.checkState() == GuiUtil.CheckState.CHECKED;
    }

    public boolean isExpanded(String nodeId) {
        return nodesById.get(nodeId) instanceof Group group && group.expanded;
    }

    // ------------------------------------------------------------------ tree model

    private void buildTree() {
        Map<String, String> savedKeys = service.profiles().getOrDefault(profileName, Map.of());
        Map<String, String> savedOptions = service.getProfileOptions(profileName);

        Group keys = addGroup(root, "keys", Text.translatable("keybindprofilesplus.contents.keys"));
        keys.expanded = true;
        KeyBinding[] bindings = client.options.allKeys.clone();
        Arrays.sort(bindings);
        Map<String, String> unknownKeys = new LinkedHashMap<>(savedKeys);
        for (KeyBinding binding : bindings) {
            String categoryId = "keys/" + binding.getCategory().id();
            Group category = nodesById.get(categoryId) instanceof Group existing ? existing : addGroup(keys, categoryId, binding.getCategory().getLabel());
            addItem(category, "key:" + binding.getId(), Text.translatable(binding.getId()), true, binding.getId(),
                    savedKeys.get(binding.getId()), KeyCombos.valueOf(binding), ProfileContentsScreen::describeKey);
            unknownKeys.remove(binding.getId());
        }
        if (!unknownKeys.isEmpty()) {
            // Saved for a mod that is not installed right now; kept so nothing is lost silently.
            Group missing = addGroup(keys, "keys/missing", Text.translatable("keybindprofilesplus.contents.keys_missing"));
            unknownKeys.forEach((id, key) -> addItem(missing, "key:" + id, Text.literal(id), true, id, key, null, ProfileContentsScreen::describeKey));
        }

        Group settings = addGroup(root, "options", Text.translatable("keybindprofilesplus.contents.settings"));
        settings.expanded = true;
        Map<String, GameOptionsBridge.Entry> current = GameOptionsBridge.readAll(client.options);
        for (OptionCatalog.Category category : OptionCatalog.Category.values()) {
            Group group = null;
            for (GameOptionsBridge.Entry entry : current.values()) {
                if (!OptionCatalog.isOffered(entry.key()) || OptionCatalog.categoryOf(entry.key()) != category) {
                    continue;
                }
                if (group == null) {
                    group = addGroup(settings, "options/" + category.name().toLowerCase(Locale.ROOT), category.label());
                }
                addItem(group, "opt:" + entry.key(), entry.name(), false, entry.key(),
                        savedOptions.get(entry.key()), entry.rawValue(), entry::describe);
            }
        }
    }

    private Group addGroup(Group parentGroup, String id, Text label) {
        Group group = new Group(id, label);
        attach(parentGroup, group);
        return group;
    }

    private void addItem(Group parentGroup, String id, Text label, boolean keyBinding, String key, String stored, String current,
                         Function<String, Text> formatter) {
        attach(parentGroup, new Item(id, label, keyBinding, key, stored, current, formatter));
    }

    private void attach(Group parentGroup, Node node) {
        node.depth = parentGroup == root ? 0 : parentGroup.depth + 1;
        parentGroup.children.add(node);
        nodesById.put(node.id, node);
    }

    private static Text describeKey(String translationKey) {
        return KeyCombo.describe(translationKey);
    }

    private static void forEachItem(Group group, Consumer<Item> action) {
        for (Node child : group.children) {
            if (child instanceof Item item) {
                action.accept(item);
            } else if (child instanceof Group nested) {
                forEachItem(nested, action);
            }
        }
    }

    private void refreshRows(boolean keepScroll) {
        double scroll = keepScroll ? list.getScrollY() : 0;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Node> rows = new ArrayList<>();
        collectRows(root, needle, rows);
        list.setRows(rows);
        list.setScrollY(scroll);
        summary = Text.translatable("keybindprofilesplus.contents.summary", checkedCount(true), checkedCount(false));
    }

    /** Depth-first list of the rows to show. While searching, only matching leaves and their parents. */
    private boolean collectRows(Group group, String needle, List<Node> rows) {
        boolean any = false;
        for (Node child : group.children) {
            if (child instanceof Item item) {
                if (needle.isEmpty() || item.matches(needle)) {
                    rows.add(item);
                    any = true;
                }
            } else if (child instanceof Group nested) {
                int index = rows.size();
                rows.add(nested);
                List<Node> below = new ArrayList<>();
                boolean hasMatch = collectRows(nested, needle, below);
                if (!needle.isEmpty()) {
                    if (hasMatch) {
                        rows.addAll(below);
                        any = true;
                    } else {
                        rows.remove(index);
                    }
                } else {
                    if (nested.expanded) {
                        rows.addAll(below);
                    }
                    any = true;
                }
            }
        }
        return any;
    }

    private abstract static class Node {
        final String id;
        final Text label;
        int depth;

        Node(String id, Text label) {
            this.id = id;
            this.label = label;
        }

        abstract void setChecked(boolean checked);

        abstract GuiUtil.CheckState checkState();
    }

    private static final class Group extends Node {
        final List<Node> children = new ArrayList<>();
        boolean expanded;

        Group(String id, Text label) {
            super(id, label);
        }

        @Override
        void setChecked(boolean checked) {
            children.forEach(child -> child.setChecked(checked));
        }

        @Override
        GuiUtil.CheckState checkState() {
            int[] counts = counts();
            if (counts[0] == 0) {
                return GuiUtil.CheckState.UNCHECKED;
            }
            return counts[0] == counts[1] ? GuiUtil.CheckState.CHECKED : GuiUtil.CheckState.PARTIAL;
        }

        /** {checked leaves, all leaves} below this group. */
        int[] counts() {
            int[] counts = new int[2];
            forEachItem(this, item -> {
                counts[1]++;
                if (item.checked) {
                    counts[0]++;
                }
            });
            return counts;
        }
    }

    private static final class Item extends Node {
        final boolean keyBinding;
        final String key;
        /** Value currently in the game; null when the game does not have this item (missing mod). */
        final String current;
        final Function<String, Text> formatter;
        /** Value saved in the profile; null when the profile does not save this item (yet). */
        String stored;
        boolean checked;

        Item(String id, Text label, boolean keyBinding, String key, String stored, String current, Function<String, Text> formatter) {
            super(id, label);
            this.keyBinding = keyBinding;
            this.key = key;
            this.stored = stored;
            this.current = current;
            this.formatter = formatter;
            this.checked = stored != null;
        }

        @Override
        void setChecked(boolean checked) {
            this.checked = checked;
        }

        @Override
        GuiUtil.CheckState checkState() {
            return checked ? GuiUtil.CheckState.CHECKED : GuiUtil.CheckState.UNCHECKED;
        }

        String valueToStore() {
            return stored != null ? stored : current;
        }

        boolean matches(String needle) {
            return label.getString().toLowerCase(Locale.ROOT).contains(needle) || key.toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    // ------------------------------------------------------------------ list widget

    private final class TreeList extends ElementListWidget<TreeList.Entry> {
        TreeList(MinecraftClient client) {
            super(client, ProfileContentsScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
        }

        void setRows(List<Node> nodes) {
            clearEntries();
            for (Node node : nodes) {
                addEntry(new Entry(node));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(220, Math.min(460, width - 40));
        }

        int[] hitPoint(String nodeId, boolean onCheckbox) {
            for (Entry entry : children()) {
                if (entry.node.id.equals(nodeId)) {
                    int x = onCheckbox ? entry.checkboxX() + GuiUtil.CHECKBOX_SIZE / 2 : entry.checkboxX() + GuiUtil.CHECKBOX_SIZE + 30;
                    return new int[]{x, entry.getContentMiddleY()};
                }
            }
            return null;
        }

        private final class Entry extends ElementListWidget.Entry<Entry> {
            private final Node node;

            Entry(Node node) {
                this.node = node;
            }

            @Override
            public List<? extends Element> children() {
                return List.of();
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return List.of();
            }

            private int checkboxX() {
                return getContentX() + node.depth * INDENT + ARROW_WIDTH;
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                if (click.button() != 0) {
                    return false;
                }

                int boxLeft = checkboxX();
                boolean onCheckbox = click.x() >= boxLeft - 2 && click.x() <= boxLeft + GuiUtil.CHECKBOX_SIZE + 2;
                if (node instanceof Group group && !onCheckbox) {
                    group.expanded = !group.expanded;
                } else {
                    node.setChecked(node.checkState() != GuiUtil.CheckState.CHECKED);
                }
                refreshRows(true);
                return true;
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                TextRenderer font = textRenderer;
                int left = getContentX();
                int right = getContentRightEnd();
                int textY = getContentMiddleY() - font.fontHeight / 2;
                if (hovered) {
                    context.fill(left - 2, getContentY() - 1, right + 2, getContentBottomEnd() + 1, GuiUtil.ROW_HOVER);
                }

                int boxLeft = checkboxX();
                GuiUtil.drawCheckbox(context, boxLeft, getContentMiddleY() - GuiUtil.CHECKBOX_SIZE / 2, node.checkState(), hovered);
                int labelLeft = boxLeft + GuiUtil.CHECKBOX_SIZE + 5;

                if (node instanceof Group group) {
                    boolean open = group.expanded || !query.isBlank();
                    context.drawTextWithShadow(font, open ? "v" : ">", boxLeft - ARROW_WIDTH + 1, textY, GuiUtil.GRAY);
                    int[] counts = group.counts();
                    String count = counts[0] + "/" + counts[1];
                    int countWidth = font.getWidth(count);
                    context.drawTextWithShadow(font, count, right - countWidth, textY, counts[0] == 0 ? GuiUtil.DARK_GRAY : GuiUtil.GRAY);
                    String label = GuiUtil.ellipsize(font, group.label.getString(), right - countWidth - 8 - labelLeft);
                    context.drawTextWithShadow(font, label, labelLeft, textY, GuiUtil.WHITE);
                } else if (node instanceof Item item) {
                    renderItem(context, font, item, labelLeft, right, textY);
                }
            }

            private void renderItem(DrawContext context, TextRenderer font, Item item, int labelLeft, int right, int textY) {
                int valueBudget = (right - labelLeft) / 2;
                int valueLeft = right;
                if (item.checked) {
                    String saved = item.valueToStore();
                    boolean differs = item.current != null && saved != null && !saved.equals(item.current);
                    if (differs) {
                        String now = GuiUtil.ellipsize(font,
                                Text.translatable("keybindprofilesplus.contents.now", item.formatter.apply(item.current)).getString(), valueBudget / 2);
                        valueLeft -= font.getWidth(now);
                        context.drawTextWithShadow(font, now, valueLeft, textY, GuiUtil.DARK_GRAY);
                        valueLeft -= 4;
                    }
                    String value = saved == null ? "" : GuiUtil.ellipsize(font, item.formatter.apply(saved).getString(), valueBudget / (differs ? 2 : 1));
                    valueLeft -= font.getWidth(value);
                    context.drawTextWithShadow(font, value, valueLeft, textY, differs ? GuiUtil.YELLOW : GuiUtil.GREEN);
                } else if (item.current != null) {
                    String value = GuiUtil.ellipsize(font, item.formatter.apply(item.current).getString(), valueBudget);
                    valueLeft -= font.getWidth(value);
                    context.drawTextWithShadow(font, value, valueLeft, textY, GuiUtil.DARK_GRAY);
                }

                String label = GuiUtil.ellipsize(font, item.label.getString(), valueLeft - 8 - labelLeft);
                context.drawTextWithShadow(font, label, labelLeft, textY, item.checked ? GuiUtil.WHITE : GuiUtil.GRAY);
            }
        }
    }
}
