package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.profile.ProfileChange;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.profile.ShareCode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The mod's main screen: the list of profiles and everything that can be done with them.
 * One click selects a profile, a double click applies it, Ctrl+click (or a right click) marks a
 * second profile so the two can be compared.
 */
public class KeyBindProfileScreen extends ResizingScreen {
    private static final int HEADER_HEIGHT = 56;
    private static final int FOOTER_HEIGHT = 82;
    private static final int ROW_HEIGHT = 34;
    private static final int BUTTON_GAP = 4;

    private final Screen parent;
    private final ProfileService service = KeyBindProfilesPlus.profileService();
    private ThreePartsLayoutWidget layout;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();

    private TextFieldWidget searchField;
    private ProfileList list;
    private ButtonWidget applyButton;
    private ButtonWidget editButton;
    private ButtonWidget compareButton;
    private ButtonWidget shareButton;
    private ButtonWidget deleteButton;
    private String query = "";
    private String selected;
    private String compareWith;

    public KeyBindProfileScreen(Screen parent) {
        super(Text.translatable("keybindprofilesplus.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = startLayout(HEADER_HEIGHT, FOOTER_HEIGHT);
        service.reloadProfiles();
        if (selected != null && !service.profiles().containsKey(selected)) {
            selected = null;
        }
        if (compareWith != null && !service.profiles().containsKey(compareWith)) {
            compareWith = null;
        }

        DirectionalLayoutWidget header = layout.addHeader(DirectionalLayoutWidget.vertical().spacing(4));
        header.getMainPositioner().alignHorizontalCenter();
        header.add(new TextWidget(title, textRenderer));
        searchField = header.add(new TextFieldWidget(textRenderer, Math.max(100, Math.min(220, width - 40)), 20,
                Text.translatable("keybindprofilesplus.search")));
        searchField.setMaxLength(64);
        searchField.setText(query);
        searchField.setPlaceholder(Text.translatable("keybindprofilesplus.search").setStyle(TextFieldWidget.SEARCH_STYLE));
        searchField.setChangedListener(value -> {
            query = value;
            refreshList();
        });

        list = layout.addBody(new ProfileList(client));

        // Three rows of the same total width: what is done most on top, then the selected profile, then the other screens.
        int total = Math.max(280, Math.min(380, width - 28));
        DirectionalLayoutWidget footer = layout.addFooter(DirectionalLayoutWidget.vertical().spacing(4));
        footer.getMainPositioner().alignHorizontalCenter();

        int[] top = split(total, 2);
        DirectionalLayoutWidget first = footer.add(DirectionalLayoutWidget.horizontal().spacing(BUTTON_GAP));
        applyButton = first.add(button("keybindprofilesplus.apply", top[0], this::applySelectedProfile));
        first.add(button("keybindprofilesplus.new", top[1],
                () -> client.setScreen(ProfileContentsScreen.forNewProfile(this, service, this::onProfileCreated))));

        int[] middle = split(total, 5);
        DirectionalLayoutWidget second = footer.add(DirectionalLayoutWidget.horizontal().spacing(BUTTON_GAP));
        editButton = second.add(button("keybindprofilesplus.edit", middle[0], this::editSelectedProfile));
        compareButton = second.add(ButtonWidget.builder(Text.translatable("keybindprofilesplus.compare.open"), button -> openCompare())
                .width(middle[1])
                .tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.compare.hint")))
                .build());
        shareButton = second.add(ButtonWidget.builder(Text.translatable("keybindprofilesplus.share.copy"), button -> copyShareCode())
                .width(middle[2])
                .tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.share.copy.tooltip")))
                .build());
        second.add(button("keybindprofilesplus.import", middle[3],
                () -> client.setScreen(new ImportScreen(this, service, this::onProfileCreated))));
        deleteButton = second.add(button("keybindprofilesplus.delete", middle[4], this::deleteSelectedProfile));

        int[] bottom = split(total, 4);
        DirectionalLayoutWidget third = footer.add(DirectionalLayoutWidget.horizontal().spacing(BUTTON_GAP));
        third.add(button("keybindprofilesplus.overview.open", bottom[0], this::openKeyBinds));
        third.add(button("keybindprofilesplus.rules.open", bottom[1], () -> client.setScreen(new ServerRulesScreen(this, service))));
        third.add(button("keybindprofilesplus.settings.open", bottom[2], () -> client.setScreen(new SettingsScreen(this, service))));
        third.add(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).width(bottom[3]).build());

        layout.forEachChild(this::addDrawableChild);
        refreshList();
        refreshWidgetPositions();
    }

    private ButtonWidget button(String translationKey, int buttonWidth, Runnable action) {
        return ButtonWidget.builder(Text.translatable(translationKey), button -> action.run()).width(buttonWidth).build();
    }

    /** Widths of {@code count} buttons that fill {@code total} together with the gaps between them. */
    private static int[] split(int total, int count) {
        int[] widths = new int[count];
        int available = total - BUTTON_GAP * (count - 1);
        for (int i = 0; i < count; i++) {
            // The first ones take the pixels left over by the division.
            widths[i] = available / count + (i < available % count ? 1 : 0);
        }
        return widths;
    }

    @Override
    protected void refreshWidgetPositions() {
        if (rebuiltAfterResize()) {
            return;
        }
        layout.refreshPositions();
        if (list != null) {
            list.position(width, layout);
            // Also reached when coming back from another screen: show what changed there.
            refreshList();
        }
    }

    @Override
    public void close() {
        if (parent instanceof KeybindsScreen originalKeybindsScreen) {
            client.setScreen(KeybindsScreenNavigation.createFreshKeybindsScreen(originalKeybindsScreen));
            return;
        }
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        Text status = statusMessage.getVisibleText();
        if (status != null) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, layout.getHeaderHeight() - 11, GuiUtil.YELLOW);
        }
        if (list.children().isEmpty()) {
            Text empty = Text.translatable(service.profiles().isEmpty() ? "keybindprofilesplus.list.empty" : "keybindprofilesplus.list.no_match");
            context.drawCenteredTextWithShadow(textRenderer, empty, width / 2, layout.getHeaderHeight() + layout.getContentHeight() / 2 - 4, GuiUtil.GRAY);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEnterOrSpace() && selected != null && !searchField.isFocused()) {
            applySelectedProfile();
            return true;
        }
        return super.keyPressed(input);
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    /** Selects a profile as a single click would. */
    public void select(String profileName) {
        if (profileName != null && !service.profiles().containsKey(profileName)) {
            return;
        }
        selected = profileName;
        if (Objects.equals(compareWith, selected)) {
            compareWith = null;
        }
        updateButtons();
    }

