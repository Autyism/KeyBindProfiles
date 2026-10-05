package io.github.autyism.keybindprofilesplus.configs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells settings apart from data a mod keeps per world or per server (map tiles, waypoints, schematic
 * placements, remembered containers): names of the worlds in {@code saves}, the servers in the server
 * list, and the shapes such names take in folders and files (server addresses, "Multiplayer_...",
 * dimension names, player and world ids).
 */
public final class WorldNames {
    private static final Pattern IPV4 = Pattern.compile("(^|[^0-9])(\\d{1,3}\\.){3}\\d{1,3}($|[^0-9])");
    private static final Pattern DOMAIN = Pattern.compile(
            "(^|[^a-z0-9.-])((?:[a-z0-9-]+\\.)+(?:com|net|org|cn|top|io|me|gg|xyz|de|uk|ru|fr|jp|kr|tw|hk|us|ca|au|nl|eu|info|biz|cc|tk|pw|"
                    + "club|online|site|fun|pro|vip|world|space|games?|network|host|ml|ga|cf|gq|co|br|pl|it|es|se|no|fi|dk|be|ch|at|"
                    + "cz|sk|hu|ro|ua|by|kz|in|sg|my|th|vn|ph|id|nz|za|mx|ar|cl|lt|lv|ee|is|ie|pt|gr|tr|il|ae|sa|ir|pk|bd|lk|mo|su|"
                    + "moe|icu|cyou|ltd|link|live|store|shop|tech|app|dev|cloud|zone|today|plus|run|wtf|lol|xin|ink|wang|fans|cool))"
                    + "($|[^a-z0-9.-])");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern MARKER = Pattern.compile(
            "^(multiplayer|singleplayer|realms|mp|sp)[_ -].+|.*_dim_.*|^dim[%-]?-?\\d+$|.*[_ -]dim-?\\d+([_ .-].*)?|"
                    + "^(worlds|servers|multiplayer|singleplayer)$|.*per[_ -]?(world|server|dimension).*");
    /** A folder named like a dimension (Xaero's "DIM-1", "dim%0"): its parent folder is a world. */
    static final Pattern DIMENSION_FOLDER = Pattern.compile("(?i)^dim[%_-]?-?\\d+$");

    private final Set<String> worlds;
    private final Set<String> hosts;

    public WorldNames(Set<String> worlds, Set<String> servers) {
        Set<String> w = new LinkedHashSet<>();
        for (String world : worlds) {
            String lower = world.toLowerCase(Locale.ROOT).trim();
            if (lower.length() >= 3) {
                w.add(lower);
                w.add(lower.replace(' ', '_'));
            }
        }
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
        this.worlds = Set.copyOf(w);
        this.hosts = Set.copyOf(h);
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
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            String reason = specificReason(i == parts.length - 1 ? ConfigRules.stem(part) : part);
            if (reason != null) {
                return reason;
            }
        }
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            String name = i == parts.length - 1 ? ConfigRules.stem(part) : part;
            if (MARKER.matcher(name).matches()) {
                return markerReason(name);
            }
        }
        return null;
    }

    /** The same test for one name (a folder, or a file without its extension). */
    public String reasonFor(String nameLower) {
        String reason = specificReason(nameLower);
        return reason != null ? reason : MARKER.matcher(nameLower).matches() ? markerReason(nameLower) : null;
    }

    /** A known server or world, an address or an id in the name. */
    private String specificReason(String nameLower) {
        for (String host : hosts) {
            if (containsToken(nameLower, host)) {
                return host;
            }
        }
        for (String world : worlds) {
            if (containsToken(nameLower, world)) {
                return world;
            }
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
        Set<String> all = new LinkedHashSet<>(worlds);
        all.addAll(moreWorlds);
        return new WorldNames(all, hosts);
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
