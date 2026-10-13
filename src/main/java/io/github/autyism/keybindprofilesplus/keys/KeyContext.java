package io.github.autyism.keybindprofilesplus.keys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Where a key does something: in the world (no screen open), in which screens, and maybe only in one
 * game mode or while something else is the case. Two keys on the same trigger can only get in each
 * other's way where their contexts {@link #meet meet}.
 *
 * <p>A context is a list of {@link Part parts}. Most keys have one ("in the world"); a vanilla key
 * like Drop has two: in the world (not in Spectator mode) and in inventory screens, where it throws
 * the item under the mouse. Besides the normal kind there are keys that never count as a conflict
 * ({@link Kind#SITUATIONAL}: held together with something else, or only acting in a situation their
 * mod sets up itself) and the F3 keys of the game's debug overlay.
 */
public final class KeyContext {
    /** The places a key can act in. Screens are grouped the way the keys of the game and of the supported mods treat them. */
    public enum Place {
        /** Playing, no screen open. */
        WORLD,
        /** The inventory and the crafting table: the screens with a crafting grid (and a recipe book). */
        CRAFTING,
        /** Villager trading. */
        MERCHANT,
        /** The Creative inventory (only exists in Creative mode). */
        CREATIVE,
        /** Every other inventory screen: chests, furnaces, anvils, shulker boxes... */
        CONTAINER,
        /** The advancements screen (its key closes it). */
        ADVANCEMENTS,
        /** The social interactions screen (its key closes it). */
        SOCIAL,
        /** Any other screen: chat, pause menu, settings, other mods' screens without a place of their own... */
        OTHER_SCREEN;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The game modes a part is limited to. */
    public enum Mode {
        ANY,
        NOT_SPECTATOR,
        CREATIVE,
        SPECTATOR
    }

    public enum Kind {
        NORMAL,
        /** Never a conflict: held together with something else, or only acting in a situation of its mod's own. */
        SITUATIONAL,
        /** The F3 key itself (debug overlay, debug modifier). */
        DEBUG_BASE,
        /** Only does something while F3 is held (F3 + G...). */
        DEBUG_COMBO
    }

    public static final Set<Place> CONTAINERS = EnumSet.of(Place.CRAFTING, Place.MERCHANT, Place.CREATIVE, Place.CONTAINER);
    public static final Set<Place> SCREENS = EnumSet.complementOf(EnumSet.of(Place.WORLD));

    /** Screens of particular mods, by "modid:name", that have their own name in the language files. */
    public static final String CONFLUX_MAP_SCREEN = "confluxmap:map";
    public static final String METEOR_SCREEN = "meteor-client:gui";

    /**
     * One part of a context.
     *
     * @param places       the game's places
     * @param modScreens   screens of particular mods ({@link #CONFLUX_MAP_SCREEN}...)
     * @param anyModScreen whether it also acts in every mod's screen (keys that act in any screen)
     * @param mode         the game modes it is limited to
     * @param condition    null, or something that must be the case as well (only "recipe_book" so far); a meeting under a condition is only a possible conflict
     */
    public record Part(Set<Place> places, Set<String> modScreens, boolean anyModScreen, Mode mode, String condition) {
        public Part {
            places = places.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(places));
            modScreens = Set.copyOf(modScreens);
        }

        static Part of(Mode mode, Collection<Place> places) {
            boolean anyScreen = places.containsAll(SCREENS);
            return new Part(EnumSet.copyOf(places), Set.of(), anyScreen, mode, null);
        }

        /** The game mode it really is limited to: the Creative inventory only exists in Creative mode. */
        Mode effectiveMode() {
            if (mode != Mode.SPECTATOR && modScreens.isEmpty() && !anyModScreen && places.equals(Set.of(Place.CREATIVE))) {
                return Mode.CREATIVE;
            }
            return mode;
        }
    }

    /** How two keys on the same trigger meet. */
    public record Meeting(boolean possibleOnly, String reason, Component where) {
    }

    private static final KeyContext SITUATIONAL = new KeyContext(Kind.SITUATIONAL, List.of(), null);
    private static final KeyContext DEBUG_BASE = new KeyContext(Kind.DEBUG_BASE, List.of(Part.of(Mode.ANY, EnumSet.allOf(Place.class))), null);
    private static final KeyContext DEBUG_COMBO = new KeyContext(Kind.DEBUG_COMBO, List.of(), null);

    private final Kind kind;
    private final List<Part> parts;
    /** For situational keys: what the situation is (translation key suffix), or null. */
    private final String note;

    private KeyContext(Kind kind, List<Part> parts, String note) {
        this.kind = kind;
        this.parts = List.copyOf(parts);
        this.note = note;
    }

    // ------------------------------------------------------------------ making contexts

    public static KeyContext world() {
        return world(Mode.ANY);
    }

    public static KeyContext world(Mode mode) {
        return of(Part.of(mode, EnumSet.of(Place.WORLD)));
    }

    /** Every inventory screen. */
    public static KeyContext containers() {
        return of(Part.of(Mode.ANY, CONTAINERS));
    }

    /** Some of the game's places. */
    public static KeyContext places(Place... places) {
        return of(Part.of(Mode.ANY, List.of(places)));
    }

    /** Any screen at all, mods' screens included. */
    public static KeyContext screens() {
        return of(Part.of(Mode.ANY, SCREENS));
    }

    /** In the world and in every screen. */
    public static KeyContext anywhere() {
        return of(Part.of(Mode.ANY, EnumSet.allOf(Place.class)));
    }

    /** One screen of a mod ({@link #CONFLUX_MAP_SCREEN}...). */
    public static Part modScreen(String screen) {
        return new Part(Set.of(), Set.of(screen), false, Mode.ANY, null);
    }

    public static Part part(Mode mode, Place... places) {
        return Part.of(mode, List.of(places));
    }

    public static Part part(Mode mode, Collection<Place> places) {
        return Part.of(mode, places);
    }

    /** A part that only counts while {@code condition} is the case ("recipe_book"). */
    public static Part conditional(String condition, Collection<Place> places) {
        return new Part(EnumSet.copyOf(places), Set.of(), false, Mode.ANY, condition);
    }

    public static KeyContext of(Part... parts) {
        return new KeyContext(Kind.NORMAL, List.of(parts), null);
    }

    /** Never a conflict. */
    public static KeyContext situational() {
        return SITUATIONAL;
    }

    /** Never a conflict, with what the situation is ("hold", "tool"...: keybindprofilesplus.context.situational.*). */
    public static KeyContext situational(String note) {
        return note == null ? SITUATIONAL : new KeyContext(Kind.SITUATIONAL, List.of(), note);
    }

    public static KeyContext debugBase() {
        return DEBUG_BASE;
    }

    public static KeyContext debugCombo() {
        return DEBUG_COMBO;
    }

    public Kind kind() {
        return kind;
    }

    public List<Part> parts() {
        return parts;
    }

    public String note() {
        return note;
    }

    /** Whether it acts in the world (in some game mode). */
    public boolean actsInWorld() {
        return parts.stream().anyMatch(part -> part.places().contains(Place.WORLD));
    }

    /** Whether it acts in this place (in some game mode, maybe under a condition). */
    public boolean actsIn(Place place) {
        return parts.stream().anyMatch(part -> part.places().contains(place));
    }

    /** Whether it acts in that mod screen. */
    public boolean actsInModScreen(String screen) {
        return parts.stream().anyMatch(part -> part.anyModScreen() || part.modScreens().contains(screen));
    }

    /** Whether it acts in no screen at all. */
    public boolean worldOnly() {
        return kind == Kind.NORMAL && parts.stream().allMatch(part -> part.places().equals(Set.of(Place.WORLD)) && part.modScreens().isEmpty() && !part.anyModScreen());
    }

    // ------------------------------------------------------------------ meeting

    /**
     * Where two keys on the same trigger both act, or null if never at the same time. A meeting in a
     * single game mode one of them is not limited to, or under a condition, is only a possible conflict.
     */
    public static Meeting meet(KeyContext first, KeyContext second) {
        if (first.kind == Kind.SITUATIONAL || second.kind == Kind.SITUATIONAL) {
            return null;
        }
        if (first.kind == Kind.DEBUG_COMBO || second.kind == Kind.DEBUG_COMBO) {
            return first.kind == second.kind ? new Meeting(false, "debug", null) : null;
        }
        if (first.kind == Kind.DEBUG_BASE && second.kind == Kind.DEBUG_BASE) {
            return null;
        }
        Meeting best = null;
        for (Part a : first.parts) {
            for (Part b : second.parts) {
                Meeting meeting = meet(a, b);
                if (meeting != null && (best == null || (best.possibleOnly() && !meeting.possibleOnly()))) {
                    best = meeting;
                }
            }
        }
        return best;
    }

    private static Meeting meet(Part a, Part b) {
        Set<Place> places = EnumSet.noneOf(Place.class);
        places.addAll(a.places());
        places.retainAll(b.places());
        Set<String> modScreens = new LinkedHashSet<>();
        for (String screen : a.modScreens()) {
            if (b.anyModScreen() || b.modScreens().contains(screen)) {
                modScreens.add(screen);
            }
        }
        for (String screen : b.modScreens()) {
            if (a.anyModScreen()) {
                modScreens.add(screen);
            }
        }
        boolean anyModScreen = a.anyModScreen() && b.anyModScreen();
        if (places.isEmpty() && modScreens.isEmpty() && !anyModScreen) {
            return null;
        }

        Mode mode = intersect(a.mode(), b.mode());
        boolean onlyCreativeInventory = places.equals(Set.of(Place.CREATIVE)) && modScreens.isEmpty() && !anyModScreen;
        if (mode != null && onlyCreativeInventory) {
            mode = intersect(mode, Mode.CREATIVE);
        }
        if (mode == null) {
            return null;
        }
        if (a.condition() != null || b.condition() != null) {
            return new Meeting(true, a.condition() != null ? a.condition() : b.condition(), null);
        }
        if (mode == Mode.CREATIVE || mode == Mode.SPECTATOR) {
            String modeName = mode == Mode.CREATIVE ? "creative" : "spectator";
            // Both only exist in that mode: there they always meet.
            boolean both = a.effectiveMode() == mode && b.effectiveMode() == mode;
            return new Meeting(!both, both ? modeName + "_both" : modeName, null);
        }
        return placeMeeting(places, modScreens, anyModScreen);
    }

    private static Meeting placeMeeting(Set<Place> places, Set<String> modScreens, boolean anyModScreen) {
        if (places.contains(Place.WORLD)) {
            return new Meeting(false, "world", Component.translatable("keybindprofilesplus.context.world"));
        }
        if (places.containsAll(SCREENS) && anyModScreen) {
            return new Meeting(false, "screens", Component.translatable("keybindprofilesplus.context.screens"));
        }
        if (places.containsAll(CONTAINERS)) {
            return new Meeting(false, "containers", Component.translatable("keybindprofilesplus.context.containers"));
        }
        List<Component> names = new ArrayList<>();
        for (Place place : places) {
            names.add(placeName(place));
        }
        for (String screen : modScreens) {
            names.add(modScreenName(screen));
        }
        if (anyModScreen) {
            names.add(Component.translatable("keybindprofilesplus.context.mod_screens"));
        }
        return new Meeting(false, "screen", join(names));
    }

    private static Mode intersect(Mode a, Mode b) {
        if (a == Mode.ANY) {
            return b;
        }
        if (b == Mode.ANY || a == b) {
            return a;
        }
        if (a == Mode.NOT_SPECTATOR) {
            return b == Mode.CREATIVE ? Mode.CREATIVE : null;
        }
        if (b == Mode.NOT_SPECTATOR) {
            return a == Mode.CREATIVE ? Mode.CREATIVE : null;
        }
        // Creative and Spectator
        return null;
    }

    // ------------------------------------------------------------------ words

    /** Where it acts, in words: "in the world and in inventory screens". */
    public Component describe() {
        switch (kind) {
            case SITUATIONAL -> {
                return Component.translatable(note == null ? "keybindprofilesplus.context.situational" : "keybindprofilesplus.context.situational." + note);
            }
            case DEBUG_BASE -> {
                return Component.translatable("keybindprofilesplus.context.debug_base");
            }
            case DEBUG_COMBO -> {
                return Component.translatable("keybindprofilesplus.context.debug_combo");
            }
            default -> {
                // handled below
            }
        }
        if (parts.size() == 1 && parts.get(0).places().equals(EnumSet.allOf(Place.class)) && parts.get(0).mode() == Mode.ANY) {
            return Component.translatable("keybindprofilesplus.context.anywhere");
        }
        List<Component> items = new ArrayList<>();
        for (Part part : parts) {
            items.add(describe(part));
        }
        return join(items);
    }

    private static Component describe(Part part) {
        if (part.condition() != null) {
            // "in inventory screens while the recipe book is open"
            return Component.translatable("keybindprofilesplus.context.condition." + part.condition());
        }
        List<Component> names = new ArrayList<>();
        Set<Place> places = EnumSet.noneOf(Place.class);
        places.addAll(part.places());
        if (places.contains(Place.WORLD)) {
            names.add(Component.translatable("keybindprofilesplus.context.world"));
            places.remove(Place.WORLD);
        }
        if (places.containsAll(SCREENS) && part.anyModScreen()) {
            names.add(Component.translatable("keybindprofilesplus.context.screens"));
            places.clear();
        } else if (places.containsAll(CONTAINERS)) {
            names.add(Component.translatable("keybindprofilesplus.context.containers"));
            places.removeAll(CONTAINERS);
        }
        for (Place place : places) {
            names.add(placeName(place));
        }
        for (String screen : part.modScreens()) {
            names.add(modScreenName(screen));
        }
        Component text = join(names);
        return switch (part.effectiveMode() == Mode.CREATIVE && part.mode() == Mode.ANY ? Mode.ANY : part.mode()) {
            case NOT_SPECTATOR -> Component.translatable("keybindprofilesplus.context.mode.not_spectator", text);
            case CREATIVE -> Component.translatable("keybindprofilesplus.context.mode.creative", text);
            case SPECTATOR -> Component.translatable("keybindprofilesplus.context.mode.spectator", text);
            case ANY -> text;
        };
    }

    static Component placeName(Place place) {
        return Component.translatable("keybindprofilesplus.context.place." + place.key());
    }

    static Component modScreenName(String screen) {
        return Component.translatable("keybindprofilesplus.context.mod_screen." + screen.replace(':', '.'));
    }

    /** "A", "A and B", "A, B and C" in the player's language. */
    static Component join(List<Component> items) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                text.append(Component.translatable(i == items.size() - 1 ? "keybindprofilesplus.context.join_last" : "keybindprofilesplus.context.join"));
            }
            text.append(items.get(i));
        }
        return text;
    }

    // ------------------------------------------------------------------ the player's own choice

    /** The choices the player clicks through, in order (stored in settings.json); null is "automatic". */
    public static final List<String> CHOICES = List.of("world", "containers", "screens", "anywhere", "situational");

    /** The context a stored choice stands for; null for "automatic" or an unknown value. Also reads the names 0.2 stored. */
    public static KeyContext fromChoice(String choice) {
        if (choice == null) {
            return null;
        }
        return switch (choice) {
            case "world", "general" -> world();
            case "containers" -> containers();
            case "screens", "screen" -> screens();
            case "anywhere" -> anywhere();
            case "situational" -> situational();
            default -> null;
        };
    }

    /** The next choice after {@code current}: automatic, world, inventory screens, any screen, everywhere, special situation, automatic. */
    public static String nextChoice(String current) {
        if (current == null) {
            return CHOICES.get(0);
        }
        String known = switch (current) {
            case "general" -> "world";
            case "screen" -> "screens";
            default -> current;
        };
        int index = CHOICES.indexOf(known);
        return index < 0 || index == CHOICES.size() - 1 ? null : CHOICES.get(index + 1);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KeyContext context && kind == context.kind && parts.equals(context.parts) && java.util.Objects.equals(note, context.note);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, parts, note);
    }

    @Override
    public String toString() {
        if (kind != Kind.NORMAL) {
            return kind + (note == null ? "" : "(" + note + ")");
        }
        StringBuilder text = new StringBuilder();
        for (Part part : parts) {
            if (!text.isEmpty()) {
                text.append(" + ");
            }
            List<String> names = new ArrayList<>();
            part.places().stream().sorted().forEach(place -> names.add(place.name()));
            names.addAll(part.modScreens());
            if (part.anyModScreen()) {
                names.add("MOD_SCREENS");
            }
            text.append(String.join("|", names));
            if (part.mode() != Mode.ANY) {
                text.append("@").append(part.mode());
            }
            if (part.condition() != null) {
                text.append("?").append(part.condition());
            }
        }
        return text.toString();
    }
}
