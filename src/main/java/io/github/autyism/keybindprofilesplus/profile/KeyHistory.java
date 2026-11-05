package io.github.autyism.keybindprofilesplus.profile;

import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.keys.KeyLabels;
import io.github.autyism.keybindprofilesplus.options.GameOptionsBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Back and forward, like a web browser, for everything about keys: rebinding, resetting, where a key counts (conflict
 * scope), applying a profile. Each step keeps the whole state before and after it (game keys, other mods' hotkeys,
 * the scope choices, the game settings, the current profile), so going back puts all of it back. Kept until the game
 * closes.
 */
public final class KeyHistory {
    private static final int MAX = 200;

    /** Everything a step can change. */
    public record State(Map<String, String> keys, Map<String, String> scopes, Map<String, String> options, String profile) {
    }

    /** One step: what it was, how things were before and after. */
    public record Entry(Component label, State before, State after) {
    }

    private static final List<Entry> entries = new ArrayList<>();
    /** How many entries are in effect (back moves it down, forward up). */
    private static int position;
    /** The state as last seen; changes from it are the next step. */
    private static State last;

    private KeyHistory() {
    }

    public static State capture() {
        Minecraft client = Minecraft.getInstance();
        Map<String, String> keys = new HashMap<>();
        for (KeyMapping binding : client.options.keyMappings) {
            if (binding != null) keys.put(binding.getName(), KeyCombos.valueOf(binding));
        }
        for (ExternalBinding external : ExternalKeys.all()) {
            if (external.editable() && external.active()) keys.put(external.hotkeyId(), external.value());
        }
        // only the game settings a profile can set (others the player changes elsewhere are none of this history's business)
        java.util.Set<String> offered = new java.util.HashSet<>();
        ProfileService service = KeyBindProfilesPlus.profileService();
        for (String profile : service.profiles().keySet()) offered.addAll(service.getProfileOptions(profile).keySet());
        Map<String, String> options = new HashMap<>();
        if (!offered.isEmpty()) {
            for (Map.Entry<String, GameOptionsBridge.Entry> e : GameOptionsBridge.readAll(client.options).entrySet()) {
                if (offered.contains(e.getKey())) options.put(e.getKey(), e.getValue().rawValue());
            }
        }
        return new State(keys, KeyBindProfilesPlus.settings().scopeOverridesCopy(), options, KeyBindProfilesPlus.profileService().getCurrentProfile());
    }

    /** Takes the state as it is now as the starting point, without making a step of what changed meanwhile. */
    public static void rebase() {
        last = capture();
    }

    /** Whatever changed since the last look becomes one step (label null: described from the change). */
    public static void track(Component label) {
        State now = capture();
        if (last == null) {
            last = now;
            return;
        }
        if (now.equals(last)) {
            return;
        }
        push(new Entry(label != null ? label : describe(last, now), last, now));
        last = now;
    }

    private static void push(Entry entry) {
        while (entries.size() > position) entries.remove(entries.size() - 1);
        entries.add(entry);
        while (entries.size() > MAX) entries.remove(0);
        position = entries.size();
    }

    public static Entry backEntry() {
        return position > 0 ? entries.get(position - 1) : null;
    }

    public static Entry forwardEntry() {
        return position < entries.size() ? entries.get(position) : null;
    }

    /** Undo the last step. */
    public static boolean back() {
        track(null);
        Entry entry = backEntry();
        if (entry == null) return false;
        restore(entry.before());
        position--;
        last = capture();
        return true;
    }

    /** Redo the step undone last. */
    public static boolean forward() {
        track(null);
        Entry entry = forwardEntry();
        if (entry == null) return false;
        restore(entry.after());
        position++;
        last = capture();
        return true;
    }

    public static int size() {
        return entries.size();
    }

    public static int position() {
        return position;
    }

