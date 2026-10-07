package io.github.autyism.keybindprofilesplus.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The tick tree of the config export and import screens: groups (a mod, a reason...) that open and
 * close, files with a check box, and files that are only listed (greyed out, cannot be ticked).
 * Ticking a group ticks every file below it that can be ticked.
 */
final class ConfigTree {
    static final int ROW_HEIGHT = 18;
    private static final int INDENT = 12;
    private static final int ARROW_WIDTH = 10;

    abstract static class Node {
        final String id;
        final Component label;
        int depth;

        Node(String id, Component label) {
            this.id = id;
            this.label = label;
        }
    }

    static final class Group extends Node {
        final List<Node> children = new ArrayList<>();
        boolean expanded;

        Group(String id, Component label) {
            super(id, label);
        }
    }

    static final class Leaf extends Node {
        final boolean enabled;
        final Component right;
        final int rightColor;
        final Object payload;
        boolean checked;

        Leaf(String id, Component label, Component right, int rightColor, boolean enabled, boolean checked, Object payload) {
            super(id, label);
            this.right = right;
            this.rightColor = rightColor;
            this.enabled = enabled;
            this.checked = enabled && checked;
            this.payload = payload;
        }
    }

    final Group root = new Group("root", Component.empty());
    private final Map<String, Node> byId = new HashMap<>();

    Group group(Group parent, String id, Component label) {
        if (byId.get(id) instanceof Group existing) {
            return existing;
        }
        Group group = new Group(id, label);
        attach(parent, group);
        return group;
    }

    Leaf leaf(Group parent, String id, Component label, Component right, int rightColor, boolean enabled, boolean checked, Object payload) {
        Leaf leaf = new Leaf(id, label, right, rightColor, enabled, checked, payload);
        attach(parent, leaf);
        return leaf;
    }

    private void attach(Group parent, Node node) {
        node.depth = parent == root ? 0 : parent.depth + 1;
        parent.children.add(node);
        byId.put(node.id, node);
    }

    Node node(String id) {
        return byId.get(id);
    }

    void clear() {
        root.children.clear();
        byId.clear();
    }

    /** Ticks or unticks a node (a group: everything below it). False for an unknown id. */
    boolean setChecked(String id, boolean checked) {
        Node node = byId.get(id);
        if (node == null) {
            return false;
        }
        setChecked(node, checked);
        return true;
    }

    void setChecked(Node node, boolean checked) {
        if (node instanceof Leaf leaf) {
            if (leaf.enabled) {
                leaf.checked = checked;
            }
        } else if (node instanceof Group group) {
            group.children.forEach(child -> setChecked(child, checked));
        }
    }

    GuiUtil.CheckState state(Node node) {
        if (node instanceof Leaf leaf) {
            return leaf.checked ? GuiUtil.CheckState.CHECKED : GuiUtil.CheckState.UNCHECKED;
        }
        int[] counts = counts((Group) node);
        if (counts[0] == 0) {
            return GuiUtil.CheckState.UNCHECKED;
        }
        return counts[0] == counts[1] ? GuiUtil.CheckState.CHECKED : GuiUtil.CheckState.PARTIAL;
    }

    /** {ticked files, files that can be ticked, all files} below a group. */
    int[] counts(Group group) {
        int[] counts = new int[3];
        forEachLeaf(group, leaf -> {
            counts[2]++;
            if (leaf.enabled) {
                counts[1]++;
                if (leaf.checked) {
                    counts[0]++;
                }
            }
        });
        return counts;
    }

    List<Leaf> checkedLeaves() {
        List<Leaf> out = new ArrayList<>();
        forEachLeaf(root, leaf -> {
            if (leaf.checked) {
                out.add(leaf);
            }
        });
        return out;
    }

    static void forEachLeaf(Group group, Consumer<Leaf> action) {
        for (Node child : group.children) {
            if (child instanceof Leaf leaf) {
                action.accept(leaf);
            } else if (child instanceof Group nested) {
                forEachLeaf(nested, action);
            }
        }
    }

    /** The rows to show: open groups show their children; while searching, only matching files and their groups. */
    List<Node> rows(String query) {
        List<Node> rows = new ArrayList<>();
        collect(root, query.trim().toLowerCase(Locale.ROOT), rows);
        return rows;
    }

    private boolean collect(Group group, String needle, List<Node> rows) {
        boolean any = false;
        for (Node child : group.children) {
            if (child instanceof Leaf leaf) {
                if (needle.isEmpty() || leaf.label.getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    rows.add(leaf);
                    any = true;
                }
            } else if (child instanceof Group nested) {
                int index = rows.size();
                rows.add(nested);
                List<Node> below = new ArrayList<>();
                boolean groupMatches = !needle.isEmpty() && nested.label.getString().toLowerCase(Locale.ROOT).contains(needle);
                boolean hasMatch = collect(nested, groupMatches ? "" : needle, below);
                if (!needle.isEmpty()) {
                    if (hasMatch) {
                        rows.addAll(below);
                        any = true;
                    } else {
                        rows.remove(index);
                    }
                } else {
                    if (nested.expanded) {
                        rows.addAll(below);
                    }
                    any = true;
                }
            }
        }
        return any;
    }

