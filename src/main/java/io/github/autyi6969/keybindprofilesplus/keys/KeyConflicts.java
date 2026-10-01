package io.github.autyi6969.keybindprofilesplus.keys;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides whether two key bindings on the same key really get in each other's way.
 *
 * <p>Vanilla only asks "is it the same key?", so binding something to G is reported as clashing
 * with F3+G. Here every binding has a {@link Scope} - the situation it is active in - and the
 * verdict depends on how the two scopes overlap:
 * <ul>
 *   <li>{@link Level#HARD} (red): same key in the same situation;</li>
 *   <li>{@link Level#SOFT} (yellow): they only meet in one game mode (Creative or Spectator);</li>
 *   <li>{@link Level#NONE}: never active together, e.g. an F3 combination and a normal key.</li>
 * </ul>
 */
public final class KeyConflicts {
    private static final Set<String> DEBUG_BASE_IDS = Set.of("key.debug.overlay", "key.debug.modifier");
    private static final Set<String> CREATIVE_ONLY_IDS = Set.of("key.saveToolbarActivator", "key.loadToolbarActivator");
    private static final Set<String> SPECTATOR_ONLY_IDS = Set.of("key.spectatorOutlines", "key.spectatorHotbar");
    private static final Set<String> NON_SPECTATOR_IDS = Set.of("key.pickItem");

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
        /** Normal play in every game mode. Mod keys are assumed to be this. */
        GENERAL,
        /** Normal play, but not in Spectator mode (pick block). */
        NON_SPECTATOR,
        /** Creative mode only (save / load hotbar). */
        CREATIVE_ONLY,
        /** Spectator mode only. */
        SPECTATOR_ONLY,
        /** The F3 key itself: debug overlay and debug modifier, which share a key on purpose. */
        DEBUG_BASE,
        /** Only does something while F3 is held (F3+G and friends). */
        DEBUG_COMBO
    }

    /**
     * @param reason translation key suffix explaining the verdict (see keybindprofilesplus.conflict.reason.*)
     */
    public record Conflict(KeyBinding other, Level level, String reason) {
        public Text describe() {
            return Text.translatable("keybindprofilesplus.conflict.line", Text.translatable(other.getId()),
                    Text.translatable("keybindprofilesplus.conflict.reason." + reason));
        }
    }

    /** How many bindings have at least one hard / only soft conflicts. */
    public record Summary(int hard, int soft) {
        public boolean isEmpty() {
            return hard == 0 && soft == 0;
        }
    }

    public static Scope scopeOf(KeyBinding binding, KeySourceResolver sources) {
        String id = binding.getId();
        if (!sources.isVanilla(id)) {
            return Scope.GENERAL;
        }
        if (DEBUG_BASE_IDS.contains(id)) {
            return Scope.DEBUG_BASE;
        }
        if (binding.getCategory() == KeyBinding.Category.DEBUG) {
            return Scope.DEBUG_COMBO;
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

    /** The verdict for one pair, or null when they do not conflict at all. */
    public static Conflict between(KeyBinding binding, KeyBinding other, KeySourceResolver sources) {
        if (binding == other || binding.isUnbound() || other.isUnbound() || !binding.equals(other)) {
            return null;
        }
        // Pairs the game ships on the same key (F3+C for both "copy location" and "crash") are intended.
        if (binding.isDefault() && other.isDefault() && sources.isVanilla(binding.getId()) && sources.isVanilla(other.getId())) {
            return null;
        }

        Scope a = scopeOf(binding, sources);
        Scope b = scopeOf(other, sources);
        if (a == Scope.DEBUG_COMBO || b == Scope.DEBUG_COMBO) {
            return a == b ? new Conflict(other, Level.HARD, "debug") : null;
        }
        if (a == Scope.DEBUG_BASE && b == Scope.DEBUG_BASE) {
            return null;
        }
        if (isRestricted(a) || isRestricted(b)) {
            if (a == b) {
                return new Conflict(other, Level.HARD, a == Scope.CREATIVE_ONLY ? "creative_both" : "spectator_both");
            }
            if (a == Scope.SPECTATOR_ONLY || b == Scope.SPECTATOR_ONLY) {
                boolean neverTogether = a == Scope.CREATIVE_ONLY || b == Scope.CREATIVE_ONLY || a == Scope.NON_SPECTATOR || b == Scope.NON_SPECTATOR;
                return neverTogether ? null : new Conflict(other, Level.SOFT, "spectator");
            }
            return new Conflict(other, Level.SOFT, "creative");
        }
        return new Conflict(other, Level.HARD, "general");
    }

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
        conflicts.sort((first, second) -> second.level().compareTo(first.level()));
        return conflicts;
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

    /** Tooltip lines: a heading per level followed by the bindings it conflicts with and why. */
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

    private static boolean isRestricted(Scope scope) {
        return scope == Scope.CREATIVE_ONLY || scope == Scope.SPECTATOR_ONLY;
    }
}
