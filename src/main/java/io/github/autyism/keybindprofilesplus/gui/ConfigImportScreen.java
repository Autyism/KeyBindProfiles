package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.configs.ConfigArchive;
import io.github.autyism.keybindprofilesplus.configs.ConfigImport;
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
 * "What would importing this file do?" - every file of an export (or of a backup made before an
 * import) with what would happen to it here: new, changed, already the same, removed again, for a
 * mod that is not installed here, or not imported at all and why. The ticked part is written the
 * next time the game starts, before any mod reads its settings.
 */
public class ConfigImportScreen extends ResizingScreen {
    private static final int HEADER_HEIGHT = 58;

    private final ModConfigsScreen parent;
    private final Path archive;
    private final CompletableFuture<ConfigImport.Plan> planning;
    private ConfigImport.Plan plan;
    private volatile Map<String, ModInfo> mods = Map.of();
    private final ConfigTree tree = new ConfigTree();
    private HeaderAndFooterLayout layout;
    private ConfigTree.ListWidget list;
    private EditBox searchField;
    private Button importButton;
    private String query = "";
    private Component error;

    public ConfigImportScreen(ModConfigsScreen parent, Path archive) {
        super(Component.translatable("keybindprofilesplus.configs.import.title", archive.getFileName().toString()));
        this.parent = parent;
        this.archive = archive;
        this.planning = ModConfigs.scan().thenApply(context -> {
            mods = context.mods();
            return ModConfigs.planImport(archive, context);
        });
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
        importButton = footer.addChild(Button.builder(Component.translatable("keybindprofilesplus.configs.import.do"), button -> confirm())
                .width(buttonWidth)
                .tooltip(Tooltip.create(Component.translatable("keybindprofilesplus.configs.import.do.tooltip")))
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
        if (plan == null && planning.isDone()) {
            plan = planning.exceptionally(e -> null).getNow(null);
            if (plan == null || plan.archive().problem() != null) {
                error = Component.translatable("keybindprofilesplus.configs.import.unreadable");
            } else {
                buildTree();
                list.refresh(query, false);
            }
        }
        importButton.active = plan != null && plan.archive().problem() == null;
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
        } else if (plan == null) {
            int[] progress = ModConfigs.progress();
            line = Component.translatable("keybindprofilesplus.configs.scanning", progress[0], progress[1]);
        } else if (plan.otherMinecraft()) {
            line = Component.translatable("keybindprofilesplus.configs.import.other_minecraft", plan.archive().minecraft(), ModConfigs.minecraftVersion());
            color = GuiUtil.YELLOW;
        } else {
            line = Component.translatable("keybindprofilesplus.configs.import.summary", tree.checkedLeaves().size(), plan.items().size());
        }
        context.drawCenteredString(font, line, width / 2, searchField.getY() + 24, color);
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    public boolean isReady() {
        return plan != null;
    }

    public ConfigImport.Plan plan() {
        return plan;
    }

    public boolean setChecked(String nodeId, boolean checked) {
        boolean known = tree.setChecked(nodeId, checked);
        list.refresh(query, true);
        return known;
    }

    public boolean isChecked(String nodeId) {
        ConfigTree.Node node = tree.node(nodeId);
        return node != null && tree.state(node) == GuiUtil.CheckState.CHECKED;
    }

    public boolean setExpanded(String nodeId, boolean expanded) {
        if (!(tree.node(nodeId) instanceof ConfigTree.Group group)) {
            return false;
        }
        group.expanded = expanded;
        list.refresh(query, true);
        return true;
    }

    public List<String> checkedPaths() {
        return tree.checkedLeaves().stream().map(leaf -> ((ConfigImport.Item) leaf.payload).path()).toList();
    }

    /** Stages the ticked files for the next start and goes back. False when nothing was ticked or writing failed. */
    public boolean confirm() {
        if (plan == null) {
            return false;
        }
        List<ConfigImport.Item> chosen = tree.checkedLeaves().stream().map(leaf -> (ConfigImport.Item) leaf.payload).toList();
        if (chosen.isEmpty()) {
            error = Component.translatable("keybindprofilesplus.configs.import.nothing");
            return false;
        }
        try {
            ModConfigs.stage(plan, chosen);
        } catch (Exception e) {
            KeyBindProfilesPlus.LOGGER.error("Staging the mod config import failed", e);
            error = Component.translatable("keybindprofilesplus.configs.status.stage_failed");
            return false;
        }
        minecraft.setScreen(parent);
        parent.showStatus("keybindprofilesplus.configs.status.staged", chosen.size());
        return true;
    }

    // ------------------------------------------------------------------ tree

    private void buildTree() {
        tree.clear();
        Map<String, List<ConfigImport.Item>> byMod = new LinkedHashMap<>();
        List<ConfigImport.Item> missing = new ArrayList<>();
        List<ConfigImport.Item> rejected = new ArrayList<>();
        for (ConfigImport.Item item : plan.items()) {
            switch (item.status()) {
                case MOD_MISSING -> missing.add(item);
                case REJECTED -> rejected.add(item);
                default -> byMod.computeIfAbsent(item.owners().isEmpty() ? "" : rootOf(item.owners().get(0)), k -> new ArrayList<>()).add(item);
            }
        }
        List<String> order = new ArrayList<>(byMod.keySet());
        order.sort(Comparator.comparing(id -> modName(id).toLowerCase(Locale.ROOT)));
        for (String mod : order) {
            Component label = Component.literal(modName(mod));
            String note = plan.versionNotes().get(mod);
            if (note != null) {
                label = Component.translatable("keybindprofilesplus.configs.import.version_note", modName(mod), note);
            }
            ConfigTree.Group group = tree.group(tree.root, "mod:" + mod, label);
            ConfigTree.Group perWorld = null;
            for (ConfigImport.Item item : byMod.get(mod)) {
                ConfigTree.Group target = group;
                if (item.perWorld()) {
                    if (perWorld == null) {
                        perWorld = tree.group(group, "mod:" + mod + "/world", Component.translatable("keybindprofilesplus.configs.group.world"));
                    }
                    target = perWorld;
                }
                String status = item.status().name().toLowerCase(Locale.ROOT);
                int color = switch (item.status()) {
                    case NEW -> GuiUtil.GREEN;
                    case CHANGE -> GuiUtil.YELLOW;
                    case DELETE -> GuiUtil.RED;
                    default -> GuiUtil.DARK_GRAY;
                };
                tree.leaf(target, "file:" + item.path(), Component.literal(item.path()), Component.translatable("keybindprofilesplus.configs.status." + status),
                        color, item.status() != ConfigImport.Status.SAME, item.suggested(), item);
            }
        }
        if (!missing.isEmpty()) {
            ConfigTree.Group group = tree.group(tree.root, "missing", Component.translatable("keybindprofilesplus.configs.import.group.missing"));
            for (ConfigImport.Item item : missing) {
                String owner = item.owners().isEmpty() ? "" : item.owners().get(0);
                ConfigArchive.ModRef ref = plan.archive().mods().get(owner);
                Component label = owner.isEmpty() ? Component.translatable("keybindprofilesplus.configs.group.orphans.unknown") : Component.literal(ref != null ? ref.name() : owner);
                ConfigTree.Group byOwner = tree.group(group, "missing/" + owner, label);
                tree.leaf(byOwner, "file:" + item.path(), Component.literal(item.path()), Component.translatable("keybindprofilesplus.configs.status.mod_missing"),
                        GuiUtil.DARK_GRAY, true, false, item);
            }
        }
        if (!rejected.isEmpty()) {
            ConfigTree.Group group = tree.group(tree.root, "rejected", Component.translatable("keybindprofilesplus.configs.import.group.rejected"));
            for (ConfigImport.Item item : rejected) {
                tree.leaf(group, "file:" + item.path(), Component.literal(item.path()), Component.translatable("keybindprofilesplus.configs.reject." + item.detail()),
                        GuiUtil.RED, false, false, item);
            }
        }
        // With few mods, show everything at once.
        if (order.size() <= 3) {
            tree.root.children.forEach(node -> {
                if (node instanceof ConfigTree.Group group) {
                    group.expanded = true;
                }
            });
        }
    }

    private String rootOf(String modId) {
        ModInfo mod = mods.get(modId);
        return mod == null ? modId : mod.rootId();
    }

    private String modName(String modId) {
        ModInfo mod = mods.get(modId);
        if (mod != null) {
            return mod.name();
        }
        ConfigArchive.ModRef ref = plan.archive().mods().get(modId);
        return ref != null ? ref.name() : modId.isEmpty() ? "?" : modId;
    }
}
