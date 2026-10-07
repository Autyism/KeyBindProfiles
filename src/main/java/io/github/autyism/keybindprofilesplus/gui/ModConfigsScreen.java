package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.configs.ConfigImport;
import io.github.autyism.keybindprofilesplus.configs.ConfigImportApplier;
import io.github.autyism.keybindprofilesplus.configs.ModConfigs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exporting and importing the settings files of all mods (separate from profiles and share codes,
 * which hold key bindings and game options). Export opens a tick tree of everything that was found;
 * import lists the export files in the exports folder - anything another player sends goes there.
 */
public class ModConfigsScreen extends ResizingScreen {
    private static final int MAX_LISTED = 12;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Screen parent;
    private HeaderAndFooterLayout layout;
    private WidgetRowList rows;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();

    public ModConfigsScreen(Screen parent) {
        super(Component.translatable("keybindprofilesplus.configs.title"));
        this.parent = parent;
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

    void showStatus(String key, Object... args) {
        statusMessage.show(key, args);
    }

    /** Opens the export tree (also used by the self-test). */
    public ConfigExportScreen openExport() {
        ConfigExportScreen screen = new ConfigExportScreen(this);
        minecraft.setScreen(screen);
        return screen;
    }

    /** Opens the import preview of a file in the exports folder (also used by the self-test). */
    public ConfigImportScreen openImport(Path archive) {
        ConfigImportScreen screen = new ConfigImportScreen(this, archive);
        minecraft.setScreen(screen);
        return screen;
    }

    /** Reads the exports folder and the import state again (also used by the self-test). */
    public void refresh() {
        rebuild();
    }

    public void cancelPending() {
        statusMessage.show(ModConfigs.cancelPending() ? "keybindprofilesplus.configs.status.cancelled" : "keybindprofilesplus.configs.status.nothing_pending");
        rebuild();
    }

    private void rebuild() {
        double scroll = rows.scrollAmount();
        rows.clear();

        rows.addHeading(Component.translatable("keybindprofilesplus.configs.section.export"));
        addWrapped(Component.translatable("keybindprofilesplus.configs.export.hint"), GuiUtil.GRAY);
        rows.addWidgets(Button.builder(Component.translatable("keybindprofilesplus.configs.export.open"), button -> openExport()).build());

        rows.addHeading(Component.translatable("keybindprofilesplus.configs.section.import"));
        String pending = ModConfigs.pendingSource();
        if (pending != null) {
            int count = ModConfigs.pendingCount();
            addWrapped(Component.translatable("keybindprofilesplus.configs.import.pending", pending, count), GuiUtil.YELLOW);
            rows.addWidgets(
                    Button.builder(Component.translatable("keybindprofilesplus.configs.import.quit"), button -> minecraft.stop())
                            .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.configs.import.quit.tooltip"))).build(),
                    Button.builder(Component.translatable("keybindprofilesplus.configs.import.cancel_pending"), button -> cancelPending()).build());
        }
        ConfigImport.LastResult last = ModConfigs.lastResult();
        if (last != null) {
            addWrapped(Component.translatable("keybindprofilesplus.configs.import.last", when(last.time()), last.written(), last.removed()), GuiUtil.GRAY);
            if (last.backup() != null && Files.isRegularFile(ModConfigs.exportsDir().resolve(last.backup()))) {
                addWrapped(Component.translatable("keybindprofilesplus.configs.import.last_backup"), GuiUtil.GRAY);
            }
            if (!last.errors().isEmpty()) {
                addWrapped(Component.translatable("keybindprofilesplus.configs.import.last_errors", last.errors().size()), GuiUtil.RED);
            }
        }
        List<Path> archives = ModConfigs.archives();
        if (archives.isEmpty()) {
            addWrapped(Component.translatable("keybindprofilesplus.configs.import.none"), GuiUtil.GRAY);
        } else {
            addWrapped(Component.translatable("keybindprofilesplus.configs.import.hint"), GuiUtil.GRAY);
            for (Path archive : archives.subList(0, Math.min(MAX_LISTED, archives.size()))) {
                rows.addWidgets(Button.builder(archiveLabel(archive), button -> openImport(archive)).build());
            }
        }

        rows.addHeading(Component.translatable("keybindprofilesplus.settings.section.files"));
        rows.addWidgets(Button.builder(Component.translatable("keybindprofilesplus.configs.open_folder"), button -> openFolder()).build());
        rows.setScrollAmount(scroll);
    }

    /** A text over as many lines as it needs at the current width. */
    private void addWrapped(Component text, int color) {
        // A little narrower than the row: its content area leaves room for padding.
        for (FormattedText line : font.getSplitter().splitLines(text, rows.getRowWidth() - 12, Style.EMPTY)) {
            Component shown = Component.literal(line.getString());
            rows.addText(() -> shown, color);
        }
    }

    private static Component archiveLabel(Path archive) {
        String name = archive.getFileName().toString();
        String date;
        try {
            date = WHEN.format(LocalDateTime.ofInstant(Files.getLastModifiedTime(archive).toInstant(), ZoneId.systemDefault()));
        } catch (IOException e) {
            date = "";
        }
        return name.startsWith(ConfigImportApplier.BACKUP_PREFIX)
                ? Component.translatable("keybindprofilesplus.configs.import.backup_label", date)
                : Component.translatable("keybindprofilesplus.configs.import.archive", name, date);
    }

    private static String when(String isoTime) {
        try {
            return WHEN.format(LocalDateTime.parse(isoTime));
        } catch (RuntimeException e) {
            return isoTime;
        }
    }

    private void openFolder() {
        try {
            Files.createDirectories(ModConfigs.exportsDir());
            //? if >=26.3 {
            /*com.mojang.blaze3d.Blaze3D.openPath(ModConfigs.exportsDir());
            *///?} else
            Util.getPlatform().openFile(ModConfigs.exportsDir().toFile());
            statusMessage.show("keybindprofilesplus.configs.status.folder_opened");
        } catch (IOException | RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.error("Could not open the exports folder", e);
            statusMessage.show("keybindprofilesplus.configs.status.folder_failed");
        }
    }
}
