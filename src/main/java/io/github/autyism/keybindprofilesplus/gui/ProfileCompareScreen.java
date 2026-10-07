package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.keys.KeySource;
import io.github.autyism.keybindprofilesplus.profile.ProfileComparison;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Two sets of settings side by side: any saved profile, or the game as it is right now, on each
 * side. Rows whose values differ are highlighted; a switch hides everything else.
 */
public class ProfileCompareScreen extends ResizingScreen {
    /** Stands for "the game as it is set up right now" in the side pickers. */
    private static final String CURRENT = "";
    private static final int HEADER_HEIGHT = 58;
    private static final int ROW_HEIGHT = 18;
    private static final int DIFFERENT_BOX = 0xC0705010;

    private final Screen parent;
    private final ProfileService service;
    private HeaderAndFooterLayout layout;

    private String left;
    private String right;
    private boolean onlyDifferences;
    private CycleButton<String> leftButton;
    private CycleButton<String> rightButton;
    private CycleButton<Boolean> onlyDifferencesButton;
    private CompareList list;
    private ProfileComparison.Result result = new ProfileComparison.Result(List.of(), 0, 0);
    private Component summary = Component.empty();

    /**
     * @param left  profile name for the left side, or null for the game's current settings
     * @param right profile name for the right side, or null for the game's current settings
     */
    public ProfileCompareScreen(Screen parent, ProfileService service, String left, String right) {
        super(Component.translatable("keybindprofilesplus.compare.title"));
        this.parent = parent;
        this.service = service;
        this.left = left == null ? CURRENT : left;
        this.right = right == null ? CURRENT : right;
    }

