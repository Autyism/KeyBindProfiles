package io.github.autyism.keybindprofilesplus.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * Decides which profile belongs to the place the player has just joined.
 *
 * <p>A profile lists rules. A rule is one of:
 * <ul>
 *   <li>{@code singleplayer}, {@code lan}, {@code realms} - that kind of world;</li>
 *   <li>{@code play.example.org:25566} - exactly that address;</li>
 *   <li>{@code example.org} - that host on any port, and its subdomains;</li>
 *   <li>{@code *.example.org}, {@code *survival*} - a wildcard on the address;</li>
 *   <li>{@code *} - anywhere at all (handy for a default profile).</li>
 * </ul>
 * When several rules match, the most specific one wins (exact address, then host, then subdomain,
 * then wildcards by how much fixed text they contain, then {@code *}); ties go to the profile
 * whose name sorts first.
 */
public final class ServerProfileMatcher {
    public static final String SINGLEPLAYER = "singleplayer";
    public static final String LAN = "lan";
    public static final String REALMS = "realms";
    private static final int DEFAULT_PORT = 25565;

    private ServerProfileMatcher() {
    }

    public enum Kind {
        SINGLEPLAYER,
        LAN,
        REALMS,
        SERVER
    }

    /** Where the player is. {@code address} is null for singleplayer. */
    public record Location(Kind kind, String address) {
        public static Location singleplayer() {
            return new Location(Kind.SINGLEPLAYER, null);
        }

        public static Location server(String address) {
            return new Location(Kind.SERVER, address);
        }

        /** Stable text identifying this location, used to notice when it changes. */
        public String key() {
            return kind + "|" + (address == null ? "" : normalizeAddress(address));
        }

        public Component describe() {
            return switch (kind) {
                case SINGLEPLAYER -> Component.translatable("keybindprofilesplus.server.kind.singleplayer");
                case LAN -> Component.translatable("keybindprofilesplus.server.kind.lan", address);
                case REALMS -> Component.translatable("keybindprofilesplus.server.kind.realms");
                case SERVER -> Component.literal(address);
            };
        }
    }

    /** The winning rule for a location. */
    public record Match(String profile, String rule, int score) {
    }

    /** Where the client currently is, or null when it is not in a world. */
    public static Location currentLocation(Minecraft client) {
        if (client.player == null || client.level == null) {
            return null;
        }
        if (client.isLocalServer()) {
            return Location.singleplayer();
        }

        ServerData serverInfo = client.getCurrentServer();
        if (serverInfo == null || serverInfo.ip == null || serverInfo.ip.isBlank()) {
            return null;
        }
        Kind kind = serverInfo.isRealm() ? Kind.REALMS : serverInfo.isLan() ? Kind.LAN : Kind.SERVER;
        return new Location(kind, serverInfo.ip);
    }

    public static Match findBestMatch(Location location, Map<String, List<String>> rulesByProfile, Set<String> existingProfiles) {
        if (location == null) {
            return null;
        }

        List<String> profileNames = new ArrayList<>(rulesByProfile.keySet());
        profileNames.sort(String.CASE_INSENSITIVE_ORDER);
        Match best = null;
        for (String profileName : profileNames) {
            List<String> rules = rulesByProfile.get(profileName);
            if (rules == null || !existingProfiles.contains(profileName)) {
                continue;
            }
            for (String rule : rules) {
                int score = score(location, rule);
                if (score > 0 && (best == null || score > best.score())) {
                    best = new Match(profileName, rule, score);
                }
            }
        }
        return best;
    }

