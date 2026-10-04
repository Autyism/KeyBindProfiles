package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.profile.ProfileChange;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Shown before a profile is applied from the profile screen: lists every key binding and game
 * setting that is about to change, with a "don't ask again" box.
 */
public class ApplyConfirmScreen extends ResizingScreen {
    private static final int ROW_HEIGHT = 18;
    private static final String ARROW = " -> ";

    private final Screen parent;
    private final List<ProfileChange> changes;
    private final ModSettings settings;
    private final Runnable onConfirm;
    private ThreePartsLayoutWidget layout;

    private ChangeList list;
    private CheckboxWidget dontAskAgain;
    private boolean dontAsk;

    public ApplyConfirmScreen(Screen parent, String profileName, List<ProfileChange> changes, ModSettings settings, Runnable onConfirm) {
        super(Text.translatable("keybindprofilesplus.confirm.title", profileName));
        this.parent = parent;
        this.changes = changes;
        this.settings = settings;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        layout = startLayout(42, 62);
        DirectionalLayoutWidget header = layout.addHeader(DirectionalLayoutWidget.vertical().spacing(4));
        header.getMainPositioner().alignHorizontalCenter();
        header.add(new TextWidget(title, textRenderer));
        header.add(new TextWidget(Text.translatable("keybindprofilesplus.confirm.subtitle", changes.size()).formatted(Formatting.GRAY), textRenderer));

        list = layout.addBody(new ChangeList(client));

        DirectionalLayoutWidget footer = layout.addFooter(DirectionalLayoutWidget.vertical().spacing(4));
        footer.getMainPositioner().alignHorizontalCenter();
        dontAskAgain = footer.add(CheckboxWidget.builder(Text.translatable("keybindprofilesplus.confirm.dont_ask"), textRenderer)
                .checked(dontAsk)
                .callback((checkbox, checked) -> dontAsk = checked)
                .build());
        DirectionalLayoutWidget buttons = footer.add(DirectionalLayoutWidget.horizontal().spacing(8));
        buttons.add(ButtonWidget.builder(Text.translatable("keybindprofilesplus.confirm.apply"), button -> confirm(dontAskAgain.isChecked())).width(150).build());
        buttons.add(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close()).width(150).build());

        layout.forEachChild(this::addDrawableChild);
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

    /** Applies the profile and returns to the previous screen. */
    public void confirm(boolean dontAskAgain) {
        if (dontAskAgain) {
            settings.setConfirmApply(false);
        }
        client.setScreen(parent);
        onConfirm.run();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private final class ChangeList extends ElementListWidget<ChangeList.Entry> {
        ChangeList(MinecraftClient client) {
            super(client, ApplyConfirmScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
            addGroup(ProfileChange.Kind.KEY_BINDING, "keybindprofilesplus.confirm.group.keys");
            addGroup(ProfileChange.Kind.EXTERNAL, "keybindprofilesplus.confirm.group.external");
            addGroup(ProfileChange.Kind.OPTION, "keybindprofilesplus.confirm.group.settings");
        }

        private void addGroup(ProfileChange.Kind kind, String titleKey) {
            List<ProfileChange> group = changes.stream().filter(change -> change.kind() == kind).toList();
            if (group.isEmpty()) {
                return;
            }
            addEntry(new HeaderEntry(Text.translatable(titleKey, group.size())));
            for (ProfileChange change : group) {
                addEntry(new ChangeEntry(change));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(220, Math.min(460, width - 40));
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

        private final class HeaderEntry extends Entry {
            private final Text label;

            HeaderEntry(Text label) {
                this.label = label;
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                context.drawCenteredTextWithShadow(textRenderer, label, ChangeList.this.width / 2, getContentBottomEnd() - textRenderer.fontHeight - 1, GuiUtil.WHITE);
            }
        }

        private final class ChangeEntry extends Entry {
            private final ProfileChange change;

            ChangeEntry(ProfileChange change) {
                this.change = change;
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

                int valueBudget = (right - left) / 4;
                String to = GuiUtil.ellipsize(font, change.to().getString(), valueBudget);
                String from = GuiUtil.ellipsize(font, change.from().getString(), valueBudget);
                int x = right - font.getWidth(to);
                context.drawTextWithShadow(font, to, x, textY, GuiUtil.GREEN);
                x -= font.getWidth(ARROW);
                context.drawTextWithShadow(font, ARROW, x, textY, GuiUtil.DARK_GRAY);
                x -= font.getWidth(from);
                context.drawTextWithShadow(font, from, x, textY, GuiUtil.GRAY);

                String name = GuiUtil.ellipsize(font, change.name().getString(), x - left - 8);
                context.drawTextWithShadow(font, name, left, textY, GuiUtil.WHITE);
            }
        }
    }
}
