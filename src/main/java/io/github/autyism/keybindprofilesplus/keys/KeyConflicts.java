package io.github.autyism.keybindprofilesplus.keys;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.keys.KeyContext.Mode;
import io.github.autyism.keybindprofilesplus.keys.KeyContext.Place;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

/**
 * Decides whether two keys really get in each other's way.
 *
 * <p>Vanilla only asks "is it the same key?", so binding something to G is reported as clashing
 * with F3+G, and a key that only does something in inventory screens as clashing with one used
 * while walking around. Here every key - the game's key bindings and the hotkeys Meteor, MaLiLib
 * mods and Inventory Profiles Next manage themselves - gets a {@link KeyContext}: where it acts
 * (in the world, in inventory screens, in one particular screen...) and in which game mode. Two keys
 * conflict only when the same press of keys makes both react ({@link KeyTrigger}) and their contexts
 * meet:
 * <ul>
 *   <li>{@link Level#HARD} (red): they meet in a normal situation - both in the world, both in inventory
 *       screens, both in the villager screen...;</li>
 *   <li>{@link Level#SOFT} (yellow): they only meet in a game mode one of them is not limited to (Creative,
 *       Spectator) or while the recipe book is open;</li>
 *   <li>{@link Level#NONE}: never at the same time - an F3 combination and a plain key, "X" and "Ctrl + X",
 *       a key used in the world and one that only works in screens, or a key that only acts in a
 *       situation of its own mod (Litematica's tool on the mouse buttons, a key held while scrolling).</li>
 * </ul>
 * The player can set where a mod's key acts when the automatic guess is wrong (settings.json
 * "scopeOverrides"). The work is done once per key layout ({@link Layout}) and reused until a key changes.
 */
public final class KeyConflicts {
    public static final String OVERRIDE_WORLD = "world";
    public static final String OVERRIDE_CONTAINERS = "containers";
    public static final String OVERRIDE_SCREENS = "screens";
    public static final String OVERRIDE_ANYWHERE = "anywhere";
    public static final String OVERRIDE_SITUATIONAL = "situational";
    /** Prefix of the setting name under which the player's choice for a hotkey of another mod is stored. */
    private static final String EXTERNAL_OVERRIDE_PREFIX = "external:";

    private static final Set<String> DEBUG_BASE_IDS = Set.of("key.debug.overlay", "key.debug.modifier");
    private static final String HOTBAR_PREFIX = "key.hotbar.";
    private static final KeyContext WORLD_AND_INVENTORIES = KeyContext.of(KeyContext.part(Mode.ANY, Place.WORLD), KeyContext.part(Mode.ANY, KeyContext.CONTAINERS));
    /**
     * Where the game's own keys act (1.21.11; checked against KeyboardHandler, Minecraft.handleKeybinds and the
     * screens that look at key bindings). Every key binding is only pressed while no screen is open; a few are
     * also checked by screens: the inventory key closes inventory screens, Drop, Swap Hands and the hotbar keys
     * act on the slot under the mouse there, Pick Block copies it (Creative mode), the chat key opens the search
     * of the Creative inventory and of an open recipe book, the advancements and social keys close their screens,
     * and fullscreen and screenshot work everywhere. Not listed: in the world only.
     */
    private static final Map<String, KeyContext> VANILLA = Map.ofEntries(
            Map.entry("key.inventory", WORLD_AND_INVENTORIES),
            Map.entry("key.swapOffhand", KeyContext.of(KeyContext.part(Mode.NOT_SPECTATOR, Place.WORLD), KeyContext.part(Mode.ANY, KeyContext.CONTAINERS))),
            Map.entry("key.drop", KeyContext.of(KeyContext.part(Mode.NOT_SPECTATOR, Place.WORLD), KeyContext.part(Mode.ANY, KeyContext.CONTAINERS))),
            Map.entry("key.pickItem", KeyContext.of(KeyContext.part(Mode.NOT_SPECTATOR, Place.WORLD), KeyContext.part(Mode.CREATIVE, KeyContext.CONTAINERS))),
            Map.entry("key.chat", KeyContext.of(KeyContext.part(Mode.ANY, Place.WORLD), KeyContext.part(Mode.ANY, Place.CREATIVE),
                    KeyContext.conditional("recipe_book", List.of(Place.CRAFTING, Place.CONTAINER)))),
            Map.entry("key.socialInteractions", KeyContext.of(KeyContext.part(Mode.ANY, Place.WORLD, Place.SOCIAL))),
            Map.entry("key.advancements", KeyContext.of(KeyContext.part(Mode.ANY, Place.WORLD, Place.ADVANCEMENTS))),
            Map.entry("key.screenshot", KeyContext.anywhere()),
            Map.entry("key.fullscreen", KeyContext.anywhere()),
            Map.entry("key.saveToolbarActivator", KeyContext.world(Mode.CREATIVE)),
            Map.entry("key.loadToolbarActivator", KeyContext.world(Mode.CREATIVE)),
            Map.entry("key.spectatorOutlines", KeyContext.world(Mode.SPECTATOR)),
            Map.entry("key.spectatorHotbar", KeyContext.world(Mode.SPECTATOR)));

