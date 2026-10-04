package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** The mod's own preferences. */
public class SettingsScreen extends ResizingScreen {
    /** Stands for "no default profile" in the picker. */
    private static final String NONE = "";

    private final Screen parent;
    private final ProfileService service;
    private ThreePartsLayoutWidget layout;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();
    private WidgetRowList rows;

    public SettingsScreen(Screen parent, ProfileService service) {
        super(Text.translatable("keybindprofilesplus.settings.title"));
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

    private void rebuild() {
        ModSettings settings = KeyBindProfilesPlus.settings();
        double scroll = rows.getScrollY();
        rows.clear();

        if (!(parent instanceof KeyBindProfileScreen)) {
            // Opened from outside the mod's own screens (the mod list): offer the way in.
            rows.addHeading(Text.translatable("keybindprofilesplus.title"));
            rows.addWidgets(
                    ButtonWidget.builder(Text.translatable("keybindprofilesplus.open"), button -> client.setScreen(new KeyBindProfileScreen(this))).build(),
                    ButtonWidget.builder(Text.translatable("keybindprofilesplus.overview.open"), button -> client.setScreen(new KeyOverviewScreen(this))).build());
        }

        rows.addHeading(Text.translatable("keybindprofilesplus.settings.section.keys"));
        rows.addWidgets(CyclingButtonWidget.onOffBuilder(settings.replaceKeyBinds())
                .tooltip(value -> Tooltip.of(Text.translatable("keybindprofilesplus.settings.replace_key_binds.tooltip")))
                .build(0, 0, 100, 20, Text.translatable("keybindprofilesplus.settings.replace_key_binds"), (button, value) -> settings.setReplaceKeyBinds(value)));

        rows.addHeading(Text.translatable("keybindprofilesplus.settings.section.apply"));
        rows.addWidgets(CyclingButtonWidget.onOffBuilder(settings.confirmApply())
                .tooltip(value -> Tooltip.of(Text.translatable("keybindprofilesplus.confirm.toggle.tooltip")))
                .build(0, 0, 100, 20, Text.translatable("keybindprofilesplus.confirm.toggle"), (button, value) -> settings.setConfirmApply(value)));

        rows.addHeading(Text.translatable("keybindprofilesplus.settings.section.auto_switch"));
        rows.addWidgets(CyclingButtonWidget.onOffBuilder(settings.autoSwitch())
                .tooltip(value -> Tooltip.of(Text.translatable("keybindprofilesplus.server.auto_switch_toggle.tooltip")))
                .build(0, 0, 100, 20, Text.translatable("keybindprofilesplus.server.auto_switch_toggle"), (button, value) -> {
                    settings.setAutoSwitch(value);
                    KeyBindProfilesPlus.autoSwitchController().reset();
                }));

        List<String> choices = new ArrayList<>();
        choices.add(NONE);
        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        choices.addAll(names);
        String current = settings.defaultProfile() != null && names.contains(settings.defaultProfile()) ? settings.defaultProfile() : NONE;
        rows.addWidgets(CyclingButtonWidget.<String>builder(SettingsScreen::profileLabel, current)
                .values(choices)
                .tooltip(value -> Tooltip.of(Text.translatable("keybindprofilesplus.settings.default_profile.tooltip")))
                .build(0, 0, 100, 20, Text.translatable("keybindprofilesplus.settings.default_profile"),
                        (button, value) -> settings.setDefaultProfile(value.equals(NONE) ? null : value)));
        rows.addWidgets(CyclingButtonWidget.onOffBuilder(settings.returnToDefault())
                .tooltip(value -> Tooltip.of(Text.translatable("keybindprofilesplus.settings.return_to_default.tooltip")))
                .build(0, 0, 100, 20, Text.translatable("keybindprofilesplus.settings.return_to_default"), (button, value) -> settings.setReturnToDefault(value)));

        rows.addHeading(Text.translatable("keybindprofilesplus.settings.section.files"));
        rows.addWidgets(ButtonWidget.builder(Text.translatable("keybindprofilesplus.open_folder"), button ->
                statusMessage.show(service.openProfilesFolder() ? "keybindprofilesplus.status.folder_opened" : "keybindprofilesplus.status.folder_open_failed")).build());

        rows.setScrollY(scroll);
    }

    private static Text profileLabel(String name) {
        return name.equals(NONE) ? Text.translatable("keybindprofilesplus.settings.default_profile.none") : Text.literal(name);
    }
}
