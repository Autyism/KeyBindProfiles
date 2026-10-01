package io.github.autyi6969.keybindprofilesplus.gui;

import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.server.ServerProfileMatcher;
import net.minecraft.client.gui.DrawContext;
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

/**
 * All auto-switch rules of all profiles in one place: which place leads to which profile.
 * Rules can be added to any profile and removed here.
 */
public class ServerRulesScreen extends ResizingScreen {
    private final Screen parent;
    private final ProfileService service;
    private ThreePartsLayoutWidget layout;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();

    private WidgetRowList rows;
    private TextFieldWidget ruleField;
    private String ruleText = "";
    private String targetProfile;

    public ServerRulesScreen(Screen parent, ProfileService service) {
        super(Text.translatable("keybindprofilesplus.rules.title"));
        this.parent = parent;
        this.service = service;
    }

    @Override
    protected void init() {
        layout = startLayout(33, 33);
        layout.addHeader(title, textRenderer);
        rows = layout.addBody(new WidgetRowList(client, width, layout));
        layout.addFooter(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).width(200).build());
        layout.forEachChild(this::addDrawableChild);
        refreshWidgetPositions();
    }

    @Override
    protected void refreshWidgetPositions() {
        if (rebuiltAfterResize()) {
            return;
        }
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
        if (input.isEnter() && ruleField != null && ruleField.isFocused()) {
            addRule(ruleField.getText(), targetProfile);
            return true;
        }
        return super.keyPressed(input);
    }

    private void rebuild() {
        double scroll = rows.getScrollY();
        rows.clear();

        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (targetProfile == null || !names.contains(targetProfile)) {
            targetProfile = names.isEmpty() ? null : names.get(0);
        }

        rows.addText(() -> ProfileEditScreen.whereAmIText(client), 0xFF7FD4FF);
        rows.addHeading(Text.translatable("keybindprofilesplus.rules.section.add"));
        if (names.isEmpty()) {
            rows.addText(() -> Text.translatable("keybindprofilesplus.list.empty"), GuiUtil.DARK_GRAY);
        } else {
            ruleField = new TextFieldWidget(textRenderer, 100, 20, Text.translatable("keybindprofilesplus.server_address"));
            ruleField.setMaxLength(128);
            ruleField.setText(ruleText);
            ruleField.setPlaceholder(Text.translatable("keybindprofilesplus.server_address").setStyle(TextFieldWidget.SEARCH_STYLE));
            ruleField.setChangedListener(value -> ruleText = value);
            ruleField.setTooltip(Tooltip.of(Text.translatable("keybindprofilesplus.server.help")));
            rows.addWidgets(new int[]{5, 5, 3}, ruleField,
                    CyclingButtonWidget.<String>builder(Text::literal, targetProfile)
                            .values(names)
                            .omitKeyText()
                            .build(0, 0, 100, 20, Text.translatable("keybindprofilesplus.rules.profile"), (button, value) -> targetProfile = value),
                    ButtonWidget.builder(Text.translatable("keybindprofilesplus.add_server"), button -> addRule(ruleField.getText(), targetProfile)).build());
        }

        rows.addHeading(Text.translatable("keybindprofilesplus.rules.section.list"));
        int count = 0;
        for (String profile : names) {
            List<String> rules = service.getProfileAutoSwitchServers(profile);
            if (rules == null) {
                continue;
            }
            for (String rule : rules) {
                count++;
                rows.addWidgets(new int[]{10, 3}, ruleButton(rule, profile),
                        ButtonWidget.builder(Text.translatable("keybindprofilesplus.remove_server"), button -> removeRule(rule, profile)).build());
            }
        }
        if (count == 0) {
            rows.addText(() -> Text.translatable("keybindprofilesplus.rules.none"), GuiUtil.DARK_GRAY);
        }
        rows.addText(() -> Text.translatable("keybindprofilesplus.rules.priority"), GuiUtil.GRAY);

        rows.setScrollY(scroll);
    }

    /** "play.example.org  ->  PvP"; clicking it puts the rule back into the field for editing. */
    private ButtonWidget ruleButton(String rule, String profile) {
        MutableText explanation = Text.translatable("keybindprofilesplus.server.rule." + ServerProfileMatcher.ruleKind(rule));
        List<String> alsoUsedBy = ServerProfileMatcher.profilesUsingRule(rule, service.profileAutoSwitchServers(), profile);
        MutableText label = Text.translatable("keybindprofilesplus.rules.row", rule, profile);
        if (!alsoUsedBy.isEmpty()) {
            label.formatted(Formatting.YELLOW);
            explanation.append("\n").append(Text.translatable("keybindprofilesplus.server.rule.shared", String.join(", ", alsoUsedBy)).formatted(Formatting.YELLOW));
        }
        return ButtonWidget.builder(label, button -> {
            ruleText = rule;
            targetProfile = profile;
            rebuild();
        }).tooltip(Tooltip.of(explanation)).build();
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