    /** Marks or unmarks the second profile for a comparison, as Ctrl+click would. */
    public void toggleCompareMark(String profileName) {
        if (selected == null || profileName.equals(selected)) {
            select(profileName);
            return;
        }
        compareWith = profileName.equals(compareWith) ? null : profileName;
        updateButtons();
    }

    public String selectedProfile() {
        return selected;
    }

    public String compareMark() {
        return compareWith;
    }

    public int visibleProfileCount() {
        return list.children().size();
    }

    /** Screen coordinates {x, y} of a profile's row, or null when it is not listed. */
    public int[] hitPoint(String profileName) {
        return list.hitPoint(profileName);
    }

    public void showStatus(String translationKey, Object... args) {
        statusMessage.show(translationKey, args);
    }

    /** The selected profile as a share code; null when nothing is selected. */
    public String shareCode() {
        if (selected == null) {
            return null;
        }
        return ShareCode.encode(new ShareCode.Content(selected,
                service.profiles().getOrDefault(selected, Map.of()), service.getProfileOptions(selected)));
    }

    private void copyShareCode() {
        String code = shareCode();
        if (code == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }
        client.keyboard.setClipboard(code);
        showStatus("keybindprofilesplus.status.share_copied", selected, code.length());
    }

    /** The key binds screen; when this screen was opened from it, simply back to it. */
    private void openKeyBinds() {
        if (parent instanceof KeyOverviewScreen) {
            close();
        } else {
            client.setScreen(new KeyOverviewScreen(this));
        }
    }

    private void onProfileCreated(String name) {
        selected = name;
        compareWith = null;
    }

    private void applySelectedProfile() {
        if (selected == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }

        String name = selected;
        List<ProfileChange> changes = service.previewApply(name);
        if (!changes.isEmpty() && KeyBindProfilesPlus.settings().confirmApply()) {
            client.setScreen(new ApplyConfirmScreen(this, name, changes, KeyBindProfilesPlus.settings(), () -> applyNow(name)));
            return;
        }
        applyNow(name);
    }

    private void applyNow(String name) {
        service.applyProfile(name);
        if (parent instanceof KeybindsScreen keybindsScreen) {
            KeybindsScreenNavigation.refreshControlsList(keybindsScreen);
        }
        refreshList();
        showStatus("keybindprofilesplus.status.profile_applied", name);
    }

    private void editSelectedProfile() {
        if (selected != null) {
            client.setScreen(new ProfileEditScreen(this, service, selected, renamed -> selected = renamed));
        }
    }

    private void openCompare() {
        String left = selected != null ? selected : service.getCurrentProfile();
        client.setScreen(new ProfileCompareScreen(this, service, left, compareWith));
    }

