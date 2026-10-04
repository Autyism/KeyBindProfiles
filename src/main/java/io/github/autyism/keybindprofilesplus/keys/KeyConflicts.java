package io.github.autyism.keybindprofilesplus.keys;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether two key bindings on the same key really get in each other's way.
 *
 * <p>Vanilla only asks "is it the same key?", so binding something to G is reported as clashing
 * with F3+G. Here every binding has a {@link Scope} - the situation it is active in - and the
 * verdict depends on how the two scopes overlap:
 * <ul>
 *   <li>{@link Level#HARD} (red): same key in the same situation;</li>
 *   <li>{@link Level#SOFT} (yellow): they overlap only in theory or only in one situation
 *       (one game mode, or a key that only works inside screens against one used during play);</li>
 *   <li>{@link Level#NONE}: never active together, e.g. an F3 combination and a normal key,
 *       "X" and "Ctrl + X", or a key that only acts in a special situation of its mod (Litematica's
 *       tool on the mouse buttons).</li>
 * </ul>
 * Hotkeys that Meteor and malilib mods manage themselves take part as well.
 */
public final class KeyConflicts {
    public static final String OVERRIDE_GENERAL = "general";
    public static final String OVERRIDE_SCREEN = "screen";
    public static final String OVERRIDE_SITUATIONAL = "situational";
    /** Prefix of the setting name under which the player's choice for a hotkey of another mod is stored. */
    private static final String EXTERNAL_OVERRIDE_PREFIX = "external:";

    private static final Set<String> DEBUG_BASE_IDS = Set.of("key.debug.overlay", "key.debug.modifier");
    private static final Set<String> CREATIVE_ONLY_IDS = Set.of("key.saveToolbarActivator", "key.loadToolbarActivator");
    private static final Set<String> SPECTATOR_ONLY_IDS = Set.of("key.spectatorOutlines", "key.spectatorHotbar");
    private static final Set<String> NON_SPECTATOR_IDS = Set.of("key.pickItem");
    /** Vanilla bindings that also do something while an inventory screen is open. */
    private static final Set<String> CONTAINER_IDS = Set.of("key.drop", "key.swapOffhand", "key.pickItem", "key.inventory");
    private static final String HOTBAR_PREFIX = "key.hotbar.";

    /** Mods whose key bindings are known to work only inside inventory and recipe screens. */
    private static final Set<String> SCREEN_ONLY_MODS = Set.of("roughlyenoughitems", "jei", "emi", "inventoryprofilesnext",
            "mousetweaks", "mousewheelie", "itemscroller", "invtweaks", "inventorysorter", "trashslot", "craftingtweaks");
    /** Words in a mod binding's id or category that suggest it belongs to a screen rather than to play. */
    private static final List<String> SCREEN_WORDS = List.of("inventory", "container", "screen", "gui", "recipe", "tooltip", "slot", "crafting");

    private static KeySourceResolver resolver;
    private static GameOptions resolverOptions;

    private KeyConflicts() {
    }

    public enum Level {
        NONE,
        SOFT,
        HARD;

        public int color() {
            return switch (this) {
                case HARD -> 0xFFFF5555;
                case SOFT -> 0xFFFFFF55;
                case NONE -> 0xFFFFFFFF;
            };
        }

        public Formatting formatting() {
            return switch (this) {
                case HARD -> Formatting.RED;
                case SOFT -> Formatting.YELLOW;
                case NONE -> Formatting.WHITE;
            };
        }
    }

    /** The situation a key binding is active in. */
    public enum Scope {
        /** Normal play in every game mode. Mod keys are this unless something says otherwise. */
        GENERAL,
        /** Normal play, but not in Spectator mode (pick block). */
        NON_SPECTATOR,
        /** Creative mode only (save / load hotbar). */
        CREATIVE_ONLY,
        /** Spectator mode only. */
        SPECTATOR_ONLY,
        /** Only while a screen (inventory, recipe viewer...) is open. Mod keys only. */
        SCREEN_ONLY,
        /**
         * Only in a special situation the mod sets up itself (its tool item held, one of its modes on,
         * or held together with something else). Never counted as a conflict. Mod keys only.
         */
        SITUATIONAL,
        /** The F3 key itself: debug overlay and debug modifier, which share a key on purpose. */
        DEBUG_BASE,
        /** Only does something while F3 is held (F3+G and friends). */
        DEBUG_COMBO;

        public Text label() {
            return Text.translatable("keybindprofilesplus.scope." + name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * One thing a binding conflicts with: another game key binding ({@code other}) or a hotkey
     * another mod manages itself ({@code external}); exactly one of the two is set.
     *
     * @param reason translation key suffix explaining the verdict (keybindprofilesplus.conflict.reason.*)
     */
    public record Conflict(KeyBinding other, ExternalBinding external, Level level, String reason) {
        public Text otherName() {
            return other != null ? KeyLabels.name(other) : external.label();
        }

        /** Stable id of the other side, for logs and tests. */
        public String otherId() {
            return other != null ? other.getId() : external.sourceId() + ":" + external.name();
        }

        public Text describe() {
            return Text.translatable("keybindprofilesplus.conflict.line", otherName(),
                    Text.translatable("keybindprofilesplus.conflict.reason." + reason));
        }
    }

    /** How many bindings have at least one hard / only soft conflicts. */
    public record Summary(int hard, int soft) {
        public boolean isEmpty() {
            return hard == 0 && soft == 0;
        }
    }

    // ------------------------------------------------------------------ scopes

    public static Scope scopeOf(KeyBinding binding, KeySourceResolver sources) {
        String id = binding.getId();
        // The debug keys first, by the game's own category: an F3 combination stays one whoever
        // registered it. Mods add their own (Language Reload's F3+J), and those are not "vanilla".
        if (DEBUG_BASE_IDS.contains(id)) {
            return Scope.DEBUG_BASE;
        }
        if (KeyBinding.Category.DEBUG.equals(binding.getCategory())) {
            return Scope.DEBUG_COMBO;
        }
        if (!sources.isVanilla(id)) {
            Scope chosen = fromOverride(KeyBindProfilesPlus.settings().scopeOverride(id));
            return chosen != null ? chosen : guessedScope(binding, sources);
        }
        if (CREATIVE_ONLY_IDS.contains(id)) {
            return Scope.CREATIVE_ONLY;
        }
        if (SPECTATOR_ONLY_IDS.contains(id)) {
            return Scope.SPECTATOR_ONLY;
        }
        if (NON_SPECTATOR_IDS.contains(id)) {
            return Scope.NON_SPECTATOR;
        }
        return Scope.GENERAL;
    }

    /**
     * The game cannot tell when a mod's key binding is active, so this is an educated guess:
     * bindings of mods known to live in inventory screens, or whose name mentions a screen, count
     * as screen-only; everything else as active during play. The player can overrule it.
     */
    public static Scope guessedScope(KeyBinding binding, KeySourceResolver sources) {
        KeySource source = sources.resolve(binding);
        if (source.modId() != null && SCREEN_ONLY_MODS.contains(source.modId().toLowerCase(Locale.ROOT))) {
            return Scope.SCREEN_ONLY;
        }
        String text = (binding.getId() + " " + binding.getCategory().id().getPath()).toLowerCase(Locale.ROOT);
        for (String word : SCREEN_WORDS) {
            if (text.contains(word)) {
                return Scope.SCREEN_ONLY;
            }
        }
        return Scope.GENERAL;
    }

    /** Whether this is a mod binding whose scope the player may set by hand. */
    public static boolean canOverrideScope(KeyBinding binding, KeySourceResolver sources) {
        return !sources.isVanilla(binding.getId()) && !KeyBinding.Category.DEBUG.equals(binding.getCategory());
    }

    /** When a hotkey of another mod is in effect: the player's own choice if there is one, else what its reader worked out. */
    public static Scope scopeOf(ExternalBinding external) {
        Scope chosen = fromOverride(KeyBindProfilesPlus.settings().scopeOverride(overrideKey(external)));
        return chosen != null ? chosen : guessedScope(external);
    }

    public static Scope guessedScope(ExternalBinding external) {
        return switch (external.when()) {
            case SCREEN_ONLY -> Scope.SCREEN_ONLY;
            case SITUATIONAL -> Scope.SITUATIONAL;
            case IN_GAME, ANYWHERE -> Scope.GENERAL;
        };
    }

    /** The name under which the player's choice for this hotkey is kept in settings.json. */
    public static String overrideKey(ExternalBinding external) {
        return EXTERNAL_OVERRIDE_PREFIX + external.id();
    }

    /**
     * The next choice when the player clicks through them: automatic (null), during play, only in
     * screens, only in a special situation, and back to automatic.
     */
    public static String nextOverride(String current) {
        if (current == null) {
            return OVERRIDE_GENERAL;
        }
        return switch (current) {
            case OVERRIDE_GENERAL -> OVERRIDE_SCREEN;
            case OVERRIDE_SCREEN -> OVERRIDE_SITUATIONAL;
            default -> null;
        };
    }

    private static Scope fromOverride(String override) {
        if (override == null) {
            return null;
        }
        return switch (override) {
            case OVERRIDE_GENERAL -> Scope.GENERAL;
            case OVERRIDE_SCREEN -> Scope.SCREEN_ONLY;
            case OVERRIDE_SITUATIONAL -> Scope.SITUATIONAL;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ pairs

    /** The verdict for one pair, or null when they do not conflict at all. */
    public static Conflict between(KeyBinding binding, KeyBinding other, KeySourceResolver sources) {
        if (binding == other || binding.isUnbound() || other.isUnbound()) {
            return null;
        }
        // Holding Ctrl for "Ctrl + X" also presses whatever is bound to Ctrl itself (sprint by default).
        // That is true of every combination and harmless in practice, so it is not reported.
        if (!binding.equals(other)) {
            return null;
        }
        // Same key but different modifiers ("X" and "Ctrl + X"): only one of them reacts to any given press.
        if (KeyCombos.modifiersOf(binding) != KeyCombos.modifiersOf(other)) {
            return null;
        }
        // Pairs the game ships on the same key (F3+C for both "copy location" and "crash") are intended.
        if (binding.isDefault() && other.isDefault() && sources.isVanilla(binding.getId()) && sources.isVanilla(other.getId())) {
            return null;
        }

        Scope a = scopeOf(binding, sources);
        Scope b = scopeOf(other, sources);
        Verdict verdict = verdict(a, b, isContainerKey(binding, sources), isContainerKey(other, sources));
        return verdict == null ? null : new Conflict(other, null, verdict.level(), verdict.reason());
    }

    private record Verdict(Level level, String reason) {
    }

    private static Verdict verdict(Scope a, Scope b, boolean aWorksInContainers, boolean bWorksInContainers) {
        // Sharing a key with something that only acts in its own special situation is how that mod is meant to be used.
        if (a == Scope.SITUATIONAL || b == Scope.SITUATIONAL) {
            return null;
        }
        if (a == Scope.DEBUG_COMBO || b == Scope.DEBUG_COMBO) {
            return a == b ? new Verdict(Level.HARD, "debug") : null;
        }
        if (a == Scope.DEBUG_BASE && b == Scope.DEBUG_BASE) {
            return null;
        }
        if (a == Scope.SCREEN_ONLY || b == Scope.SCREEN_ONLY) {
            if (a == b) {
                return new Verdict(Level.HARD, "screen_both");
            }
            // Drop, swap hands, the hotbar numbers... also act on the slot under the mouse in inventories.
            boolean otherWorksInScreens = a == Scope.SCREEN_ONLY ? bWorksInContainers : aWorksInContainers;
            return otherWorksInScreens ? new Verdict(Level.HARD, "screen_vanilla") : new Verdict(Level.SOFT, "screen");
        }
        if (isGameModeOnly(a) || isGameModeOnly(b)) {
            if (a == b) {
                return new Verdict(Level.HARD, a == Scope.CREATIVE_ONLY ? "creative_both" : "spectator_both");
            }
            if (a == Scope.SPECTATOR_ONLY || b == Scope.SPECTATOR_ONLY) {
                boolean neverTogether = a == Scope.CREATIVE_ONLY || b == Scope.CREATIVE_ONLY || a == Scope.NON_SPECTATOR || b == Scope.NON_SPECTATOR;
                return neverTogether ? null : new Verdict(Level.SOFT, "spectator");
            }
            return new Verdict(Level.SOFT, "creative");
        }
        return new Verdict(Level.HARD, "general");
    }

    /** A hotkey another mod manages itself, on the same key (and modifiers) as a game binding. */
    private static Conflict withExternal(KeyBinding binding, ExternalBinding external, KeySourceResolver sources) {
        if (!sameTrigger(binding, external)) {
            return null;
        }
        Scope scope = scopeOf(binding, sources);
        // An F3 combination only happens with F3 held; the other mod's plain key is a different press.
        if (scope == Scope.DEBUG_COMBO) {
            return null;
        }
        Scope externalScope = scopeOf(external);
        // Meteor and malilib leave their in-game hotkeys alone while a screen is open, so such a
        // hotkey never meets a key that only works inside screens.
        if (scope == Scope.SCREEN_ONLY && externalScope == Scope.GENERAL && external.when() == ExternalBinding.When.IN_GAME) {
            return null;
        }
        Verdict verdict = verdict(scope, externalScope, isContainerKey(binding, sources), false);
        if (verdict == null) {
            return null;
        }
        // The other mod's key is only known from its file; say so instead of the usual explanation.
        String reason = verdict.level() == Level.HARD ? "external" : "external_partial";
        return new Conflict(null, external, verdict.level(), reason);
    }

    private static boolean sameTrigger(KeyBinding binding, ExternalBinding external) {
        return external.key() != null && external.key().equals(binding.boundKey) && external.modifiers() == KeyCombos.modifiersOf(binding);
    }

    private static boolean isContainerKey(KeyBinding binding, KeySourceResolver sources) {
        String id = binding.getId();
        return sources.isVanilla(id) && (CONTAINER_IDS.contains(id) || id.startsWith(HOTBAR_PREFIX));
    }

    private static boolean isGameModeOnly(Scope scope) {
        return scope == Scope.CREATIVE_ONLY || scope == Scope.SPECTATOR_ONLY;
    }

    // ------------------------------------------------------------------ whole layout

    /** Everything this binding conflicts with, hard conflicts first. */
    public static List<Conflict> conflictsOf(KeyBinding binding, GameOptions options) {
        List<Conflict> conflicts = new ArrayList<>();
        if (binding.isUnbound()) {
            return conflicts;
        }

        KeySourceResolver sources = sources(options);
        for (KeyBinding other : options.allKeys) {
            Conflict conflict = between(binding, other, sources);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }
        for (ExternalBinding external : ExternalKeys.active()) {
            Conflict conflict = withExternal(binding, external, sources);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }
        conflicts.sort((first, second) -> second.level().compareTo(first.level()));
        return conflicts;
    }

    /** Game key bindings on the same key as a hotkey another mod manages. */
    public static List<Conflict> conflictsOf(ExternalBinding external, GameOptions options) {
        List<Conflict> conflicts = new ArrayList<>();
        if (!external.active() || external.key() == null) {
            return conflicts;
        }
        KeySourceResolver sources = sources(options);
        for (KeyBinding binding : options.allKeys) {
            if (binding.isUnbound()) {
                continue;
            }
            Conflict conflict = withExternal(binding, external, sources);
            if (conflict != null) {
                conflicts.add(new Conflict(binding, null, conflict.level(), conflict.reason()));
            }
        }
        for (ExternalBinding other : ExternalKeys.active()) {
            Conflict conflict = betweenExternal(external, other);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }
        conflicts.sort((first, second) -> second.level().compareTo(first.level()));
        return conflicts;
    }

    /** Two hotkeys of other mods (Meteor, malilib) on the same key and modifiers. */
    private static Conflict betweenExternal(ExternalBinding external, ExternalBinding other) {
        if (other.equals(external) || other.key() == null || external.key() == null
                || !other.key().equals(external.key()) || other.modifiers() != external.modifiers()) {
            return null;
        }
        // Hotkeys of one malilib mod on one key are that mod's own business (they ship several on purpose and
        // tell them apart by their own settings). Two Meteor modules on one key do both toggle: that is reported.
        if (other.sourceId().equals(external.sourceId()) && !other.sourceId().equals("meteor")) {
            return null;
        }
        Scope a = scopeOf(external);
        Scope b = scopeOf(other);
        Verdict verdict = verdict(a, b, false, false);
        if (verdict == null) {
            return null;
        }
        return new Conflict(null, other, verdict.level(), verdict.level() == Level.HARD ? "external" : "external_partial");
    }

    /**
     * What sits on the same key as this binding without counting as a conflict because it only acts
     * in a special situation - so a tooltip can say why nothing is marked.
     */
    public static List<Text> sharedWithoutConflict(KeyBinding binding, GameOptions options) {
        List<Text> names = new ArrayList<>();
        if (binding.isUnbound()) {
            return names;
        }
        KeySourceResolver sources = sources(options);
        Scope own = scopeOf(binding, sources);
        if (own == Scope.DEBUG_COMBO) {
            return names;
        }
        for (KeyBinding other : options.allKeys) {
            if (other != binding && !other.isUnbound() && KeyCombos.sameTrigger(binding, other)
                    && (own == Scope.SITUATIONAL || scopeOf(other, sources) == Scope.SITUATIONAL)) {
                names.add(KeyLabels.name(other));
            }
        }
        for (ExternalBinding external : ExternalKeys.active()) {
            if (sameTrigger(binding, external) && (own == Scope.SITUATIONAL || scopeOf(external) == Scope.SITUATIONAL)) {
                names.add(external.label());
            }
        }
        return names;
    }

    /** The same for a hotkey of another mod: the game bindings on its key that are not counted. */
    public static List<Text> sharedWithoutConflict(ExternalBinding external, GameOptions options) {
        List<Text> names = new ArrayList<>();
        if (!external.active() || external.key() == null) {
            return names;
        }
        KeySourceResolver sources = sources(options);
        boolean situational = scopeOf(external) == Scope.SITUATIONAL;
        for (KeyBinding binding : options.allKeys) {
            if (binding.isUnbound() || !sameTrigger(binding, external)) {
                continue;
            }
            Scope scope = scopeOf(binding, sources);
            if (scope != Scope.DEBUG_COMBO && (situational || scope == Scope.SITUATIONAL)) {
                names.add(KeyLabels.name(binding));
            }
        }
        return names;
    }

    /** Tooltip lines for {@link #sharedWithoutConflict}; empty when there is nothing to say. */
    public static List<Text> describeShared(List<Text> names) {
        List<Text> lines = new ArrayList<>();
        if (!names.isEmpty()) {
            lines.add(Text.translatable("keybindprofilesplus.conflict.shared").formatted(Formatting.GRAY));
            for (Text name : names) {
                lines.add(Text.translatable("keybindprofilesplus.conflict.shared_line", name).formatted(Formatting.GRAY));
            }
        }
        return lines;
    }

    public static Level worst(List<Conflict> conflicts) {
        Level worst = Level.NONE;
        for (Conflict conflict : conflicts) {
            if (conflict.level().compareTo(worst) > 0) {
                worst = conflict.level();
            }
        }
        return worst;
    }

    public static Summary summarize(GameOptions options) {
        int hard = 0;
        int soft = 0;
        for (KeyBinding binding : options.allKeys) {
            Level level = worst(conflictsOf(binding, options));
            if (level == Level.HARD) {
                hard++;
            } else if (level == Level.SOFT) {
                soft++;
            }
        }
        return new Summary(hard, soft);
    }

    /** Tooltip lines: a heading per level followed by what it conflicts with and why. */
    public static List<Text> describe(List<Conflict> conflicts) {
        List<Text> lines = new ArrayList<>();
        Level heading = null;
        for (Conflict conflict : conflicts) {
            if (conflict.level() != heading) {
                heading = conflict.level();
                lines.add(Text.translatable(heading == Level.HARD ? "keybindprofilesplus.conflict.hard" : "keybindprofilesplus.conflict.soft")
                        .formatted(heading.formatting()));
            }
            lines.add(conflict.describe());
        }
        return lines;
    }

    /** The source resolver is only needed to tell vanilla bindings from mod ones; one instance is enough. */
    public static synchronized KeySourceResolver sources(GameOptions options) {
        if (resolver == null || resolverOptions != options) {
            resolver = new KeySourceResolver(options);
            resolverOptions = options;
        }
        return resolver;
    }
}
