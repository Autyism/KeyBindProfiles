package io.github.autyism.keybindprofilesplus.configs;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A mod as far as config files are concerned: its id, name and version, the other ids it provides,
 * whether it is installed (or only lying in the mods folder switched off), and what its code says
 * about file names.
 */
public record ModInfo(String id, String name, String version, Set<String> aliases, boolean installed, BytecodeEvidence.Evidence evidence,
                      String parentId) {
    public ModInfo {
        Set<String> all = new LinkedHashSet<>();
        all.add(id.toLowerCase(Locale.ROOT));
        aliases.forEach(alias -> all.add(alias.toLowerCase(Locale.ROOT)));
        aliases = Set.copyOf(all);
        evidence = evidence == null ? BytecodeEvidence.Evidence.EMPTY : evidence;
        name = name == null || name.isBlank() ? id : name;
        version = version == null ? "" : version;
    }

    /** The mod this one is packed inside (a library or module shipped in another mod's jar), or its own id. */
    public String rootId() {
        return parentId == null ? id : parentId;
    }
}
