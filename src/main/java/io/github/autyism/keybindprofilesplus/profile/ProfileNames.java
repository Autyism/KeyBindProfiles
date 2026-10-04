package io.github.autyism.keybindprofilesplus.profile;

import java.util.Locale;
import java.util.Set;

/** Rules for profile names. A profile is stored as "<name>.kbp", so the name must be a valid file name. */
public final class ProfileNames {
    public static final int MAX_LENGTH = 32;
    private static final String FORBIDDEN_CHARACTERS = "\\/:*?\"<>|";
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private ProfileNames() {
    }

    /** Returns the translation key of what is wrong with the name, or null when it is usable. */
    public static String validate(String name) {
        if (name == null || name.isBlank()) {
            return "keybindprofilesplus.status.profile_name_required";
        }
        if (name.length() > MAX_LENGTH) {
            return "keybindprofilesplus.status.profile_name_too_long";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || FORBIDDEN_CHARACTERS.indexOf(c) >= 0) {
                return "keybindprofilesplus.status.profile_name_invalid";
            }
        }
        if (name.endsWith(".") || name.endsWith(" ") || name.startsWith(" ") || name.startsWith(".")
                || RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
            return "keybindprofilesplus.status.profile_name_invalid";
        }
        return null;
    }

    /** A name based on {@code wanted} that no existing profile has: "name", "name (2)", "name (3)"... */
    public static String firstFree(String wanted, Set<String> existing) {
        if (!containsIgnoreCase(existing, wanted)) {
            return wanted;
        }
        for (int i = 2; ; i++) {
            String suffix = " (" + i + ")";
            String base = wanted.length() + suffix.length() > MAX_LENGTH ? wanted.substring(0, MAX_LENGTH - suffix.length()) : wanted;
            String candidate = base + suffix;
            if (!containsIgnoreCase(existing, candidate)) {
                return candidate;
            }
        }
    }

    /** Windows file names ignore case, so "Test" and "test" would be the same file. */
    public static boolean containsIgnoreCase(Set<String> existing, String name) {
        for (String other : existing) {
            if (other.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
