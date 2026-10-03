package io.github.autyi6969.keybindprofilesplus.profile;

import io.github.autyi6969.keybindprofilesplus.external.ExternalBinding;
import io.github.autyi6969.keybindprofilesplus.external.ExternalKeys;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombos;
import io.github.autyi6969.keybindprofilesplus.keys.KeyLabels;
import io.github.autyi6969.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyi6969.keybindprofilesplus.options.OptionCatalog;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Side-by-side comparison of two sets of settings. Each side is either a saved profile or the
 * game as it is set up right now (profile name {@code null}).
 */
public final class ProfileComparison {
    private ProfileComparison() {
    }

    public enum State {
        /** Both sides have this item with the same value. */
        SAME,
        /** Both sides have this item and the values differ. */
        DIFFERENT,
        /** Only one side saves this item, so there is nothing to compare against. */
        ONE_SIDED
    }

    /**
     * @param group heading the row belongs under (key binding category or settings group)
     * @param left  formatted value on the left side; null when that side does not save the item
     */
    public record Row(boolean keyBinding, String id, Text name, Text group, Text left, Text right, State state) {
    }

    public record Result(List<Row> rows, int different, int oneSided) {
    }

    public static Result compare(ProfileService service, GameOptions options, String leftProfile, String rightProfile) {
        List<Row> rows = new ArrayList<>();
        Map<String, String> leftKeys = keysOf(service, leftProfile);
        Map<String, String> rightKeys = keysOf(service, rightProfile);

        KeyBinding[] bindings = options.allKeys.clone();
        Arrays.sort(bindings);
        Set<String> liveIds = new LinkedHashSet<>();
        for (KeyBinding binding : bindings) {
            liveIds.add(binding.getId());
            String left = leftProfile == null ? KeyCombos.valueOf(binding) : leftKeys.get(binding.getId());
            String right = rightProfile == null ? KeyCombos.valueOf(binding) : rightKeys.get(binding.getId());
            addRow(rows, true, binding.getId(), KeyLabels.name(binding), KeyLabels.category(binding.getCategory()),
                    left, right, describeKey(binding.getId(), left), describeKey(binding.getId(), right));
        }

        // Hotkeys of other mods (Meteor, malilib) that are running and can be saved in profiles.
        for (ExternalBinding external : ExternalKeys.all()) {
            if (!external.editable()) {
                continue;
            }
            String id = external.hotkeyId();
            liveIds.add(id);
            String left = leftProfile == null ? external.value() : leftKeys.get(id);
            String right = rightProfile == null ? external.value() : rightKeys.get(id);
            addRow(rows, true, id, external.title(), external.group(), left, right, describeKey(id, left), describeKey(id, right));
        }

        // Bindings saved for mods that are not installed right now.
        Set<String> orphanIds = new LinkedHashSet<>(leftKeys.keySet());
        orphanIds.addAll(rightKeys.keySet());
        orphanIds.removeAll(liveIds);
        Text orphanGroup = Text.translatable("keybindprofilesplus.contents.keys_missing");
        for (String id : orphanIds) {
            String left = leftKeys.get(id);
            String right = rightKeys.get(id);
            Text name = ExternalKeys.isExternalId(id) ? ExternalKeys.nameOf(id).copy().append(" [").append(ExternalKeys.groupOf(id)).append("]") : KeyLabels.name(id);
            addRow(rows, true, id, name, orphanGroup, left, right, describeKey(id, left), describeKey(id, right));
        }

        Map<String, String> leftOptions = leftProfile == null ? Map.of() : service.getProfileOptions(leftProfile);
        Map<String, String> rightOptions = rightProfile == null ? Map.of() : service.getProfileOptions(rightProfile);
        if (!leftOptions.isEmpty() || !rightOptions.isEmpty()) {
            // Only settings that at least one of the profiles saves are worth a row.
            Map<String, GameOptionsBridge.Entry> current = GameOptionsBridge.readAll(options);
            for (GameOptionsBridge.Entry entry : current.values()) {
                String key = entry.key();
                if (!OptionCatalog.isOffered(key) || (!leftOptions.containsKey(key) && !rightOptions.containsKey(key))) {
                    continue;
                }
                String left = leftProfile == null ? entry.rawValue() : leftOptions.get(key);
                String right = rightProfile == null ? entry.rawValue() : rightOptions.get(key);
                addRow(rows, false, key, entry.name(), OptionCatalog.categoryOf(key).label(), left, right,
                        left == null ? null : entry.describe(left), right == null ? null : entry.describe(right));
            }
        }

        int different = 0;
        int oneSided = 0;
        for (Row row : rows) {
            if (row.state() == State.DIFFERENT) {
                different++;
            } else if (row.state() == State.ONE_SIDED) {
                oneSided++;
            }
        }
        return new Result(rows, different, oneSided);
    }

    private static void addRow(List<Row> rows, boolean keyBinding, String id, Text name, Text group,
                               String leftRaw, String rightRaw, Text left, Text right) {
        if (leftRaw == null && rightRaw == null) {
            return;
        }
        State state = leftRaw == null || rightRaw == null ? State.ONE_SIDED : sameValue(id, leftRaw, rightRaw) ? State.SAME : State.DIFFERENT;
        rows.add(new Row(keyBinding, id, name, group, left, right, state));
    }

    private static Map<String, String> keysOf(ProfileService service, String profile) {
        if (profile == null) {
            return Map.of();
        }
        Map<String, String> keys = service.profiles().get(profile);
        return keys == null ? Map.of() : new LinkedHashMap<>(keys);
    }

    private static boolean sameValue(String id, String left, String right) {
        // Other mods write "no key" in different ways.
        return left.equals(right) || (ExternalKeys.isExternalId(id) && ExternalKeys.isUnboundValue(left) && ExternalKeys.isUnboundValue(right));
    }

    private static Text describeKey(String id, String value) {
        if (value == null) {
            return null;
        }
        return ExternalKeys.isExternalId(id) ? ExternalKeys.describeValue(id, value) : KeyCombo.describe(value);
    }
}