    /** How well a rule fits a location: 0 means it does not match, higher means more specific. */
    public static int score(Location location, String rule) {
        String pattern = normalizeRule(rule);
        if (pattern.isEmpty() || location == null) {
            return 0;
        }

        switch (pattern) {
            case "*":
                return 100;
            case SINGLEPLAYER:
                return location.kind() == Kind.SINGLEPLAYER ? 1000 : 0;
            case LAN:
                return location.kind() == Kind.LAN ? 1000 : 0;
            case REALMS:
                return location.kind() == Kind.REALMS ? 1000 : 0;
            default:
                break;
        }
        if (location.address() == null) {
            return 0;
        }

        Address server = Address.parse(location.address());
        if (pattern.indexOf('*') >= 0) {
            String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
            if (server.host().matches(regex) || server.withPort().matches(regex)) {
                return 110 + Math.min(390, pattern.replace("*", "").length());
            }
            return 0;
        }

        Address expected = Address.parse(pattern);
        if (expected.host().isEmpty()) {
            return 0;
        }
        boolean portMatters = expected.explicitPort();
        if (portMatters && expected.port() != server.port()) {
            return 0;
        }
        if (server.host().equals(expected.host())) {
            return portMatters ? 900 : 800;
        }
        if (!portMatters && server.host().endsWith("." + expected.host())) {
            return 600 + Math.min(99, expected.host().length());
        }
        return 0;
    }

    /** Lower-case, without scheme or path; used to compare rules and spot duplicates. */
    public static String normalizeRule(String rule) {
        return rule == null ? "" : normalizeAddress(rule);
    }

    /** Translation key suffix explaining what kind of rule this is (keybindprofilesplus.server.rule.*). */
    public static String ruleKind(String rule) {
        String pattern = normalizeRule(rule);
        if (pattern.equals("*")) {
            return "anywhere";
        }
        if (pattern.equals(SINGLEPLAYER) || pattern.equals(LAN) || pattern.equals(REALMS)) {
            return pattern;
        }
        if (pattern.indexOf('*') >= 0) {
            return "wildcard";
        }
        return Address.parse(pattern).explicitPort() ? "exact" : "host";
    }

    /** Returns a translation key describing what is wrong with a rule, or null when it is fine. */
    public static String validate(String rule) {
        if (rule == null || rule.isBlank()) {
            return "keybindprofilesplus.status.server_required";
        }
        String pattern = normalizeRule(rule);
        if (pattern.isEmpty() || pattern.chars().anyMatch(Character::isWhitespace)) {
            return "keybindprofilesplus.status.server_invalid";
        }
        return null;
    }

    /** Profiles other than {@code exceptProfile} that already use the same rule. */
    public static List<String> profilesUsingRule(String rule, Map<String, List<String>> rulesByProfile, String exceptProfile) {
        String pattern = normalizeRule(rule);
        List<String> users = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : rulesByProfile.entrySet()) {
            if (entry.getKey().equals(exceptProfile) || entry.getValue() == null) {
                continue;
            }
            for (String other : entry.getValue()) {
                if (normalizeRule(other).equals(pattern)) {
                    users.add(entry.getKey());
                    break;
                }
            }
        }
        users.sort(String.CASE_INSENSITIVE_ORDER);
        return users;
    }

    private static String normalizeAddress(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceFirst("^[a-z]+://", "");
        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }
        return normalized;
    }

    /** A host with an optional port. Understands [IPv6]:port and bare IPv6 addresses. */
    private record Address(String host, int port, boolean explicitPort) {
        static Address parse(String value) {
            String text = normalizeAddress(value);
            String host = text;
            String portText = null;

            if (text.startsWith("[")) {
                int end = text.indexOf(']');
                if (end > 0) {
                    host = text.substring(1, end);
                    if (end + 1 < text.length() && text.charAt(end + 1) == ':') {
                        portText = text.substring(end + 2);
                    }
                }
            } else {
                int colon = text.lastIndexOf(':');
                // More than one colon without brackets is a bare IPv6 address, not host:port.
                if (colon > 0 && text.indexOf(':') == colon) {
                    host = text.substring(0, colon);
                    portText = text.substring(colon + 1);
                }
            }

            while (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }

            int port = DEFAULT_PORT;
            boolean explicit = false;
            if (portText != null && !portText.isEmpty()) {
                try {
                    port = Integer.parseInt(portText);
                    explicit = true;
                } catch (NumberFormatException e) {
                    // Not a number: treat the whole thing as a host so a typo never matches by accident.
                    host = text;
                }
            }
            return new Address(host, port, explicit);
        }

        String withPort() {
            return host + ":" + port;
        }
    }
}