    private static void restore(State state) {
        Minecraft client = Minecraft.getInstance();
        State now = capture();
        KeyCombos.batch(() -> {
            for (KeyMapping binding : client.options.keyMappings) {
                String value = state.keys().get(binding.getName());
                if (value != null && !value.equals(now.keys().get(binding.getName()))) KeyCombos.applyValue(binding, value);
            }
        });
        KeyMapping.resetMapping();
        Map<String, String> external = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : state.keys().entrySet()) {
            if (ExternalKeys.isExternalId(e.getKey()) && !Objects.equals(e.getValue(), now.keys().get(e.getKey()))) external.put(e.getKey(), e.getValue());
        }
        if (!external.isEmpty()) {
            try {
                ExternalKeys.applyValues(external);
            } catch (RuntimeException ex) {
                KeyBindProfilesPlus.LOGGER.error("Failed to put other mods' hotkeys back", ex);
            }
        }
        if (!state.scopes().equals(now.scopes())) KeyBindProfilesPlus.settings().setScopeOverrides(state.scopes());
        Map<String, String> options = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : state.options().entrySet()) {
            if (!e.getValue().equals(now.options().get(e.getKey()))) options.put(e.getKey(), e.getValue());
        }
        if (!options.isEmpty()) GameOptionsBridge.apply(client.options, options);
        if (!Objects.equals(state.profile(), now.profile())) KeyBindProfilesPlus.profileService().saveCurrentProfile(state.profile());
        client.options.save();
    }

    /** "Jump: Space -> J", or "3 changes". */
    private static Component describe(State before, State after) {
        List<String> changedKeys = new ArrayList<>();
        for (Map.Entry<String, String> e : after.keys().entrySet()) {
            if (!Objects.equals(e.getValue(), before.keys().get(e.getKey()))) changedKeys.add(e.getKey());
        }
        int scopes = 0;
        String scopeKey = null;
        for (String k : union(before.scopes(), after.scopes())) {
            if (!Objects.equals(before.scopes().get(k), after.scopes().get(k))) {
                scopes++;
                scopeKey = k;
            }
        }
        int options = 0;
        for (Map.Entry<String, String> e : after.options().entrySet()) {
            if (!e.getValue().equals(before.options().get(e.getKey()))) options++;
        }
        int total = changedKeys.size() + scopes + options;
        if (changedKeys.size() == 1 && total == 1) {
            String id = changedKeys.get(0);
            return Component.translatable("keybindprofilesplus.history.key", name(id), describeValue(id, before.keys().get(id)), describeValue(id, after.keys().get(id)));
        }
        if (scopes == 1 && total == 1) {
            return Component.translatable("keybindprofilesplus.history.scope", scopeName(scopeKey));
        }
        return Component.translatable("keybindprofilesplus.history.many", total);
    }

    private static Component describeValue(String id, String value) {
        if (value == null) return Component.literal("-");
        return ExternalKeys.isExternalId(id) ? ExternalKeys.describeValue(id, value) : KeyCombo.describe(value);
    }

    private static List<String> union(Map<String, String> a, Map<String, String> b) {
        List<String> keys = new ArrayList<>(a.keySet());
        for (String k : b.keySet()) if (!a.containsKey(k)) keys.add(k);
        return keys;
    }

    private static Component name(String id) {
        if (ExternalKeys.isExternalId(id)) {
            ExternalBinding external = ExternalKeys.find(id);
            return external != null ? external.label() : Component.literal(id);
        }
        for (KeyMapping binding : Minecraft.getInstance().options.keyMappings) {
            if (binding.getName().equals(id)) return KeyLabels.name(binding);
        }
        return Component.literal(id);
    }

    private static Component scopeName(String overrideKey) {
        for (KeyMapping binding : Minecraft.getInstance().options.keyMappings) {
            if (binding.getName().equals(overrideKey)) return KeyLabels.name(binding);
        }
        int colon = overrideKey.lastIndexOf(':');
        return Component.literal(colon >= 0 ? overrideKey.substring(colon + 1) : overrideKey);
    }
}
