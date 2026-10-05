package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.configs.ConfigImport;
import io.github.autyism.keybindprofilesplus.configs.ConfigImportApplier;
import io.github.autyism.keybindprofilesplus.configs.ModConfigs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
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
    private ThreePartsLayoutWidget layout;
    private WidgetRowList rows;
    private final ScreenStatusMessage statusMessage = new ScreenStatusMessage();

    public ModConfigsScreen(Screen parent) {
        super(Text.translatable("keybindprofilesplus.configs.title"));
        this.parent = parent;
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

    void showStatus(String key, Object... args) {
        statusMessage.show(key, args);
    }

    /** Opens the export tree (also used by the self-test). */
    public ConfigExportScreen openExport() {
        ConfigExportScreen screen = new ConfigExportScreen(this);
        client.setScreen(screen);
        return screen;
    }

    /** Opens the import preview of a file in the exports folder (also used by the self-test). */
    public ConfigImportScreen openImport(Path archive) {
        ConfigImportScreen screen = new ConfigImportScreen(this, archive);
        client.setScreen(screen);
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
        double scroll = rows.getScrollY();
        rows.clear();

        rows.addHeading(Text.translatable("keybindprofilesplus.configs.section.export"));
        addWrapped(Text.translatable("keybindprofilesplus.configs.export.hint"), GuiUtil.GRAY);
        rows.addWidgets(ButtonWidget.builder(Text.translatable("keybindprofilesplus.configs.export.open"), button -> openExport()).build());

        rows.addHeading(Text.translatable("keybindprofilesplus.configs.section.import"));
        String pending = ModConfigs.pendingSource();
        if (pending != null) {
            int count = ModConfigs.pendingCount();
            addWrapped(Text.translatable("keybindprofilesplus.configs.import.pending", pending, count), GuiUtil.YELLOW);
            rows.addWidgets(
                    ButtonWidget.builder(Text.translatable("keybindprofilesplus.configs.import.quit"), button -> client.scheduleStop())
                            .tooltip(Tooltip.of(Text.translatable("keybindprofilesplus.configs.import.quit.tooltip"))).build(),
                    ButtonWidget.builder(Text.translatable("keybindprofilesplus.configs.import.cancel_pending"), button -> cancelPending()).build());
        }
        ConfigImport.LastResult last = ModConfigs.lastResult();
        if (last != null) {
            addWrapped(Text.translatable("keybindprofilesplus.configs.import.last", when(last.time()), last.written(), last.removed()), GuiUtil.GRAY);
            if (last.backup() != null && Files.isRegularFile(ModConfigs.exportsDir().resolve(last.backup()))) {
                addWrapped(Text.translatable("keybindprofilesplus.configs.import.last_backup"), GuiUtil.GRAY);
            }
            if (!last.errors().isEmpty()) {
                addWrapped(Text.translatable("keybindprofilesplus.configs.import.last_errors", last.errors().size()), GuiUtil.RED);
            }
        }
        List<Path> archives = ModConfigs.archives();
        if (archives.isEmpty()) {
            addWrapped(Text.translatable("keybindprofilesplus.configs.import.none"), GuiUtil.GRAY);
        } else {
            addWrapped(Text.translatable("keybindprofilesplus.configs.import.hint"), GuiUtil.GRAY);
            for (Path archive : archives.subList(0, Math.min(MAX_LISTED, archives.size()))) {
                rows.addWidgets(ButtonWidget.builder(archiveLabel(archive), button -> openImport(archive)).build());
            }
        }

        rows.addHeading(Text.translatable("keybindprofilesplus.settings.section.files"));
        rows.addWidgets(ButtonWidget.builder(Text.translatable("keybindprofilesplus.configs.open_folder"), button -> openFolder()).build());
        rows.setScrollY(scroll);
    }

    /** A text over as many lines as it needs at the current width. */
    private void addWrapped(Text text, int color) {
        // A little narrower than the row: its content area leaves room for padding.
        for (StringVisitable line : textRenderer.getTextHandler().wrapLines(text, rows.getRowWidth() - 12, Style.EMPTY)) {
            Text shown = Text.literal(line.getString());
            rows.addText(() -> shown, color);
        }
    }

    private static Text archiveLabel(Path archive) {
        String name = archive.getFileName().toString();
        String date;
        try {
            date = WHEN.format(LocalDateTime.ofInstant(Files.getLastModifiedTime(archive).toInstant(), ZoneId.systemDefault()));
        } catch (IOException e) {
            date = "";
        }
        return name.startsWith(ConfigImportApplier.BACKUP_PREFIX)
                ? Text.translatable("keybindprofilesplus.configs.import.backup_label", date)
                : Text.translatable("keybindprofilesplus.configs.import.archive", name, date);
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
            Util.getOperatingSystem().open(ModConfigs.exportsDir().toFile());
            statusMessage.show("keybindprofilesplus.configs.status.folder_opened");
        } catch (IOException | RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.error("Could not open the exports folder", e);
            statusMessage.show("keybindprofilesplus.configs.status.folder_failed");
        }
    }
}
