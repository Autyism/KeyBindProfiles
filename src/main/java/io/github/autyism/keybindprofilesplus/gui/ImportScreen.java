package io.github.autyism.keybindprofilesplus.gui;

import io.github.autyism.keybindprofilesplus.profile.ProfileNames;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.profile.ShareCode;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Turns a pasted share code into a profile. The code is checked as it is typed or pasted; a
 * broken one produces an explanation, never an error screen.
 */
public class ImportScreen extends ResizingScreen {
    private static final int MAX_CODE_LENGTH = 400_000;

    private final Screen parent;
    private final ProfileService service;
    private final Consumer<String> onImported;
    private HeaderAndFooterLayout layout;

    private WidgetRowList rows;
    private EditBox codeField;
    private EditBox nameField;
    private Button importButton;
    private String codeText = "";
    private String nameText = "";
    private ShareCode.Content content;
    private Component message = Component.translatable("keybindprofilesplus.share.paste_prompt");
    private int messageColor = GuiUtil.GRAY;

    /**
     * @param onImported told the name of the profile that was created
     */
    public ImportScreen(Screen parent, ProfileService service, Consumer<String> onImported) {
        super(Component.translatable("keybindprofilesplus.import.title"));
        this.parent = parent;
        this.service = service;
        this.onImported = onImported;
    }

    @Override
    protected void init() {
        layout = startLayout(33, 33);
        layout.addTitleHeader(title, font);
        rows = layout.addToContents(new WidgetRowList(minecraft, width, layout));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        importButton = footer.addChild(Button.builder(Component.translatable("keybindprofilesplus.import.confirm"), button -> importProfile()).width(150).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).width(150).build());

        codeField = new EditBox(font, 100, 20, Component.translatable("keybindprofilesplus.share.code"));
        codeField.setMaxLength(MAX_CODE_LENGTH);
        codeField.setValue(codeText);
        codeField.setHint(Component.literal(ShareCode.PREFIX + "...").setStyle(EditBox.SEARCH_HINT_STYLE));
        codeField.setResponder(this::onCodeChanged);
        nameField = new EditBox(font, 100, 20, Component.translatable("keybindprofilesplus.profile_name"));
        nameField.setMaxLength(ProfileNames.MAX_LENGTH);
        nameField.setValue(nameText);
        nameField.setResponder(value -> {
            nameText = value;
            updateImportButton();
        });

        rows.addHeading(Component.translatable("keybindprofilesplus.share.code"));
        rows.addWidgets(new int[]{3, 1}, codeField,
                Button.builder(Component.translatable("keybindprofilesplus.share.paste"), button -> setCode(minecraft.keyboardHandler.getClipboard())).build());
        rows.addText(() -> message, () -> messageColor);
        rows.addHeading(Component.translatable("keybindprofilesplus.profile_name"));
        rows.addWidgets(nameField);
        rows.addText(this::nameProblem, GuiUtil.RED);

        layout.visitWidgets(this::addRenderableWidget);
        updateImportButton();
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
        }
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(codeField);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    /** Fills the code field, as pasting would. */
    public void setCode(String code) {
        String text = code == null ? "" : code.replaceAll("\\s+", "");
        if (text.length() > MAX_CODE_LENGTH) {
            text = text.substring(0, MAX_CODE_LENGTH);
        }
        codeField.setValue(text);
    }

    public void setName(String name) {
        nameField.setValue(name);
    }

    /** The problem with the pasted code, or null when it is valid (or nothing was pasted yet). */
    public ShareCode.Problem problem() {
        if (codeText.isBlank() || content != null) {
            return null;
        }
        try {
            ShareCode.decode(codeText);
            return null;
        } catch (ShareCode.InvalidShareCodeException e) {
            return e.problem();
        }
    }

    public boolean canImport() {
        return importButton != null && importButton.active;
    }

    /** Creates the profile and goes back. Returns false when the code or the name is not usable. */
    public boolean importProfile() {
        if (content == null || nameProblem() != null) {
            return false;
        }
        String name = nameText.trim();
        if (!service.createProfile(name, content.keyBindings(), content.options())) {
            return false;
        }
        minecraft.setScreen(parent);
        onImported.accept(name);
        if (parent instanceof KeyBindProfileScreen main) {
            main.showStatus("keybindprofilesplus.status.profile_imported", name);
        }
        return true;
    }

    private void onCodeChanged(String value) {
        codeText = value;
        content = null;
        if (value.isBlank()) {
            message = Component.translatable("keybindprofilesplus.share.paste_prompt");
            messageColor = GuiUtil.GRAY;
        } else {
            try {
                content = ShareCode.decode(value);
                message = Component.translatable("keybindprofilesplus.share.preview", content.name(), content.keyBindings().size(), content.options().size());
                messageColor = GuiUtil.GREEN;
                // Suggest the name from the code, moved aside if a profile of that name already exists.
                nameField.setValue(ProfileNames.firstFree(content.name(), service.profiles().keySet()));
            } catch (ShareCode.InvalidShareCodeException e) {
                message = Component.translatable(e.problem().translationKey());
                messageColor = GuiUtil.RED;
            }
        }
        updateImportButton();
    }

    private Component nameProblem() {
        if (content == null) {
            return null;
        }
        String name = nameText.trim();
        String problem = ProfileNames.validate(name);
        if (problem != null) {
            return Component.translatable(problem);
        }
        if (ProfileNames.containsIgnoreCase(service.profiles().keySet(), name)) {
            return Component.translatable("keybindprofilesplus.status.profile_exists", name);
        }
        return null;
    }

    private void updateImportButton() {
        if (importButton != null) {
            importButton.active = content != null && nameProblem() == null;
        }
    }
}
