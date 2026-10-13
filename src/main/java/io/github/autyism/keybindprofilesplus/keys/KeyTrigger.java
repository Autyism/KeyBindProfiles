package io.github.autyism.keybindprofilesplus.keys;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;

/**
 * What has to be pressed for a key to react, in a form that keys of every kind can be compared in:
 * a game key binding with its Ctrl / Shift / Alt combination, a Meteor bind, or a MaLiLib or
 * Inventory Profiles Next hotkey of one or several keys.
 *
 * <p>A trigger is a set of {@link Unit units} that must all be held: Ctrl, Shift and Alt (held
 * together with another key, either side) and plain keys. {@code last} is the unit whose press
 * completes the trigger, or null when the keys may be pressed in any order. Some triggers also react
 * while further keys are held: {@code extraKeys} (any further key - MaLiLib's "allow extra keys") or
 * {@code extraModifiers} (only further Ctrl / Shift / Alt - a Meteor bind without modifiers reacts
 * whatever modifiers are held).
 *
 * <p>Two triggers {@link #overlaps overlap} when one press of keys makes both react. A game key
 * binding counts as exactly its combination: with "X" and "Ctrl + X" on one key only one of them
 * reacts to any given press (see {@link KeyCombos}), and a hotkey of two plain keys ("M + R") is a
 * different press than its last key alone.
 */
public record KeyTrigger(Set<Unit> units, Unit last, boolean extraKeys, boolean extraModifiers) {
    /** One thing that must be held: a modifier (either side) or a particular key. Exactly one of the two is set. */
    public record Unit(int modifier, InputConstants.Key key) {
        static Unit modifierUnit(int modifier) {
            return new Unit(modifier, null);
        }

        static Unit keyUnit(InputConstants.Key key) {
            return new Unit(0, key);
        }

        boolean isModifier() {
            return key == null;
        }
    }

    /** A game key binding with the combination it is on; null when it has no key. */
    public static KeyTrigger of(KeyMapping binding) {
        if (binding.isUnbound()) {
            return null;
        }
        return single(binding.key, KeyCombos.modifiersOf(binding), false);
    }

    /**
     * One key with Ctrl / Shift / Alt held before it.
     *
     * @param anyModifiers whether it also reacts while further modifiers are held
     */
    public static KeyTrigger single(InputConstants.Key key, int modifiers, boolean anyModifiers) {
        Set<Unit> units = new LinkedHashSet<>();
        addModifiers(units, modifiers & ~ownModifier(key));
        Unit main = Unit.keyUnit(key);
        units.add(main);
        return new KeyTrigger(Set.copyOf(units), main, false, anyModifiers);
    }

    /**
     * Several keys as MaLiLib and libIPN store a hotkey ("LEFT_CONTROL,C", "R,T"); null when there are none.
     * Ctrl / Shift / Alt keys count as modifiers (either side) when the hotkey also has another key; a
     * hotkey of modifier keys only keeps its last key as that very key.
     */
    public static KeyTrigger chord(List<InputConstants.Key> keys, boolean allowExtraKeys, boolean orderSensitive) {
        if (keys.isEmpty()) {
            return null;
        }
        boolean anyPlainKey = keys.stream().anyMatch(key -> ownModifier(key) == 0);
        Set<Unit> units = new LinkedHashSet<>();
        Unit last = null;
        for (int i = 0; i < keys.size(); i++) {
            InputConstants.Key key = keys.get(i);
            int modifier = ownModifier(key);
            boolean asModifier = modifier != 0 && (anyPlainKey || i < keys.size() - 1);
            Unit unit = asModifier ? Unit.modifierUnit(modifier) : Unit.keyUnit(key);
            units.add(unit);
            last = unit;
        }
        return new KeyTrigger(Set.copyOf(units), orderSensitive ? last : null, allowExtraKeys, false);
    }

    /** Whether one press of keys exists that makes both triggers react. */
    public boolean overlaps(KeyTrigger other) {
        Set<Unit> onlyHere = new LinkedHashSet<>(units);
        onlyHere.removeAll(other.units);
        Set<Unit> onlyThere = new LinkedHashSet<>(other.units);
        onlyThere.removeAll(units);
        if (!allowsHeld(onlyThere) || !other.allowsHeld(onlyHere)) {
            return false;
        }
        // The press that completes both: a unit of both, and the last one where the order matters.
        Set<Unit> completing = new LinkedHashSet<>(units);
        completing.retainAll(other.units);
        if (last != null) {
            completing.removeIf(unit -> !unit.equals(last));
        }
        if (other.last != null) {
            completing.removeIf(unit -> !unit.equals(other.last));
        }
        return !completing.isEmpty();
    }

    /** Exactly the same keys (in whatever order they are pressed). */
    public boolean sameKeys(KeyTrigger other) {
        return units.equals(other.units);
    }

    /** Whether it reacts to {@code other}'s keys only because it lets further keys be held: its own keys are fewer. */
    public boolean fewerKeysThan(KeyTrigger other) {
        return other.units.containsAll(units) && !units.equals(other.units);
    }

    /** Whether it reacts while keys it does not name are held. */
    public boolean allowsMoreKeys() {
        return extraKeys || extraModifiers;
    }

    /** The only plain key when the trigger is one key plus (optionally) modifiers; else null. */
    public InputConstants.Key mainKey() {
        InputConstants.Key found = null;
        for (Unit unit : units) {
            if (!unit.isModifier()) {
                if (found != null) {
                    return null;
                }
                found = unit.key();
            }
        }
        return found;
    }

    /** The modifiers that must be held with the other keys. */
    public int modifiers() {
        int bits = 0;
        for (Unit unit : units) {
            bits |= unit.modifier();
        }
        return bits;
    }

    /** Whether it is a single Ctrl, Shift or Alt key and nothing else. */
    public boolean isBareModifier() {
        InputConstants.Key key = mainKey();
        return units.size() == 1 && key != null && ownModifier(key) != 0;
    }

    /** Whether it is the left or the right mouse button and nothing else. */
    public boolean isBareClick() {
        InputConstants.Key key = mainKey();
        if (units.size() != 1 || key == null || key.getType() != InputConstants.Type.MOUSE) {
            return false;
        }
        //? if >=26.3 {
        /*return key.getValue() == InputConstants.MOUSE_BUTTON_LEFT || key.getValue() == InputConstants.MOUSE_BUTTON_RIGHT;
        *///?} else
        return key.getValue() <= 1;
    }

    private boolean allowsHeld(Set<Unit> extra) {
        if (extra.isEmpty() || extraKeys) {
            return true;
        }
        return extraModifiers && extra.stream().allMatch(Unit::isModifier);
    }

    private static void addModifiers(Set<Unit> units, int modifiers) {
        for (int bit : new int[]{KeyCombo.CTRL, KeyCombo.SHIFT, KeyCombo.ALT}) {
            if ((modifiers & bit) != 0) {
                units.add(Unit.modifierUnit(bit));
            }
        }
    }

    /** The modifier bit a key stands for when it is a Ctrl / Shift / Alt key itself, else 0. */
    static int ownModifier(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYSYM ? KeyCombo.modifierOfKeyCode(key.getValue()) : 0;
    }
}
