package io.github.autyism.keybindprofilesplus.external;

import io.github.autyism.keybindprofilesplus.keys.KeyContext;
import io.github.autyism.keybindprofilesplus.keys.KeyTrigger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Where the hotkeys of MaLiLib mods and Inventory Profiles Next really act. Both libraries let a
 * hotkey declare a context - in game (no screen open), in screens ("GUI") or both - and that is the
 * starting point. What the mods then do with a key press narrows it further; that part comes from
 * reading their code (checked 2026-10 against Item Scroller 0.30.8, Litematica 0.26.16, MiniHUD
 * 0.38.16, Tweakeroo 0.27.15, Conflux Map 0.1.11 and Inventory Profiles Next 2.2.6 with libIPN 6.6.3):
 * <ul>
 *   <li>Item Scroller's screen hotkeys only act in inventory screens; its recipe view only in crafting
 *       screens, its trading hotkey only in the villager screen; "store recipe" only while the recipe
 *       view is held, "sort inventory" only when that feature is switched on in its settings, and none
 *       of them while Item Scroller itself is switched off;</li>
 *   <li>the inventory preview hotkeys of MiniHUD and Tweakeroo only act while the preview is switched on
 *       in their settings, and their "toggle screen" hotkeys only while the preview is shown;</li>
 *   <li>Conflux Map's "open map" acts during play and inside its own map screen (it closes it there);</li>
 *   <li>Inventory Profiles Next acts on inventory screens, and its trading hotkeys only on the villager screen;</li>
 *   <li>a hotkey that is a single Ctrl / Shift / Alt key, or whose name says it is a modifier, is held together
 *       with something else; a hotkey on the bare left or right mouse button cannot replace attacking, using
 *       or clicking in screens: both only act in a situation of their mod's own and never count as a conflict;</li>
 *   <li>IPN's scroll keys, Item Scroller's recipe view and the inventory preview keys are only held while doing
 *       something else, so further keys pressed with them do not count ({@link #onlyHeld}).</li>
 * </ul>
 */
final class KnownHotkeys {
    /** MaLiLib hotkeys known to act only in a situation of their mod's own (tool item held, looking at a schematic block...). */
    private static final Map<String, Map<String, String>> SITUATIONAL = Map.of(
            "litematica", Map.of("toolPlaceCorner1", "tool", "toolPlaceCorner2", "tool", "toolSelectElements", "tool",
                    "toolSelectModifierBlock1", "tool", "toolSelectModifierBlock2", "tool", "pickBlockFirst", "tool", "pickBlockLast", "tool",
                    "easyPlaceActivation", "tool", "renderOverlayThroughBlocks", "hold"),
            "itemscroller", Map.of("storeRecipe", "recipe_view"),
            "minihud", Map.of("inventoryPreviewToggleScreen", "inventory_preview"),
            "tweakeroo", Map.of("inventoryPreviewToggleScreen", "inventory_preview"));

    private static final Set<String> ITEMSCROLLER_IN_GAME = Set.of("openConfigGui", "massCraftToggle");
    private static final String ITEMSCROLLER_SETTINGS = "fi.dy.masa.itemscroller.config.Configs$Generic";
    /** Hotkeys that do nothing while an on/off setting of their mod is off: mod id -> hotkey -> {class, static field}. */
    private static final Map<String, Map<String, String[]>> SWITCHED = Map.of(
            "itemscroller", Map.of("sortInventory", new String[]{ITEMSCROLLER_SETTINGS, "SORT_INVENTORY_TOGGLE"}),
            "minihud", Map.of("inventoryPreview", new String[]{"fi.dy.masa.minihud.config.Configs$Generic", "INVENTORY_PREVIEW_ENABLED"}),
            "tweakeroo", Map.of("inventoryPreview", new String[]{"fi.dy.masa.tweakeroo.config.FeatureToggle", "TWEAK_INVENTORY_PREVIEW"}));

    /**
     * Hotkeys that are only held while doing something else (scrolling, clicking, looking at a container): they
     * do nothing on their own, so pressing further keys with them does not count, even where their mod lets them
     * react with further keys held. Mod id -> hotkey names (malilib names; IPN's config names).
     */
    private static final Map<String, Set<String>> HELD = Map.of(
            "inventoryprofilesnext", Set.of("scroll_full_stack", "scroll_leave_last", "scroll_spread", "scroll_throw"),
            "itemscroller", Set.of("recipeView"),
            "minihud", Set.of("inventoryPreview"),
            "tweakeroo", Set.of("inventoryPreview"));

    private static final Pattern IPN_TRADING = Pattern.compile("(global|local)_bookmark_trade\\d*|do_(global|local)_trade\\d*");
    private static final Pattern IPN_INVENTORY = Pattern.compile("sort_inventory.*|scroll_.*|lock_slots_.*|move_all_items|throw_all_items|dump_item_nbt_to_chat"
            + "|copy_components|copy_item_id|open_gui_editor|auto_refill_gui_toggle_for_slot");

    private KnownHotkeys() {
    }

    /**
     * Where a MaLiLib hotkey acts.
     *
     * @param context its KeybindSettings context ("INGAME", "GUI", "ANY"), or null when not known (its file
     *                only says so once the player changed it)
     */
    static KeyContext malilib(String modId, String name, String context, KeyTrigger trigger) {
        String situation = SITUATIONAL.getOrDefault(modId, Map.of()).get(name);
        if (situation != null) {
            return KeyContext.situational(situation);
        }
        KeyContext held = heldOrClick(name, trigger);
        if (held != null) {
            return held;
        }
        String[] setting = SWITCHED.getOrDefault(modId, Map.of()).get(name);
        if (setting != null && booleanSetting(setting[0], setting[1]) == Boolean.FALSE) {
            return KeyContext.situational("feature_off");
        }
        switch (modId) {
            case "itemscroller" -> {
                if (ITEMSCROLLER_IN_GAME.contains(name)) {
                    break;
                }
                if (name.equals("toggleModOnOff")) {
                    return context == null ? KeyContext.screens() : byContext(context);
                }
                // With Item Scroller switched off, its other hotkeys do nothing.
                if (booleanSetting(ITEMSCROLLER_SETTINGS, "MOD_MAIN_TOGGLE") == Boolean.FALSE) {
                    return KeyContext.situational("feature_off");
                }
                if (name.equals("recipeView")) {
                    return KeyContext.places(KeyContext.Place.CRAFTING);
                }
                if (name.equals("villagerTradeFavorites")) {
                    return KeyContext.places(KeyContext.Place.MERCHANT);
                }
                // Its hotkeys only do something in inventory screens (whatever screen is open otherwise).
                if (!"INGAME".equalsIgnoreCase(context)) {
                    return KeyContext.containers();
                }
            }
            case "confluxmap" -> {
                if (name.equals("openMap")) {
                    // Opens the map during play and closes it again inside the map; in other screens it does nothing.
                    if ("INGAME".equalsIgnoreCase(context)) {
                        return KeyContext.world();
                    }
                    if ("GUI".equalsIgnoreCase(context)) {
                        return KeyContext.of(KeyContext.modScreen(KeyContext.CONFLUX_MAP_SCREEN));
                    }
                    return KeyContext.of(KeyContext.part(KeyContext.Mode.ANY, KeyContext.Place.WORLD), KeyContext.modScreen(KeyContext.CONFLUX_MAP_SCREEN));
                }
            }
            default -> {
                // nothing known beyond the hotkey's own settings
            }
        }
        return byContext(context);
    }

    /**
     * Whether a hotkey is only held while doing something else, so its "allow extra keys" setting does not make it
     * meet combinations of more keys: holding C to throw items while scrolling does not get in the way of Ctrl + C.
     * Pressing exactly its keys still meets whatever else acts on them (holding X when X also closes the inventory).
     */
    static boolean onlyHeld(String modId, String name) {
        return HELD.getOrDefault(modId, Set.of()).contains(name);
    }

    /** Where a hotkey of Inventory Profiles Next acts; {@code context} as libIPN declares it (INGAME / GUI / ANY). */
    static KeyContext inventoryProfiles(String name, String context, KeyTrigger trigger) {
        KeyContext held = heldOrClick(name, trigger);
        if (held != null) {
            return held;
        }
        if (IPN_TRADING.matcher(name).matches()) {
            return KeyContext.places(KeyContext.Place.MERCHANT);
        }
        if (IPN_INVENTORY.matcher(name).matches() && !"INGAME".equalsIgnoreCase(context)) {
            return KeyContext.containers();
        }
        return byContext(context);
    }

    private static KeyContext heldOrClick(String name, KeyTrigger trigger) {
        if (name.toLowerCase(java.util.Locale.ROOT).contains("modifier") || (trigger != null && trigger.isBareModifier())) {
            return KeyContext.situational("hold");
        }
        if (trigger != null && trigger.isBareClick()) {
            return KeyContext.situational("tool");
        }
        return null;
    }

    private static KeyContext byContext(String context) {
        if ("GUI".equalsIgnoreCase(context)) {
            return KeyContext.screens();
        }
        if ("ANY".equalsIgnoreCase(context)) {
            return KeyContext.anywhere();
        }
        return KeyContext.world();
    }

    /** The value of a MaLiLib on/off setting held in a static field, or null when it cannot be read (mod not running). */
    private static Boolean booleanSetting(String className, String fieldName) {
        // The setting object and its getter are looked up once; its value is read every time (the player may switch it).
        Optional<Getter> getter = GETTERS.computeIfAbsent(className + "#" + fieldName, key -> getter(className, fieldName));
        if (getter.isEmpty()) {
            return null;
        }
        try {
            return getter.get().method().invoke(getter.get().option()) instanceof Boolean flag ? flag : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private record Getter(Object option, Method method) {
    }

    private static final Map<String, Optional<Getter>> GETTERS = new ConcurrentHashMap<>();

    private static Optional<Getter> getter(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getField(fieldName);
            Object option = field.get(null);
            return Optional.of(new Getter(option, option.getClass().getMethod("getBooleanValue")));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    /** For the self-test: Item Scroller's "sort inventory" switch as Item Scroller has it, or null when not running. */
    static Boolean itemScrollerSorting() {
        return booleanSetting(ITEMSCROLLER_SETTINGS, "SORT_INVENTORY_TOGGLE");
    }
}
