package io.github.autyism.keybindprofilesplus.external;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

/**
 * A hotkey that another mod manages on its own, outside the game's key binding system (a Meteor
 * module, a malilib hotkey). Read from the running mod when it is loaded - then it can also be
 * changed, see {@link ExternalKeys#set} - or else from that mod's config file (read-only).
 *
 * @param sourceId     "meteor" or the malilib mod's id
 * @param group        heading it is listed under ("Meteor", "Meteor profile: pvp", "Litematica")
 * @param name         what the hotkey does, readable ("Auto Totem"); also part of {@link #id()}
 * @param title        what the hotkey is called in the player's language (the mod's own translation), for display
 * @param modifiers    Ctrl / Shift / Alt bits that must be held with {@code key}
 * @param key          the main key, or null when the hotkey is unbound or a chord of several ordinary keys
 * @param keyText      the whole trigger as shown to the player
 * @param when         when the hotkey does something, as far as its settings and what is known about the mod tell
 * @param active       false for hotkeys of a configuration that is not in use (other Meteor profiles)
 * @param file         the config file it is saved in, relative to the game directory
 * @param hotkeyId     id under which it is changed and saved in profiles ({@code ext:...}); null when read-only
 * @param value        its trigger in the form profiles store (see {@link ExternalKeys}); null when read-only
 * @param defaultValue the trigger the mod gives it out of the box; null when read-only or not known
 */
public record ExternalBinding(String sourceId, Component group, String name, Component title, int modifiers, InputConstants.Key key, Component keyText,
                              When when, boolean active, String file, String hotkeyId, String value, String defaultValue) {
    /** When a hotkey of another mod does something. */
    public enum When {
        /** During play, but not while a screen is open (what Meteor modules and most malilib hotkeys do). */
        IN_GAME,
        /** During play and inside screens alike. */
        ANYWHERE,
        /** Only while a screen (an inventory...) is open. */
        SCREEN_ONLY,
        /**
         * Only in a special situation the mod sets up itself: while its tool item is held, while one
         * of its modes is on, or as a key that is held together with something else. Such a hotkey
         * shares its key with ordinary ones on purpose (Litematica's tool uses the mouse buttons).
         */
        SITUATIONAL
    }

    /** A hotkey only known from a config file: shown and compared against, never changed. */
    public static ExternalBinding readOnly(String sourceId, Component group, String name, int modifiers, InputConstants.Key key, Component keyText,
                                           When when, boolean active, String file) {
        return new ExternalBinding(sourceId, group, name, Component.literal(name), modifiers, key, keyText, when, active, file, null, null, null);
    }

    /** Whether it can be rebound from this mod (the other mod is running and was reached). */
    public boolean editable() {
        return hotkeyId != null;
    }

    /** Whether it has no key at all. */
    public boolean unbound() {
        return editable() && ExternalKeys.isUnboundValue(value);
    }

    /** Whether it is set to something else than the mod's own default. */
    public boolean changed() {
        return editable() && defaultValue != null && !defaultValue.equals(value);
    }

    /** Label for lists and conflict messages: "Auto Totem [Meteor]". */
    public Component label() {
        return title.copy().append(" [").append(group).append("]");
    }

    /** Stable id, also the name its "when does it work" setting is stored under. */
    public String id() {
        return sourceId + ":" + name;
    }
}
