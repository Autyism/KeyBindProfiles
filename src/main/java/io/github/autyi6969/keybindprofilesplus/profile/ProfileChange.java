package io.github.autyi6969.keybindprofilesplus.profile;

import net.minecraft.text.Text;

/**
 * One thing that applying a profile would change: a key binding, a hotkey of another mod (Meteor,
 * malilib) or another game setting.
 *
 * @param id   the key binding id, the other mod's hotkey id ({@code ext:...}) or the options.txt name
 * @param name what the player knows it as
 * @param from the current value, formatted for display
 * @param to   the value stored in the profile, formatted for display
 */
public record ProfileChange(Kind kind, String id, Text name, Text from, Text to) {
    public enum Kind {
        KEY_BINDING,
        EXTERNAL,
        OPTION
    }
}
