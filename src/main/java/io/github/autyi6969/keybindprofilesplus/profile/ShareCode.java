package io.github.autyi6969.keybindprofilesplus.profile;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A profile as one line of text that can be pasted into a chat or a file and imported elsewhere.
 *
 * <p>Format: {@code KBP1-} followed by URL-safe Base64 of a 4-byte CRC32 and the deflated JSON
 * {@code {"v":1,"name":...,"keys":{...},"options":{...}}}. The checksum catches text that was cut
 * off or altered on the way. A profile's hotkey and server rules are not part of the code: they
 * are personal to the machine it came from.
 */
public final class ShareCode {
    public static final String PREFIX = "KBP1-";
    private static final int VERSION = 1;
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;
    private static final Gson GSON = new Gson();

    private ShareCode() {
    }

    /** What a share code carries. */
    public record Content(String name, Map<String, String> keyBindings, Map<String, String> options) {
    }

    /** Why a pasted text could not be imported. */
    public enum Problem {
        EMPTY,
        NOT_A_CODE,
        CORRUPTED,
        UNSUPPORTED_VERSION,
        INVALID_CONTENT;

        public String translationKey() {
            return "keybindprofilesplus.share.error." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final class InvalidShareCodeException extends Exception {
        private final Problem problem;

        public InvalidShareCodeException(Problem problem) {
            super(problem.name());
            this.problem = problem;
        }

        public Problem problem() {
            return problem;
        }
    }

    public static String encode(Content content) {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        json.addProperty("name", content.name());
        json.add("keys", GSON.toJsonTree(content.keyBindings()));
        json.add("options", GSON.toJsonTree(content.options()));

        byte[] deflated = deflate(GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
        CRC32 crc = new CRC32();
        crc.update(deflated);
        ByteBuffer buffer = ByteBuffer.allocate(4 + deflated.length);
        buffer.putInt((int) crc.getValue());
        buffer.put(deflated);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    public static Content decode(String text) throws InvalidShareCodeException {
        if (text == null) {
            throw new InvalidShareCodeException(Problem.EMPTY);
        }
        String code = text.replaceAll("\\s+", "");
        if (code.isEmpty()) {
            throw new InvalidShareCodeException(Problem.EMPTY);
        }
        if (!code.startsWith(PREFIX)) {
            throw new InvalidShareCodeException(code.startsWith("KBP") ? Problem.UNSUPPORTED_VERSION : Problem.NOT_A_CODE);
        }

        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new InvalidShareCodeException(Problem.CORRUPTED);
        }
        if (bytes.length <= 4) {
            throw new InvalidShareCodeException(Problem.CORRUPTED);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int expectedCrc = buffer.getInt();
        byte[] deflated = new byte[bytes.length - 4];
        buffer.get(deflated);
        CRC32 crc = new CRC32();
        crc.update(deflated);
        if ((int) crc.getValue() != expectedCrc) {
            throw new InvalidShareCodeException(Problem.CORRUPTED);
        }

        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(new String(inflate(deflated), StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new InvalidShareCodeException(Problem.INVALID_CONTENT);
            }
            json = parsed.getAsJsonObject();
        } catch (JsonParseException | DataFormatException e) {
            throw new InvalidShareCodeException(Problem.CORRUPTED);
        }

        if (!json.has("v") || !json.get("v").isJsonPrimitive() || !json.get("v").getAsJsonPrimitive().isNumber()) {
            throw new InvalidShareCodeException(Problem.INVALID_CONTENT);
        }
        if (json.get("v").getAsInt() != VERSION) {
            throw new InvalidShareCodeException(Problem.UNSUPPORTED_VERSION);
        }
        if (!json.has("name") || !json.get("name").isJsonPrimitive() || json.get("name").getAsString().isBlank()) {
            throw new InvalidShareCodeException(Problem.INVALID_CONTENT);
        }

        Map<String, String> keys = readStringMap(json.get("keys"));
        Map<String, String> options = readStringMap(json.get("options"));
        if (keys.isEmpty() && options.isEmpty()) {
            throw new InvalidShareCodeException(Problem.INVALID_CONTENT);
        }
        return new Content(json.get("name").getAsString().trim(), keys, options);
    }

    private static Map<String, String> readStringMap(JsonElement element) throws InvalidShareCodeException {
        Map<String, String> result = new LinkedHashMap<>();
        if (element == null || element.isJsonNull()) {
            return result;
        }
        if (!element.isJsonObject()) {
            throw new InvalidShareCodeException(Problem.INVALID_CONTENT);
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new InvalidShareCodeException(Problem.INVALID_CONTENT);
            }
            result.put(entry.getKey(), value.getAsString());
            if (result.size() > MAX_ENTRIES) {
                throw new InvalidShareCodeException(Problem.INVALID_CONTENT);
            }
        }
        return result;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] data) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        try {
            while (!inflater.finished()) {
                int read = inflater.inflate(chunk);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DataFormatException("truncated");
                }
                out.write(chunk, 0, read);
                if (out.size() > MAX_JSON_BYTES) {
                    throw new DataFormatException("too large");
                }
            }
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }
}
