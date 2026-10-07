package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The mod's own preferences. */
public class SettingsScreen extends ResizingScreen {
    /** Stands for "no default profile" in the picker. */
    private static final String NONE = "";

    private final Screen parent;
    private final ProfileService service;
    private HeaderAndFooterLayout layout;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();
    private WidgetRowList rows;

    public SettingsScreen(Screen parent, ProfileService service) {
        super(Component.translatable("keybindprofilesplus.settings.title"));
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

    private void rebuild() {
        ModSettings settings = KeyBindProfilesPlus.settings();
        double scroll = rows.scrollAmount();
        rows.clear();

        if (!(parent instanceof KeyBindProfileScreen)) {
            // Opened from outside the mod's own screens (the mod list): offer the way in.
            rows.addHeading(Component.translatable("keybindprofilesplus.title"));
            rows.addWidgets(
                    Button.builder(Component.translatable("keybindprofilesplus.open"), button -> minecraft.setScreen(new KeyBindProfileScreen(this))).build(),
                    Button.builder(Component.translatable("keybindprofilesplus.overview.open"), button -> minecraft.setScreen(new KeyOverviewScreen(this))).build());
        }

        rows.addHeading(Component.translatable("keybindprofilesplus.settings.section.keys"));
        rows.addWidgets(CycleButton.onOffBuilder(settings.replaceKeyBinds())
                .withTooltip(value -> Tooltip.create(Component.translatable("keybindprofilesplus.settings.replace_key_binds.tooltip")))
                .create(0, 0, 100, 20, Component.translatable("keybindprofilesplus.settings.replace_key_binds"), (button, value) -> settings.setReplaceKeyBinds(value)));

        rows.addHeading(Component.translatable("keybindprofilesplus.settings.section.apply"));
        rows.addWidgets(CycleButton.onOffBuilder(settings.confirmApply())
                .withTooltip(value -> Tooltip.create(Component.translatable("keybindprofilesplus.confirm.toggle.tooltip")))
                .create(0, 0, 100, 20, Component.translatable("keybindprofilesplus.confirm.toggle"), (button, value) -> settings.setConfirmApply(value)));

        rows.addHeading(Component.translatable("keybindprofilesplus.settings.section.auto_switch"));
        rows.addWidgets(CycleButton.onOffBuilder(settings.autoSwitch())
                .withTooltip(value -> Tooltip.create(Component.translatable("keybindprofilesplus.server.auto_switch_toggle.tooltip")))
                .create(0, 0, 100, 20, Component.translatable("keybindprofilesplus.server.auto_switch_toggle"), (button, value) -> {
                    settings.setAutoSwitch(value);
                    KeyBindProfilesPlus.autoSwitchController().reset();
                }));

        List<String> choices = new ArrayList<>();
        choices.add(NONE);
        List<String> names = new ArrayList<>(service.profiles().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        choices.addAll(names);
        String current = settings.defaultProfile() != null && names.contains(settings.defaultProfile()) ? settings.defaultProfile() : NONE;
        rows.addWidgets(CycleButton.<String>builder(SettingsScreen::profileLabel, current)
                .withValues(choices)
                .withTooltip(value -> Tooltip.create(Component.translatable("keybindprofilesplus.settings.default_profile.tooltip")))
                .create(0, 0, 100, 20, Component.translatable("keybindprofilesplus.settings.default_profile"),
                        (button, value) -> settings.setDefaultProfile(value.equals(NONE) ? null : value)));
        rows.addWidgets(CycleButton.onOffBuilder(settings.returnToDefault())
                .withTooltip(value -> Tooltip.create(Component.translatable("keybindprofilesplus.settings.return_to_default.tooltip")))
                .create(0, 0, 100, 20, Component.translatable("keybindprofilesplus.settings.return_to_default"), (button, value) -> settings.setReturnToDefault(value)));

        rows.addHeading(Component.translatable("keybindprofilesplus.configs.section"));
        rows.addWidgets(Button.builder(Component.translatable("keybindprofilesplus.configs.open"), button -> minecraft.setScreen(new ModConfigsScreen(this)))
                .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.configs.open.tooltip")))
                .build());

        rows.addHeading(Component.translatable("keybindprofilesplus.settings.section.files"));
        rows.addWidgets(Button.builder(Component.translatable("keybindprofilesplus.open_folder"), button ->
                statusMessage.show(service.openProfilesFolder() ? "keybindprofilesplus.status.folder_opened" : "keybindprofilesplus.status.folder_open_failed")).build());

        rows.setScrollAmount(scroll);
    }

    private static Component profileLabel(String name) {
        return name.equals(NONE) ? Component.translatable("keybindprofilesplus.settings.default_profile.none") : Component.literal(name);
    }
}
