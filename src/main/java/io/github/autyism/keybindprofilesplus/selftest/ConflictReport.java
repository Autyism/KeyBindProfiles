package io.github.autyism.keybindprofilesplus.selftest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.gui.KeyOverviewScreen;
import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Development tool: puts a whole key layout in place (the "keybindings" of a profile file, its game
 * settings are left out), writes every conflict the Key Binds screen would mark into the log, takes
 * screenshots of that screen with "Conflicts only" on, and puts everything back.
 *
 * <p>It runs instead of the self-test when the game folder holds {@value #LAYOUT_FILE}
 * ({@code tools/selftest.ps1 -Report <profile file>} puts it there and removes it afterwards).
 * Log lines start with "[SelfTest] REPORT".
 */
final class ConflictReport {
    static final String LAYOUT_FILE = "selftest-report.kbp";
    private static final String PROFILE = LogicChecks.PROFILE_PREFIX + "report";
    /** Sources whose rows get a screenshot of their own (the ones whose hotkeys act only in some screens). */
    private static final List<String> SOURCES = List.of("Inventory Profiles Next", "Item Scroller", "Conflux Map", "MiniHUD", "Tweakeroo",
            "Litematica", "Meteor");
    /** Mods whose bound hotkeys get a "REPORT CONTEXT" line saying where they act. */
    private static final List<String> CONTEXT_SOURCES = List.of("inventoryprofilesnext", "itemscroller", "confluxmap", "minihud", "tweakeroo");

    private final SelfTestRunner t;
    private final ProfileService service;
    private Map<String, String> externalBefore = Map.of();
    private String languageBefore = "en_us";

    ConflictReport(SelfTestRunner runner, ProfileService service) {
        this.t = runner;
        this.service = service;
    }

    static boolean requested() {
        return Files.isRegularFile(layoutFile());
    }

    private static Path layoutFile() {
        return FabricLoader.getInstance().getGameDir().resolve(LAYOUT_FILE);
    }

    void register() {
        t.step("report: remember the other mods' hotkeys", () -> externalBefore = new LinkedHashMap<>(ExternalKeys.currentValues()));
        t.step("report: put the layout in place", 10, this::applyLayout);
        t.step("report: list the conflicts", this::listConflicts);
        t.open("report: key binds screen", () -> new KeyOverviewScreen(SelfTestRunner.client().screen));
        t.step("report: timing", this::timing);
        t.step("report: only conflicts", 4, () -> t.screen(KeyOverviewScreen.class).setConflictsOnly(true));
        t.shot("report_conflicts_all");
        for (String source : SOURCES) {
            t.step("report: only " + source, 4, () -> {
                KeyOverviewScreen screen = t.screen(KeyOverviewScreen.class);
                if (screen.sourceFilterValues().contains(source)) {
                    screen.setSourceFilter(source);
                } else {
                    SelfTestRunner.log("REPORT no rows from " + source);
                }
            });
            t.shot("report_conflicts_" + source.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_"));
        }
        // Tooltips that explain a verdict: what shares Conflux Map's zoom key, and what IPN's sort key really clashes with.
        tooltips("");
        // The same tooltips in Chinese: other words, other lengths. The rows are built again in the new language.
        t.step("report: Chinese", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> {
            languageBefore = SelfTestRunner.client().getLanguageManager().getSelected();
            language("zh_cn");
            t.screen(KeyOverviewScreen.class).refreshKeys();
        });
        tooltips("_zh");
        t.step("report: language back", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> {
            language(languageBefore);
            t.screen(KeyOverviewScreen.class).refreshKeys();
        });
        t.step("report: leave the screen", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> SelfTestRunner.client().setScreen(t.homeScreen));
        t.step("report: put the other mods' hotkeys back", () -> {
            int restored = ExternalKeys.applyValues(externalBefore);
            t.check("report: the other mods' hotkeys are back (" + restored + " set)", ExternalKeys.currentValues().equals(externalBefore));
        });
    }

    /**
     * How long the work takes with this layout: the Key Binds screen showing everything again after a key changed
     * (other mods' hotkeys read again, every verdict worked out), the verdicts alone, and one question while nothing changed.
     */
    private void timing() {
        var options = SelfTestRunner.client().options;
        KeyOverviewScreen screen = t.screen(KeyOverviewScreen.class);
        long[] refresh = new long[5];
        long[] verdicts = new long[5];
        for (int i = 0; i < refresh.length; i++) {
            long start = System.nanoTime();
            screen.refreshKeys();
            refresh[i] = System.nanoTime() - start;
            KeyConflicts.invalidate();
            start = System.nanoTime();
            KeyConflicts.summarize(options);
            verdicts[i] = System.nanoTime() - start;
        }
        Arrays.sort(refresh);
        Arrays.sort(verdicts);
        KeyMapping jump = SelfTestRunner.binding("key.jump");
        KeyConflicts.conflictsOf(jump, options);
        int questions = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < questions; i++) {
            KeyConflicts.conflictsOf(jump, options);
        }
        double perQuestion = (System.nanoTime() - start) / (double) questions;
        SelfTestRunner.log(String.format(java.util.Locale.ROOT,
                "REPORT TIMING %d game keys + %d hotkeys of other mods: Key Binds screen refreshed %.1f ms (median of 5, fastest %.1f), "
                        + "verdicts of every game key %.1f ms (median, fastest %.1f), one question with nothing changed %.2f us",
                options.keyMappings.length, ExternalKeys.all().size(), refresh[2] / 1e6, refresh[0] / 1e6, verdicts[2] / 1e6, verdicts[0] / 1e6,
                perQuestion / 1e3));
    }

    private void tooltips(String suffix) {
        tooltip("ext:malilib:confluxmap/zoomIn", "]", "report_tooltip_conflux_zoom" + suffix);
        tooltip("ext:ipn:sort_inventory", "Sort Inventory", "report_tooltip_ipn_sort" + suffix);
        tooltip("ext:ipn:global_bookmark_trade", "Bookmark", "report_tooltip_ipn_trade" + suffix);
    }

    /** Only the in-memory language is switched; options.txt keeps the player's choice. */
    private static void language(String code) {
        var client = SelfTestRunner.client();
        client.getLanguageManager().setSelected(code);
        client.getLanguageManager().onResourceManagerReload(client.getResourceManager());
    }

    /** A screenshot of the Key Binds screen with the key button tooltip of one hotkey showing (when that hotkey exists). */
    private void tooltip(String hotkeyId, String query, String shotName) {
        t.step("report: tooltip of " + hotkeyId, 4, () -> {
            KeyOverviewScreen screen = t.screen(KeyOverviewScreen.class);
            screen.setConflictsOnly(false);
            screen.setSourceFilter(KeyOverviewScreen.FILTER_ALL);
            screen.setQuery(query);
            screen.showTooltipForTesting(hotkeyId, true);
            SelfTestRunner.log("REPORT TOOLTIP " + hotkeyId + (screen.keyButtonPoint(hotkeyId) == null ? " (not listed)" : ""));
        });
        t.shot(shotName);
        t.step("report: tooltip off", () -> t.screen(KeyOverviewScreen.class).showTooltipForTesting(null, false));
    }

    private void applyLayout() {
        Map<String, String> keys = new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(layoutFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("keybindings").entrySet()) {
                keys.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException | RuntimeException e) {
            t.fail("report: could not read " + LAYOUT_FILE + ": " + e);
            return;
        }
        service.deleteProfile(PROFILE);
        // Key bindings only: the layout file's game settings (window mode, volume...) are never applied.
        t.check("report: layout read (" + keys.size() + " keys)", service.createProfile(PROFILE, keys, Map.of()));
        service.applyProfile(PROFILE);
        ExternalKeys.refresh();
    }

    private void listConflicts() {
        var options = SelfTestRunner.client().options;
        int rows = 0;
        int hard = 0;
        int soft = 0;
        KeyMapping[] bindings = options.keyMappings.clone();
        Arrays.sort(bindings, (first, second) -> first.getName().compareTo(second.getName()));
        for (KeyMapping binding : bindings) {
            rows++;
            List<KeyConflicts.Conflict> conflicts = KeyConflicts.conflictsOf(binding, options);
            KeyConflicts.Level level = KeyConflicts.worst(conflicts);
            hard += level == KeyConflicts.Level.HARD ? 1 : 0;
            soft += level == KeyConflicts.Level.SOFT ? 1 : 0;
            for (KeyConflicts.Conflict conflict : conflicts) {
                line("key", binding.getName(), binding.getTranslatedKeyMessage().getString(), conflict);
            }
        }
        for (ExternalBinding external : ExternalKeys.all()) {
            if (!external.active()) {
                continue;
            }
            if (!external.unbound() && CONTEXT_SOURCES.contains(external.sourceId())) {
                SelfTestRunner.log("REPORT CONTEXT " + external.hotkeyId() + " [" + external.keyText().getString() + "] acts "
                        + KeyConflicts.contextOf(external).describe().getString());
            }
            rows++;
            List<KeyConflicts.Conflict> conflicts = KeyConflicts.conflictsOf(external, options);
            KeyConflicts.Level level = KeyConflicts.worst(conflicts);
            hard += level == KeyConflicts.Level.HARD ? 1 : 0;
            soft += level == KeyConflicts.Level.SOFT ? 1 : 0;
            String id = external.hotkeyId() != null ? external.hotkeyId() : external.sourceId() + ":" + external.name();
            for (KeyConflicts.Conflict conflict : conflicts) {
                line("ext", id, external.keyText().getString(), conflict);
            }
        }
        SelfTestRunner.log("REPORT SUMMARY rows=" + rows + " marked red=" + hard + " marked yellow=" + soft);
    }

    private static void line(String kind, String id, String key, KeyConflicts.Conflict conflict) {
        SelfTestRunner.log("REPORT " + kind + " " + id + " [" + key + "] -> " + conflict.level() + " " + conflict.otherId()
                + " \"" + conflict.otherName().getString() + "\" (" + conflict.reason() + ": " + conflict.explanation().getString() + ")");
    }
}
