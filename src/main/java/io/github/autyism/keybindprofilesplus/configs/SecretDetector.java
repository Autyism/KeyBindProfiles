package io.github.autyism.keybindprofilesplus.configs;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises files that hold login data (account tokens, passwords, session cookies, API keys),
 * by name and by content. Such files are never exported and never imported: a share file must not
 * hand over somebody's accounts, and an import must not replace somebody's logins.
 *
 * <p>By content it looks for a key that names a secret together with a value that looks like one: a
 * password key with any value, a token / cookie / key with a long random-looking value, or a value
 * that is itself a token (a JWT or a Microsoft refresh token), in JSON, TOML, properties, YAML and
 * NBT files alike. A setting that merely mentions such a word ("showTokenCount": true,
 * "tokenColor": "#ffffff") is not a secret.</p>
 */
public final class SecretDetector {
    /** Parts of a file name that mean "accounts or logins in here". */
    private static final Pattern SECRET_NAME = Pattern.compile(
            "account|token|session|passw|secret|credential|cookie|oauth|(^|[^a-z])auth($|[^o])|login|proxies|proxy|keystore|"
                    + "privatekey|private_key|apikey|api_key|(^|[^a-z])alts?($|[^a-z])|(^|[^a-z])msa($|[^a-z])");

    /** Keys whose value is secret whatever it looks like. */
    private static final Pattern ALWAYS_SECRET_KEY = Pattern.compile("passw|secret|credential|private_?key");
    /** Keys whose value is secret when it looks like a token. */
    private static final Pattern TOKEN_KEY = Pattern.compile("token|cookie|api_?key|access_?key|session_?id|refresh|bearer|auth_?code|client_?secret");

    private static final Pattern JSON_STRING_PAIR = Pattern.compile("\"([^\"\\\\]{1,80})\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){1,4096})\"");
    private static final Pattern LINE_PAIR = Pattern.compile("(?m)^\\s*([A-Za-z0-9_.\\-\\[\\]\"']{1,80})\\s*[=:]\\s*\"?([^\"\\r\\n#]{1,4096})\"?\\s*$");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern MS_REFRESH_TOKEN = Pattern.compile("\\bM\\.[A-Za-z0-9]{2,8}_[A-Za-z0-9!*$_.\\-]{40,}");

    private SecretDetector() {
    }

    /** Why the path itself says "secret" (the matched word), or null. Only the file name is looked at. */
    public static String secretName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        Matcher matcher = SECRET_NAME.matcher(name);
        return matcher.find() ? matcher.group().replaceAll("[^a-z_]", "") : null;
    }

    /** The key (or "token") that makes the content secret, or null when nothing secret was found. */
    public static String secretContent(String fileName, byte[] data) {
        if (ConfigRules.extension(fileName).equals("nbt")) {
            String[] found = new String[1];
            String[] lastName = new String[1];
            NbtReader.walk(data, (name, text) -> {
                if (found[0] != null) {
                    return;
                }
                if (!name.isEmpty()) {
                    lastName[0] = name;
                }
                if (text != null) {
                    String key = name.isEmpty() && lastName[0] != null ? lastName[0] : name;
                    if (isSecretPair(key, text)) {
                        found[0] = key.isEmpty() ? "token" : key;
                    }
                }
            });
            return found[0];
        }
        String text = new String(data, StandardCharsets.UTF_8);
        Matcher json = JSON_STRING_PAIR.matcher(text);
        while (json.find()) {
            if (isSecretPair(json.group(1), json.group(2))) {
                return json.group(1);
            }
        }
        Matcher line = LINE_PAIR.matcher(text);
        while (line.find()) {
            if (isSecretPair(line.group(1).replaceAll("[\"'\\[\\]]", ""), line.group(2).trim())) {
                return line.group(1);
            }
        }
        if (JWT.matcher(text).find() || MS_REFRESH_TOKEN.matcher(text).find()) {
            return "token";
        }
        return null;
    }

    static boolean isSecretPair(String key, String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("null") || v.equalsIgnoreCase("none") || v.equalsIgnoreCase("false") || v.equalsIgnoreCase("true")) {
            return false;
        }
        if (JWT.matcher(v).find() || MS_REFRESH_TOKEN.matcher(v).find()) {
            return true;
        }
        String k = key.toLowerCase(Locale.ROOT).replace('-', '_');
        if (ALWAYS_SECRET_KEY.matcher(k).find()) {
            // "passwordLength": 12 or "secretMode": "on" are settings, not secrets.
            return !k.matches(".*(length|len|size|mode|enabled|enable|visible|show|hide|color|colour|count|field|prompt|hint|style).*")
                    && !v.matches("-?\\d{1,3}(\\.\\d+)?|on|off|yes|no|enabled|disabled");
        }
        if (TOKEN_KEY.matcher(k).find()) {
            return looksLikeToken(v);
        }
        return false;
    }

    /** At least 16 characters, no spaces, letters and digits mixed: what tokens and keys look like. */
    static boolean looksLikeToken(String value) {
        if (value.length() < 16 || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        if (value.startsWith("#") || value.startsWith("http://") || value.startsWith("https://")) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        return letter && digit;
    }
}
