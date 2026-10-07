package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.profile.ProfileChange;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.profile.ShareCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
    private HeaderAndFooterLayout layout;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();

    private EditBox searchField;
    private ProfileList list;
    private Button applyButton;
    private Button editButton;
    private Button compareButton;
    private Button shareButton;
    private Button deleteButton;
    private String query = "";
    private String selected;
    private String compareWith;

    public KeyBindProfileScreen(Screen parent) {
        super(Component.translatable("keybindprofilesplus.title"));
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

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        searchField = header.addChild(new EditBox(font, Math.max(100, Math.min(220, width - 40)), 20,
                Component.translatable("keybindprofilesplus.search")));
        searchField.setMaxLength(64);
        searchField.setValue(query);
        searchField.setHint(Component.translatable("keybindprofilesplus.search").setStyle(EditBox.SEARCH_HINT_STYLE));
        searchField.setResponder(value -> {
            query = value;
            refreshList();
        });

        list = layout.addToContents(new ProfileList(minecraft));

        // Three rows of the same total width: what is done most on top, then the selected profile, then the other screens.
        int total = Math.max(280, Math.min(380, width - 28));
        LinearLayout footer = layout.addToFooter(LinearLayout.vertical().spacing(4));
        footer.defaultCellSetting().alignHorizontallyCenter();

        int[] top = split(total, 2);
        LinearLayout first = footer.addChild(LinearLayout.horizontal().spacing(BUTTON_GAP));
        applyButton = first.addChild(button("keybindprofilesplus.apply", top[0], this::applySelectedProfile));
        first.addChild(button("keybindprofilesplus.new", top[1],
                () -> minecraft.setScreen(ProfileContentsScreen.forNewProfile(this, service, this::onProfileCreated))));

        int[] middle = split(total, 5);
        LinearLayout second = footer.addChild(LinearLayout.horizontal().spacing(BUTTON_GAP));
        editButton = second.addChild(button("keybindprofilesplus.edit", middle[0], this::editSelectedProfile));
        compareButton = second.addChild(Button.builder(Component.translatable("keybindprofilesplus.compare.open"), button -> openCompare())
                .width(middle[1])
                .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.compare.hint")))
                .build());
        shareButton = second.addChild(Button.builder(Component.translatable("keybindprofilesplus.share.copy"), button -> copyShareCode())
                .width(middle[2])
                .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.share.copy.tooltip")))
                .build());
        second.addChild(button("keybindprofilesplus.import", middle[3],
                () -> minecraft.setScreen(new ImportScreen(this, service, this::onProfileCreated))));
        deleteButton = second.addChild(button("keybindprofilesplus.delete", middle[4], this::deleteSelectedProfile));

        int[] bottom = split(total, 4);
        LinearLayout third = footer.addChild(LinearLayout.horizontal().spacing(BUTTON_GAP));
        third.addChild(button("keybindprofilesplus.overview.open", bottom[0], this::openKeyBinds));
        third.addChild(button("keybindprofilesplus.rules.open", bottom[1], () -> minecraft.setScreen(new ServerRulesScreen(this, service))));
        third.addChild(button("keybindprofilesplus.settings.open", bottom[2], () -> minecraft.setScreen(new SettingsScreen(this, service))));
        third.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(bottom[3]).build());

        layout.visitWidgets(this::addRenderableWidget);
        refreshList();
        repositionElements();
    }

    private Button button(String translationKey, int buttonWidth, Runnable action) {
        return Button.builder(Component.translatable(translationKey), button -> action.run()).width(buttonWidth).build();
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
    protected void repositionElements() {
        if (rebuiltAfterResize()) {
            return;
        }
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
            // Also reached when coming back from another screen: show what changed there.
            refreshList();
        }
    }

    @Override
    public void onClose() {
        if (parent instanceof KeyBindsScreen originalKeybindsScreen) {
            minecraft.setScreen(KeybindsScreenNavigation.createFreshKeybindsScreen(originalKeybindsScreen));
            return;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        Component status = statusMessage.getVisibleText();
        if (status != null) {
            context.drawCenteredString(font, status, width / 2, layout.getHeaderHeight() - 11, GuiUtil.YELLOW);
        }
        if (list.children().isEmpty()) {
            Component empty = Component.translatable(service.profiles().isEmpty() ? "keybindprofilesplus.list.empty" : "keybindprofilesplus.list.no_match");
            context.drawCenteredString(font, empty, width / 2, layout.getHeaderHeight() + layout.getContentHeight() / 2 - 4, GuiUtil.GRAY);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.isSelection() && selected != null && !searchField.isFocused()) {
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
        minecraft.keyboardHandler.setClipboard(code);
        showStatus("keybindprofilesplus.status.share_copied", selected, code.length());
    }

    /** The key binds screen; when this screen was opened from it, simply back to it. */
    private void openKeyBinds() {
        if (parent instanceof KeyOverviewScreen) {
            onClose();
        } else {
            minecraft.setScreen(new KeyOverviewScreen(this));
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
            minecraft.setScreen(new ApplyConfirmScreen(this, name, changes, KeyBindProfilesPlus.settings(), () -> applyNow(name)));
            return;
        }
        applyNow(name);
    }

    private void applyNow(String name) {
        service.applyProfile(name);
        if (parent instanceof KeyBindsScreen keybindsScreen) {
            KeybindsScreenNavigation.refreshControlsList(keybindsScreen);
        }
        refreshList();
        showStatus("keybindprofilesplus.status.profile_applied", name);
    }

    private void editSelectedProfile() {
        if (selected != null) {
            minecraft.setScreen(new ProfileEditScreen(this, service, selected, renamed -> selected = renamed));
        }
    }

    private void openCompare() {
        String left = selected != null ? selected : service.getCurrentProfile();
        minecraft.setScreen(new ProfileCompareScreen(this, service, left, compareWith));
    }

    private void deleteSelectedProfile() {
        if (selected == null) {
            return;
        }
        String name = selected;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                KeyBindProfilesPlus.deleteProfile(name);
                selected = null;
                compareWith = null;
            }
            minecraft.setScreen(this);
            if (confirmed) {
                showStatus("keybindprofilesplus.status.profile_deleted", name);
            }
        }, Component.translatable("keybindprofilesplus.delete.confirm.title", name), Component.translatable("keybindprofilesplus.delete.confirm.message")));
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
        compareButton.setMessage(Component.translatable(compareWith != null ? "keybindprofilesplus.compare.open_two" : "keybindprofilesplus.compare.open"));
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
    private Component describe(String name) {
        Map<String, String> keys = service.profiles().getOrDefault(name, Map.of());
        MutableComponent text = Component.translatable("keybindprofilesplus.list.contents", keys.size(), service.getProfileOptions(name).size());
        List<String> hotkey = service.getProfileHotkey(name);
        if (hotkey != null && !hotkey.isEmpty()) {
            text.append(" - ").append(Component.translatable("keybindprofilesplus.list.hotkey", ProfileHotkeyCapture.formatKeys(hotkey)));
        }
        List<String> rules = service.getProfileAutoSwitchServers(name);
        if (rules != null && !rules.isEmpty()) {
            text.append(" - ").append(Component.translatable("keybindprofilesplus.list.rules", rules.size()));
        }
        return text;
    }

    private final class ProfileList extends ContainerObjectSelectionList<ProfileList.Entry> {
        ProfileList(Minecraft client) {
            super(client, KeyBindProfileScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
        }

        void setProfiles(List<String> names) {
            double scroll = scrollAmount();
            clearEntries();
            for (String name : names) {
                addEntry(new Entry(name));
            }
            setScrollAmount(scroll);
        }

        int[] hitPoint(String profileName) {
            for (Entry entry : children()) {
                if (entry.name.equals(profileName)) {
                    return new int[]{entry.getContentX() + 20, entry.getContentYMiddle()};
                }
            }
            return null;
        }

        @Override
        public int getRowWidth() {
            return Math.max(200, Math.min(380, width - 40));
        }

        private final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            private final String name;
            private final Component contents;

            Entry(String name) {
                this.name = name;
                this.contents = describe(name);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                boolean secondary = click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT || (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && (click.modifiers() & (KeyCombo.CTRL | KeyCombo.SHIFT)) != 0);
                if (secondary) {
                    toggleCompareMark(name);
                    return true;
                }
                if (click.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
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
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                Font font = KeyBindProfileScreen.this.font;
                int left = getContentX();
                int right = getContentRight();
                int top = getContentY();
                int bottom = getContentBottom();
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
                context.drawString(font, title, left + 2, top + 4, GuiUtil.WHITE);
                String details = GuiUtil.ellipsize(font, contents.getString(), right - left - 4);
                context.drawString(font, details, left + 2, top + 17, GuiUtil.GRAY);
            }

            private int badge(GuiGraphics context, Font font, String translationKey, int right, int y, int color) {
                if (translationKey == null) {
                    return right;
                }
                Component text = Component.translatable(translationKey).withStyle(ChatFormatting.ITALIC);
                int x = right - font.width(text);
                context.drawString(font, text, x, y, color);
                return x - 6;
            }
        }
    }
}
