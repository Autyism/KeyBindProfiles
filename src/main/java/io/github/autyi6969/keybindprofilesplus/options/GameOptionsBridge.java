package io.github.autyi6969.keybindprofilesplus.options;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and changes the game's own settings (everything in options.txt except key bindings) by
 * their options.txt names and values, e.g. {@code fov -> "0.0"}. It walks the options the same way
 * {@link GameOptions#load()} and {@link GameOptions#write()} do, so values are validated by the
 * game and its own "option changed" reactions run.
 */
public final class GameOptionsBridge {
    private static final String KEY_BINDING_PREFIX = "key_";
    private static final String MODEL_PART_PREFIX = "modelPart_";
    private static final Gson GSON = new Gson();
    private static final Pattern LABELLED_VALUE = Pattern.compile("^(.+?)\\s*[:：]\\s*(.+)$");

    private GameOptionsBridge() {
    }

    /**
     * One setting as the game currently has it.
     *
     * @param option the game's option object when it has one (gives a translated name and nicely
     *               formatted values); null for the handful of plain values
     */
    public record Entry(String key, String rawValue, SimpleOption<?> option) {
        public Text name() {
            if (option != null) {
                return option.text;
            }
            if (key.startsWith(MODEL_PART_PREFIX)) {
                return Text.translatable("options.modelPart." + key.substring(MODEL_PART_PREFIX.length()));
            }
            return Text.literal(key);
        }

        /** Formats a stored raw value of this setting for display, e.g. "0.5" -> "50%". */
        public Text describe(String rawValue) {
            return option == null ? Text.literal(rawValue) : describeOption(option, rawValue);
        }
    }

    /** Every current setting by its options.txt name, in the game's own order. Key bindings are left out. */
    public static Map<String, Entry> readAll(GameOptions options) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        options.accept(new GameOptions.Visitor() {
            @Override
            public <T> void accept(String key, SimpleOption<T> option) {
                option.getCodec().encodeStart(JsonOps.INSTANCE, option.getValue()).result()
                        .ifPresent(json -> entries.put(key, new Entry(key, GSON.toJson(json), option)));
            }

            @Override
            public int visitInt(String key, int current) {
                entries.put(key, new Entry(key, String.valueOf(current), null));
                return current;
            }

            @Override
            public boolean visitBoolean(String key, boolean current) {
                entries.put(key, new Entry(key, String.valueOf(current), null));
                return current;
            }

            @Override
            public String visitString(String key, String current) {
                if (!key.startsWith(KEY_BINDING_PREFIX)) {
                    entries.put(key, new Entry(key, current, null));
                }
                return current;
            }

            @Override
            public float visitFloat(String key, float current) {
                entries.put(key, new Entry(key, String.valueOf(current), null));
                return current;
            }

            @Override
            public <T> T visitObject(String key, T current, Function<String, T> decoder, Function<T, String> encoder) {
                entries.put(key, new Entry(key, encoder.apply(current), null));
                return current;
            }
        });
        return entries;
    }

    /**
     * Sets the given settings (options.txt name -> raw value). Settings not mentioned keep their
     * value; names the game does not know and key bindings are ignored. The caller saves options.txt.
     */
    public static void apply(GameOptions options, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        options.accept(new GameOptions.Visitor() {
            private String find(String key) {
                return key.startsWith(KEY_BINDING_PREFIX) ? null : values.get(key);
            }

            @Override
            public <T> void accept(String key, SimpleOption<T> option) {
                String value = find(key);
                if (value == null) {
                    return;
                }
                try {
                    JsonElement json = JsonParser.parseString(value.isEmpty() ? "\"\"" : value);
                    option.getCodec().parse(JsonOps.INSTANCE, json)
                            .ifError(error -> KeyBindProfilesPlus.LOGGER.warn("Ignoring saved value '{}' for option {}: {}", value, key, error.message()))
                            .ifSuccess(option::setValue);
                } catch (RuntimeException e) {
                    KeyBindProfilesPlus.LOGGER.warn("Ignoring unreadable saved value '{}' for option {}", value, key);
                }
            }

            @Override
            public int visitInt(String key, int current) {
                String value = find(key);
                if (value != null) {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        KeyBindProfilesPlus.LOGGER.warn("Ignoring saved value '{}' for option {}", value, key);
                    }
                }
                return current;
            }

            @Override
            public boolean visitBoolean(String key, boolean current) {
                String value = find(key);
                return value != null ? "true".equals(value) : current;
            }

            @Override
            public String visitString(String key, String current) {
                String value = find(key);
                return value != null ? value : current;
            }

            @Override
            public float visitFloat(String key, float current) {
                String value = find(key);
                if (value != null) {
                    try {
                        return Float.parseFloat(value);
                    } catch (NumberFormatException e) {
                        KeyBindProfilesPlus.LOGGER.warn("Ignoring saved value '{}' for option {}", value, key);
                    }
                }
                return current;
            }

            @Override
            public <T> T visitObject(String key, T current, Function<String, T> decoder, Function<T, String> encoder) {
                String value = find(key);
                if (value != null) {
                    try {
                        return decoder.apply(value);
                    } catch (RuntimeException e) {
                        KeyBindProfilesPlus.LOGGER.warn("Ignoring saved value '{}' for option {}", value, key);
                    }
                }
                return current;
            }
        });
    }

    private static <T> Text describeOption(SimpleOption<T> option, String rawValue) {
        try {
            JsonElement json = JsonParser.parseString(rawValue.isEmpty() ? "\"\"" : rawValue);
            Optional<T> value = option.getCodec().parse(JsonOps.INSTANCE, json).result();
            if (value.isEmpty()) {
                return Text.literal(rawValue);
            }

            // Many options format their value as "Name: value"; only the value part is wanted here.
            String full = option.textGetter.apply(value.get()).getString();
            String name = option.text.getString();
            Matcher labelled = LABELLED_VALUE.matcher(full);
            if (labelled.matches()) {
                // The label is the option name or a shortened form of it ("Chunk Fade: 0.75 s").
                String label = labelled.group(1);
                if (name.startsWith(label) || label.startsWith(name)) {
                    return Text.literal(labelled.group(2));
                }
            }
            return Text.literal(full);
        } catch (RuntimeException e) {
            return Text.literal(rawValue);
        }
    }
}
