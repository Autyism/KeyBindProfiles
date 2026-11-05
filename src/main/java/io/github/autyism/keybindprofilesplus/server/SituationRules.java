package io.github.autyism.keybindprofilesplus.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Rules that switch a profile for a while, by what is happening rather than where the player is:
 * <ul>
 *   <li>{@code holding:minecraft:bow} - while that item is in the main hand;</li>
 *   <li>{@code flying} - while gliding with an elytra;</li>
 *   <li>{@code wearing:minecraft:elytra} - while that item is worn (any armor slot);</li>
 *   <li>{@code in:the_nether} - while in that dimension ({@code in:minecraft:the_end} works too).</li>
 * </ul>
 * They are kept in the same rule list as the server rules, never match a server, and win over them while they apply.
 * When several apply, holding beats flying beats wearing beats a dimension.
 */
public final class SituationRules {
    public static final String HOLDING = "holding:";
    public static final String WEARING = "wearing:";
    public static final String IN = "in:";
    public static final String FLYING = "flying";

    private SituationRules() {
    }

    public static boolean isSituation(String rule) {
        String r = normalize(rule);
        return r.equals(FLYING) || r.startsWith(HOLDING) || r.startsWith(WEARING) || r.startsWith(IN);
    }

    /** Translation key suffix (keybindprofilesplus.server.rule.*) for a situation rule. */
    public static String kind(String rule) {
        String r = normalize(rule);
        if (r.equals(FLYING)) return "situation_flying";
        if (r.startsWith(HOLDING)) return "situation_holding";
        if (r.startsWith(WEARING)) return "situation_wearing";
        return "situation_in";
    }

    /** Problem with a situation rule (a translation key), or null. */
    public static String validate(String rule) {
        String r = normalize(rule);
        if (r.equals(FLYING)) return null;
        String rest = r.startsWith(HOLDING) ? r.substring(HOLDING.length()) : r.startsWith(WEARING) ? r.substring(WEARING.length())
                : r.startsWith(IN) ? r.substring(IN.length()) : "";
        return rest.isEmpty() || rest.chars().anyMatch(Character::isWhitespace) ? "keybindprofilesplus.status.situation_invalid" : null;
    }

    public static String normalize(String rule) {
        return rule == null ? "" : rule.trim().toLowerCase(Locale.ROOT);
    }

    /** "minecraft:bow" for "bow" and "minecraft:bow". */
    private static String withNamespace(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    /** 0 = does not apply now; higher = wins. */
    public static int score(String rule, LocalPlayer player, String dimension) {
        String r = normalize(rule);
        if (player == null) return 0;
        if (r.startsWith(HOLDING)) {
            return itemId(player.getMainHandItem()).equals(withNamespace(r.substring(HOLDING.length()))) ? 400 : 0;
        }
        if (r.equals(FLYING)) {
            return player.isFallFlying() ? 300 : 0;
        }
        if (r.startsWith(WEARING)) {
            String want = withNamespace(r.substring(WEARING.length()));
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (itemId(player.getItemBySlot(slot)).equals(want)) return 200;
            }
            return 0;
        }
        if (r.startsWith(IN)) {
            return dimension != null && dimension.equals(withNamespace(r.substring(IN.length()))) ? 100 : 0;
        }
        return 0;
    }

    /** "ResourceKey[minecraft:dimension / minecraft:the_nether]" -> "minecraft:the_nether" (the same text in every version). */
    static String dimensionId(String key) {
        int slash = key.lastIndexOf(" / ");
        String id = slash >= 0 ? key.substring(slash + 3) : key;
        return id.endsWith("]") ? id.substring(0, id.length() - 1) : id;
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** The profile of the best situation rule that applies now, or null. Ties go to the profile whose name sorts first. */
    public static String bestProfile(Minecraft client, Map<String, List<String>> rulesByProfile, Set<String> existingProfiles) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return null;
        String dimension = dimensionId(client.level.dimension().toString());
        List<String> names = new ArrayList<>(rulesByProfile.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        String best = null;
        int bestScore = 0;
        for (String name : names) {
            List<String> rules = rulesByProfile.get(name);
            if (rules == null || !existingProfiles.contains(name)) continue;
            for (String rule : rules) {
                if (!isSituation(rule)) continue;
                int s = score(rule, player, dimension);
                if (s > bestScore) {
                    bestScore = s;
                    best = name;
                }
            }
        }
        return best;
    }

    /** The rule for the item in the main hand right now ("holding:minecraft:bow"), or null with an empty hand. */
    public static String currentHolding(Minecraft client) {
        if (client.player == null) return null;
        String id = itemId(client.player.getMainHandItem());
        return id.isEmpty() ? null : HOLDING + id;
    }
}
