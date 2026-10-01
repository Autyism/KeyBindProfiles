package io.github.autyi6969.keybindprofilesplus.gui;

import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileNames;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.profile.ShareCode;
import io.github.autyi6969.keybindprofilesplus.server.ServerProfileMatcher;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Everything about one profile: its name, the hotkey that switches to it, what it saves, its
 * share code, the places it is applied automatically, and deleting it.
 */
public class ProfileEditScreen extends Screen {
    private final Screen parent;
    private final ProfileService service;
    private final Consumer<String> onRenamed;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, 33, 33);
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();
    private final ProfileHotkeyCapture hotkeyCapture;

    private String profileName;
    private WidgetRowList rows;
    private TextFieldWidget nameField;
    private TextFieldWidget ruleField;
    private ButtonWidget hotkeyButton;
    private String nameText;
    private String ruleText = "";

    /**
     * @param onRenamed told the new name whenever the profile is renamed
     */
    public ProfileEditScreen(Screen parent, ProfileService service, String profileName, Consumer<String> onRenamed) {
        super(Text.translatable("keybindprofilesplus.edit.title"));
        this.parent = parent;
        this.service = service;
        this.profileName = profileName;
        this.nameText = profileName;
        this.onRenamed = onRenamed;
        this.hotkeyCapture = new ProfileHotkeyCapture(service);
    }

    @Override
    protected void init() {
        layout.addHeader(title, textRenderer);
        rows = layout.addBody(new WidgetRowList(client, width, layout));
        layout.addFooter(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).width(200).build());
        layout.forEachChild(this::addDrawableChild);
        refreshWidgetPositions();
    }

    @Override
    protected void refreshWidgetPositions() {
        layout.refreshPositions();
        if (rows != null) {
            rows.position(width, layout);
            rebuild();
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        Text status = statusMessage.getVisibleText();
        if (status != null) {
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, layout.getHeaderHeight() - 11, GuiUtil.YELLOW);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (hotkeyCapture.handleKeyPressed(input)) {
            refreshHotkeyButton();
            return true;
        }
        if (input.isEnter() && ruleField != null && ruleField.isFocused()) {
            addRule(ruleField.getText());
            return true;
        }
        if (input.isEnter() && nameField != null && nameField.isFocused()) {
            rename();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
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
        double scroll = rows.getScrollY();
        rows.clear();
        if (!service.profiles().containsKey(profileName)) {
            rows.setScrollY(0);
            return;
        }

        rows.addHeading(Text.translatable("keybindprofilesplus.edit.section.name"));
        nameField = new TextFieldWidget(textRenderer, 100, 20, Text.translatable("keybindprofilesplus.profile_name"));
        nameField.setMaxLength(ProfileNames.MAX_LENGTH);
        nameField.setText(nameText);
        nameField.setChangedListener(value -> nameText = value);
        rows.addWidgets(new int[]{3, 1}, nameField, ButtonWidget.builder(Text.translatable("keybindprofilesplus.rename"), button -> rename()).build());

        rows.addHeading(Text.translatable("keybindprofilesplus.edit.section.hotkey"));
        hotkeyButton = ButtonWidget.builder(hotkeyCapture.getButtonText(profileName), button -> {
            hotkeyCapture.toggle(profileName);
            refreshHotkeyButton();
        }).tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.hotkey_hint"))).build();
        rows.addWidgets(new int[]{3, 1}, hotkeyButton, ButtonWidget.builder(Text.translatable("keybindprofilesplus.hotkey.clear"), button -> {
            hotkeyCapture.cancel();
            service.setProfileHotkey(profileName, null);
            refreshHotkeyButton();
        }).build());
        rows.addText(() -> hotkeyCapture.isCapturing() ? Text.translatable("keybindprofilesplus.hotkey_hint") : Text.translatable("keybindprofilesplus.hotkey.explain"),
                GuiUtil.GRAY);

        rows.addHeading(Text.translatable("keybindprofilesplus.edit.section.contents"));
        rows.addText(() -> Text.translatable("keybindprofilesplus.contents.summary",
                service.profiles().getOrDefault(profileName, Map.of()).size(), service.getProfileOptions(profileName).size()), GuiUtil.GRAY);
        rows.addWidgets(
                ButtonWidget.builder(Text.translatable("keybindprofilesplus.contents.open"), button -> client.setScreen(
                        new ProfileContentsScreen(this, service, profileName, name -> showStatus("keybindprofilesplus.status.contents_saved", name)))).build(),
                ButtonWidget.builder(Text.translatable("keybindprofilesplus.share.copy"), button -> copyShareCode())
                        .tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.share.copy.tooltip"))).build());

        rows.addHeading(Text.translatable("keybindprofilesplus.auto_switch_servers"));
        ruleField = new TextFieldWidget(textRenderer, 100, 20, Text.translatable("keybindprofilesplus.server_address"));
        ruleField.setMaxLength(128);
        ruleField.setText(ruleText);
        ruleField.setPlaceholder(Text.translatable("keybindprofilesplus.server_address").setStyle(TextFieldWidget.SEARCH_STYLE));
        ruleField.setChangedListener(value -> ruleText = value);
        ruleField.setTooltip(Tooltip.of(Text.translatable("keybindprofilesplus.server.help")));
        rows.addWidgets(new int[]{3, 1}, ruleField,
                ButtonWidget.builder(Text.translatable("keybindprofilesplus.add_server"), button -> addRule(ruleField.getText())).build());

        String suggested = suggestedServerAddress();
        ButtonWidget addCurrent = ButtonWidget.builder(suggested == null
                        ? Text.translatable("keybindprofilesplus.server.add_current_none")
                        : Text.translatable("keybindprofilesplus.server.add_current", suggested), button -> addRule(suggested))
                .tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.server.help"))).build();
        addCurrent.active = suggested != null;
        rows.addWidgets(
                ButtonWidget.builder(Text.translatable("keybindprofilesplus.server.add_singleplayer"), button -> addRule(ServerProfileMatcher.SINGLEPLAYER))
                        .tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.server.help"))).build(),
                addCurrent);

        List<String> rules = service.getProfileAutoSwitchServers(profileName);
        if (rules == null || rules.isEmpty()) {
            rows.addText(() -> Text.translatable("keybindprofilesplus.no_servers"), GuiUtil.DARK_GRAY);
        } else {
            for (String rule : rules) {
                rows.addWidgets(new int[]{3, 1}, ruleButton(rule),
                        ButtonWidget.builder(Text.translatable("keybindprofilesplus.remove_server"), button -> removeRule(rule)).build());
            }
        }
        rows.addText(this::whereAmIText, 0xFF7FD4FF);

        rows.addHeading(Text.translatable("keybindprofilesplus.edit.section.default"));
        rows.addWidgets(CyclingButtonWidget.onOffBuilder(profileName.equals(KeyBindProfilesPlus.settings().defaultProfile()))
                .tooltip(value -> Tooltip.of(Text.translatable("keybindprofilesplus.settings.default_profile.tooltip")))
                .build(0, 0, 100, 20, Text.translatable("keybindprofilesplus.edit.is_default"),
                        (button, value) -> KeyBindProfilesPlus.settings().setDefaultProfile(value ? profileName : null)));

        rows.addHeading(Text.translatable("keybindprofilesplus.edit.section.delete"));
        rows.addWidgets(ButtonWidget.builder(Text.translatable("keybindprofilesplus.delete").formatted(Formatting.RED), button -> delete()).build());

        rows.setScrollY(scroll);
    }

    private ButtonWidget ruleButton(String rule) {
        MutableText explanation = Text.translatable("keybindprofilesplus.server.rule." + ServerProfileMatcher.ruleKind(rule));
        List<String> alsoUsedBy = ServerProfileMatcher.profilesUsingRule(rule, service.profileAutoSwitchServers(), profileName);
        MutableText label = Text.literal(rule);
        if (!alsoUsedBy.isEmpty()) {
            label.formatted(Formatting.YELLOW);
            explanation.append("\n").append(Text.translatable("keybindprofilesplus.server.rule.shared", String.join(", ", alsoUsedBy)).formatted(Formatting.YELLOW));
        }
        return ButtonWidget.builder(label, button -> ruleField.setText(rule)).tooltip(Tooltip.of(explanation)).build();
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

    /** The profile as a share code. */
    public String shareCode() {
        return ShareCode.encode(new ShareCode.Content(profileName,
                service.profiles().getOrDefault(profileName, Map.of()), service.getProfileOptions(profileName)));
    }

    private void copyShareCode() {
        String code = shareCode();
        client.keyboard.setClipboard(code);
        showStatus("keybindprofilesplus.status.share_copied", code.length());
    }

    public void setNameText(String text) {
        nameField.setText(text);
    }

    private void delete() {
        String name = profileName;
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                KeyBindProfilesPlus.deleteProfile(name);
                client.setScreen(parent);
            } else {
                client.setScreen(this);
            }
        }, Text.translatable("keybindprofilesplus.delete.confirm.title", name), Text.translatable("keybindprofilesplus.delete.confirm.message")));
    }

    private void showStatus(String translationKey, Object... args) {
        statusMessage.show(translationKey, args);
    }

    /** While in a world: which profile the auto-switch rules pick for this place. Null on the main menu. */
    private Text whereAmIText() {
        return whereAmIText(client);
    }

    static Text whereAmIText(net.minecraft.client.MinecraftClient client) {
        ServerProfileMatcher.Location location = ServerProfileMatcher.currentLocation(client);
        if (location == null) {
            return null;
        }
        if (!KeyBindProfilesPlus.settings().autoSwitch()) {
            return Text.translatable("keybindprofilesplus.server.here_off");
        }
        ServerProfileMatcher.Match match = KeyBindProfilesPlus.autoSwitchController().match(location);
        return match == null
                ? Text.translatable("keybindprofilesplus.server.here_none", location.describe())
                : Text.translatable("keybindprofilesplus.server.here_match", location.describe(), match.profile());
    }

    /** The server the player is on, or failing that the last one joined; null when there is none. */
    static String suggestedServerAddress(net.minecraft.client.MinecraftClient client) {
        ServerProfileMatcher.Location location = ServerProfileMatcher.currentLocation(client);
        if (location != null && location.address() != null) {
            return location.address();
        }
        String last = client.options.lastServer;
        return last == null || last.isBlank() ? null : last;
    }

    private String suggestedServerAddress() {
        return suggestedServerAddress(client);
    }
}
