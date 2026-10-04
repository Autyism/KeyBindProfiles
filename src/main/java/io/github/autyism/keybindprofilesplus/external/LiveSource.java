package io.github.autyism.keybindprofilesplus.external;

import net.minecraft.client.util.InputUtil;

import java.util.List;

/**
 * The hotkeys of another mod as that mod has them right now, reached while it runs - so they can
 * be changed, not only read. Every change goes through the mod's own objects and is written by the
 * mod's own save code; this mod never writes another mod's file itself.
 */
public interface LiveSource {
    /** Whether the mod is running and its hotkeys could be reached (false before it finished starting). */
    boolean available();

    /** Whether the hotkeys of Meteor's main configuration ({@code modules.nbt}) come from here. */
    default boolean coversMeteor() {
        return false;
    }

    /** Whether the hotkeys of malilib mods come from here. */
    default boolean coversMalilib() {
        return false;
    }

    /** Every hotkey, bound or not. */
    List<ExternalBinding> list();

    /** Whether a {@link ExternalBinding#hotkeyId()} belongs to this source. */
    boolean owns(String hotkeyId);

    /**
     * Binds a hotkey to a key plus Ctrl / Shift / Alt ({@link InputUtil#UNKNOWN_KEY} = no key).
     * False when the hotkey is unknown or the mod does not accept that key for it.
     */
    boolean bind(String hotkeyId, InputUtil.Key key, int modifiers);

    /** Sets a hotkey to a value as {@link ExternalBinding#value()} gives it (from a profile). False if that did not work. */
    boolean setValue(String hotkeyId, String value);

    /** Lets the mod write everything changed since the last call to its config files, the way its own screens do. */
    void save();
}
