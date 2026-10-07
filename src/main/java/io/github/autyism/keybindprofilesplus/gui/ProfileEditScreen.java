package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.profile.ProfileNames;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.server.ServerProfileMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Everything about one profile: its name, the hotkey that switches to it, what it saves, the
 * places it is applied automatically, and deleting it.
 */
public class ProfileEditScreen extends ResizingScreen {
    private final Screen parent;
    private final ProfileService service;
    private final Consumer<String> onRenamed;
    private HeaderAndFooterLayout layout;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();
    private final ProfileHotkeyCapture hotkeyCapture;

    private String profileName;
    private WidgetRowList rows;
    private EditBox nameField;
    private EditBox ruleField;
    private Button hotkeyButton;
    private String nameText;
    private String ruleText = "";

    /**
     * @param onRenamed told the new name whenever the profile is renamed
     */
    public ProfileEditScreen(Screen parent, ProfileService service, String profileName, Consumer<String> onRenamed) {
        super(Component.translatable("keybindprofilesplus.edit.title"));
        this.parent = parent;
        this.service = service;
        this.profileName = profileName;
        this.nameText = profileName;
        this.onRenamed = onRenamed;
        this.hotkeyCapture = new ProfileHotkeyCapture(service);
    }

    @Override
    protected void init() {
        layout = startLayout(33, 33);
        layout.addTitleHeader(title, font);
        rows = layout.addToContents(new WidgetRowList(minecraft, width, layout));
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (rebuiltAfterResize()) {
            return;
        }
        layout.arrangeElements();
        if (rows != null) {
            rows.updateSize(width, layout);
            rebuild();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        Component status = statusMessage.getVisibleText();
        if (status != null) {
            context.drawCenteredString(font, status, width / 2, layout.getHeaderHeight() - 11, GuiUtil.YELLOW);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (hotkeyCapture.handleKeyPressed(input)) {
            refreshHotkeyButton();
            return true;
        }
        if (input.isConfirmation() && ruleField != null && ruleField.isFocused()) {
            addRule(ruleField.getValue());
            return true;
        }
        if (input.isConfirmation() && nameField != null && nameField.isFocused()) {
            rename();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (hotkeyCapture.handleMouseClicked(click.button())) {
            refreshHotkeyButton();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !hotkeyCapture.isCapturing();
    }

    // ------------------------------------------------------------------ rows

    private void rebuild() {
        double scroll = rows.scrollAmount();
        rows.clear();
        if (!service.profiles().containsKey(profileName)) {
            rows.setScrollAmount(0);
            return;
        }

        rows.addHeading(Component.translatable("keybindprofilesplus.edit.section.name"));
        nameField = new EditBox(font, 100, 20, Component.translatable("keybindprofilesplus.profile_name"));
        nameField.setMaxLength(ProfileNames.MAX_LENGTH);
        nameField.setValue(nameText);
        nameField.setResponder(value -> nameText = value);
        rows.addWidgets(new int[]{3, 1}, nameField, Button.builder(Component.translatable("keybindprofilesplus.rename"), button -> rename()).build());

        rows.addHeading(Component.translatable("keybindprofilesplus.edit.section.hotkey"));
        hotkeyButton = Button.builder(hotkeyCapture.getButtonText(profileName), button -> {
            hotkeyCapture.toggle(profileName);
            refreshHotkeyButton();
        }).tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.hotkey_hint"))).build();
        rows.addWidgets(new int[]{3, 1}, hotkeyButton, Button.builder(Component.translatable("keybindprofilesplus.hotkey.clear"), button -> {
            hotkeyCapture.cancel();
            service.setProfileHotkey(profileName, null);
            refreshHotkeyButton();
        }).build());
        rows.addText(() -> hotkeyCapture.isCapturing() ? Component.translatable("keybindprofilesplus.hotkey_hint") : Component.translatable("keybindprofilesplus.hotkey.explain"),
                GuiUtil.GRAY);

        rows.addHeading(Component.translatable("keybindprofilesplus.edit.section.contents"));
        rows.addText(() -> Component.translatable("keybindprofilesplus.contents.summary",
                service.profiles().getOrDefault(profileName, Map.of()).size(), service.getProfileOptions(profileName).size()), GuiUtil.GRAY);
        rows.addWidgets(Button.builder(Component.translatable("keybindprofilesplus.contents.open"), button -> minecraft.setScreen(
                new ProfileContentsScreen(this, service, profileName, name -> showStatus("keybindprofilesplus.status.contents_saved", name)))).build());

        rows.addHeading(Component.translatable("keybindprofilesplus.auto_switch_servers"));
        ruleField = new EditBox(font, 100, 20, Component.translatable("keybindprofilesplus.server_address"));
        ruleField.setMaxLength(128);
        ruleField.setValue(ruleText);
        ruleField.setHint(Component.translatable("keybindprofilesplus.server_address").setStyle(EditBox.SEARCH_HINT_STYLE));
        ruleField.setResponder(value -> ruleText = value);
        ruleField.setTooltip(Tooltip.create(Component.translatable("keybindprofilesplus.server.help")));
        rows.addWidgets(new int[]{3, 1}, ruleField,
                Button.builder(Component.translatable("keybindprofilesplus.add_server"), button -> addRule(ruleField.getValue())).build());

        String suggested = suggestedServerAddress();
        Button addCurrent = Button.builder(suggested == null
                        ? Component.translatable("keybindprofilesplus.server.add_current_none")
                        : Component.translatable("keybindprofilesplus.server.add_current", suggested), button -> addRule(suggested))
                .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.server.help"))).build();
        addCurrent.active = suggested != null;
        rows.addWidgets(
                Button.builder(Component.translatable("keybindprofilesplus.server.add_singleplayer"), button -> addRule(ServerProfileMatcher.SINGLEPLAYER))
                        .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.server.help"))).build(),
                addCurrent);

        List<String> rules = service.getProfileAutoSwitchServers(profileName);
        if (rules == null || rules.isEmpty()) {
            rows.addText(() -> Component.translatable("keybindprofilesplus.no_servers"), GuiUtil.DARK_GRAY);
        } else {
            for (String rule : rules) {
                rows.addWidgets(new int[]{3, 1}, ruleButton(rule),
                        Button.builder(Component.translatable("keybindprofilesplus.remove_server"), button -> removeRule(rule)).build());
            }
        }
        rows.addText(this::whereAmIText, 0xFF7FD4FF);

        rows.addHeading(Component.translatable("keybindprofilesplus.edit.section.default"));
        rows.addWidgets(CycleButton.onOffBuilder(profileName.equals(KeyBindProfilesPlus.settings().defaultProfile()))
                .withTooltip(value -> Tooltip.create(Component.translatable("keybindprofilesplus.settings.default_profile.tooltip")))
                .create(0, 0, 100, 20, Component.translatable("keybindprofilesplus.edit.is_default"),
                        (button, value) -> KeyBindProfilesPlus.settings().setDefaultProfile(value ? profileName : null)));

        rows.addHeading(Component.translatable("keybindprofilesplus.edit.section.delete"));
        rows.addWidgets(Button.builder(Component.translatable("keybindprofilesplus.delete").withStyle(ChatFormatting.RED), button -> delete()).build());

        rows.setScrollAmount(scroll);
    }

    private Button ruleButton(String rule) {
        MutableComponent explanation = Component.translatable("keybindprofilesplus.server.rule." + ServerProfileMatcher.ruleKind(rule));
        List<String> alsoUsedBy = ServerProfileMatcher.profilesUsingRule(rule, service.profileAutoSwitchServers(), profileName);
        MutableComponent label = Component.literal(rule);
        if (!alsoUsedBy.isEmpty()) {
            label.withStyle(ChatFormatting.YELLOW);
            explanation.append("\n").append(Component.translatable("keybindprofilesplus.server.rule.shared", String.join(", ", alsoUsedBy)).withStyle(ChatFormatting.YELLOW));
        }
        return Button.builder(label, button -> ruleField.setValue(rule)).tooltip(Tooltip.create(explanation)).build();
    }

    private void refreshHotkeyButton() {
        if (hotkeyButton != null) {
            hotkeyButton.setMessage(hotkeyCapture.getButtonText(profileName));
        }
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    public String profileName() {
        return profileName;
    }

    /** Renames the profile to what is in the name field. */
    public void rename() {
        String newName = nameText.trim();
        if (newName.equals(profileName)) {
            showStatus("keybindprofilesplus.status.rename_same_name");
            return;
        }
        String problem = ProfileNames.validate(newName);
        if (problem != null) {
            showStatus(problem);
            return;
        }
        // Changing only the capitalisation is fine; clashing with a different profile is not.
        if (!newName.equalsIgnoreCase(profileName) && ProfileNames.containsIgnoreCase(service.profiles().keySet(), newName)) {
            showStatus("keybindprofilesplus.status.profile_exists", newName);
            return;
        }
        if (!KeyBindProfilesPlus.renameProfile(profileName, newName)) {
            showStatus("keybindprofilesplus.status.profile_exists", newName);
            return;
        }

        hotkeyCapture.cancel();
        profileName = newName;
        nameText = newName;
        onRenamed.accept(newName);
        rebuild();
        showStatus("keybindprofilesplus.status.profile_renamed", newName);
    }

    /** Adds an auto-switch rule (an address, a wildcard, or singleplayer / lan / realms / *). */
    public void addRule(String rule) {
        String trimmed = rule == null ? "" : rule.trim();
        String problem = ServerProfileMatcher.validate(trimmed);
        if (problem != null) {
            showStatus(problem);
            return;
        }

        List<String> rules = new ArrayList<>();
        List<String> existing = service.getProfileAutoSwitchServers(profileName);
        if (existing != null) {
            rules.addAll(existing);
        }
        String normalized = ServerProfileMatcher.normalizeRule(trimmed);
        for (String other : rules) {
            if (ServerProfileMatcher.normalizeRule(other).equals(normalized)) {
                showStatus("keybindprofilesplus.status.server_exists", trimmed);
                return;
            }
        }

        rules.add(trimmed);
        service.setProfileAutoSwitchServers(profileName, rules);
        ruleText = "";
        rebuild();

        List<String> alsoUsedBy = ServerProfileMatcher.profilesUsingRule(trimmed, service.profileAutoSwitchServers(), profileName);
        if (alsoUsedBy.isEmpty()) {
            showStatus("keybindprofilesplus.status.server_added", trimmed);
        } else {
            showStatus("keybindprofilesplus.status.server_added_shared", trimmed, String.join(", ", alsoUsedBy));
        }
    }

    public void removeRule(String rule) {
        List<String> rules = new ArrayList<>();
        List<String> existing = service.getProfileAutoSwitchServers(profileName);
        if (existing != null) {
            rules.addAll(existing);
        }
        rules.remove(rule);
        service.setProfileAutoSwitchServers(profileName, rules);
        rebuild();
        showStatus("keybindprofilesplus.status.server_removed", rule);
    }

    public void setNameText(String text) {
        nameField.setValue(text);
    }

    private void delete() {
        String name = profileName;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                KeyBindProfilesPlus.deleteProfile(name);
                minecraft.setScreen(parent);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("keybindprofilesplus.delete.confirm.title", name), Component.translatable("keybindprofilesplus.delete.confirm.message")));
    }

    private void showStatus(String translationKey, Object... args) {
        statusMessage.show(translationKey, args);
    }

    /** While in a world: which profile the auto-switch rules pick for this place. Null on the main menu. */
    private Component whereAmIText() {
        return whereAmIText(minecraft);
    }

    static Component whereAmIText(net.minecraft.client.Minecraft client) {
        ServerProfileMatcher.Location location = ServerProfileMatcher.currentLocation(client);
        if (location == null) {
            return null;
        }
        if (!KeyBindProfilesPlus.settings().autoSwitch()) {
            return Component.translatable("keybindprofilesplus.server.here_off");
        }
        ServerProfileMatcher.Match match = KeyBindProfilesPlus.autoSwitchController().match(location);
        return match == null
                ? Component.translatable("keybindprofilesplus.server.here_none", location.describe())
                : Component.translatable("keybindprofilesplus.server.here_match", location.describe(), match.profile());
    }

    /** The server the player is on, or failing that the last one joined; null when there is none. */
    static String suggestedServerAddress(net.minecraft.client.Minecraft client) {
        ServerProfileMatcher.Location location = ServerProfileMatcher.currentLocation(client);
        if (location != null && location.address() != null) {
            return location.address();
        }
        String last = client.options.lastMpIp;
        return last == null || last.isBlank() ? null : last;
    }

    private String suggestedServerAddress() {
        return suggestedServerAddress(minecraft);
    }
}