    /** The scrolling list that shows a {@link ConfigTree}. */
    static final class ListWidget extends ContainerObjectSelectionList<ListWidget.Entry> {
        private final ConfigTree tree;
        private final Font font;
        private final Runnable onChange;
        private String query = "";

        ListWidget(Minecraft client, int width, HeaderAndFooterLayout layout, ConfigTree tree, Runnable onChange) {
            super(client, width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
            this.tree = tree;
            this.font = client.font;
            this.onChange = onChange;
        }

        void refresh(String newQuery, boolean keepScroll) {
            query = newQuery;
            double scroll = keepScroll ? scrollAmount() : 0;
            clearEntries();
            for (Node node : tree.rows(query)) {
                addEntry(new Entry(node));
            }
            setScrollAmount(scroll);
        }

        @Override
        public int getRowWidth() {
            return Math.max(220, Math.min(480, width - 40));
        }

        int visibleRows() {
            return children().size();
        }

        /** Screen coordinates of a row's check box (or label), scrolled into view; null when it is not shown. */
        int[] hitPoint(String id, boolean onCheckbox) {
            for (Entry entry : children()) {
                if (entry.node.id.equals(id)) {
                    scrollToEntry(entry);
                    int x = onCheckbox ? entry.checkboxX() + GuiUtil.CHECKBOX_SIZE / 2 : entry.checkboxX() + GuiUtil.CHECKBOX_SIZE + 30;
                    return new int[]{x, entry.getContentYMiddle()};
                }
            }
            return null;
        }

        final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            private final Node node;

            Entry(Node node) {
                this.node = node;
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }

            private int checkboxX() {
                return getContentX() + node.depth * INDENT + ARROW_WIDTH;
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                if (click.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                    return false;
                }
                int boxLeft = checkboxX();
                boolean onCheckbox = click.x() >= boxLeft - 2 && click.x() <= boxLeft + GuiUtil.CHECKBOX_SIZE + 2;
                if (node instanceof Group group && !onCheckbox) {
                    group.expanded = !group.expanded;
                } else {
                    tree.setChecked(node, tree.state(node) != GuiUtil.CheckState.CHECKED);
                    onChange.run();
                }
                refresh(query, true);
                return true;
            }

            @Override
            public void renderContent(GuiGraphics context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                int left = getContentX();
                int right = getContentRight();
                int textY = getContentYMiddle() - font.lineHeight / 2;
                boolean tickable = !(node instanceof Leaf leaf) || leaf.enabled;
                if (hovered && tickable) {
                    context.fill(left - 2, getContentY() - 1, right + 2, getContentBottom() + 1, GuiUtil.ROW_HOVER);
                }
                int boxLeft = checkboxX();
                int labelLeft = boxLeft + GuiUtil.CHECKBOX_SIZE + 5;
                if (node instanceof Group group) {
                    int[] counts = tree.counts(group);
                    if (counts[1] > 0) {
                        GuiUtil.drawCheckbox(context, boxLeft, getContentYMiddle() - GuiUtil.CHECKBOX_SIZE / 2, tree.state(group), hovered);
                    }
                    boolean open = group.expanded || !query.isBlank();
                    context.drawString(font, open ? "v" : ">", boxLeft - ARROW_WIDTH + 1, textY, GuiUtil.GRAY);
                    String count = counts[1] > 0 ? counts[0] + "/" + counts[1] : String.valueOf(counts[2]);
                    int countWidth = font.width(count);
                    context.drawString(font, count, right - countWidth, textY, counts[0] == 0 ? GuiUtil.DARK_GRAY : GuiUtil.GRAY);
                    String label = GuiUtil.ellipsize(font, group.label.getString(), right - countWidth - 8 - labelLeft);
                    context.drawString(font, label, labelLeft, textY, counts[1] > 0 ? GuiUtil.WHITE : GuiUtil.GRAY);
                } else if (node instanceof Leaf leaf) {
                    if (leaf.enabled) {
                        GuiUtil.drawCheckbox(context, boxLeft, getContentYMiddle() - GuiUtil.CHECKBOX_SIZE / 2, tree.state(leaf), hovered);
                    }
                    String rightText = leaf.right == null ? "" : GuiUtil.ellipsize(font, leaf.right.getString(), (right - labelLeft) / 2);
                    int rightWidth = font.width(rightText);
                    context.drawString(font, rightText, right - rightWidth, textY, leaf.rightColor);
                    String label = GuiUtil.ellipsize(font, leaf.label.getString(), right - rightWidth - 8 - labelLeft);
                    int color = !leaf.enabled ? GuiUtil.DARK_GRAY : leaf.checked ? GuiUtil.WHITE : GuiUtil.GRAY;
                    context.drawString(font, label, labelLeft, textY, color);
                }
            }
        }
    }
}
