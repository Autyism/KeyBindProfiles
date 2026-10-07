package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.configs.ConfigScan;
import io.github.autyism.keybindprofilesplus.configs.ModConfigs;
import io.github.autyism.keybindprofilesplus.configs.ModInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * "Which mod configs go into the export?" - a tick tree like the one for profile contents: one
 * group per mod with its settings files (ticked when the owner is certain) and, in a group of their
 * own, the files it keeps per world or server (not ticked); then the files of mods that are not
 * installed (not ticked), and the files that are never exported, with the reason, greyed out. A mod
 * that saves the settings of add-ons (Meteor Client) names them in its group's title.
 */
public class ConfigExportScreen extends ResizingScreen {
    private static final int HEADER_HEIGHT = 58;

    private final ModConfigsScreen parent;
    private final CompletableFuture<ModConfigs.Context> scan;
    private ModConfigs.Context context;
    private final ConfigTree tree = new ConfigTree();
    private HeaderAndFooterLayout layout;
    private ConfigTree.ListWidget list;
    private EditBox searchField;
    private Button exportButton;
    private String query = "";
    private Component error;

    public ConfigExportScreen(ModConfigsScreen parent) {
        super(Component.translatable("keybindprofilesplus.configs.export.title"));
        this.parent = parent;
        this.scan = ModConfigs.scan();
    }

