package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Adds the mod's two buttons ("Manage Profiles" and "Compare Profiles") to the bottom row of the
 * vanilla Key Binds screen and squeezes the vanilla "Reset Keys" / "Done" buttons next to them.
 */
public final class ControlsScreenProfileButton {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 26;
    private static final int GAP = 8;
    private static final int SLOTS = 4;
    private static final int DEFAULT_BUTTON_WIDTH = 150;
    private static final int MIN_BUTTON_WIDTH = 70;
    private static final int SCREEN_PADDING = 8;

    private ControlsScreenProfileButton() {
    }

    public static void addOrReplace(Screen screen, int scaledWidth, int scaledHeight) {
        List<AbstractWidget> buttons = Screens.getButtons(screen);
        String manageText = Component.translatable("keybindprofilesplus.open").getString();
        String compareText = Component.translatable("keybindprofilesplus.compare.open_short").getString();
        buttons.removeIf(button -> {
            String message = button.getMessage().getString();
            return message.equals(manageText) || message.equals(compareText);
        });

        List<AbstractWidget> vanillaBottomButtons = findBottomButtons(buttons, scaledHeight);
        RowLayout row = calculateRowLayout(scaledWidth, scaledHeight);

        moveVanillaButtons(vanillaBottomButtons, row);
        buttons.add(Button.builder(Component.literal(manageText), button -> KeyBindProfilesPlus.openConfigScreen(screen))
                .bounds(row.slotX(0), row.y(), row.buttonWidth(), BUTTON_HEIGHT)
                .build());
        buttons.add(Button.builder(Component.literal(compareText), button -> openCompare(screen))
                .bounds(row.slotX(1), row.y(), row.buttonWidth(), BUTTON_HEIGHT)
                .build());
    }

    /** Compares the applied profile (left) with the keys as they are set right now (right). */
    private static void openCompare(Screen screen) {
        ProfileService service = KeyBindProfilesPlus.profileService();
        String applied = service.getCurrentProfile();
        if (applied == null || !service.profiles().containsKey(applied)) {
            applied = service.profiles().keySet().stream().min(String.CASE_INSENSITIVE_ORDER).orElse(null);
        }
        Minecraft.getInstance().setScreen(new ProfileCompareScreen(screen, service, applied, null));
    }

    private static List<AbstractWidget> findBottomButtons(List<AbstractWidget> buttons, int scaledHeight) {
        List<AbstractWidget> bottomButtons = new ArrayList<>();
        for (AbstractWidget button : buttons) {
            if (button.getY() >= scaledHeight - 32) {
                bottomButtons.add(button);
            }
        }
        bottomButtons.sort(Comparator.comparingInt(AbstractWidget::getX));
        return bottomButtons;
    }

    private static RowLayout calculateRowLayout(int scaledWidth, int scaledHeight) {
        int fullRowWidth = DEFAULT_BUTTON_WIDTH * SLOTS + GAP * (SLOTS - 1);
        int buttonWidth = scaledWidth >= fullRowWidth + SCREEN_PADDING * 2
                ? DEFAULT_BUTTON_WIDTH
                : Math.max(MIN_BUTTON_WIDTH, (scaledWidth - SCREEN_PADDING * 2 - GAP * (SLOTS - 1)) / SLOTS);
        int rowWidth = buttonWidth * SLOTS + GAP * (SLOTS - 1);
        int startX = Math.max(SCREEN_PADDING, (scaledWidth - rowWidth) / 2);
        int y = Math.max(SCREEN_PADDING, scaledHeight - BOTTOM_MARGIN);
        return new RowLayout(startX, y, buttonWidth);
    }

    private static void moveVanillaButtons(List<AbstractWidget> bottomButtons, RowLayout row) {
        for (int i = 0; i < bottomButtons.size() && i < 2; i++) {
            AbstractWidget button = bottomButtons.get(i);
            button.setX(row.slotX(i + 2));
            button.setY(row.y());
            button.setWidth(row.buttonWidth());
        }
    }

    private record RowLayout(int startX, int y, int buttonWidth) {
        int slotX(int slot) {
            return startX + slot * (buttonWidth + GAP);
        }
    }
}