    @Override
    protected void init() {
        layout = startLayout(HEADER_HEIGHT, 33);
        List<String> sides = new ArrayList<>();
        sides.add(CURRENT);
        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        sides.addAll(names);
        if (!sides.contains(left)) {
            left = CURRENT;
        }
        if (!sides.contains(right)) {
            right = CURRENT;
        }

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));

        LinearLayout controls = header.addChild(LinearLayout.horizontal().spacing(4));
        int buttonWidth = Math.max(80, Math.min(160, (width - 28) / 3));
        leftButton = controls.addChild(CycleButton.<String>builder(ProfileCompareScreen::sideLabel, left)
                .withValues(sides)
                .create(0, 0, buttonWidth, 20, Component.translatable("keybindprofilesplus.compare.side_a"), (button, value) -> {
                    left = value;
                    refresh();
                }));
        rightButton = controls.addChild(CycleButton.<String>builder(ProfileCompareScreen::sideLabel, right)
                .withValues(sides)
                .create(0, 0, buttonWidth, 20, Component.translatable("keybindprofilesplus.compare.side_b"), (button, value) -> {
                    right = value;
                    refresh();
                }));
        onlyDifferencesButton = controls.addChild(CycleButton.onOffBuilder(onlyDifferences)
                .create(0, 0, buttonWidth, 20, Component.translatable("keybindprofilesplus.compare.only_differences"), (button, value) -> {
                    onlyDifferences = value;
                    refresh();
                }));
        // Placeholder line: the summary text is drawn here by render().
        header.addChild(new SpacerElement(1, font.lineHeight));

        list = layout.addToContents(new CompareList(minecraft));
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());

        layout.visitWidgets(this::addRenderableWidget);
        refresh();
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (rebuiltAfterResize()) {
            return;
        }
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredString(font, summary, width / 2, leftButton.getY() + 24, GuiUtil.GRAY);
        if (visibleRowCount() == 0) {
            context.drawCenteredString(font, Component.translatable("keybindprofilesplus.compare.no_differences"),
                    width / 2, layout.getHeaderHeight() + layout.getContentHeight() / 2 - 4, GuiUtil.GRAY);
        }
    }

    // ------------------------------------------------------------------ also used by the self-test

    /** Picks what is compared; null means the game's current settings. */
    public void setSides(String leftProfile, String rightProfile) {
        left = leftProfile == null ? CURRENT : leftProfile;
        right = rightProfile == null ? CURRENT : rightProfile;
        leftButton.setValue(left);
        rightButton.setValue(right);
        refresh();
    }

    public void setOnlyDifferences(boolean onlyDifferences) {
        this.onlyDifferences = onlyDifferences;
        onlyDifferencesButton.setValue(onlyDifferences);
        refresh();
    }

    public int differenceCount() {
        return result.different();
    }

    public int visibleRowCount() {
        return (int) list.children().stream().filter(entry -> entry instanceof CompareList.RowEntry).count();
    }

    public String leftSide() {
        return left.equals(CURRENT) ? null : left;
    }

    public String rightSide() {
        return right.equals(CURRENT) ? null : right;
    }

    private void refresh() {
        result = ProfileComparison.compare(service, minecraft.options, leftSide(), rightSide());
        list.setRows(result.rows());
        summary = Component.translatable("keybindprofilesplus.compare.summary", result.different(), result.rows().size(), result.oneSided());
    }

    private static Component sideLabel(String side) {
        return side.equals(CURRENT) ? Component.translatable("keybindprofilesplus.compare.current") : Component.literal(side);
    }

    private final class CompareList extends ContainerObjectSelectionList<CompareList.Entry> {
        CompareList(Minecraft client) {
            super(client, ProfileCompareScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
        }

        void setRows(List<ProfileComparison.Row> rows) {
            clearEntries();
            List<ProfileComparison.Row> visible = rows.stream()
                    .filter(row -> !onlyDifferences || row.state() == ProfileComparison.State.DIFFERENT)
                    .toList();
            if (!visible.isEmpty()) {
                addEntry(new ColumnsEntry());
            }

            String group = null;
            for (ProfileComparison.Row row : visible) {
                String rowGroup = row.group().getString();
                if (!rowGroup.equals(group)) {
                    group = rowGroup;
                    String current = group;
                    long differing = rows.stream()
                            .filter(other -> other.group().getString().equals(current) && other.state() == ProfileComparison.State.DIFFERENT)
                            .count();
                    addEntry(new GroupEntry(row.group(), (int) differing));
                }
                addEntry(new RowEntry(row));
            }
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return Math.max(240, Math.min(520, width - 40));
        }

        private int boxWidth() {
            return Math.max(60, Math.min(120, getRowWidth() / 4));
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

        /** The two side names above the value columns. */
        private final class ColumnsEntry extends Entry {
            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                Font font = ProfileCompareScreen.this.font;
                int box = boxWidth();
                int right = getContentRight();
                int textY = getContentYMiddle() - font.lineHeight / 2;
                String leftName = GuiUtil.ellipsize(font, sideLabel(left).getString(), box - 2);
                String rightName = GuiUtil.ellipsize(font, sideLabel(ProfileCompareScreen.this.right).getString(), box - 2);
                context.drawCenteredString(font, leftName, right - box - 4 - box / 2, textY, GuiUtil.GRAY);
                context.drawCenteredString(font, rightName, right - box / 2, textY, GuiUtil.GRAY);
            }
        }

        private final class GroupEntry extends Entry {
            private final Component label;

            GroupEntry(Component group, int differing) {
                this.label = differing == 0 ? group : Component.empty().append(group)
                        .append(Component.translatable("keybindprofilesplus.compare.group_differs", differing).withStyle(ChatFormatting.YELLOW));
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                context.drawString(font, label, getContentX(), getContentBottom() - font.lineHeight - 1, GuiUtil.WHITE);
            }
        }

        private final class RowEntry extends Entry {
            private final ProfileComparison.Row row;

            /** Which mod the key binding comes from; null for vanilla ones and for game settings. */
            private final KeySource source;

            RowEntry(ProfileComparison.Row row) {
                this.row = row;
                KeyMapping binding = row.keyBinding() ? KeyMapping.get(row.id()) : null;
                KeySource resolved = binding == null ? null : KeyConflicts.sources(minecraft.options).resolve(binding);
                if (resolved == null && row.keyBinding() && ExternalKeys.isExternalId(row.id())) {
                    ExternalBinding external = ExternalKeys.find(row.id());
                    resolved = external == null ? null : KeySource.external(external.sourceId(), external.group().getString());
                }
                this.source = resolved == null || resolved.isVanilla() ? null : resolved;
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                Font font = ProfileCompareScreen.this.font;
                int left = getContentX();
                int right = getContentRight();
                int top = getContentY();
                int bottom = getContentBottom();
                int textY = getContentYMiddle() - font.lineHeight / 2;
                if (hovered) {
                    context.fill(left - 2, top - 1, right + 2, bottom + 1, GuiUtil.ROW_HOVER);
                }

                boolean different = row.state() == ProfileComparison.State.DIFFERENT;
                int box = boxWidth();
                int rightBox = right - box;
                int leftBox = rightBox - 4 - box;
                drawValue(context, font, row.left(), leftBox, box, top, bottom, textY, different);
                drawValue(context, font, row.right(), rightBox, box, top, bottom, textY, different);

                int nameColor = different ? GuiUtil.YELLOW : row.state() == ProfileComparison.State.ONE_SIDED ? GuiUtil.GRAY : GuiUtil.WHITE;
                int nameRight = leftBox - 8;
                if (source != null) {
                    String sourceText = GuiUtil.ellipsize(font, source.label().getString(), (nameRight - left) / 3);
                    nameRight -= font.width(sourceText);
                    context.drawString(font, sourceText, nameRight, textY, source.color());
                    nameRight -= 6;
                }
                String name = GuiUtil.ellipsize(font, row.name().getString(), nameRight - left - 8);
                context.drawString(font, name, left + 8, textY, nameColor);
            }

            private void drawValue(GuiGraphics context, Font font, Component value, int x, int width, int top, int bottom, int textY, boolean different) {
                context.fill(x, top, x + width, bottom, different ? DIFFERENT_BOX : GuiUtil.VALUE_BOX);
                if (value == null) {
                    context.drawCenteredString(font, "-", x + width / 2, textY, GuiUtil.DARK_GRAY);
                } else {
                    String text = GuiUtil.ellipsize(font, value.getString(), width - 6);
                    context.drawCenteredString(font, text, x + width / 2, textY, different ? GuiUtil.YELLOW : GuiUtil.WHITE);
                }
            }
        }
    }
}
