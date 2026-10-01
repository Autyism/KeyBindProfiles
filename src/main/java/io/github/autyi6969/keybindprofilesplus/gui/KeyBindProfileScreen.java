package io.github.autyi6969.keybindprofilesplus.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileChange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class KeyBindProfileScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget profileNameField;
    private TextFieldWidget searchField;
    private TextFieldWidget serverInputField;
    private final ProfileListWidget profileListWidget = new ProfileListWidget();
    private final ServerListWidget serverListWidget = new ServerListWidget();
    private ButtonWidget createButton;
    private ButtonWidget applyButton;
    private ButtonWidget renameButton;
    private ButtonWidget deleteButton;
    private ButtonWidget contentsButton;
    private ButtonWidget openFolderButton;
    private ButtonWidget addServerButton;
    private String selectedProfile = null;
    private int scrollOffset = 0;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();
    private final ProfileHotkeyCapture hotkeyCapture = new ProfileHotkeyCapture();

    public KeyBindProfileScreen(Screen parent) {
        super(Text.translatable("keybindprofilesplus.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KeyBindProfilesPlus.reloadProfilesFromDirectory();

        KeyBindProfileScreenLayout layout = layout();
        int leftX = layout.leftPanelX();
        int rightX = layout.rightPanelX();
        int fieldY = KeyBindProfileScreenLayout.CONTENT_TOP;

        profileNameField = new TextFieldWidget(textRenderer, leftX, fieldY, KeyBindProfileScreenLayout.FIELD_WIDTH, KeyBindProfileScreenLayout.BUTTON_HEIGHT, Text.translatable("keybindprofilesplus.profile_name"));
        profileNameField.setMaxLength(32);
        addDrawableChild(profileNameField);

        searchField = new TextFieldWidget(textRenderer, leftX, fieldY + KeyBindProfileScreenLayout.LABELED_FIELD_SPACING, KeyBindProfileScreenLayout.LEFT_PANEL_WIDTH, KeyBindProfileScreenLayout.BUTTON_HEIGHT, Text.translatable("keybindprofilesplus.search"));
        searchField.setMaxLength(64);
        searchField.setChangedListener(value -> {
            scrollOffset = 0;
            refreshProfileList();
        });
        addDrawableChild(searchField);

        Text openFolderLabel = Text.translatable("keybindprofilesplus.open_folder");
        int openFolderWidth = textRenderer.getWidth(openFolderLabel) + 12;
        openFolderButton = ButtonWidget.builder(openFolderLabel, button -> {
            if (KeyBindProfilesPlus.openProfilesFolder()) {
                showStatus("keybindprofilesplus.status.folder_opened");
            } else {
                showStatus("keybindprofilesplus.status.folder_open_failed");
            }
        }).dimensions(width - 10 - openFolderWidth, 6, openFolderWidth, 20).build();
        addDrawableChild(openFolderButton);

        Text overviewLabel = Text.translatable("keybindprofilesplus.overview.open");
        int overviewWidth = textRenderer.getWidth(overviewLabel) + 12;
        addDrawableChild(ButtonWidget.builder(overviewLabel, button -> client.setScreen(new KeyOverviewScreen(this)))
                .dimensions(openFolderButton.getX() - 4 - overviewWidth, 6, overviewWidth, 20)
                .build());

        createButton = ButtonWidget.builder(Text.translatable("keybindprofilesplus.create"), button -> createProfile())
                .dimensions(leftX + KeyBindProfileScreenLayout.FIELD_WIDTH + 8, fieldY, 130, KeyBindProfileScreenLayout.BUTTON_HEIGHT)
                .build();
        addDrawableChild(createButton);

        applyButton = ButtonWidget.builder(Text.translatable("keybindprofilesplus.apply"), button -> applySelectedProfile())
                .dimensions(rightX, KeyBindProfileScreenLayout.CONTENT_TOP, KeyBindProfileScreenLayout.RIGHT_PANEL_WIDTH, KeyBindProfileScreenLayout.BUTTON_HEIGHT)
                .build();
        addDrawableChild(applyButton);

        renameButton = ButtonWidget.builder(Text.translatable("keybindprofilesplus.rename"), button -> renameSelectedProfile())
                .dimensions(rightX, KeyBindProfileScreenLayout.CONTENT_TOP + KeyBindProfileScreenLayout.BUTTON_SPACING, KeyBindProfileScreenLayout.RIGHT_PANEL_WIDTH, KeyBindProfileScreenLayout.BUTTON_HEIGHT)
                .build();
        addDrawableChild(renameButton);

        deleteButton = ButtonWidget.builder(Text.translatable("keybindprofilesplus.delete"), button -> deleteSelectedProfile())
                .dimensions(rightX, KeyBindProfileScreenLayout.CONTENT_TOP + KeyBindProfileScreenLayout.BUTTON_SPACING * 2, KeyBindProfileScreenLayout.RIGHT_PANEL_WIDTH, KeyBindProfileScreenLayout.BUTTON_HEIGHT)
                .build();
        addDrawableChild(deleteButton);

        int halfWidth = (KeyBindProfileScreenLayout.RIGHT_PANEL_WIDTH - 4) / 2;
        contentsButton = ButtonWidget.builder(Text.translatable("keybindprofilesplus.contents.open"), button -> editSelectedProfileContents())
                .dimensions(rightX, KeyBindProfileScreenLayout.CONTENT_TOP + KeyBindProfileScreenLayout.BUTTON_SPACING * 3, halfWidth, KeyBindProfileScreenLayout.BUTTON_HEIGHT)
                .build();
        addDrawableChild(contentsButton);

        int toggleWidth = 170;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(KeyBindProfilesPlus.settings().confirmApply())
                .build(width - 10 - toggleWidth, layout.doneButtonY(), toggleWidth, KeyBindProfileScreenLayout.BUTTON_HEIGHT,
                        Text.translatable("keybindprofilesplus.confirm.toggle"),
                        (button, value) -> KeyBindProfilesPlus.settings().setConfirmApply(value)));

        serverInputField = new TextFieldWidget(textRenderer, rightX, layout.serverInputY(), 196, KeyBindProfileScreenLayout.BUTTON_HEIGHT, Text.translatable("keybindprofilesplus.server_address"));
        serverInputField.setMaxLength(128);
        addDrawableChild(serverInputField);

        addServerButton = ButtonWidget.builder(Text.translatable("keybindprofilesplus.add_server"), button -> {
            addServerToSelectedProfile();
        }).dimensions(rightX + 204, layout.serverInputY(), 96, KeyBindProfileScreenLayout.BUTTON_HEIGHT).build();
        addDrawableChild(addServerButton);

        ButtonWidget doneButton = ButtonWidget.builder(Text.translatable("gui.done"), button -> returnToParent())
                .dimensions(layout.doneButtonX(), layout.doneButtonY(), 200, KeyBindProfileScreenLayout.BUTTON_HEIGHT)
                .build();
        addDrawableChild(doneButton);

        if (selectedProfile != null && KeyBindProfilesPlus.PROFILES.containsKey(selectedProfile)) {
            profileNameField.setText(selectedProfile);
        } else {
            selectedProfile = null;
        }

        refreshProfileList();
        refreshServerList();
        updateActionButtons();
    }

    private void createProfile() {
        String name = profileNameField.getText().trim();
        if (name.isEmpty()) {
            showStatus("keybindprofilesplus.status.profile_name_required");
            return;
        }

        if (KeyBindProfilesPlus.PROFILES.containsKey(name)) {
            showStatus("keybindprofilesplus.status.profile_exists", name);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return;
        }

        KeyBindProfilesPlus.saveProfile(name, client.options.allKeys);
        KeyBindProfilesPlus.reloadProfilesFromDirectory();
        selectProfile(name);
        searchField.setText("");
        scrollOffset = 0;
        refreshProfileList();
        refreshServerList();
        showStatus("keybindprofilesplus.status.profile_created", name);
    }

    private void applySelectedProfile() {
        if (selectedProfile == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }

        String name = selectedProfile;
        List<ProfileChange> changes = KeyBindProfilesPlus.profileService().previewApply(name);
        if (!changes.isEmpty() && KeyBindProfilesPlus.settings().confirmApply()) {
            client.setScreen(new ApplyConfirmScreen(this, name, changes, KeyBindProfilesPlus.settings(), () -> applyNow(name)));
            return;
        }
        applyNow(name);
    }

    private void applyNow(String name) {
        KeyBindProfilesPlus.applyProfile(name);
        refreshParentKeybindsScreen();
        showStatus("keybindprofilesplus.status.profile_applied", name);
    }

    private void editSelectedProfileContents() {
        if (selectedProfile == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }
        client.setScreen(new ProfileContentsScreen(this, KeyBindProfilesPlus.profileService(), selectedProfile,
                name -> showStatus("keybindprofilesplus.status.contents_saved", name)));
    }

    private void renameSelectedProfile() {
        if (selectedProfile == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }

        String newName = profileNameField.getText().trim();
        if (newName.isEmpty()) {
            showStatus("keybindprofilesplus.status.profile_name_required");
            return;
        }

        if (newName.equals(selectedProfile)) {
            showStatus("keybindprofilesplus.status.rename_same_name");
            return;
        }

        if (KeyBindProfilesPlus.PROFILES.containsKey(newName)) {
            showStatus("keybindprofilesplus.status.profile_exists", newName);
            return;
        }

        renameProfile(selectedProfile, newName);
    }

    private void renameProfile(String oldName, String newName) {
        boolean wasCurrent = Objects.equals(KeyBindProfilesPlus.getCurrentProfile(), oldName);
        if (!KeyBindProfilesPlus.renameProfile(oldName, newName)) {
            return;
        }

        selectProfile(newName);
        refreshProfileList();
        refreshServerList();
        showStatus("keybindprofilesplus.status.profile_renamed", newName);

        if (wasCurrent) {
            this.init(this.width, this.height);
        }
    }

    private void deleteSelectedProfile() {
        if (selectedProfile == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }

        String deletedProfile = selectedProfile;
        KeyBindProfilesPlus.deleteProfile(deletedProfile);
        selectedProfile = null;
        profileNameField.setText("");
        serverInputField.setText("");
        refreshProfileList();
        refreshServerList();
        showStatus("keybindprofilesplus.status.profile_deleted", deletedProfile);

        if (Objects.equals(KeyBindProfilesPlus.getCurrentProfile(), deletedProfile)) {
            this.init(this.width, this.height);
        }
    }

    private void selectProfile(String profileName) {
        selectedProfile = profileName;
        profileNameField.setText(profileName);
        serverInputField.setText("");
    }

    private void refreshParentKeybindsScreen() {
        if (!(parent instanceof KeybindsScreen keybindsScreen)) {
            return;
        }

        KeybindsScreenNavigation.refreshControlsList(keybindsScreen);
        this.init(this.width, this.height);
    }

    private void returnToParent() {
        if (client == null) {
            return;
        }

        if (parent instanceof KeybindsScreen originalKeybindsScreen) {
            client.setScreen(KeybindsScreenNavigation.createFreshKeybindsScreen(originalKeybindsScreen));
            return;
        }

        client.setScreen(parent);
    }

    private KeyBindProfileScreenLayout layout() {
        return new KeyBindProfileScreenLayout(width, height);
    }

    void addButton(ButtonWidget button) {
        addDrawableChild(button);
    }

    void removeButton(ButtonWidget button) {
        remove(button);
    }

    private void addServerToSelectedProfile() {
        if (selectedProfile == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }

        String server = serverInputField.getText().trim();
        if (server.isEmpty()) {
            showStatus("keybindprofilesplus.status.server_required");
            return;
        }

        List<String> servers = new ArrayList<>();
        List<String> existingServers = KeyBindProfilesPlus.getProfileAutoSwitchServers(selectedProfile);
        if (existingServers != null) {
            servers.addAll(existingServers);
        }

        String normalizedServer = normalizeServer(server);
        for (String existingServer : servers) {
            if (normalizeServer(existingServer).equals(normalizedServer)) {
                showStatus("keybindprofilesplus.status.server_exists", server);
                return;
            }
        }

        servers.add(server);
        KeyBindProfilesPlus.setProfileAutoSwitchServers(selectedProfile, servers);
        serverInputField.setText("");
        refreshServerList();
        showStatus("keybindprofilesplus.status.server_added", server);
    }

    private String normalizeServer(String server) {
        return server.trim().toLowerCase(Locale.ROOT);
    }

    private void removeServerFromSelectedProfile(String server) {
        if (selectedProfile == null) {
            showStatus("keybindprofilesplus.status.select_profile");
            return;
        }

        List<String> servers = new ArrayList<>();
        List<String> existingServers = KeyBindProfilesPlus.getProfileAutoSwitchServers(selectedProfile);
        if (existingServers != null) {
            servers.addAll(existingServers);
        }
        servers.remove(server);
        KeyBindProfilesPlus.setProfileAutoSwitchServers(selectedProfile, servers);
        refreshServerList();
        showStatus("keybindprofilesplus.status.server_removed", server);
    }

    private void refreshServerList() {
        serverListWidget.refresh(new ServerListWidget.RefreshRequest(
                this,
                layout(),
                serverInputField,
                selectedProfile,
                height,
                this::removeServerFromSelectedProfile,
                status -> showStatus(status.translationKey(), status.args()),
                this::updateActionButtons
        ));
    }

    private void updateActionButtons() {
        if (applyButton == null || renameButton == null || deleteButton == null || serverInputField == null || addServerButton == null) {
            return;
        }
        boolean hasSelectedProfile = selectedProfile != null;
        applyButton.active = hasSelectedProfile;
        renameButton.active = hasSelectedProfile;
        deleteButton.active = hasSelectedProfile;
        if (contentsButton != null) {
            contentsButton.active = hasSelectedProfile;
        }
        serverInputField.active = hasSelectedProfile;
        addServerButton.active = hasSelectedProfile;
    }

    private void showStatus(String translationKey, Object... args) {
        statusMessage.show(translationKey, args);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == InputUtil.GLFW_KEY_ENTER && serverInputField != null && serverInputField.isFocused()) {
            addServerToSelectedProfile();
            return true;
        }
        if (keyCode == InputUtil.GLFW_KEY_ENTER && profileNameField != null && profileNameField.isFocused()) {
            createProfile();
            return true;
        }

        if (hotkeyCapture.handleKeyPressed(input)) {
            refreshProfileList();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (hotkeyCapture.handleMouseClicked(click.button())) {
            refreshProfileList();
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    public void refreshProfileList() {
        scrollOffset = profileListWidget.refresh(new ProfileListWidget.RefreshRequest(
                this,
                layout(),
                searchField,
                selectedProfile,
                scrollOffset,
                hotkeyCapture,
                this::selectProfile,
                this::refreshProfileList,
                this::refreshServerList,
                this::updateActionButtons
        ));
        updateActionButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        KeyBindProfileScreenLayout layout = layout();
        int listX = layout.leftPanelX();
        int listWidth = KeyBindProfileScreenLayout.PROFILE_BUTTON_WIDTH + KeyBindProfileScreenLayout.HOTKEY_BUTTON_WIDTH + 8;

        if (mouseX >= listX &&
                mouseX <= listX + listWidth &&
                mouseY >= layout.listTop() &&
                mouseY <= layout.listBottom()) {

            int listHeight = layout.listHeight();
            int totalHeight = profileListWidget.getVisibleProfileCount(searchField) * KeyBindProfileScreenLayout.BUTTON_SPACING;

            if (totalHeight > listHeight) {
                int maxOffset = Math.max(0, totalHeight - listHeight);
                scrollOffset = (int) Math.max(0, Math.min(scrollOffset - (int)(vertical * KeyBindProfileScreenLayout.BUTTON_SPACING), maxOffset));
                refreshProfileList();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66000000);

        KeyBindProfileScreenLayout layout = layout();
        int listX = layout.leftPanelX();
        int listRight = listX + KeyBindProfileScreenLayout.PROFILE_BUTTON_WIDTH + KeyBindProfileScreenLayout.HOTKEY_BUTTON_WIDTH + 8;
        context.fill(listX - 5, layout.listTop() - 5, listRight + 5, layout.listBottom() + 5, 0x40000000);

        super.render(context, mouseX, mouseY, delta);

        String currentProfileName = KeyBindProfilesPlus.getCurrentProfile();
        Text fullProfileText;
        if (currentProfileName != null) {
            fullProfileText = Text.translatable("keybindprofilesplus.applied_profile", currentProfileName);
        } else {
            fullProfileText = Text.translatable("keybindprofilesplus.applied_profile", Text.translatable("options.off"));
        }
        context.drawText(textRenderer, fullProfileText, 10, 10, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.translatable("keybindprofilesplus.profile_name"), profileNameField.getX(), profileNameField.getY() - 11, 0xFFA0A0A0, false);
        context.drawText(textRenderer, Text.translatable("keybindprofilesplus.search"), searchField.getX(), searchField.getY() - 11, 0xFFA0A0A0, false);
        context.drawText(textRenderer, Text.translatable("keybindprofilesplus.server_address"), serverInputField.getX(), serverInputField.getY() - 11, 0xFFA0A0A0, false);
        context.drawText(textRenderer, Text.translatable("keybindprofilesplus.auto_switch_servers"), layout.rightPanelX(), layout.serverListTop() - 11, 0xFFA0A0A0, false);

        if (selectedProfile != null) {
            List<String> servers = KeyBindProfilesPlus.getProfileAutoSwitchServers(selectedProfile);
            if (servers == null || servers.isEmpty()) {
                context.drawText(textRenderer, Text.translatable("keybindprofilesplus.no_servers"), layout.rightPanelX(), layout.serverListTop() + 5, 0xFF777777, false);
            }
        }

        Text statusText = statusMessage.getVisibleText();
        if (statusText != null) {
            int statusX = (width - textRenderer.getWidth(statusText)) / 2;
            context.drawTextWithShadow(textRenderer, statusText, statusX, 24, 0xFFFFFF55);
        }

        if (hotkeyCapture.isCapturing()) {
            Text hint = Text.translatable("keybindprofilesplus.hotkey_hint");
            int hintX = (width - textRenderer.getWidth(hint)) / 2;
            context.drawText(textRenderer, hint, hintX, layout.listBottom() - 12, 0xFFFFFF55, true);
        }
    }
}
