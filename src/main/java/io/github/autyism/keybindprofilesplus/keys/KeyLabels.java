package io.github.autyism.keybindprofilesplus.keys;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Names of key bindings and their categories for the mod's own screens.
 *
 * <p>A mod that ships no translation for a binding makes the game show the bare id
 * ("key.somemod.toggle_zoom"). Here such an id is turned into readable words instead
 * ("Toggle Zoom"); bindings that do have a translation are shown exactly as the game shows them.
 */
public final class KeyLabels {
    private static final Set<String> ID_PREFIXES = Set.of("key", "keys", "keybind", "keybinds", "keybinding", "keybindings");

    private KeyLabels() {
    }

    public static Component name(KeyMapping binding) {
        return name(binding.getName());
    }

    /** The name of the binding with this id; also works for bindings of mods that are not installed. */
    public static Component name(String bindingId) {
        if (Language.getInstance().has(bindingId)) {
            return Component.translatable(bindingId);
        }

        String[] parts = bindingId.split("\\.");
        int from = 0;
        if (parts.length > 1 && ID_PREFIXES.contains(parts[0].toLowerCase(Locale.ROOT))) {
            from = 1;
        }
        // What follows is usually the mod's own name, which the source column already shows.
        if (parts.length - from > 1) {
            from++;
        }
        String readable = humanize(String.join(" ", Arrays.copyOfRange(parts, from, parts.length)));
        return Component.literal(readable.isEmpty() ? bindingId : readable);
    }

    //? if >=1.21.9 {
    public static Component category(KeyMapping.Category category) {
        Identifier id = category.id();
        if (Language.getInstance().has(id.toLanguageKey("key.category"))) {
            return category.label();
        }
        String readable = humanize("minecraft".equals(id.getNamespace()) ? id.getPath() : id.getNamespace() + " " + id.getPath());
        return readable.isEmpty() ? category.label() : Component.literal(readable);
    }
    //?} else {
    /*// Before 1.21.9 a category was just its translation key, mods' ones often without a translation.
    private static final Set<String> GAME_CATEGORIES = Set.of(KeyMapping.CATEGORY_MOVEMENT, KeyMapping.CATEGORY_MISC,
            KeyMapping.CATEGORY_MULTIPLAYER, KeyMapping.CATEGORY_GAMEPLAY, KeyMapping.CATEGORY_INVENTORY,
            KeyMapping.CATEGORY_INTERFACE, KeyMapping.CATEGORY_CREATIVE);

    public static Component category(String category) {
        if (Language.getInstance().has(category)) {
            return Component.translatable(category);
        }
        String readable = humanize(withoutCategoryPrefix(category));
        return Component.literal(readable.isEmpty() ? category : readable);
    }

    /^* The mod a category belongs to as far as its name tells ("key.categories.mymod" -> "mymod"); "minecraft" for the game's own. ^/
    public static String categoryNamespace(String category) {
        if (GAME_CATEGORIES.contains(category)) {
            return "minecraft";
        }
        return withoutCategoryPrefix(category).split("[.:/]")[0];
    }

    private static String withoutCategoryPrefix(String category) {
        return category.replaceFirst("^(key\\.categories\\.|key\\.category\\.|category\\.|key\\.)", "");
    }
    *///?}

    /** "toggle_freeCam-mode" becomes "Toggle Free Cam Mode". */
    public static String humanize(String raw) {
        String spaced = raw.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[_\\-./:]+", " ").trim();
        StringBuilder result = new StringBuilder();
        for (String word : spaced.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
