package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.profile.ProfileChange;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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
    private HeaderAndFooterLayout layout;

    private ChangeList list;
    private Checkbox dontAskAgain;
    private boolean dontAsk;

    public ApplyConfirmScreen(Screen parent, String profileName, List<ProfileChange> changes, ModSettings settings, Runnable onConfirm) {
        super(Component.translatable("keybindprofilesplus.confirm.title", profileName));
        this.parent = parent;
        this.changes = changes;
        this.settings = settings;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        layout = startLayout(42, 62);
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        header.addChild(new StringWidget(Component.translatable("keybindprofilesplus.confirm.subtitle", changes.size()).withStyle(ChatFormatting.GRAY), font));

        list = layout.addToContents(new ChangeList(minecraft));

        LinearLayout footer = layout.addToFooter(LinearLayout.vertical().spacing(4));
        footer.defaultCellSetting().alignHorizontallyCenter();
        dontAskAgain = footer.addChild(Checkbox.builder(Component.translatable("keybindprofilesplus.confirm.dont_ask"), font)
                .selected(dontAsk)
                .onValueChange((checkbox, checked) -> dontAsk = checked)
                .build());
        LinearLayout buttons = footer.addChild(LinearLayout.horizontal().spacing(8));
        buttons.addChild(Button.builder(Component.translatable("keybindprofilesplus.confirm.apply"), button -> confirm(dontAskAgain.selected())).width(150).build());
        buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).width(150).build());

        layout.visitWidgets(this::addRenderableWidget);
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

    /** Applies the profile and returns to the previous screen. */
    public void confirm(boolean dontAskAgain) {
        if (dontAskAgain) {
            settings.setConfirmApply(false);
        }
        minecraft.setScreen(parent);
        onConfirm.run();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private final class ChangeList extends ContainerObjectSelectionList<ChangeList.Entry> {
        ChangeList(Minecraft client) {
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
            addEntry(new HeaderEntry(Component.translatable(titleKey, group.size())));
            for (ProfileChange change : group) {
                addEntry(new ChangeEntry(change));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(220, Math.min(460, width - 40));
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

        private final class HeaderEntry extends Entry {
            private final Component label;

            HeaderEntry(Component label) {
                this.label = label;
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                context.drawCenteredString(font, label, ChangeList.this.width / 2, getContentBottom() - font.lineHeight - 1, GuiUtil.WHITE);
            }
        }

        private final class ChangeEntry extends Entry {
            private final ProfileChange change;

            ChangeEntry(ProfileChange change) {
                this.change = change;
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                Font font = ApplyConfirmScreen.this.font;
                int left = getContentX();
                int right = getContentRight();
                int textY = getContentYMiddle() - font.lineHeight / 2;
                if (hovered) {
                    context.fill(left - 2, getContentY() - 1, right + 2, getContentBottom() + 1, GuiUtil.ROW_HOVER);
                }

                int valueBudget = (right - left) / 4;
                String to = GuiUtil.ellipsize(font, change.to().getString(), valueBudget);
                String from = GuiUtil.ellipsize(font, change.from().getString(), valueBudget);
                int x = right - font.width(to);
                context.drawString(font, to, x, textY, GuiUtil.GREEN);
                x -= font.width(ARROW);
                context.drawString(font, ARROW, x, textY, GuiUtil.DARK_GRAY);
                x -= font.width(from);
                context.drawString(font, from, x, textY, GuiUtil.GRAY);

                String name = GuiUtil.ellipsize(font, change.name().getString(), x - left - 8);
                context.drawString(font, name, left, textY, GuiUtil.WHITE);
            }
        }
    }
}
