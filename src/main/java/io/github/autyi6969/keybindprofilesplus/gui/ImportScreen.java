package io.github.autyi6969.keybindprofilesplus.gui;

import io.github.autyi6969.keybindprofilesplus.profile.ProfileNames;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.profile.ShareCode;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Turns a pasted share code into a profile. The code is checked as it is typed or pasted; a
 * broken one produces an explanation, never an error screen.
 */
public class ImportScreen extends Screen {
    private static final int MAX_CODE_LENGTH = 400_000;

    private final Screen parent;
    private final ProfileService service;
    private final Consumer<String> onImported;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, 33, 33);

    private WidgetRowList rows;
    private TextFieldWidget codeField;
    private TextFieldWidget nameField;
    private ButtonWidget importButton;
    private String codeText = "";
    private String nameText = "";
    private ShareCode.Content content;
    private Text message = Text.translatable("keybindprofilesplus.share.paste_prompt");
    private int messageColor = GuiUtil.GRAY;

    /**
     * @param onImported told the name of the profile that was created
     */
    public ImportScreen(Screen parent, ProfileService service, Consumer<String> onImported) {
        super(Text.translatable("keybindprofilesplus.import.title"));
        this.parent = parent;
        this.service = service;
        this.onImported = onImported;
    }

    @Override
    protected void init() {
        layout.addHeader(title, textRenderer);
        rows = layout.addBody(new WidgetRowList(client, width, layout));

        DirectionalLayoutWidget footer = layout.addFooter(DirectionalLayoutWidget.horizontal().spacing(8));
        importButton = footer.add(ButtonWidget.builder(Text.translatable("keybindprofilesplus.import.confirm"), button -> importProfile()).width(150).build());
        footer.add(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close()).width(150).build());

        codeField = new TextFieldWidget(textRenderer, 100, 20, Text.translatable("keybindprofilesplus.share.code"));
        codeField.setMaxLength(MAX_CODE_LENGTH);
        codeField.setText(codeText);
        codeField.setPlaceholder(Text.literal(ShareCode.PREFIX + "...").setStyle(TextFieldWidget.SEARCH_STYLE));
        codeField.setChangedListener(this::onCodeChanged);
        nameField = new TextFieldWidget(textRenderer, 100, 20, Text.translatable("keybindprofilesplus.profile_name"));
        nameField.setMaxLength(ProfileNames.MAX_LENGTH);
        nameField.setText(nameText);
        nameField.setChangedListener(value -> {
            nameText = value;
            updateImportButton();
        });

        rows.addHeading(Text.translatable("keybindprofilesplus.share.code"));
        rows.addWidgets(new int[]{3, 1}, codeField,
                ButtonWidget.builder(Text.translatable("keybindprofilesplus.share.paste"), button -> setCode(client.keyboard.getClipboard())).build());
        rows.addText(() -> message, () -> messageColor);
        rows.addHeading(Text.translatable("keybindprofilesplus.profile_name"));
        rows.addWidgets(nameField);
        rows.addText(this::nameProblem, GuiUtil.RED);

        layout.forEachChild(this::addDrawableChild);
        updateImportButton();
        refreshWidgetPositions();
    }

    @Override
    protected void refreshWidgetPositions() {
        layout.refreshPositions();
        if (rows != null) {
            rows.position(width, layout);
        }
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(codeField);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    // ------------------------------------------------------------------ actions (also used by the self-test)

    /** Fills the code field, as pasting would. */
    public void setCode(String code) {
        String text = code == null ? "" : code.replaceAll("\\s+", "");
        if (text.length() > MAX_CODE_LENGTH) {
            text = text.substring(0, MAX_CODE_LENGTH);
        }
        codeField.setText(text);
    }

    public void setName(String name) {
        nameField.setText(name);
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
        client.setScreen(parent);
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
            message = Text.translatable("keybindprofilesplus.share.paste_prompt");
            messageColor = GuiUtil.GRAY;
        } else {
            try {
                content = ShareCode.decode(value);
                message = Text.translatable("keybindprofilesplus.share.preview", content.name(), content.keyBindings().size(), content.options().size());
                messageColor = GuiUtil.GREEN;
                // Suggest the name from the code, moved aside if a profile of that name already exists.
                nameField.setText(ProfileNames.firstFree(content.name(), service.profiles().keySet()));
            } catch (ShareCode.InvalidShareCodeException e) {
                message = Text.translatable(e.problem().translationKey());
                messageColor = GuiUtil.RED;
            }
        }
        updateImportButton();
    }

    private Text nameProblem() {
        if (content == null) {
            return null;
        }
        String name = nameText.trim();
        String problem = ProfileNames.validate(name);
        if (problem != null) {
            return Text.translatable(problem);
        }
        if (ProfileNames.containsIgnoreCase(service.profiles().keySet(), name)) {
            return Text.translatable("keybindprofilesplus.status.profile_exists", name);
        }
        return null;
    }

    private void updateImportButton() {
        if (importButton != null) {
            importButton.active = content != null && nameProblem() == null;
        }
    }
}
