package io.github.autyism.keybindprofilesplus.options;

import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sorts the game's settings (by their options.txt names) into a few groups for the
 * "what does this profile save" tree, and lists the ones a profile must never touch.
 */
public final class OptionCatalog {
    /**
     * Not offered at all: bookkeeping values, things tied to this machine, and things that only
     * take effect after a resource reload (language, resource packs).
     */
    private static final Set<String> EXCLUDED = Set.of(
            "version", "lastServer", "lang", "resourcePacks", "incompatibleResourcePacks", "tutorialStep",
            "skipMultiplayerWarning", "joinedFirstServer", "onboardAccessibility", "startedCleanly",
            "glDebugVerbosity", "telemetryOptInExtra", "overrideWidth", "overrideHeight", "useNativeTransport",
            "syncChunkWrites", "soundDevice", "fullscreenResolution", "realmsNotifications", "allowServerListing"
    );

    private static final Map<String, Category> CATEGORY_BY_KEY = new HashMap<>();

    static {
        assign(Category.CONTROLS, "mouseSensitivity", "invertXMouse", "invertYMouse", "mouseWheelSensitivity",
                "discrete_mouse_scroll", "rawMouseInput", "touchscreen", "allowCursorChanges", "autoJump", "toggleCrouch",
                "toggleSprint", "toggleAttack", "toggleUse", "sprintWindow", "rotateWithMinecart", "operatorItemsTab");
        assign(Category.VIDEO, "fov", "fovEffectScale", "gamma", "renderDistance", "simulationDistance", "maxFps",
                "inactivityFpsLimit", "enableVsync", "fullscreen", "guiScale", "graphicsPreset", "ao", "biomeBlendRadius",
                "chunkSectionFadeInTime", "cutoutLeaves", "entityDistanceScaling", "entityShadows", "prioritizeChunkUpdates",
                "maxAnisotropyBit", "textureFiltering", "improvedTransparency", "mipmapLevels", "particles", "renderClouds",
                "cloudRange", "vignette", "weatherRadius", "bobView", "attackIndicator", "screenEffectScale",
                "darknessEffectScale", "damageTiltStrength", "glintSpeed", "glintStrength", "menuBackgroundBlurriness",
                "showAutosaveIndicator", "hideLightningFlashes");
        assign(Category.SOUND, "showSubtitles", "directionalAudio", "musicToast", "musicFrequency");
        assign(Category.CHAT, "chatVisibility", "chatColors", "chatLinks", "chatLinksPrompt", "chatOpacity",
                "chatLineSpacing", "textBackgroundOpacity", "backgroundForChatOnly", "chatHeightFocused",
                "chatHeightUnfocused", "chatScale", "chatWidth", "chatDelay", "autoSuggestions", "hideMatchedNames",
                "onlyShowSecureChat", "saveChatDrafts", "notificationDisplayTime");
        assign(Category.ACCESSIBILITY, "narrator", "narratorHotkey", "highContrast", "highContrastBlockOutline",
                "forceUnicodeFont", "japaneseGlyphVariants", "reducedDebugInfo", "darkMojangStudiosBackground",
                "hideSplashTexts", "panoramaScrollSpeed", "hideServerAddress", "advancedItemTooltips", "pauseOnLostFocus");
        assign(Category.SKIN, "mainHand");
    }

    private OptionCatalog() {
    }

    public enum Category {
        CONTROLS("controls"),
        VIDEO("video"),
        SOUND("sound"),
        CHAT("chat"),
        ACCESSIBILITY("accessibility"),
        SKIN("skin"),
        OTHER("other");

        private final String key;

        Category(String key) {
            this.key = key;
        }

        public Text label() {
            return Text.translatable("keybindprofilesplus.option_category." + key);
        }
    }

    public static boolean isOffered(String optionKey) {
        return !EXCLUDED.contains(optionKey);
    }

    public static Category categoryOf(String optionKey) {
        Category category = CATEGORY_BY_KEY.get(optionKey);
        if (category != null) {
            return category;
        }
        if (optionKey.startsWith("soundCategory_")) {
            return Category.SOUND;
        }
        if (optionKey.startsWith("modelPart_")) {
            return Category.SKIN;
        }
        return Category.OTHER;
    }

    private static void assign(Category category, String... optionKeys) {
        for (String key : optionKeys) {
            CATEGORY_BY_KEY.put(key, category);
        }
    }
}
