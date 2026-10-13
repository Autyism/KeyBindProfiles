package io.github.autyism.keybindprofilesplus.external;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.keys.KeyContext;
import io.github.autyism.keybindprofilesplus.keys.KeyTrigger;
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
 * @param trigger      everything that must be pressed, with the mod's own rules on further keys held; null when it has no key
 * @param keyText      the whole trigger as shown to the player
 * @param context      where it does something, as far as its settings and what is known about its mod tell
 * @param active       false for hotkeys of a configuration that is not in use (other Meteor profiles)
 * @param file         the config file it is saved in, relative to the game directory
 * @param hotkeyId     id under which it is changed and saved in profiles ({@code ext:...}); null when read-only
 * @param value        its trigger in the form profiles store (see {@link ExternalKeys}); null when read-only
 * @param defaultValue the trigger the mod gives it out of the box; null when read-only or not known
 */
public record ExternalBinding(String sourceId, Component group, String name, Component title, int modifiers, InputConstants.Key key,
                              KeyTrigger trigger, Component keyText, KeyContext context, boolean active, String file, String hotkeyId,
                              String value, String defaultValue) {
    /** A hotkey only known from a config file: shown and compared against, never changed. */
    public static ExternalBinding readOnly(String sourceId, Component group, String name, int modifiers, InputConstants.Key key,
                                           KeyTrigger trigger, Component keyText, KeyContext context, boolean active, String file) {
        return new ExternalBinding(sourceId, group, name, Component.literal(name), modifiers, key, trigger, keyText, context, active, file,
                null, null, null);
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

    /** Stable id, also the name its "where does it act" setting is stored under. */
    public String id() {
        return sourceId + ":" + name;
    }
}
