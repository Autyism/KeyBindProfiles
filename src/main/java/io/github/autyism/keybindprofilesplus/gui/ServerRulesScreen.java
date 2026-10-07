package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.server.ServerProfileMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * All auto-switch rules of all profiles in one place: which place leads to which profile.
 * Rules can be added to any profile and removed here.
 */
public class ServerRulesScreen extends ResizingScreen {
    private final Screen parent;
    private final ProfileService service;
    private HeaderAndFooterLayout layout;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();

    private WidgetRowList rows;
    private EditBox ruleField;
    private String ruleText = "";
    private String targetProfile;

    public ServerRulesScreen(Screen parent, ProfileService service) {
        super(Component.translatable("keybindprofilesplus.rules.title"));
        this.parent = parent;
        this.service = service;
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
        if (input.isConfirmation() && ruleField != null && ruleField.isFocused()) {
            addRule(ruleField.getValue(), targetProfile);
            return true;
        }
        return super.keyPressed(input);
    }

    private void rebuild() {
        double scroll = rows.scrollAmount();
        rows.clear();

        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (targetProfile == null || !names.contains(targetProfile)) {
            targetProfile = names.isEmpty() ? null : names.get(0);
        }

        rows.addText(() -> ProfileEditScreen.whereAmIText(minecraft), 0xFF7FD4FF);
        rows.addHeading(Component.translatable("keybindprofilesplus.rules.section.add"));
        if (names.isEmpty()) {
            rows.addText(() -> Component.translatable("keybindprofilesplus.list.empty"), GuiUtil.DARK_GRAY);
        } else {
            ruleField = new EditBox(font, 100, 20, Component.translatable("keybindprofilesplus.server_address"));
            ruleField.setMaxLength(128);
            ruleField.setValue(ruleText);
            ruleField.setHint(Component.translatable("keybindprofilesplus.server_address").setStyle(EditBox.SEARCH_HINT_STYLE));
            ruleField.setResponder(value -> ruleText = value);
            ruleField.setTooltip(Tooltip.create(Component.translatable("keybindprofilesplus.server.help")));
            rows.addWidgets(new int[]{5, 5, 3}, ruleField,
                    CycleButton.<String>builder(Component::literal, targetProfile)
                            .withValues(names)
                            .displayOnlyValue()
                            .create(0, 0, 100, 20, Component.translatable("keybindprofilesplus.rules.profile"), (button, value) -> targetProfile = value),
                    Button.builder(Component.translatable("keybindprofilesplus.add_server"), button -> addRule(ruleField.getValue(), targetProfile)).build());
        }

        rows.addHeading(Component.translatable("keybindprofilesplus.rules.section.list"));
        int count = 0;
        for (String profile : names) {
            List<String> rules = service.getProfileAutoSwitchServers(profile);
            if (rules == null) {
                continue;
            }
            for (String rule : rules) {
                count++;
                rows.addWidgets(new int[]{10, 3}, ruleButton(rule, profile),
                        Button.builder(Component.translatable("keybindprofilesplus.remove_server"), button -> removeRule(rule, profile)).build());
            }
        }
        if (count == 0) {
            rows.addText(() -> Component.translatable("keybindprofilesplus.rules.none"), GuiUtil.DARK_GRAY);
        }
        rows.addText(() -> Component.translatable("keybindprofilesplus.rules.priority"), GuiUtil.GRAY);

        rows.setScrollAmount(scroll);
    }

    /** "play.example.org  ->  PvP"; clicking it puts the rule back into the field for editing. */
    private Button ruleButton(String rule, String profile) {
        MutableComponent explanation = Component.translatable("keybindprofilesplus.server.rule." + ServerProfileMatcher.ruleKind(rule));
        List<String> alsoUsedBy = ServerProfileMatcher.profilesUsingRule(rule, service.profileAutoSwitchServers(), profile);
        MutableComponent label = Component.translatable("keybindprofilesplus.rules.row", rule, profile);
        if (!alsoUsedBy.isEmpty()) {
            label.withStyle(ChatFormatting.YELLOW);
            explanation.append("\n").append(Component.translatable("keybindprofilesplus.server.rule.shared", String.join(", ", alsoUsedBy)).withStyle(ChatFormatting.YELLOW));
        }
        return Button.builder(label, button -> {
            ruleText = rule;
            targetProfile = profile;
            rebuild();
        }).tooltip(Tooltip.create(explanation)).build();
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    public void addRule(String rule, String profile) {
        String trimmed = rule == null ? "" : rule.trim();
        String problem = ServerProfileMatcher.validate(trimmed);
        if (problem != null || profile == null) {
            statusMessage.show(problem == null ? "keybindprofilesplus.status.select_profile" : problem);
            return;
        }

        List<String> rules = new ArrayList<>();
        List<String> existing = service.getProfileAutoSwitchServers(profile);
        if (existing != null) {
            rules.addAll(existing);
        }
        String normalized = ServerProfileMatcher.normalizeRule(trimmed);
        for (String other : rules) {
            if (ServerProfileMatcher.normalizeRule(other).equals(normalized)) {
                statusMessage.show("keybindprofilesplus.status.server_exists", trimmed);
                return;
            }
        }
        rules.add(trimmed);
        service.setProfileAutoSwitchServers(profile, rules);
        ruleText = "";
        rebuild();

        List<String> alsoUsedBy = ServerProfileMatcher.profilesUsingRule(trimmed, service.profileAutoSwitchServers(), profile);
        if (alsoUsedBy.isEmpty()) {
            statusMessage.show("keybindprofilesplus.status.server_added", trimmed);
        } else {
            statusMessage.show("keybindprofilesplus.status.server_added_shared", trimmed, String.join(", ", alsoUsedBy));
        }
    }

    public void removeRule(String rule, String profile) {
        List<String> rules = new ArrayList<>();
        List<String> existing = service.getProfileAutoSwitchServers(profile);
        if (existing != null) {
            rules.addAll(existing);
        }
        rules.remove(rule);
        service.setProfileAutoSwitchServers(profile, rules);
        rebuild();
        statusMessage.show("keybindprofilesplus.status.server_removed", rule);
    }

    /** How many rules are listed across all profiles. */
    public int ruleCount() {
        int count = 0;
        for (Map.Entry<String, List<String>> entry : service.profileAutoSwitchServers().entrySet()) {
            if (service.profiles().containsKey(entry.getKey()) && entry.getValue() != null) {
                count += entry.getValue().size();
            }
        }
        return count;
    }
}