    private void deleteSelectedProfile() {
        if (selected == null) {
            return;
        }
        String name = selected;
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                KeyBindProfilesPlus.deleteProfile(name);
                selected = null;
                compareWith = null;
            }
            client.setScreen(this);
            if (confirmed) {
                showStatus("keybindprofilesplus.status.profile_deleted", name);
            }
        }, Text.translatable("keybindprofilesplus.delete.confirm.title", name), Text.translatable("keybindprofilesplus.delete.confirm.message")));
    }

    private void updateButtons() {
        if (applyButton == null) {
            return;
        }
        boolean hasSelection = selected != null;
        applyButton.active = hasSelection;
        editButton.active = hasSelection;
        shareButton.active = hasSelection;
        deleteButton.active = hasSelection;
        compareButton.setMessage(Text.translatable(compareWith != null ? "keybindprofilesplus.compare.open_two" : "keybindprofilesplus.compare.open"));
    }

    private void refreshList() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        names.removeIf(name -> !needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle));
        list.setProfiles(names);
        updateButtons();
    }

    /** "62 keys, 3 settings - hotkey Num 5 + F6 - 2 server rules". */
    private Text describe(String name) {
        Map<String, String> keys = service.profiles().getOrDefault(name, Map.of());
        MutableText text = Text.translatable("keybindprofilesplus.list.contents", keys.size(), service.getProfileOptions(name).size());
        List<String> hotkey = service.getProfileHotkey(name);
        if (hotkey != null && !hotkey.isEmpty()) {
            text.append(" - ").append(Text.translatable("keybindprofilesplus.list.hotkey", ProfileHotkeyCapture.formatKeys(hotkey)));
        }
        List<String> rules = service.getProfileAutoSwitchServers(name);
        if (rules != null && !rules.isEmpty()) {
            text.append(" - ").append(Text.translatable("keybindprofilesplus.list.rules", rules.size()));
        }
        return text;
    }

    private final class ProfileList extends ElementListWidget<ProfileList.Entry> {
        ProfileList(MinecraftClient client) {
            super(client, KeyBindProfileScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
        }

        void setProfiles(List<String> names) {
            double scroll = getScrollY();
            clearEntries();
            for (String name : names) {
                addEntry(new Entry(name));
            }
            setScrollY(scroll);
        }

        int[] hitPoint(String profileName) {
            for (Entry entry : children()) {
                if (entry.name.equals(profileName)) {
                    return new int[]{entry.getContentX() + 20, entry.getContentMiddleY()};
                }
            }
            return null;
        }

        @Override
        public int getRowWidth() {
            return Math.max(200, Math.min(380, width - 40));
        }

        private final class Entry extends ElementListWidget.Entry<Entry> {
            private final String name;
            private final Text contents;

            Entry(String name) {
                this.name = name;
                this.contents = describe(name);
            }

            @Override
            public List<? extends Element> children() {
                return List.of();
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return List.of();
            }

            @Override
            public boolean mouseClicked(Click click, boolean doubled) {
                boolean secondary = click.button() == 1 || (click.button() == 0 && (click.modifiers() & (KeyCombo.CTRL | KeyCombo.SHIFT)) != 0);
                if (secondary) {
                    toggleCompareMark(name);
                    return true;
                }
                if (click.button() != 0) {
                    return false;
                }
                boolean wasSelected = name.equals(selected);
                select(name);
                if (doubled && wasSelected) {
                    applySelectedProfile();
                }
                return true;
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                TextRenderer font = textRenderer;
                int left = getContentX();
                int right = getContentRightEnd();
                int top = getContentY();
                int bottom = getContentBottomEnd();
                boolean isSelected = name.equals(selected);
                boolean isMarked = name.equals(compareWith);

                // Frames stay inside the row so neighbouring rows never overlap.
                if (isSelected) {
                    context.fill(left - 3, top - 1, right + 3, bottom + 1, 0xFFFFFFFF);
                    context.fill(left - 2, top, right + 2, bottom, 0xFF202020);
                } else if (isMarked) {
                    context.fill(left - 3, top - 1, right + 3, bottom + 1, 0xFF7FD4FF);
                    context.fill(left - 2, top, right + 2, bottom, 0xFF182830);
                } else if (hovered) {
                    context.fill(left - 2, top, right + 2, bottom, GuiUtil.ROW_HOVER);
                }

                // Badges on the right of the first line: applied / default / marked for comparison.
                int badgeRight = right - 2;
                badgeRight = badge(context, font, isMarked ? "keybindprofilesplus.list.badge.compare" : null, badgeRight, top + 4, 0xFF7FD4FF);
                badgeRight = badge(context, font, name.equals(service.getCurrentProfile()) ? "keybindprofilesplus.list.badge.applied" : null, badgeRight, top + 4, GuiUtil.GREEN);
                badgeRight = badge(context, font, name.equals(KeyBindProfilesPlus.settings().defaultProfile()) ? "keybindprofilesplus.list.badge.default" : null, badgeRight, top + 4, GuiUtil.YELLOW);

                String title = GuiUtil.ellipsize(font, name, badgeRight - left - 6);
                context.drawTextWithShadow(font, title, left + 2, top + 4, GuiUtil.WHITE);
                String details = GuiUtil.ellipsize(font, contents.getString(), right - left - 4);
                context.drawTextWithShadow(font, details, left + 2, top + 17, GuiUtil.GRAY);
            }

            private int badge(DrawContext context, TextRenderer font, String translationKey, int right, int y, int color) {
                if (translationKey == null) {
                    return right;
                }
                Text text = Text.translatable(translationKey).formatted(Formatting.ITALIC);
                int x = right - font.getWidth(text);
                context.drawTextWithShadow(font, text, x, y, color);
                return x - 6;
            }
        }
    }
}
