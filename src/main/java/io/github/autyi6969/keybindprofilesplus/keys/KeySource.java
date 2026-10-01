package io.github.autyi6969.keybindprofilesplus.keys;

import net.minecraft.text.Text;

/**
 * Where a key binding comes from: the vanilla game, a known mod, or a mod we could not identify.
 *
 * @param modId the mod id for {@link Kind#MOD}; for {@link Kind#UNKNOWN} an optional hint (the
 *              namespace the binding uses) or null; "minecraft" for vanilla
 * @param name  the mod's display name, used for {@link Kind#MOD} only
 */
public record KeySource(Kind kind, String modId, String name) {
    public static final KeySource VANILLA = new KeySource(Kind.VANILLA, "minecraft", "Minecraft");

    public enum Kind {
        VANILLA,
        MOD,
        UNKNOWN,
        /** A hotkey another mod manages on its own (Meteor, malilib), read from its config file. */
        EXTERNAL
    }

    public static KeySource mod(String modId, String name) {
        return new KeySource(Kind.MOD, modId, name);
    }

    public static KeySource external(String sourceId, String groupName) {
        return new KeySource(Kind.EXTERNAL, sourceId, groupName);
    }

    public static KeySource unknown(String hint) {
        return new KeySource(Kind.UNKNOWN, hint, null);
    }

    public boolean isVanilla() {
        return kind == Kind.VANILLA;
    }

    /** Short label for list rows. */
    public Text label() {
        return switch (kind) {
            case VANILLA -> Text.translatable("keybindprofilesplus.source.vanilla");
            case MOD, EXTERNAL -> Text.literal(name);
            case UNKNOWN -> modId == null
                    ? Text.translatable("keybindprofilesplus.source.unknown")
                    : Text.translatable("keybindprofilesplus.source.unknown_hint", modId);
        };
    }

    /** Longer description for tooltips, e.g. "Fabric API (fabric-api)". */
    public Text description() {
        return kind == Kind.MOD ? Text.literal(name + " (" + modId + ")") : label();
    }

    public int color() {
        return switch (kind) {
            case VANILLA -> 0xFF9A9A9A;
            case MOD -> 0xFF7FD4FF;
            case EXTERNAL -> 0xFFFFB060;
            case UNKNOWN -> 0xFFE0C060;
        };
    }
}
