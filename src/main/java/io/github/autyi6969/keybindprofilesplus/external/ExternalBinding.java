package io.github.autyi6969.keybindprofilesplus.external;

import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/**
 * A hotkey that another mod manages on its own, outside the game's key binding system (a Meteor
 * module, a malilib hotkey). It was read from that mod's config file and is only ever shown and
 * compared against; nothing here can change it.
 *
 * @param sourceId   "meteor" or the malilib mod's id
 * @param group      heading it is listed under ("Meteor", "Meteor profile: pvp", "Litematica")
 * @param name       what the hotkey does, readable ("Auto Totem")
 * @param modifiers  Ctrl / Shift / Alt bits that must be held with {@code key}
 * @param key        the main key, or null when the hotkey is a chord of several ordinary keys
 * @param keyText    the whole trigger as shown to the player
 * @param screenOnly true when the mod says the hotkey only works while a screen is open
 * @param active     false for hotkeys of a configuration that is not in use (other Meteor profiles)
 * @param file       the config file it was read from, relative to the game directory
 */
public record ExternalBinding(String sourceId, Text group, String name, int modifiers, InputUtil.Key key, Text keyText,
                              boolean screenOnly, boolean active, String file) {
    /** Label for lists and conflict messages: "Auto Totem [Meteor]". */
    public Text label() {
        return Text.literal(name + " [").append(group).append("]");
    }
}
