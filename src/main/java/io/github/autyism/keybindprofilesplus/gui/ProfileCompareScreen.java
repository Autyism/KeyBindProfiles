package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.keys.KeySource;
import io.github.autyism.keybindprofilesplus.profile.ProfileComparison;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
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
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

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
    private ThreePartsLayoutWidget layout;

    private String left;
    private String right;
    private boolean onlyDifferences;
    private CyclingButtonWidget<String> leftButton;
    private CyclingButtonWidget<String> rightButton;
    private CyclingButtonWidget<Boolean> onlyDifferencesButton;
    private CompareList list;
    private ProfileComparison.Result result = new ProfileComparison.Result(List.of(), 0, 0);
    private Text summary = Text.empty();

    /**
     * @param left  profile name for the left side, or null for the game's current settings
     * @param right profile name for the right side, or null for the game's current settings
     */
    public ProfileCompareScreen(Screen parent, ProfileService service, String left, String right) {
        super(Text.translatable("keybindprofilesplus.compare.title"));
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

        DirectionalLayoutWidget header = layout.addHeader(DirectionalLayoutWidget.vertical().spacing(4));
        header.getMainPositioner().alignHorizontalCenter();
        header.add(new TextWidget(title, textRenderer));

        DirectionalLayoutWidget controls = header.add(DirectionalLayoutWidget.horizontal().spacing(4));
        int buttonWidth = Math.max(80, Math.min(160, (width - 28) / 3));
        leftButton = controls.add(CyclingButtonWidget.<String>builder(ProfileCompareScreen::sideLabel, left)
                .values(sides)
                .build(0, 0, buttonWidth, 20, Text.translatable("keybindprofilesplus.compare.side_a"), (button, value) -> {
                    left = value;
                    refresh();
                }));
        rightButton = controls.add(CyclingButtonWidget.<String>builder(ProfileCompareScreen::sideLabel, right)
                .values(sides)
                .build(0, 0, buttonWidth, 20, Text.translatable("keybindprofilesplus.compare.side_b"), (button, value) -> {
                    right = value;
                    refresh();
                }));
        onlyDifferencesButton = controls.add(CyclingButtonWidget.onOffBuilder(onlyDifferences)
                .build(0, 0, buttonWidth, 20, Text.translatable("keybindprofilesplus.compare.only_differences"), (button, value) -> {
                    onlyDifferences = value;
                    refresh();
                }));
        // Placeholder line: the summary text is drawn here by render().
        header.add(new EmptyWidget(1, textRenderer.fontHeight));

        list = layout.addBody(new CompareList(client));
        layout.addFooter(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).width(200).build());

        layout.forEachChild(this::addDrawableChild);
        refresh();
        refreshWidgetPositions();
    }

    @Override
    protected void refreshWidgetPositions() {
        if (rebuiltAfterResize()) {
            return;
        }
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
        context.drawCenteredTextWithShadow(textRenderer, summary, width / 2, leftButton.getY() + 24, GuiUtil.GRAY);
        if (visibleRowCount() == 0) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("keybindprofilesplus.compare.no_differences"),
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
        result = ProfileComparison.compare(service, client.options, leftSide(), rightSide());
        list.setRows(result.rows());
        summary = Text.translatable("keybindprofilesplus.compare.summary", result.different(), result.rows().size(), result.oneSided());
    }

    private static Text sideLabel(String side) {
        return side.equals(CURRENT) ? Text.translatable("keybindprofilesplus.compare.current") : Text.literal(side);
    }

    private final class CompareList extends ElementListWidget<CompareList.Entry> {
        CompareList(MinecraftClient client) {
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
            setScrollY(0);
        }

        @Override
        public int getRowWidth() {
            return Math.max(240, Math.min(520, width - 40));
        }

        private int boxWidth() {
            return Math.max(60, Math.min(120, getRowWidth() / 4));
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

        /** The two side names above the value columns. */
        private final class ColumnsEntry extends Entry {
            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                TextRenderer font = textRenderer;
                int box = boxWidth();
                int right = getContentRightEnd();
                int textY = getContentMiddleY() - font.fontHeight / 2;
                String leftName = GuiUtil.ellipsize(font, sideLabel(left).getString(), box - 2);
                String rightName = GuiUtil.ellipsize(font, sideLabel(ProfileCompareScreen.this.right).getString(), box - 2);
                context.drawCenteredTextWithShadow(font, leftName, right - box - 4 - box / 2, textY, GuiUtil.GRAY);
                context.drawCenteredTextWithShadow(font, rightName, right - box / 2, textY, GuiUtil.GRAY);
            }
        }

        private final class GroupEntry extends Entry {
            private final Text label;

            GroupEntry(Text group, int differing) {
                this.label = differing == 0 ? group : Text.empty().append(group)
                        .append(Text.translatable("keybindprofilesplus.compare.group_differs", differing).formatted(Formatting.YELLOW));
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                context.drawTextWithShadow(textRenderer, label, getContentX(), getContentBottomEnd() - textRenderer.fontHeight - 1, GuiUtil.WHITE);
            }
        }

        private final class RowEntry extends Entry {
            private final ProfileComparison.Row row;

            /** Which mod the key binding comes from; null for vanilla ones and for game settings. */
            private final KeySource source;

            RowEntry(ProfileComparison.Row row) {
                this.row = row;
                KeyBinding binding = row.keyBinding() ? KeyBinding.byId(row.id()) : null;
                KeySource resolved = binding == null ? null : KeyConflicts.sources(client.options).resolve(binding);
                if (resolved == null && row.keyBinding() && ExternalKeys.isExternalId(row.id())) {
                    ExternalBinding external = ExternalKeys.find(row.id());
                    resolved = external == null ? null : KeySource.external(external.sourceId(), external.group().getString());
                }
                this.source = resolved == null || resolved.isVanilla() ? null : resolved;
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
                    nameRight -= font.getWidth(sourceText);
                    context.drawTextWithShadow(font, sourceText, nameRight, textY, source.color());
                    nameRight -= 6;
                }
                String name = GuiUtil.ellipsize(font, row.name().getString(), nameRight - left - 8);
                context.drawTextWithShadow(font, name, left + 8, textY, nameColor);
            }

            private void drawValue(DrawContext context, TextRenderer font, Text value, int x, int width, int top, int bottom, int textY, boolean different) {
                context.fill(x, top, x + width, bottom, different ? DIFFERENT_BOX : GuiUtil.VALUE_BOX);
                if (value == null) {
                    context.drawCenteredTextWithShadow(font, "-", x + width / 2, textY, GuiUtil.DARK_GRAY);
                } else {
                    String text = GuiUtil.ellipsize(font, value.getString(), width - 6);
                    context.drawCenteredTextWithShadow(font, text, x + width / 2, textY, different ? GuiUtil.YELLOW : GuiUtil.WHITE);
                }
            }
        }
    }
}
