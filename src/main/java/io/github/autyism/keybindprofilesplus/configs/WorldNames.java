package io.github.autyism.keybindprofilesplus.configs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells settings apart from data a mod keeps per world or per server (map tiles, waypoints, schematic
 * placements, remembered containers): names of the worlds in {@code saves}, the servers in the server
 * list, and the shapes such names take in folders and files (server addresses, "Multiplayer_...",
 * dimension names, player and world ids) - also after a mod made a file name of a world's name
 * ("New World (1)" -> "local_New_World__1_"), and when one file holds the data of all worlds.
 */
public final class WorldNames {
    private static final Pattern IPV4 = Pattern.compile("(^|[^0-9])(\\d{1,3}\\.){3}\\d{1,3}($|[^0-9])");
    private static final String TLDS = "(?:com|net|org|cn|top|io|me|gg|xyz|de|uk|ru|fr|jp|kr|tw|hk|us|ca|au|nl|eu|info|biz|cc|tk|pw|"
            + "club|online|site|fun|pro|vip|world|space|games?|network|host|ml|ga|cf|gq|co|br|pl|it|es|se|no|fi|dk|be|ch|at|"
            + "cz|sk|hu|ro|ua|by|kz|in|sg|my|th|vn|ph|id|nz|za|mx|ar|cl|lt|lv|ee|is|ie|pt|gr|tr|il|ae|sa|ir|pk|bd|lk|mo|su|"
            + "moe|icu|cyou|ltd|link|live|store|shop|tech|app|dev|cloud|zone|today|plus|run|wtf|lol|xin|ink|wang|fans|cool)";
    private static final Pattern DOMAIN = Pattern.compile("(^|[^a-z0-9.-])((?:[a-z0-9-]+\\.)+" + TLDS + ")($|[^a-z0-9.-])");
    /** A whole text that is a server address: a domain or an IP address, maybe with a port. */
    private static final Pattern ADDRESS = Pattern.compile("^((?:[a-z0-9-]+\\.)+" + TLDS + "|(?:\\d{1,3}\\.){3}\\d{1,3})(:\\d{1,5})?$");
    private static final Pattern IP_ADDRESS = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?$");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern MARKER = Pattern.compile(
            "^(multiplayer|singleplayer|realms|mp|sp)[_ -].+|.*_dim_.*|^dim[%-]?-?\\d+$|.*[_ -]dim-?\\d+([_ .-].*)?|"
                    + "^(worlds|servers|multiplayer|singleplayer)$|.*per[_ -]?(world|server|dimension).*");
    /** How mods start the name of a world's or server's file or entry: "local_New_World", "server:play.example.org". */
    private static final Pattern WORLD_PREFIX = Pattern.compile("^(local|save|saves|world|sp|singleplayer|server|mp|multiplayer|realms|lan)([:_ -])(.+)$");
    /** A folder named like a dimension (Xaero's "DIM-1", "dim%0"): its parent folder is a world. */
    static final Pattern DIMENSION_FOLDER = Pattern.compile("(?i)^dim[%_-]?-?\\d+$");

    /** The names as they were given, to make more of these. */
    private final Set<String> worldNames;
    private final Set<String> serverNames;
    /** Lower-case forms a world's name takes in other names, longest first, with the world's name to show. */
    private final List<Map.Entry<String, String>> worldForms;
    /** Forms with (almost) no letters or digits left ("新的世界" as a file name is "____"): they only match a whole name. */
    private final Map<String, String> opaqueForms;
    /** Server hosts in lower case without the port, longest first. */
    private final List<String> hosts;

    public WorldNames(Set<String> worlds, Set<String> servers) {
        worldNames = Set.copyOf(worlds);
        serverNames = Set.copyOf(servers);
        Map<String, String> forms = new LinkedHashMap<>();
        Map<String, String> opaque = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (String world : worlds) {
            String shown = world.trim();
            String lower = shown.toLowerCase(Locale.ROOT);
            for (String form : List.of(lower, lower.replace(' ', '_'), fileNameForm(lower))) {
                if (form.length() < 2) {
                    continue;
                }
                if (form.length() >= 3 && lettersAndDigits(form) >= 3) {
                    forms.putIfAbsent(form, shown);
                } else if (opaque.containsKey(form) && !opaque.get(form).equals(shown)) {
                    ambiguous.add(form);
                } else {
                    opaque.putIfAbsent(form, shown);
                }
            }
        }
        ambiguous.forEach(opaque::remove);
        List<Map.Entry<String, String>> sortedForms = new ArrayList<>(forms.entrySet());
        sortedForms.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
        Set<String> h = new LinkedHashSet<>();
        for (String server : servers) {
            String lower = server.toLowerCase(Locale.ROOT).trim();
            if (lower.isEmpty()) {
                continue;
            }
            String host = lower.contains(":") && !lower.startsWith("[") ? lower.substring(0, lower.lastIndexOf(':')) : lower;
            if (host.length() >= 4 || host.equals("::1")) {
                h.add(host);
            }
        }
        h.add("localhost");
        List<String> sortedHosts = new ArrayList<>(h);
        sortedHosts.sort(Comparator.comparingInt(String::length).reversed());
        this.worldForms = List.copyOf(sortedForms);
        this.opaqueForms = Map.copyOf(opaque);
        this.hosts = List.copyOf(sortedHosts);
    }

    /** Worlds in the saves folder and servers in servers.dat of this game folder. */
    public static WorldNames of(Path gameDir) {
        Set<String> worlds = new LinkedHashSet<>();
        Path saves = gameDir.resolve("saves");
        if (Files.isDirectory(saves)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(saves)) {
                for (Path world : stream) {
                    if (Files.isDirectory(world)) {
                        worlds.add(world.getFileName().toString());
                    }
                }
            } catch (IOException ignored) {
                // No worlds known: only the shapes of names are used.
            }
        }
        Set<String> servers = new LinkedHashSet<>();
        Path serverList = gameDir.resolve("servers.dat");
        if (Files.isRegularFile(serverList)) {
            try {
                String[] lastName = new String[1];
                NbtReader.walk(Files.readAllBytes(serverList), (name, text) -> {
                    if (!name.isEmpty()) {
                        lastName[0] = name;
                    }
                    if (text != null && "ip".equals(name.isEmpty() ? lastName[0] : name)) {
                        servers.add(text);
                    }
                });
            } catch (IOException ignored) {
                // Same as above.
            }
        }
        return new WorldNames(worlds, servers);
    }

    /**
     * Why a path (relative, '/' separated) is per-world or per-server data - the world, server or
     * kind of name found - or null when nothing in it points to a world or a server.
     */
    public String perWorldReason(String path) {
        String known = knownWorld(path);
        if (known != null) {
            return known;
        }
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            String name = i == parts.length - 1 ? ConfigRules.stem(part) : part;
            if (MARKER.matcher(name).matches()) {
                return markerReason(name);
            }
        }
        return null;
    }

    /** Like {@link #perWorldReason}, but only a world or server named in the path, not a kind of name ("worlds", "dim%0"). */
    public String knownWorld(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            String reason = specificReason(i == parts.length - 1 ? ConfigRules.stem(part) : part);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    /** The same test for one name (a folder, or a file without its extension). */
    public String reasonFor(String nameLower) {
        String reason = specificReason(nameLower);
        return reason != null ? reason : MARKER.matcher(nameLower).matches() ? markerReason(nameLower) : null;
    }

    /**
     * The worlds or servers a JSON file holds data for when the whole file is one map from world or
     * server to data (the waypoints of all worlds in one file: {@code {"server:play.example.org": [...],
     * "save:New World": [...]}}), else null. A settings file that has such a map somewhere inside is
     * settings.
     */
    public String perWorldByContent(String fileName, byte[] data) {
        String extension = ConfigRules.extension(fileName);
        if (!extension.equals("json") && !extension.equals("json5") && !extension.equals("jsonc")) {
            return null;
        }
        List<String> keys = topLevelKeys(new String(data, StandardCharsets.UTF_8));
        if (keys.isEmpty()) {
            return null;
        }
        Set<String> found = new LinkedHashSet<>();
        int sure = 0;
        int addresses = 0;
        for (String key : keys) {
            String lower = key.toLowerCase(Locale.ROOT).trim();
            String world = keyWorld(lower);
            if (world != null) {
                sure++;
                found.add(world);
            } else if (ADDRESS.matcher(lower).matches()) {
                // A lone "general.info" could be a setting; several addresses are a map of servers.
                addresses++;
                found.add(lower);
            }
        }
        if ((sure + addresses) * 2 < keys.size() || (sure == 0 && addresses < 2)) {
            return null;
        }
        List<String> names = new ArrayList<>(found);
        String shown = String.join(", ", names.subList(0, Math.min(2, names.size())));
        return names.size() > 2 ? shown + " +" + (names.size() - 2) : shown;
    }

    /** A key that names a world or server: "server:x" / "save:x", "local_" plus a known world, a known world or server, an IP address. */
    private String keyWorld(String lower) {
        Matcher prefixed = WORLD_PREFIX.matcher(lower);
        if (prefixed.matches()) {
            String rest = prefixed.group(3).trim();
            String known = known(rest);
            if (known != null) {
                return known;
            }
            // "server:anything" is clearly about a server; "world_border" is not about a world.
            if (prefixed.group(2).equals(":") && !rest.isEmpty()) {
                return rest;
            }
            return ADDRESS.matcher(rest).matches() ? rest : null;
        }
        String known = known(lower);
        if (known != null) {
            return known;
        }
        return IP_ADDRESS.matcher(lower).matches() ? lower : null;
    }

    /** The world or server this whole name is (in any of its forms), or null. */
    private String known(String lower) {
        String host = lower.contains(":") && !lower.startsWith("[") ? lower.substring(0, lower.lastIndexOf(':')) : lower;
        if (hosts.contains(host)) {
            return host;
        }
        for (Map.Entry<String, String> form : worldForms) {
            if (form.getKey().equals(lower)) {
                return form.getValue();
            }
        }
        return opaqueForms.get(lower);
    }

    /** The top-level keys of a JSON (or JSON5) object, or nothing when the text is not an object. */
    static List<String> topLevelKeys(String text) {
        List<String> keys = new ArrayList<>();
        int depth = 0;
        boolean expectKey = false;
        int n = text.length();
        for (int i = 0; i < n && keys.size() <= 10_000; i++) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < n && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
                boolean line = text.charAt(i + 1) == '/';
                int end = line ? text.indexOf('\n', i) : text.indexOf("*/", i + 2);
                if (end < 0) {
                    break;
                }
                i = line ? end : end + 1;
                continue;
            }
            if (c == '"' || c == '\'') {
                StringBuilder value = new StringBuilder();
                int j = i + 1;
                while (j < n && text.charAt(j) != c) {
                    if (text.charAt(j) == '\\' && j + 1 < n) {
                        j++;
                    }
                    value.append(text.charAt(j));
                    j++;
                }
                if (depth == 1 && expectKey) {
                    keys.add(value.toString());
                }
                expectKey = false;
                i = j;
                continue;
            }
            switch (c) {
                case '{', '[' -> {
                    depth++;
                    if (depth == 1 && c == '[') {
                        return List.of();
                    }
                    expectKey = depth == 1;
                }
                case '}', ']' -> {
                    depth--;
                    if (depth <= 0) {
                        return keys;
                    }
                    expectKey = false;
                }
                case ',' -> expectKey = depth == 1;
                default -> {
                    // JSON5 allows keys without quotes.
                    if (depth == 1 && expectKey && (Character.isLetter(c) || c == '_' || c == '$')) {
                        int j = i;
                        while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || "_$-.".indexOf(text.charAt(j)) >= 0)) {
                            j++;
                        }
                        keys.add(text.substring(i, j));
                        expectKey = false;
                        i = j - 1;
                    }
                }
            }
        }
        return depth == 0 ? keys : List.of();
    }

    /** A known server or world, an address or an id in the name. */
    private String specificReason(String nameLower) {
        for (String host : hosts) {
            if (containsToken(nameLower, host)) {
                return host;
            }
        }
        for (Map.Entry<String, String> form : worldForms) {
            if (containsToken(nameLower, form.getKey())) {
                return form.getValue();
            }
        }
        Matcher prefixed = WORLD_PREFIX.matcher(nameLower);
        if (prefixed.matches() && opaqueForms.containsKey(prefixed.group(3))) {
            return opaqueForms.get(prefixed.group(3));
        }
        Matcher ip = IPV4.matcher(nameLower);
        if (ip.find()) {
            return ip.group().replaceAll("^[^0-9]|[^0-9]$", "");
        }
        Matcher domain = DOMAIN.matcher(nameLower);
        if (domain.find()) {
            return domain.group(2);
        }
        if (UUID.matcher(nameLower).find()) {
            return "id";
        }
        return null;
    }

    /** What to show for a name that has the shape of per-world data ("x_dim_overworld" -> "x"). */
    private static String markerReason(String name) {
        int dim = name.indexOf("_dim_");
        return dim > 0 ? name.substring(0, dim) : name;
    }

    /** These names plus more worlds and servers found elsewhere (in the data itself). */
    public WorldNames with(Set<String> moreWorlds) {
        Set<String> all = new LinkedHashSet<>(worldNames);
        all.addAll(moreWorlds);
        return new WorldNames(all, serverNames);
    }

    /** How QoL-style mods turn a world's name into a file name: everything but a-z, 0-9, '.', '_' and '-' becomes '_'. */
    static String fileNameForm(String lower) {
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-' ? c : '_');
        }
        return out.toString();
    }

    private static int lettersAndDigits(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /** True when {@code token} appears in {@code text} and is not glued to letters or digits on either side. */
    static boolean containsToken(String text, String token) {
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return false;
            }
            int end = at + token.length();
            boolean startOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = at + 1;
        }
    }
}