    /**
     * Key bindings of particular mods whose place is known from their code: Conflux Map's map key (without
     * MaLiLib) and Meteor's menu key also close their screen again; QoL Bundle's diagnostics layer keys only
     * act while a scan is shown and its placement lock is held while placing.
     */
    private static final Map<String, KeyContext> KNOWN_MOD_KEYS = Map.of(
            "key.confluxmap.open_map", KeyContext.of(KeyContext.part(Mode.ANY, Place.WORLD), KeyContext.modScreen(KeyContext.CONFLUX_MAP_SCREEN)),
            "key.meteor-client.open-gui", KeyContext.of(KeyContext.part(Mode.ANY, Place.WORLD), KeyContext.modScreen(KeyContext.METEOR_SCREEN)),
            "key.qolbundle.diagnostics_slice_next", KeyContext.situational("diagnostics"),
            "key.qolbundle.diagnostics_slice_previous", KeyContext.situational("diagnostics"),
            "key.qolbundle.placement_lock", KeyContext.situational("hold"));

    /** Mods whose key bindings are known to work only inside inventory and recipe screens. */
    private static final Set<String> INVENTORY_MODS = Set.of("roughlyenoughitems", "jei", "emi", "inventoryprofilesnext",
            "mousetweaks", "mousewheelie", "itemscroller", "invtweaks", "inventorysorter", "trashslot", "craftingtweaks");
    /** Words in a mod key's name that say it opens or shows something during play. */
    private static final Set<String> OPENING_WORDS = Set.of("open", "show", "toggle", "menu", "config", "configure", "settings", "options",
            "gui", "screen", "hud", "overlay", "editor");
    /** Words in a mod key's name that say it acts on an inventory screen. */
    private static final Set<String> INVENTORY_WORDS = Set.of("inventory", "container", "slot", "slots", "recipe", "recipes", "crafting",
            "craft", "sort", "sorting", "tooltip", "tooltips", "stack", "stacks");
    private static final Set<String> GENERIC_WORDS = Set.of("key", "keys", "keybind", "keybinds", "keybinding", "keybindings", "bind", "binding");

    private static KeySourceResolver resolver;
    private static Options resolverOptions;
    private static Layout layout;

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

