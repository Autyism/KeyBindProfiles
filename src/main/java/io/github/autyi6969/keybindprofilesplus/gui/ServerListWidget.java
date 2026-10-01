package io.github.autyi6969.keybindprofilesplus.gui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import io.github.autyi6969.keybindprofilesplus.server.ServerProfileMatcher;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ServerListWidget {
    private final List<ServerButtonPair> rows = new ArrayList<>();

    void refresh(RefreshRequest request) {
        clear(request.host());

        if (request.selectedProfile() == null) {
            request.updateActionButtons().run();
            return;
        }

        List<String> servers = KeyBindProfilesPlus.getProfileAutoSwitchServers(request.selectedProfile());
        if (servers == null || servers.isEmpty()) {
            request.updateActionButtons().run();
            return;
        }

        addVisibleRows(request, servers);
        request.updateActionButtons().run();
    }

    private void clear(KeyBindProfileScreen host) {
        for (ServerButtonPair row : rows) {
            host.removeButton(row.serverButton());
            host.removeButton(row.removeButton());
        }
        rows.clear();
    }

    private void addVisibleRows(RefreshRequest request, List<String> servers) {
        KeyBindProfileScreenLayout layout = request.layout();
        int rowX = layout.rightPanelX();
        int rowY = layout.serverListTop();
        int maxRows = Math.max(1, (request.screenHeight() - 32 - rowY) / KeyBindProfileScreenLayout.SERVER_ROW_SPACING);

        for (int i = 0; i < servers.size() && i < maxRows; i++) {
            addRow(request, servers.get(i), rowX, rowY + i * KeyBindProfileScreenLayout.SERVER_ROW_SPACING);
        }

        if (servers.size() > maxRows) {
            request.showStatus().accept(new StatusRequest("keybindprofilesplus.status.server_list_trimmed", maxRows, servers.size()));
        }
    }

    private void addRow(RefreshRequest request, String server, int rowX, int rowY) {
        // What the rule means, plus a warning when another profile has the very same rule.
        MutableText explanation = Text.translatable("keybindprofilesplus.server.rule." + ServerProfileMatcher.ruleKind(server));
        List<String> alsoUsedBy = ServerProfileMatcher.profilesUsingRule(server, KeyBindProfilesPlus.PROFILE_AUTO_SWITCH_SERVERS, request.selectedProfile());
        MutableText label = Text.literal(server);
        if (!alsoUsedBy.isEmpty()) {
            label.formatted(Formatting.YELLOW);
            explanation.append("\n").append(Text.translatable("keybindprofilesplus.server.rule.shared", String.join(", ", alsoUsedBy)).formatted(Formatting.YELLOW));
        }

        ButtonWidget serverButton = ButtonWidget.builder(label, button -> {
            request.serverInputField().setText(server);
        }).dimensions(rowX, rowY, 196, KeyBindProfileScreenLayout.SERVER_ROW_HEIGHT).tooltip(Tooltip.of(explanation)).build();

        ButtonWidget removeButton = ButtonWidget.builder(Text.translatable("keybindprofilesplus.remove_server"), button -> {
            request.removeServer().accept(server);
        }).dimensions(rowX + 204, rowY, 96, KeyBindProfileScreenLayout.SERVER_ROW_HEIGHT).build();

        rows.add(new ServerButtonPair(serverButton, removeButton));
        request.host().addButton(serverButton);
        request.host().addButton(removeButton);
    }

    private record ServerButtonPair(ButtonWidget serverButton, ButtonWidget removeButton) {
    }

    record StatusRequest(String translationKey, Object... args) {
    }

    record RefreshRequest(
            KeyBindProfileScreen host,
            KeyBindProfileScreenLayout layout,
            TextFieldWidget serverInputField,
            String selectedProfile,
            int screenHeight,
            Consumer<String> removeServer,
            Consumer<StatusRequest> showStatus,
            Runnable updateActionButtons
    ) {
    }
}
