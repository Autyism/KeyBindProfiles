package io.github.autyism.keybindprofilesplus.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Long-press keys of Accurate Hotkeys, when it is installed: each control can have a second key that works when held
 * (Accurate Hotkeys keeps them and does the holding). The Key Binds screen shows a ⇄ button that turns a binding over to
 * that key, like a card. Reached through Accurate Hotkeys' public methods by name, so this mod never needs it.
 */
public final class HoldBindsBridge {
    public static final int BACK = 0xFFB86A12;
    public static final int BACK_DARK = 0xFF5A3208;
    private static final long FLIP_MILLIS = 220L;
    private static final Set<String> FLIPPED = new HashSet<>();
    private static final Map<String, Long> FLIP_START = new HashMap<>();
    private static Method get, set, holdMillis, describe;
    private static Boolean available;

    private HoldBindsBridge() {
    }

    public static boolean available() {
        if (available == null) {
            available = false;
            if (FabricLoader.getInstance().isModLoaded("accurate-hotkeys")) {
                try {
                    Class<?> c = Class.forName("com.autyism.accuratehotkeys.hold.HoldBinds");
                    get = c.getMethod("get", String.class);
                    set = c.getMethod("set", String.class, String.class);
                    holdMillis = c.getMethod("holdMillis");
                    describe = c.getMethod("describe", String.class);
                    available = true;
                } catch (ReflectiveOperationException | LinkageError e) {
                    // an older Accurate Hotkeys without long presses
                }
            }
        }
        return available;
    }

    public static String get(String control) {
        try {
            return available() ? (String) get.invoke(null, control) : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static void set(String control, String combo) {
        try {
            if (available()) set.invoke(null, control, combo);
        } catch (ReflectiveOperationException ignored) {
            // nothing to set
        }
    }

    public static long holdMillis() {
        try {
            return available() ? (long) holdMillis.invoke(null) : 3000L;
        } catch (ReflectiveOperationException e) {
            return 3000L;
        }
    }

    /** "⏱ 3 s: Ctrl + G" */
    public static Component label(String control) {
        String combo = get(control);
        Component keys = Component.literal("-");
        if (combo != null) {
            try {
                keys = (Component) describe.invoke(null, combo);
            } catch (ReflectiveOperationException e) {
                keys = Component.literal(combo);
            }
        }
        double seconds = holdMillis() / 1000.0;
        return Component.translatable("keybindprofilesplus.hold.button", seconds == Math.rint(seconds) ? String.format("%.0f", seconds) : String.format("%.1f", seconds), keys);
    }

    public static boolean isFlipped(String control) {
        return FLIPPED.contains(control);
    }

    public static void flip(String control) {
        if (!FLIPPED.remove(control)) FLIPPED.add(control);
        FLIP_START.put(control, Util.getMillis());
    }

    /** 0..1 while the card turns, else -1 */
    public static float turning(String control) {
        Long start = FLIP_START.get(control);
        if (start == null) return -1F;
        float t = (Util.getMillis() - start) / (float) FLIP_MILLIS;
        if (t >= 1F) {
            FLIP_START.remove(control);
            return -1F;
        }
        return t;
    }
}