        public ChatFormatting formatting() {
            return switch (this) {
                case HARD -> ChatFormatting.RED;
                case SOFT -> ChatFormatting.YELLOW;
                case NONE -> ChatFormatting.WHITE;
            };
        }
    }

    /**
     * One thing a key conflicts with: a game key binding ({@code other}) or a hotkey another mod manages
     * itself ({@code external}); exactly one of the two is set.
     *
     * @param reason      short code of why ("world", "containers", "screens", "screen", "creative", "spectator",
     *                    "creative_both", "spectator_both", "recipe_book", "debug")
     * @param explanation the reason in words, for the tooltip
     */
    public record Conflict(KeyMapping other, ExternalBinding external, Level level, String reason, Component explanation) {
        public Component otherName() {
            return other != null ? KeyLabels.name(other) : external.label();
        }

        /** Stable id of the other side, for logs and tests. */
        public String otherId() {
            return other != null ? other.getName() : external.hotkeyId() != null ? external.hotkeyId() : external.sourceId() + ":" + external.name();
        }

        public Component describe() {
            return Component.translatable("keybindprofilesplus.conflict.line", otherName(), explanation);
        }
    }

    /** Something on the same keys that is no conflict because the two never act at the same time, and where it acts. */
    public record Shared(Component name, Component where) {
    }

    /** How many keys have at least one hard / only soft conflicts. */
    public record Summary(int hard, int soft) {
        public boolean isEmpty() {
            return hard == 0 && soft == 0;
        }
    }

    // ------------------------------------------------------------------ where keys act

    /** Where a game key binding acts: the player's own choice for a mod's key if there is one, else the rules. */
    public static KeyContext contextOf(KeyMapping binding, KeySourceResolver sources) {
        if (canOverrideContext(binding, sources)) {
            KeyContext chosen = KeyContext.fromChoice(KeyBindProfilesPlus.settings().scopeOverride(binding.getName()));
            if (chosen != null) {
                return chosen;
            }
        }
        return guessedContext(binding, sources);
    }

    /** Where a game key binding acts by the mod's rules, without the player's choice. */
    public static KeyContext guessedContext(KeyMapping binding, KeySourceResolver sources) {
        String id = binding.getName();
        // The debug keys first, by the game's own category: an F3 combination stays one whoever registered it.
        // Mods add their own (Language Reload's F3+J), and those are not "vanilla".
        if (DEBUG_BASE_IDS.contains(id)) {
            return KeyContext.debugBase();
        }
        //? if >=1.21.11 {
        if (KeyMapping.Category.DEBUG.equals(binding.getCategory())) {
            return KeyContext.debugCombo();
        }
        //?}
        if (sources.isVanilla(id)) {
            if (id.startsWith(HOTBAR_PREFIX)) {
                return WORLD_AND_INVENTORIES;
            }
            return VANILLA.getOrDefault(id, KeyContext.world());
        }
        KeyContext known = KNOWN_MOD_KEYS.get(id);
        if (known != null) {
            return known;
        }
        KeySource source = sources.resolve(binding);
        if (source.modId() != null && INVENTORY_MODS.contains(source.modId().toLowerCase(Locale.ROOT))) {
            return KeyContext.containers();
        }
        return guessFromName(id, source.modId());
    }

    /** For the self-test: the context a key binding of a particular mod gets from the table of known ones, or null. */
    public static KeyContext knownModKey(String bindingId) {
        return KNOWN_MOD_KEYS.get(bindingId);
    }

    /**
     * A mod key the game cannot tell anything about: it acts in the world (mods read their keys during play)
     * unless its name says it works on inventory screens ("...inventory_sort", "...slot_lock") and not
     * that it opens or toggles something. The mod's own id in the name is not counted ("hysk-tooltips").
     */
    public static KeyContext guessFromName(String id, String modId) {
        Set<String> words = words(id);
        words.removeAll(GENERIC_WORDS);
        if (modId != null) {
            words.removeAll(words(modId));
        }
        if (words.stream().anyMatch(OPENING_WORDS::contains)) {
            return KeyContext.world();
        }
        return words.stream().anyMatch(INVENTORY_WORDS::contains) ? KeyContext.containers() : KeyContext.world();
    }

    private static Set<String> words(String text) {
        String spaced = text.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return new LinkedHashSet<>(Arrays.asList(spaced.split("[^a-z0-9]+")));
    }

    /** Whether this is a mod's key binding whose context the player may set by hand (not the game's own, not F3 keys). */
    public static boolean canOverrideContext(KeyMapping binding, KeySourceResolver sources) {
        //? if >=1.21.11 {
        return !sources.isVanilla(binding.getName()) && !KeyMapping.Category.DEBUG.equals(binding.getCategory());
        //?} else
        /*return !sources.isVanilla(binding.getName());*/
    }

    /** Where a hotkey of another mod acts: the player's own choice if there is one, else what its reader worked out. */
    public static KeyContext contextOf(ExternalBinding external) {
        KeyContext chosen = KeyContext.fromChoice(KeyBindProfilesPlus.settings().scopeOverride(overrideKey(external)));
        return chosen != null ? chosen : external.context();
    }

    /** The name under which the player's choice for this hotkey is kept in settings.json. */
    public static String overrideKey(ExternalBinding external) {
        return EXTERNAL_OVERRIDE_PREFIX + external.id();
    }

    /** The next choice when the player clicks through them: automatic (null), world, inventory screens, any screen, everywhere, special situation. */
    public static String nextOverride(String current) {
        return KeyContext.nextChoice(current);
    }

    // ------------------------------------------------------------------ queries (on the cached layout)

    /** Everything this binding conflicts with, hard conflicts first. */
    public static List<Conflict> conflictsOf(KeyMapping binding, Options options) {
        return layout(options).conflictsOf(binding);
    }

    /** Everything a hotkey of another mod conflicts with, hard conflicts first. */
    public static List<Conflict> conflictsOf(ExternalBinding external, Options options) {
        return layout(options).conflictsOf(external);
    }

    /** What is on the same keys as this binding without being a conflict, so a tooltip can say why nothing is marked. */
    public static List<Shared> sharedWithoutConflict(KeyMapping binding, Options options) {
        return layout(options).sharedWithoutConflict(binding);
    }

    public static List<Shared> sharedWithoutConflict(ExternalBinding external, Options options) {
        return layout(options).sharedWithoutConflict(external);
    }

    /** Where the binding acts right now (also used for its tooltip). */
    public static KeyContext contextOf(KeyMapping binding, Options options) {
        return contextOf(binding, sources(options));
    }

    public static Summary summarize(Options options) {
        return layout(options).summary();
    }

    /**
     * The verdicts for the whole key layout as it is now, for a screen that asks about every key at once: the layout
     * is looked up once instead of once per question.
     */
    public static Verdicts verdicts(Options options) {
        return layout(options);
    }

    /** Questions about the keys of one key layout; see {@link #verdicts}. */
    public interface Verdicts {
        List<Conflict> conflictsOf(KeyMapping binding);

        List<Conflict> conflictsOf(ExternalBinding external);

        List<Shared> sharedWithoutConflict(KeyMapping binding);

        List<Shared> sharedWithoutConflict(ExternalBinding external);
    }

    /** The pair verdict for two game key bindings, or null when they do not conflict. */
    public static Conflict between(KeyMapping binding, KeyMapping other, KeySourceResolver sources) {
        if (binding == other || binding.isUnbound() || other.isUnbound() || !KeyCombos.sameTrigger(binding, other) || intended(binding, other, sources)) {
            return null;
        }
        return conflict(contextOf(binding, sources), KeyTrigger.of(binding), contextOf(other, sources), KeyTrigger.of(other), other, null);
    }

    /**
     * Keys that ship on the same key on purpose: two of the game's own on their defaults (F3+C for both "copy
     * location" and "crash"), or two of one mod on their defaults (the mod shares them out of the box).
     */
    private static boolean intended(KeyMapping binding, KeyMapping other, KeySourceResolver sources) {
        if (!binding.isDefault() || !other.isDefault()) {
            return false;
        }
        if (sources.isVanilla(binding.getName()) && sources.isVanilla(other.getName())) {
            return true;
        }
        KeySource first = sources.resolve(binding);
        KeySource second = sources.resolve(other);
        return first.kind() == KeySource.Kind.MOD && second.kind() == KeySource.Kind.MOD && first.modId().equals(second.modId());
    }

    /** Hotkeys of one MaLiLib mod (or of Inventory Profiles Next) on one key are that mod's own business; two Meteor modules on one key do both toggle. */
    private static boolean sameModsBusiness(ExternalBinding first, ExternalBinding second) {
        return first.sourceId().equals(second.sourceId()) && !first.sourceId().equals("meteor");
    }

    /** The verdict for two keys that react to the same press, seen from the first; null when they never act at the same time. */
    private static Conflict conflict(KeyContext mine, KeyTrigger myTrigger, KeyContext theirs, KeyTrigger theirTrigger,
                                     KeyMapping otherBinding, ExternalBinding otherExternal) {
        KeyContext.Meeting meeting = KeyContext.meet(mine, theirs);
        if (meeting == null) {
            return null;
        }
        Component reason = meeting.where() != null
                ? Component.translatable("keybindprofilesplus.conflict.reason.place", meeting.where())
                : Component.translatable("keybindprofilesplus.conflict.reason." + meeting.reason());
        // Say so when the two only meet because one of them also reacts while further keys are held.
        if (myTrigger != null && theirTrigger != null && !myTrigger.sameKeys(theirTrigger)) {
            String loose = theirTrigger.fewerKeysThan(myTrigger) ? "other" : myTrigger.fewerKeysThan(theirTrigger) ? "self" : "both";
            reason = Component.translatable("keybindprofilesplus.conflict.loose_" + loose, reason);
        }
        return new Conflict(otherBinding, otherExternal, meeting.possibleOnly() ? Level.SOFT : Level.HARD, meeting.reason(), reason);
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

    /** Tooltip lines: a heading per level followed by what it conflicts with and why. */
    public static List<Component> describe(List<Conflict> conflicts) {
        List<Component> lines = new ArrayList<>();
        Level heading = null;
        for (Conflict conflict : conflicts) {
            if (conflict.level() != heading) {
                heading = conflict.level();
                lines.add(Component.translatable(heading == Level.HARD ? "keybindprofilesplus.conflict.hard" : "keybindprofilesplus.conflict.soft")
                        .withStyle(heading.formatting()));
            }
            lines.add(conflict.describe());
        }
        return lines;
    }

    /**
     * "Acts in the world and in inventory screens", with whether the mod worked it out, the player set it, or it is
     * one of the game's fixed rules ({@code adjustable} false).
     */
    public static Component contextLine(KeyContext context, boolean adjustable, boolean overridden) {
        String key = !adjustable ? "keybindprofilesplus.overview.tooltip.scope_fixed"
                : overridden ? "keybindprofilesplus.overview.tooltip.scope_set" : "keybindprofilesplus.overview.tooltip.scope_guessed";
        return Component.translatable(key, context.describe()).withStyle(ChatFormatting.GRAY);
    }

    /** The tooltip of a game key binding's key button: where it acts, what it conflicts with and what shares its key; empty when nothing shares it. */
    public static List<Component> keyTooltip(KeyMapping binding, Options options) {
        Verdicts verdicts = verdicts(options);
        List<Component> lines = new ArrayList<>(describe(verdicts.conflictsOf(binding)));
        lines.addAll(describeShared(verdicts.sharedWithoutConflict(binding)));
        if (!lines.isEmpty()) {
            KeySourceResolver sources = sources(options);
            boolean adjustable = canOverrideContext(binding, sources);
            lines.add(0, contextLine(contextOf(binding, sources), adjustable,
                    adjustable && KeyBindProfilesPlus.settings().scopeOverride(binding.getName()) != null));
        }
        return lines;
    }

    /** Tooltip lines for {@link #sharedWithoutConflict}; empty when there is nothing to say. */
    public static List<Component> describeShared(List<Shared> shared) {
        List<Component> lines = new ArrayList<>();
        if (!shared.isEmpty()) {
            lines.add(Component.translatable("keybindprofilesplus.conflict.shared").withStyle(ChatFormatting.GRAY));
            for (Shared item : shared) {
                lines.add(Component.translatable("keybindprofilesplus.conflict.shared_line", item.name(), item.where()).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /** The source resolver is only needed to tell vanilla bindings from mod ones; one instance is enough. */
    public static synchronized KeySourceResolver sources(Options options) {
        if (resolver == null || resolverOptions != options) {
            resolver = new KeySourceResolver(options);
            resolverOptions = options;
        }
        return resolver;
    }

    // ------------------------------------------------------------------ the layout

    /**
     * The verdicts for the whole key layout as it is now. Building it looks at every key once; queries then
     * only compare keys that share a key. It is kept until a key, a combination, a hotkey of another mod or a
     * context choice changes, so screens and tooltips that ask again and again cost next to nothing.
     */
    static synchronized Layout layout(Options options) {
        long fingerprint = fingerprint(options);
        List<ExternalBinding> externals = ExternalKeys.all();
        if (layout == null || layout.options != options || layout.externals != externals || layout.fingerprint != fingerprint) {
            layout = new Layout(options, sources(options), externals, fingerprint);
        }
        return layout;
    }

    /**
     * Changes whenever any game key binding's key or combination changes, or a context choice. It is worked out on
     * every question, so it stays cheap: the game keeps one object per key, so a binding on another key holds
     * another object, and the combinations and choices have counters of their own.
     */
    private static long fingerprint(Options options) {
        long hash = KeyBindProfilesPlus.settings().overridesVersion() * 1_000_003L + KeyCombos.version();
        for (KeyMapping binding : options.keyMappings) {
            hash = hash * 31 + System.identityHashCode(binding.key);
        }
        return hash;
    }

    /** Drops the cached verdicts (after something the fingerprint does not see changed). */
    public static synchronized void invalidate() {
        layout = null;
    }

    static final class Layout implements Verdicts {
        private final Options options;
        private final List<ExternalBinding> externals;
        private final long fingerprint;
        private final KeySourceResolver sources;
        /** Every key with a trigger, by the plain keys it contains (a key of modifiers only: by that key). */
        private final Map<Object, List<Entry>> byKey = new HashMap<>();
        private final Map<KeyMapping, Entry> bindings = new IdentityHashMap<>();
        /** The hotkeys of other mods in use, by {@link #hotkeyKey}. */
        private final Map<String, Entry> hotkeys = new HashMap<>();
        private final Map<Entry, List<Conflict>> conflicts = new IdentityHashMap<>();
        private Summary summary;

        private record Entry(KeyMapping binding, ExternalBinding external, KeyTrigger trigger, KeyContext context) {
        }

        Layout(Options options, KeySourceResolver sources, List<ExternalBinding> externals, long fingerprint) {
            this.options = options;
            this.externals = externals;
            this.fingerprint = fingerprint;
            this.sources = sources;
            for (KeyMapping binding : options.keyMappings) {
                Entry entry = new Entry(binding, null, KeyTrigger.of(binding), contextOf(binding, sources));
                bindings.put(binding, entry);
                index(entry);
            }
            for (ExternalBinding external : externals) {
                // Hotkeys of a configuration that is not in use (other Meteor profiles) take no part.
                if (external.active() && !external.unbound() && external.trigger() != null) {
                    Entry entry = new Entry(null, external, external.trigger(), contextOf(external));
                    hotkeys.put(hotkeyKey(external), entry);
                    index(entry);
                }
            }
        }

        /** What tells a hotkey apart from all others in one listing, also when the object comes from another listing. */
        private static String hotkeyKey(ExternalBinding external) {
            return external.hotkeyId() != null ? external.hotkeyId() : external.group().getString() + "\n" + external.id();
        }

        private void index(Entry entry) {
            if (entry.trigger() == null) {
                return;
            }
            for (KeyTrigger.Unit unit : entry.trigger().units()) {
                if (unit.key() != null) {
                    byKey.computeIfAbsent(unit.key(), key -> new ArrayList<>()).add(entry);
                }
            }
        }

        @Override
        public List<Conflict> conflictsOf(KeyMapping binding) {
            Entry entry = bindings.get(binding);
            return entry == null ? List.of() : conflictsOf(entry);
        }

        @Override
        public List<Conflict> conflictsOf(ExternalBinding external) {
            Entry entry = hotkeys.get(hotkeyKey(external));
            return entry == null ? List.of() : conflictsOf(entry);
        }

        private List<Conflict> conflictsOf(Entry entry) {
            return conflicts.computeIfAbsent(entry, this::compute);
        }

        private List<Conflict> compute(Entry entry) {
            List<Conflict> found = new ArrayList<>();
            for (Entry other : candidates(entry)) {
                Conflict conflict = verdict(entry, other);
                if (conflict != null) {
                    found.add(conflict);
                }
            }
            found.sort((first, second) -> second.level().compareTo(first.level()));
            return List.copyOf(found);
        }

        private Conflict verdict(Entry entry, Entry other) {
            if (entry.binding() != null && other.binding() != null) {
                // Two game key bindings: exactly the same key and combination.
                if (!KeyCombos.sameTrigger(entry.binding(), other.binding()) || intended(entry.binding(), other.binding(), sources)) {
                    return null;
                }
            } else if (!entry.trigger().overlaps(other.trigger())) {
                return null;
            }
            if (entry.external() != null && other.external() != null && sameModsBusiness(entry.external(), other.external())) {
                return null;
            }
            return conflict(entry.context(), entry.trigger(), other.context(), other.trigger(), other.binding(), other.external());
        }

        /** The keys that share at least one key with this one (itself left out). */
        private Set<Entry> candidates(Entry entry) {
            Set<Entry> found = new LinkedHashSet<>();
            if (entry.trigger() == null) {
                return found;
            }
            for (KeyTrigger.Unit unit : entry.trigger().units()) {
                if (unit.key() != null) {
                    found.addAll(byKey.getOrDefault(unit.key(), List.of()));
                }
            }
            found.remove(entry);
            return found;
        }

        @Override
        public List<Shared> sharedWithoutConflict(KeyMapping binding) {
            Entry entry = bindings.get(binding);
            return entry == null ? List.of() : shared(entry);
        }

        @Override
        public List<Shared> sharedWithoutConflict(ExternalBinding external) {
            Entry entry = hotkeys.get(hotkeyKey(external));
            return entry == null ? List.of() : shared(entry);
        }

        /** The keys that react to the same press without ever acting at the same time, with where they act. */
        private List<Shared> shared(Entry entry) {
            List<Shared> found = new ArrayList<>();
            if (isDebug(entry.context())) {
                return found;
            }
            for (Entry other : candidates(entry)) {
                if (isDebug(other.context()) || !sameTrigger(entry, other) || verdict(entry, other) != null || leftAlone(entry, other)) {
                    continue;
                }
                Component name = other.binding() != null ? KeyLabels.name(other.binding()) : other.external().label();
                found.add(new Shared(name, other.context().describe()));
            }
            return found;
        }

        private boolean sameTrigger(Entry entry, Entry other) {
            if (entry.binding() != null && other.binding() != null) {
                return KeyCombos.sameTrigger(entry.binding(), other.binding());
            }
            return entry.trigger().overlaps(other.trigger());
        }

        /** Pairs that are nobody's business: shipped together by the game or one mod, or one MaLiLib mod's own. */
        private boolean leftAlone(Entry entry, Entry other) {
            if (entry.binding() != null && other.binding() != null) {
                return intended(entry.binding(), other.binding(), sources);
            }
            return entry.external() != null && other.external() != null && sameModsBusiness(entry.external(), other.external());
        }

        private static boolean isDebug(KeyContext context) {
            return context.kind() == KeyContext.Kind.DEBUG_COMBO || context.kind() == KeyContext.Kind.DEBUG_BASE;
        }

        /** How many keys (game bindings and hotkeys in use) have a hard / only soft conflicts. */
        Summary summary() {
            if (summary == null) {
                int hard = 0;
                int soft = 0;
                for (Entry entry : bindings.values()) {
                    Level level = worst(conflictsOf(entry));
                    hard += level == Level.HARD ? 1 : 0;
                    soft += level == Level.SOFT ? 1 : 0;
                }
                summary = new Summary(hard, soft);
            }
            return summary;
        }
    }
}