    @Override
    protected void init() {
        layout = startLayout(HEADER_HEIGHT, 33);
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        searchField = header.addChild(new EditBox(font, Math.max(100, Math.min(260, width - 40)), 20,
                Component.translatable("keybindprofilesplus.configs.search")));
        searchField.setMaxLength(64);
        searchField.setValue(query);
        searchField.setHint(Component.translatable("keybindprofilesplus.configs.search").setStyle(EditBox.SEARCH_HINT_STYLE));
        searchField.setResponder(value -> {
            query = value;
            list.refresh(query, false);
        });
        header.addChild(new SpacerElement(1, font.lineHeight));

        list = layout.addToContents(new ConfigTree.ListWidget(minecraft, width, layout, tree, () -> error = null));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        int buttonWidth = Math.max(70, Math.min(150, (width - 40) / 2));
        exportButton = footer.addChild(Button.builder(Component.translatable("keybindprofilesplus.configs.export.do"), button -> export())
                .width(buttonWidth)
                .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.configs.export.do.tooltip")))
                .build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).width(buttonWidth).build());
        layout.visitWidgets(this::addRenderableWidget);
        list.refresh(query, false);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (rebuiltAfterResize()) {
            return;
        }
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
        }
    }

    @Override
    public void tick() {
        if (context == null && scan.isDone()) {
            context = scan.getNow(null);
            if (context == null) {
                error = Component.translatable("keybindprofilesplus.configs.scan_failed");
            } else {
                buildTree();
                list.refresh(query, false);
            }
        }
        exportButton.active = context != null;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        Component line;
        int color = GuiUtil.GRAY;
        if (error != null) {
            line = error;
            color = GuiUtil.RED;
        } else if (this.context == null) {
            int[] progress = ModConfigs.progress();
            line = Component.translatable("keybindprofilesplus.configs.scanning", progress[0], progress[1]);
        } else {
            line = summary();
        }
        context.drawCenteredString(font, line, width / 2, searchField.getY() + 24, color);
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    public boolean isReady() {
        return context != null;
    }

    public boolean setChecked(String nodeId, boolean checked) {
        boolean known = tree.setChecked(nodeId, checked);
        list.refresh(query, true);
        return known;
    }

    public boolean setExpanded(String nodeId, boolean expanded) {
        if (!(tree.node(nodeId) instanceof ConfigTree.Group group)) {
            return false;
        }
        group.expanded = expanded;
        list.refresh(query, true);
        return true;
    }

    public void setQuery(String text) {
        searchField.setValue(text);
    }

    public boolean isChecked(String nodeId) {
        ConfigTree.Node node = tree.node(nodeId);
        return node != null && tree.state(node) == GuiUtil.CheckState.CHECKED;
    }

    public boolean hasNode(String nodeId) {
        return tree.node(nodeId) != null;
    }

    /** Ticks exactly these files (paths as in the game folder) and nothing else. */
    public void selectOnly(java.util.Collection<String> paths) {
        tree.setChecked(tree.root, false);
        paths.forEach(path -> tree.setChecked(fileId(path), true));
        list.refresh(query, true);
    }

    public List<String> checkedPaths() {
        return tree.checkedLeaves().stream().map(leaf -> ((ConfigScan.Found) leaf.payload).path()).toList();
    }

    public int visibleRowCount() {
        return list.visibleRows();
    }

    public int[] hitPoint(String nodeId, boolean onCheckbox) {
        return list.hitPoint(nodeId, onCheckbox);
    }

    public ModConfigs.Context scanContext() {
        return context;
    }

    /** Writes the ticked files into a new export file and goes back. Returns the file, or null. */
    public Path export() {
        if (context == null) {
            return null;
        }
        List<ConfigScan.Found> selected = tree.checkedLeaves().stream().map(leaf -> (ConfigScan.Found) leaf.payload).toList();
        if (selected.isEmpty()) {
            error = Component.translatable("keybindprofilesplus.configs.export.nothing");
            return null;
        }
        try {
            Path file = ModConfigs.export(context, selected);
            minecraft.setScreen(parent);
            parent.showStatus("keybindprofilesplus.configs.status.exported", file.getFileName().toString(), selected.size());
            return file;
        } catch (Exception e) {
            KeyBindProfilesPlus.LOGGER.error("Exporting mod configs failed", e);
            error = Component.translatable("keybindprofilesplus.configs.status.export_failed");
            return null;
        }
    }

    // ------------------------------------------------------------------ tree

    /** Node id of a file row. */
    public static String fileId(String path) {
        return "file:" + path;
    }

    /** Node id of a mod's group (its outermost mod, for libraries packed inside another mod). */
    public static String modId(String rootModId) {
        return "mod:" + rootModId;
    }

    private void buildTree() {
        tree.clear();
        ConfigScan.Result result = context.result();
        Map<String, List<ConfigScan.Found>> byMod = new LinkedHashMap<>();
        List<ConfigScan.Found> orphans = new ArrayList<>();
        for (ConfigScan.Found found : result.files()) {
            if (found.kind() == ConfigScan.Kind.ORPHAN) {
                orphans.add(found);
            } else {
                byMod.computeIfAbsent(rootOf(found.owners().get(0)), k -> new ArrayList<>()).add(found);
            }
        }
        List<String> mods = new ArrayList<>(byMod.keySet());
        mods.sort(Comparator.comparing(id -> modName(id).toLowerCase(Locale.ROOT)));
        for (String mod : mods) {
            ConfigTree.Group group = tree.group(tree.root, modId(mod), groupTitle(mod, result));
            ConfigTree.Group perWorld = null;
            for (ConfigScan.Found found : byMod.get(mod)) {
                if (found.kind() == ConfigScan.Kind.PER_WORLD) {
                    if (perWorld == null) {
                        perWorld = tree.group(group, modId(mod) + "/world", Component.translatable("keybindprofilesplus.configs.group.world"));
                    }
                    Component right = found.detail().isEmpty() ? size(found.size()) : Component.literal(found.detail() + "  ").append(size(found.size()));
                    tree.leaf(perWorld, fileId(found.path()), Component.literal(found.path()), right, GuiUtil.DARK_GRAY, true, false, found);
                } else {
                    Component right = found.sure() ? size(found.size())
                            : Component.translatable("keybindprofilesplus.configs.probably").append("  ").append(size(found.size()));
                    tree.leaf(group, fileId(found.path()), Component.literal(found.path()), right, found.sure() ? GuiUtil.DARK_GRAY : GuiUtil.YELLOW,
                            true, found.sure(), found);
                }
            }
        }
        if (!orphans.isEmpty()) {
            ConfigTree.Group group = tree.group(tree.root, "orphans", Component.translatable("keybindprofilesplus.configs.group.orphans"));
            for (ConfigScan.Found found : orphans) {
                Component label = found.detail().isEmpty() ? Component.translatable("keybindprofilesplus.configs.group.orphans.unknown")
                        : Component.translatable("keybindprofilesplus.configs.group.orphans.off", found.detail());
                ConfigTree.Group byHint = tree.group(group, "orphans/" + found.detail(), label);
                tree.leaf(byHint, fileId(found.path()), Component.literal(found.path()), size(found.size()), GuiUtil.DARK_GRAY, true, false, found);
            }
        }
        if (!result.skipped().isEmpty()) {
            ConfigTree.Group never = tree.group(tree.root, "never", Component.translatable("keybindprofilesplus.configs.group.never"));
            for (ConfigScan.Skipped skipped : result.skipped()) {
                String reason = skipped.reason().name().toLowerCase(Locale.ROOT);
                ConfigTree.Group group = tree.group(never, "never/" + reason, Component.translatable("keybindprofilesplus.configs.reason." + reason));
                Component right = skipped.files() > 1 ? Component.translatable("keybindprofilesplus.configs.files", skipped.files()) : size(Math.max(0, skipped.size()));
                tree.leaf(group, "never:" + skipped.path(), Component.literal(skipped.path()), right, GuiUtil.DARK_GRAY, false, false, skipped);
            }
        }
    }

    /** The mod's name, and the add-ons whose settings it saves ("Meteor Client (incl. Some Addon)"). */
    private Component groupTitle(String rootModId, ConfigScan.Result result) {
        List<String> addOns = result.addOns().entrySet().stream().filter(entry -> rootOf(entry.getKey()).equals(rootModId))
                .flatMap(entry -> entry.getValue().stream()).map(this::modName).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return addOns.isEmpty() ? Component.literal(modName(rootModId))
                : Component.translatable("keybindprofilesplus.configs.group.with", modName(rootModId), String.join(", ", addOns));
    }

    private String rootOf(String modId) {
        ModInfo mod = context.mods().get(modId);
        return mod == null ? modId : mod.rootId();
    }

    private String modName(String modId) {
        ModInfo mod = context.mods().get(modId);
        return mod == null ? modId : mod.name();
    }

    private Component summary() {
        List<ConfigTree.Leaf> checked = tree.checkedLeaves();
        long bytes = checked.stream().mapToLong(leaf -> ((ConfigScan.Found) leaf.payload).size()).sum();
        long mods = checked.stream().map(leaf -> rootOf(((ConfigScan.Found) leaf.payload).owners().isEmpty() ? ""
                : ((ConfigScan.Found) leaf.payload).owners().get(0))).distinct().count();
        return Component.translatable("keybindprofilesplus.configs.export.summary", checked.size(), size(bytes), mods);
    }

    static Component size(long bytes) {
        if (bytes < 1024) {
            return Component.translatable("keybindprofilesplus.configs.size.bytes", bytes);
        }
        if (bytes < 1024 * 1024) {
            return Component.translatable("keybindprofilesplus.configs.size.kb", (bytes + 512) / 1024);
        }
        return Component.translatable("keybindprofilesplus.configs.size.mb", String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0)));
    }
}
