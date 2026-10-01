package io.github.autyi6969.keybindprofilesplus.selftest;

import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.gui.ApplyConfirmScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ImportScreen;
import io.github.autyi6969.keybindprofilesplus.gui.KeyBindProfileScreen;
import io.github.autyi6969.keybindprofilesplus.gui.KeyOverviewScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileCompareScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileContentsScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileEditScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ServerRulesScreen;
import io.github.autyi6969.keybindprofilesplus.gui.SettingsScreen;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombos;
import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyi6969.keybindprofilesplus.keys.KeySourceResolver;
import io.github.autyi6969.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.profile.ShareCode;
import io.github.autyi6969.keybindprofilesplus.storage.ModSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.List;
import java.util.Map;

import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.DEMO_MOD_BINDING;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_A;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_A_KEY;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_C;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_FOV;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_PREFIX;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.TEST_BINDING_ID;
import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.TEST_KEY;
import static io.github.autyi6969.keybindprofilesplus.selftest.SelfTestRunner.SCREEN_SETTLE_TICKS;
import static io.github.autyi6969.keybindprofilesplus.selftest.SelfTestRunner.translated;

/**
 * The part of the self-test that drives the screens: it opens each one, takes screenshots, and
 * uses them the way a player would (clicking buttons by label, real mouse clicks on list rows,
 * key events through the game's key handler), checking the outcome each time.
 */
final class ScreenChecks {
    private static final String PROFILE_FOV_ONLY = PROFILE_PREFIX + "fov";
    private static final String PROFILE_RENAMED = PROFILE_PREFIX + "renamed";

    private final SelfTestRunner t;
    private final LogicChecks logic;
    private final ProfileService service;
    private String shareCode;
    private int ruleBaseline;

    ScreenChecks(SelfTestRunner runner, LogicChecks logic, ProfileService service) {
        this.t = runner;
        this.logic = logic;
        this.service = service;
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    // ------------------------------------------------------------------ recording combinations with real key events

    /**
     * Rebinding on the mod's Key Binds screen the way a player does it: a real mouse click on the
     * key button, then key events through the game's own key handler.
     */
    void rebindOnKeyBindsScreen() {
        String id = "key.socialInteractions";
        String[] original = new String[1];
        Map<String, String> layout = new java.util.LinkedHashMap<>();
        t.open("key binds screen, the way the options menu opens it", () -> new KeybindsScreen(t.homeScreen, client().options));
        t.step("keys: the mod's screen is shown in place of the vanilla one", 3, () -> {
            t.check("keys: opening the vanilla Key Binds screen shows the mod's screen instead", t.isScreen(KeyOverviewScreen.class));
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            original[0] = KeyCombos.valueOf(SelfTestRunner.binding(id));
            keys.setQuery("social");
            t.check("keys: searching leaves one row", keys.visibleBindingCount() == 1);
        });
        t.step("keys: click the key button", 2, () -> {
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(id), 0);
            t.check("keys: a click on the key button makes the binding wait for a key", id.equals(keys.waitingFor()));
        });
        t.shot("en_keybinds_waiting_for_key");
        t.step("keys: Ctrl goes down", 2, () -> {
            t.sendKey(InputUtil.GLFW_KEY_LEFT_CONTROL, true, KeyCombo.CTRL);
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.check("keys: holding Ctrl does not bind Left Control yet", id.equals(keys.waitingFor())
                    && KeyCombos.valueOf(SelfTestRunner.binding(id)).equals(original[0]));
            t.check("keys: the button shows the modifier being held", t.hasWidget("> Ctrl + ... <"));
        });
        t.step("keys: F15 goes down with Ctrl held", 2, () -> {
            t.sendKey(InputUtil.GLFW_KEY_F15, true, KeyCombo.CTRL);
            KeyBinding binding = SelfTestRunner.binding(id);
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.check("keys: Ctrl + F15 was recorded", KeyCombos.valueOf(binding).equals("ctrl+key.keyboard.f15") && keys.waitingFor() == null);
            t.check("keys: the list shows Ctrl + F15", t.hasWidget("Ctrl + F15"));
            t.sendKey(InputUtil.GLFW_KEY_F15, false, KeyCombo.CTRL);
            t.sendKey(InputUtil.GLFW_KEY_LEFT_CONTROL, false, 0);
            t.check("keys: releasing the keys afterwards changes nothing", KeyCombos.valueOf(binding).equals("ctrl+key.keyboard.f15"));
            keys.charTyped(new net.minecraft.client.input.CharInput('x', 0));
            t.check("keys: the character of the key just bound does not land in the search box", keys.visibleBindingCount() == 1);
        });
        t.step("keys: a modifier pressed and released alone is bound as a plain key", 2, () -> {
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(id), 0);
            t.sendKey(InputUtil.GLFW_KEY_RIGHT_SHIFT, true, KeyCombo.SHIFT);
            t.sendKey(InputUtil.GLFW_KEY_RIGHT_SHIFT, false, 0);
            t.check("keys: Right Shift alone -> " + KeyCombos.valueOf(SelfTestRunner.binding(id)),
                    KeyCombos.valueOf(SelfTestRunner.binding(id)).equals("key.keyboard.right.shift") && keys.waitingFor() == null);
        });
        t.step("keys: a mouse button can be bound", 2, () -> {
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(id), 0);
            t.check("keys: waiting again", id.equals(keys.waitingFor()));
            t.mouseClick(new int[]{5, 5}, 0);
            t.check("keys: the next click binds that mouse button -> " + KeyCombos.valueOf(SelfTestRunner.binding(id)),
                    KeyCombos.valueOf(SelfTestRunner.binding(id)).equals("key.mouse.left") && keys.waitingFor() == null);
        });
        t.step("keys: the reset button", 2, () -> {
            KeyBinding binding = SelfTestRunner.binding(id);
            t.click(translated("controls.reset"));
            t.check("keys: Reset puts the default key back", binding.isDefault() && !t.widget(translated("controls.reset")).active);
            t.bind(id, "alt+" + binding.getDefaultKey().getTranslationKey());
            t.check("keys: a combination on the default key is not 'default'", !binding.isDefault());
            t.screen(KeyOverviewScreen.class).reset(id);
            t.check("keys: resetting drops the modifiers", binding.isDefault() && KeyCombos.modifiersOf(binding) == 0);
        });
        t.step("keys: escape unbinds", 2, () -> {
            KeyBinding binding = SelfTestRunner.binding(id);
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(id), 0);
            t.sendKey(InputUtil.GLFW_KEY_ESCAPE, true, 0);
            t.sendKey(InputUtil.GLFW_KEY_ESCAPE, false, 0);
            t.check("keys: Escape while waiting leaves the binding without a key and keeps the screen open",
                    binding.isUnbound() && KeyCombos.modifiersOf(binding) == 0 && t.isScreen(KeyOverviewScreen.class));
            t.bind(id, original[0]);
            keys.setQuery("");
        });
        t.step("keys: reset all asks first", SCREEN_SETTLE_TICKS, () -> {
            layout.putAll(SelfTestRunner.currentKeyValues());
            t.bind("key.jump", "key.keyboard.f18");
            client().setScreen(new KeybindsScreen(t.homeScreen, client().options));
        });
        t.step("keys: reset all asks first (2)", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("controls.resetAll"));
            t.check("keys: Reset Keys asks for confirmation", t.isScreen(ConfirmScreen.class));
        });
        t.step("keys: reset all, no", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.no"));
            t.check("keys: answering No changes nothing", t.isScreen(KeyOverviewScreen.class)
                    && "key.keyboard.f18".equals(SelfTestRunner.binding("key.jump").getBoundKeyTranslationKey()));
            t.click(translated("controls.resetAll"));
        });
        t.step("keys: reset all, yes", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.yes"));
            boolean allDefault = true;
            for (KeyBinding binding : client().options.allKeys) {
                allDefault &= binding.isDefault();
            }
            t.check("keys: answering Yes puts every binding back on its default key", t.isScreen(KeyOverviewScreen.class) && allDefault
                    && !t.widget(translated("controls.resetAll")).active);
            layout.forEach(t::bind);
        });
        t.step("keys: done", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("keys: Done leads back to where the vanilla screen would have", client().currentScreen == t.homeScreen);
        });
    }

    /**
     * With "replace the vanilla Key Binds screen" switched off the vanilla screen is kept, and
     * combinations are recorded on it by the same real key events.
     */
    void recordCombinationsOnVanillaScreen() {
        String[] original = new String[1];
        t.step("vanilla mode: switch the replacement off", () -> KeyBindProfilesPlus.settings().setReplaceKeyBinds(false));
        t.open("vanilla key binds screen (recording)", () -> new KeybindsScreen(t.homeScreen, client().options));
        t.step("record: start waiting for a key", 2, () -> {
            KeyBinding binding = SelfTestRunner.binding("key.socialInteractions");
            original[0] = KeyCombos.valueOf(binding);
            t.check("vanilla mode: with the replacement off the vanilla Key Binds screen is kept", t.isScreen(KeybindsScreen.class));
            KeybindsScreen screen = t.screen(KeybindsScreen.class);
            screen.selectedKeyBinding = binding;
            screen.controlsList.update();
        });
        t.step("record: Ctrl goes down", 2, () -> {
            t.sendKey(InputUtil.GLFW_KEY_LEFT_CONTROL, true, KeyCombo.CTRL);
            KeybindsScreen screen = t.screen(KeybindsScreen.class);
            t.check("record: holding Ctrl does not bind Left Control yet", screen.selectedKeyBinding != null
                    && KeyCombos.valueOf(screen.selectedKeyBinding).equals(original[0]));
            t.check("record: the button shows the modifier being held", t.hasWidget("> Ctrl + ... <"));
        });
        t.step("record: F15 goes down with Ctrl held", 2, () -> {
            t.sendKey(InputUtil.GLFW_KEY_F15, true, KeyCombo.CTRL);
            KeyBinding binding = SelfTestRunner.binding("key.socialInteractions");
            t.check("record: Ctrl + F15 was recorded", KeyCombos.valueOf(binding).equals("ctrl+key.keyboard.f15")
                    && t.screen(KeybindsScreen.class).selectedKeyBinding == null);
            t.check("record: the list shows Ctrl + F15", t.hasWidget("Ctrl + F15"));
            t.sendKey(InputUtil.GLFW_KEY_F15, false, KeyCombo.CTRL);
            t.sendKey(InputUtil.GLFW_KEY_LEFT_CONTROL, false, 0);
            t.check("record: releasing the keys afterwards changes nothing", KeyCombos.valueOf(binding).equals("ctrl+key.keyboard.f15"));
        });
        t.step("record: a modifier pressed and released alone is bound as a plain key", 2, () -> {
            KeyBinding binding = SelfTestRunner.binding("key.socialInteractions");
            KeybindsScreen screen = t.screen(KeybindsScreen.class);
            screen.selectedKeyBinding = binding;
            screen.controlsList.update();
            t.sendKey(InputUtil.GLFW_KEY_RIGHT_SHIFT, true, KeyCombo.SHIFT);
            t.sendKey(InputUtil.GLFW_KEY_RIGHT_SHIFT, false, 0);
            t.check("record: Right Shift alone -> " + KeyCombos.valueOf(binding),
                    KeyCombos.valueOf(binding).equals("key.keyboard.right.shift") && screen.selectedKeyBinding == null);
        });
        t.step("record: the reset button clears a combination", 2, () -> {
            KeyBinding binding = SelfTestRunner.binding("key.socialInteractions");
            t.bind("key.socialInteractions", "alt+" + binding.getDefaultKey().getTranslationKey());
            t.check("record: a combination on the default key is not 'default'", !binding.isDefault());
            binding.setBoundKey(binding.getDefaultKey());
            t.check("record: resetting drops the modifiers", binding.isDefault() && KeyCombos.modifiersOf(binding) == 0);
        });
        t.step("record: escape unbinds", 2, () -> {
            KeyBinding binding = SelfTestRunner.binding("key.socialInteractions");
            KeybindsScreen screen = t.screen(KeybindsScreen.class);
            t.bind("key.socialInteractions", "alt+key.keyboard.f15");
            screen.selectedKeyBinding = binding;
            screen.controlsList.update();
            t.sendKey(InputUtil.GLFW_KEY_ESCAPE, true, 0);
            t.sendKey(InputUtil.GLFW_KEY_ESCAPE, false, 0);
            t.check("record: Escape leaves it unbound without modifiers", binding.isUnbound() && KeyCombos.modifiersOf(binding) == 0);
            t.bind("key.socialInteractions", original[0]);
        });
        t.step("record: close", 2, () -> {
            client().setScreen(t.homeScreen);
            KeyBindProfilesPlus.settings().setReplaceKeyBinds(true);
        });
    }

    // ------------------------------------------------------------------ examples that make the screenshots meaningful

    /**
     * Leaves a few situations in the live key bindings for the screenshots: a real conflict, a
     * possible one, a harmless overlap with an F3 combination, a combination, and a clash with a
     * Meteor module from the fixture files.
     */
    void setUpVisibleExamples() {
        KeySourceResolver sources = KeyConflicts.sources(client().options);
        // F13 / F14 are used so the examples do not depend on the key layout the dev client happens to have.
        t.bind("key.inventory", "key.keyboard.f13");
        t.bind("key.drop", "key.keyboard.f13");
        t.bind("key.loadToolbarActivator", "key.keyboard.f14");
        t.bind("key.sprint", "key.keyboard.f14");
        t.bind("key.playerlist", "ctrl+key.keyboard.f16");
        t.bind("key.smoothCamera", "key.keyboard.z");
        for (KeyBinding debug : client().options.allKeys) {
            if (KeyConflicts.scopeOf(debug, sources) != KeyConflicts.Scope.DEBUG_COMBO || debug.isUnbound()) {
                continue;
            }
            boolean usedElsewhere = false;
            for (KeyBinding other : client().options.allKeys) {
                if (other != debug && other.equals(debug) && KeyConflicts.scopeOf(other, sources) != KeyConflicts.Scope.DEBUG_COMBO) {
                    usedElsewhere = true;
                    break;
                }
            }
            for (io.github.autyi6969.keybindprofilesplus.external.ExternalBinding external : io.github.autyi6969.keybindprofilesplus.external.ExternalKeys.active()) {
                usedElsewhere |= external.key() != null && external.key().getTranslationKey().equals(debug.getBoundKeyTranslationKey());
            }
            if (!usedElsewhere) {
                t.bind("key.advancements", debug.getBoundKeyTranslationKey());
                SelfTestRunner.log("examples: advancements now shares " + SelfTestRunner.keyLabel("key.advancements") + " with F3 combination " + debug.getId());
                break;
            }
        }
        t.check("examples: advancements shares its key only with an F3 combination",
                KeyConflicts.conflictsOf(SelfTestRunner.binding("key.advancements"), client().options).isEmpty());
        t.check("examples: the cinematic camera on Z clashes with the Meteor module on Z",
                KeyConflicts.conflictsOf(SelfTestRunner.binding("key.smoothCamera"), client().options).stream().anyMatch(conflict -> conflict.external() != null));
    }

    // ------------------------------------------------------------------ the tour

    /** Walks through every screen once in the current language; {@code tag} is "en" or "zh". */
    void tour(String tag) {
        boolean english = tag.equals("en");

        // --- main screen: selecting, marking a second profile, comparing two
        t.open("main screen", () -> new KeyBindProfileScreen(null));
        if (english) {
            t.shot(tag + "_main_nothing_selected");
            t.step("main: nothing selected yet", () -> {
                KeyBindProfileScreen main = t.screen(KeyBindProfileScreen.class);
                t.check("main: every profile is listed (" + main.visibleProfileCount() + ")", main.visibleProfileCount() == service.profiles().size());
                t.check("main: Apply, Edit, Copy Code and Delete are disabled until a profile is selected",
                        !t.widget(translated("keybindprofilesplus.apply")).active && !t.widget(translated("keybindprofilesplus.edit")).active
                                && !t.widget(translated("keybindprofilesplus.share.copy")).active
                                && !t.widget(translated("keybindprofilesplus.delete")).active && main.shareCode() == null);
            });
        }
        t.step("main: click the row of " + PROFILE_A, 3, () -> {
            KeyBindProfileScreen main = t.screen(KeyBindProfileScreen.class);
            t.mouseClick(main.hitPoint(PROFILE_A), 0);
            t.check("main: a click selects the profile", PROFILE_A.equals(main.selectedProfile()));
        });
        t.step("main: Ctrl+click the row of " + PROFILE_C, 3, () -> {
            KeyBindProfileScreen main = t.screen(KeyBindProfileScreen.class);
            t.mouseClick(main.hitPoint(PROFILE_C), KeyCombo.CTRL);
            t.check("main: Ctrl+click marks a second profile and keeps the first selected",
                    PROFILE_A.equals(main.selectedProfile()) && PROFILE_C.equals(main.compareMark()));
        });
        t.shot(tag + "_main_two_profiles_selected");
        t.step("main: compare the two selected profiles", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.compare.open_two"));
            t.check("compare: opened with the two selected profiles",
                    t.isScreen(ProfileCompareScreen.class) && PROFILE_A.equals(t.screen(ProfileCompareScreen.class).leftSide())
                            && PROFILE_C.equals(t.screen(ProfileCompareScreen.class).rightSide()));
        });
        t.shot(tag + "_compare_two_profiles");
        if (english) {
            t.step("compare: only differences", 3, () -> {
                ProfileCompareScreen compare = t.screen(ProfileCompareScreen.class);
                compare.setOnlyDifferences(true);
                t.check("compare: only-differences shows exactly the differing rows (" + compare.visibleRowCount() + " of " + compare.differenceCount() + ")",
                        compare.differenceCount() >= 1 && compare.visibleRowCount() == compare.differenceCount());
            });
            t.shot(tag + "_compare_only_differences");
            t.step("compare: identical sides", 3, () -> {
                ProfileCompareScreen compare = t.screen(ProfileCompareScreen.class);
                compare.setSides(PROFILE_C, PROFILE_C);
                t.check("compare: a profile against itself has no differences", compare.differenceCount() == 0 && compare.visibleRowCount() == 0);
            });
            t.shot(tag + "_compare_no_differences");
        }
        t.step("compare: done", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("compare: done returns to the main screen", t.isScreen(KeyBindProfileScreen.class));
        });
        if (english) {
            t.step("main: right-click unmarks, then compare with the current settings", SCREEN_SETTLE_TICKS, () -> {
                KeyBindProfileScreen main = t.screen(KeyBindProfileScreen.class);
                main.toggleCompareMark(PROFILE_C);
                t.check("main: the mark is gone", main.compareMark() == null);
                t.click(translated("keybindprofilesplus.compare.open"));
                t.check("compare: one selected profile is compared with the current settings",
                        t.isScreen(ProfileCompareScreen.class) && PROFILE_A.equals(t.screen(ProfileCompareScreen.class).leftSide())
                                && t.screen(ProfileCompareScreen.class).rightSide() == null);
            });
            t.step("compare: done", SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.done")));
        } else {
            t.step("main: unmark", () -> t.screen(KeyBindProfileScreen.class).toggleCompareMark(PROFILE_C));
        }

        // --- key binds screen
        t.step("main: open the key binds screen", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.overview.open"));
            t.check("overview: opened", t.isScreen(KeyOverviewScreen.class));
        });
        t.shot(tag + "_overview_by_category");
        t.step("overview: conflict markers", () -> {
            String hardKey = SelfTestRunner.keyLabel("key.inventory");
            String softKey = SelfTestRunner.keyLabel("key.loadToolbarActivator");
            String debugKey = SelfTestRunner.keyLabel("key.advancements");
            t.check("overview: the real conflict on " + hardKey + " is marked", t.hasWidget("[ " + hardKey + " ]"));
            t.check("overview: the possible conflict on " + softKey + " is marked", t.hasWidget("[ " + softKey + " ]"));
            t.check("overview: sharing " + debugKey + " with an F3 combination is not marked", !t.hasWidget("[ " + debugKey + " ]") && t.hasWidget(debugKey));
            t.check("overview: a key that clashes with a Meteor module is marked", t.hasWidget("[ " + SelfTestRunner.keyLabel("key.smoothCamera") + " ]"));
            t.check("overview: attack and use, which only share the mouse buttons with Litematica's tool, are not marked",
                    !t.hasWidget("[ " + SelfTestRunner.keyLabel("key.attack") + " ]") && !t.hasWidget("[ " + SelfTestRunner.keyLabel("key.use") + " ]"));
            t.check("overview: combinations are shown as such", t.hasWidget("Ctrl + F16"));
            KeyConflicts.Summary summary = KeyConflicts.summarize(client().options);
            t.check("overview: summary counts them (" + summary.hard() + " conflicts, " + summary.soft() + " possible)", summary.hard() >= 3 && summary.soft() >= 2);
        });
        if (english) {
            t.step("overview: compare button", SCREEN_SETTLE_TICKS, () -> {
                t.click(translated("keybindprofilesplus.compare.open_short"));
                t.check("overview: Compare Profiles opens the comparison against the current settings",
                        t.isScreen(ProfileCompareScreen.class) && t.screen(ProfileCompareScreen.class).rightSide() == null);
            });
            t.shot(tag + "_compare_from_keybinds");
            t.step("overview: compare done", SCREEN_SETTLE_TICKS, () -> {
                t.click(translated("gui.done"));
                t.check("overview: the comparison returns to the key binds screen", t.isScreen(KeyOverviewScreen.class));
            });
            t.step("overview: external groups", () -> {
                KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
                List<String> headings = overview.groupHeadings();
                t.check("overview: Meteor and malilib hotkeys appear as their own groups " + headings,
                        headings.contains("Meteor") && headings.contains("Meteor profile: pvp") && headings.contains("Litematica") && headings.contains("Item Scroller"));
                t.check("overview: sources can be picked one by one " + overview.sourceFilterValues(),
                        overview.sourceFilterValues().containsAll(List.of("KeyBind Profiles+", "Fabric API", "Meteor", "Litematica")));
            });
        }
        t.step("overview: group by source", 3, () -> {
            KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
            overview.setGroupBySource(true);
            List<String> headings = overview.groupHeadings();
            t.check("overview: grouped by source, Minecraft first " + headings, !headings.isEmpty() && headings.get(0).equals("Minecraft") && headings.contains("Meteor"));
        });
        t.shot(tag + "_overview_by_source");
        if (english) {
            t.step("overview: one source only", 3, () -> {
                KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
                overview.setSourceFilter("Meteor");
                t.check("overview: filtering by Meteor shows its 8 hotkeys, got " + overview.visibleBindingCount(), overview.visibleBindingCount() == 8);
            });
            t.shot(tag + "_overview_meteor_only");
            t.step("overview: all mods", 3, () -> {
                KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
                overview.setGroupBySource(false);
                overview.setSourceFilter(KeyOverviewScreen.FILTER_VANILLA);
                int vanilla = overview.visibleBindingCount();
                overview.setSourceFilter(KeyOverviewScreen.FILTER_MODS);
                int mods = overview.visibleBindingCount();
                overview.setSourceFilter(KeyOverviewScreen.FILTER_ALL);
                t.check("overview: Minecraft (" + vanilla + ") + mods (" + mods + ") = everything (" + overview.visibleBindingCount() + ")",
                        vanilla > 50 && mods >= 4 && vanilla + mods == overview.visibleBindingCount());
            });
            t.step("overview: conflicts only", 3, () -> {
                KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
                overview.setConflictsOnly(true);
                t.check("overview: conflicts-only shows the conflicting keys and the Meteor module, got " + overview.visibleBindingCount(),
                        overview.visibleBindingCount() >= 6 && overview.visibleBindingCount() < 30);
            });
            t.shot(tag + "_overview_conflicts_only");
            t.step("overview: search", 3, () -> {
                KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
                overview.setConflictsOnly(false);
                overview.setQuery("auto totem");
                t.check("overview: search finds Meteor modules by name, got " + overview.visibleBindingCount(), overview.visibleBindingCount() == 3);
                overview.setQuery("zzzz no such key");
                t.check("overview: a search without hits shows nothing", overview.visibleBindingCount() == 0);
                overview.setQuery("");
            });
            t.step("overview: change when a mod key is active", 3, () -> {
                KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
                t.check("overview: clicking a mod key row cycles automatic -> play -> screens -> special situation -> automatic",
                        KeyConflicts.OVERRIDE_GENERAL.equals(overview.cycleScope(DEMO_MOD_BINDING))
                                && KeyConflicts.OVERRIDE_SCREEN.equals(overview.cycleScope(DEMO_MOD_BINDING))
                                && KeyConflicts.OVERRIDE_SITUATIONAL.equals(overview.cycleScope(DEMO_MOD_BINDING))
                                && overview.cycleScope(DEMO_MOD_BINDING) == null);
                t.check("overview: vanilla keys cannot be changed that way", overview.cycleScope("key.jump") == null
                        && KeyBindProfilesPlus.settings().scopeOverride("key.jump") == null);
                t.check("overview: F3 combinations cannot be changed that way either", overview.cycleScope("key.debug.showHitboxes") == null);
            });
            t.step("overview: change when another mod's hotkey is in use", 3, () -> {
                KeyOverviewScreen overview = t.screen(KeyOverviewScreen.class);
                String attackKey = SelfTestRunner.keyLabel("key.attack");
                t.check("overview: a click on Litematica's tool key row makes it count as used during play",
                        KeyConflicts.OVERRIDE_GENERAL.equals(overview.cycleExternalScope("Litematica", "Tool Place Corner 1")));
                t.check("overview: ... and attack is then marked as clashing with it", t.hasWidget("[ " + attackKey + " ]"));
                overview.cycleExternalScope("Litematica", "Tool Place Corner 1");
                overview.cycleExternalScope("Litematica", "Tool Place Corner 1");
                t.check("overview: clicking on round to automatic removes the mark again",
                        overview.cycleExternalScope("Litematica", "Tool Place Corner 1") == null && !t.hasWidget("[ " + attackKey + " ]"));
            });
            t.step("overview: manage profiles button", SCREEN_SETTLE_TICKS, () -> {
                t.click(translated("keybindprofilesplus.open"));
                t.check("overview: opened from the main screen, Manage Profiles simply goes back to it", t.isScreen(KeyBindProfileScreen.class));
                t.click(translated("keybindprofilesplus.overview.open"));
            });
        }
        t.step("overview: done", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("overview: done returns to the main screen", t.isScreen(KeyBindProfileScreen.class));
        });

        // --- apply with confirmation
        t.step("make the game differ from " + PROFILE_A, logic::makeGameDifferFromProfileA);
        t.step("main: apply (expect the confirm screen)", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.apply"));
            t.check("apply with pending changes opens the confirm screen", t.isScreen(ApplyConfirmScreen.class));
        });
        t.shot(tag + "_apply_confirm");
        t.step("confirm: cancel", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.cancel"));
            t.check("confirm: cancel returns to the main screen and applies nothing",
                    t.isScreen(KeyBindProfileScreen.class) && TEST_KEY.equals(SelfTestRunner.binding(TEST_BINDING_ID).getBoundKeyTranslationKey()));
        });

        // --- edit screen and the contents tree
        t.step("main: edit", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.edit"));
            t.check("edit: opened for the selected profile", t.isScreen(ProfileEditScreen.class) && PROFILE_A.equals(t.screen(ProfileEditScreen.class).profileName()));
        });
        t.shot(tag + "_edit_profile");
        if (english) {
            editFlow();
        }
        t.step("edit: open saved contents", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.contents.open"));
            t.check("contents: opened", t.isScreen(ProfileContentsScreen.class));
        });
        if (english) {
            t.shot(tag + "_contents_collapsed");
        }
        t.step("contents: expand movement keys and video settings", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.check("contents: movement group can be expanded", contents.setExpanded("keys/minecraft:movement", true));
            t.check("contents: video group can be expanded", contents.setExpanded("options/video", true));
        });
        t.shot(tag + "_contents_expanded");
        if (english) {
            contentsFlow();
            t.step("edit: no share button in here", () ->
                    t.check("edit: the share code button is not hidden inside the edit screen", !t.hasWidget(translated("keybindprofilesplus.share.copy"))));
        } else {
            t.step("contents: cancel", SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.cancel")));
        }
        t.step("edit: done", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("edit: done returns to the main screen", t.isScreen(KeyBindProfileScreen.class));
        });
        if (english) {
            t.step("main: share code", 3, () -> {
                // The code is taken from the screen directly: the self-test leaves the system clipboard alone.
                shareCode = t.screen(KeyBindProfileScreen.class).shareCode();
                t.check("main: the selected profile has a share code and an enabled button to copy it, right on the main screen",
                        shareCode != null && shareCode.startsWith(ShareCode.PREFIX) && t.widget(translated("keybindprofilesplus.share.copy")).active);
            });
        }

        // --- new profile
        t.step("main: new profile", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.new"));
            t.check("new profile: the contents tree opens", t.isScreen(ProfileContentsScreen.class));
        });
        t.shot(tag + "_new_profile");
        if (english) {
            newProfileFlow();
        } else {
            t.step("new profile: cancel", SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.cancel")));
        }

        // --- import
        t.step("main: import", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.import"));
            t.check("import: opened", t.isScreen(ImportScreen.class));
        });
        t.step("import: paste something that is not a code", 3, () -> {
            ImportScreen screen = t.screen(ImportScreen.class);
            screen.setCode("this is not a share code");
            t.check("import: refused with an explanation, Import stays disabled",
                    screen.problem() == ShareCode.Problem.NOT_A_CODE && !screen.canImport());
        });
        t.shot(tag + "_import_error");
        if (english) {
            importFlow();
        } else {
            t.step("import: cancel", SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.cancel")));
        }

        // --- settings and rules
        t.step("main: settings", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.settings.open"));
            t.check("settings: opened", t.isScreen(SettingsScreen.class));
        });
        t.shot(tag + "_settings");
        if (english) {
            settingsFlow();
        }
        t.step("settings: done", SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.done")));
        t.step("main: rules", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.rules.open"));
            t.check("rules: opened", t.isScreen(ServerRulesScreen.class));
        });
        if (english) {
            rulesFlow();
        }
        t.shot(tag + "_server_rules");
        t.step("rules: done", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("rules: done returns to the main screen", t.isScreen(KeyBindProfileScreen.class));
        });

        // --- the Key Binds screen as the options menu opens it
        keyBindsFromOptions(tag);
        if (english) {
            // ... and the vanilla screen with the mod's additions, for players who switch the replacement off.
            t.step("vanilla mode: switch the replacement off", () -> KeyBindProfilesPlus.settings().setReplaceKeyBinds(false));
            vanillaKeyBinds(tag, true);
            t.step("vanilla mode: switch the replacement back on", () -> KeyBindProfilesPlus.settings().setReplaceKeyBinds(true));
        }
    }

    private void keyBindsFromOptions(String tag) {
        t.open("key binds screen from the options", () -> new KeybindsScreen(t.homeScreen, client().options));
        t.step("keys: buttons", () -> {
            t.check("keys: the options menu's Key Binds button leads to the mod's screen", t.isScreen(KeyOverviewScreen.class));
            t.check("keys: it has Manage Profiles, Compare Profiles, Reset Keys and Done",
                    t.hasWidget(translated("keybindprofilesplus.open")) && t.hasWidget(translated("keybindprofilesplus.compare.open_short"))
                            && t.hasWidget(translated("controls.resetAll")) && t.hasWidget(translated("gui.done")));
        });
        t.shot(tag + "_keybinds_from_options");
        t.step("keys: manage button", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.open"));
            t.check("keys: Manage Profiles opens the main screen", t.isScreen(KeyBindProfileScreen.class));
        });
        t.step("keys: its Key Binds button goes back instead of stacking another screen", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.overview.open"));
            t.check("keys: back on the key binds screen", t.isScreen(KeyOverviewScreen.class));
        });
        t.step("keys: done", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("keys: Done leads back to where the options menu's screen would have", client().currentScreen == t.homeScreen);
        });
    }

    /** The configure button Mod Menu shows for the mod: the settings screen, with the way on to everything else. */
    void modMenu() {
        Screen[] settingsScreen = new Screen[1];
        t.step("mod menu: the mod offers a configure screen", SCREEN_SETTLE_TICKS, () -> {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
                t.fail("mod menu: Mod Menu is not in the dev client, its configure button cannot be checked");
                return;
            }
            for (var container : net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypointContainers("modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class)) {
                if (container.getProvider().getMetadata().getId().equals(KeyBindProfilesPlus.MOD_ID)) {
                    settingsScreen[0] = container.getEntrypoint().getModConfigScreenFactory().create(t.homeScreen);
                }
            }
            t.check("mod menu: the configure button opens the settings screen", settingsScreen[0] instanceof SettingsScreen);
            if (settingsScreen[0] != null) {
                client().setScreen(settingsScreen[0]);
            }
        });
        t.shot("en_settings_from_mod_menu");
        t.step("mod menu: on to the profiles", SCREEN_SETTLE_TICKS, () -> {
            t.check("mod menu: opened from the mod list, the settings screen offers the profiles and the key binds",
                    t.hasWidget(translated("keybindprofilesplus.open")) && t.hasWidget(translated("keybindprofilesplus.overview.open")));
            t.click(translated("keybindprofilesplus.open"));
            t.check("mod menu: Manage Profiles opens the main screen", t.isScreen(KeyBindProfileScreen.class));
        });
        t.step("mod menu: and back", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("mod menu: Done returns to the settings screen", client().currentScreen == settingsScreen[0]);
            t.click(translated("gui.done"));
        });
        t.step("mod menu: done", 2, () -> t.check("mod menu: Done on the settings screen returns to the mod list", client().currentScreen == t.homeScreen));
    }

    private void editFlow() {
        ModSettings settings = KeyBindProfilesPlus.settings();
        t.step("edit: rename", 3, () -> {
            ProfileEditScreen edit = t.screen(ProfileEditScreen.class);
            edit.setNameText("bad/name");
            t.click(translated("keybindprofilesplus.rename"));
            t.check("edit: a name that cannot be a file name is refused", service.profiles().containsKey(PROFILE_A));
            edit.setNameText(PROFILE_C);
            t.click(translated("keybindprofilesplus.rename"));
            t.check("edit: the name of another profile is refused", service.profiles().containsKey(PROFILE_A) && service.profiles().containsKey(PROFILE_C));
            edit.setNameText(PROFILE_RENAMED);
            t.click(translated("keybindprofilesplus.rename"));
            t.check("edit: rename works", service.profiles().containsKey(PROFILE_RENAMED) && !service.profiles().containsKey(PROFILE_A)
                    && PROFILE_RENAMED.equals(edit.profileName()) && logic.profileFile(PROFILE_RENAMED).isFile() && !logic.profileFile(PROFILE_A).exists());
        });
        t.step("edit: rename back", 3, () -> {
            ProfileEditScreen edit = t.screen(ProfileEditScreen.class);
            edit.setNameText(PROFILE_A);
            t.click(translated("keybindprofilesplus.rename"));
            t.check("edit: renamed back", service.profiles().containsKey(PROFILE_A) && PROFILE_A.equals(edit.profileName()));
        });
        t.step("edit: record a hotkey", 3, () -> {
            t.click(translated("keybindprofilesplus.hotkey.none"));
            t.sendKey(InputUtil.GLFW_KEY_F7, true, 0);
            t.sendKey(InputUtil.GLFW_KEY_F7, false, 0);
            t.check("edit: the button shows the key being recorded", t.hasWidget("F7 ..."));
            t.sendKey(InputUtil.GLFW_KEY_ENTER, true, 0);
            t.sendKey(InputUtil.GLFW_KEY_ENTER, false, 0);
            t.check("edit: Enter saves the hotkey", List.of("key.keyboard.f7").equals(service.getProfileHotkey(PROFILE_A)) && t.hasWidget("F7"));
        });
        t.step("edit: clear the hotkey", 3, () -> {
            t.click(translated("keybindprofilesplus.hotkey.clear"));
            t.check("edit: Clear removes the hotkey", service.getProfileHotkey(PROFILE_A) == null && t.hasWidget(translated("keybindprofilesplus.hotkey.none")));
        });
        t.step("edit: auto-switch rules", 3, () -> {
            ProfileEditScreen edit = t.screen(ProfileEditScreen.class);
            t.click(translated("keybindprofilesplus.server.add_singleplayer"));
            t.check("edit: + Singleplayer adds the rule", List.of("singleplayer").equals(service.getProfileAutoSwitchServers(PROFILE_A)));
            t.click(translated("keybindprofilesplus.server.add_singleplayer"));
            t.check("edit: the same rule twice is refused", List.of("singleplayer").equals(service.getProfileAutoSwitchServers(PROFILE_A)));
            edit.addRule("two words");
            t.check("edit: a rule with a space is refused", List.of("singleplayer").equals(service.getProfileAutoSwitchServers(PROFILE_A)));
            t.type("keybindprofilesplus.server_address", "*.selftest.example");
            t.click(translated("keybindprofilesplus.add_server"));
            t.check("edit: typing a rule and pressing Add adds it (and a rule another profile has is still allowed)",
                    List.of("singleplayer", "*.selftest.example").equals(service.getProfileAutoSwitchServers(PROFILE_A)));
        });
        t.shot("en_edit_profile_with_rules");
        t.step("edit: remove the rules", 3, () -> {
            ProfileEditScreen edit = t.screen(ProfileEditScreen.class);
            edit.removeRule("*.selftest.example");
            t.click(translated("keybindprofilesplus.remove_server"));
            t.check("edit: Remove takes a rule away", service.getProfileAutoSwitchServers(PROFILE_A) == null);
        });
        t.step("edit: default profile switch", 3, () -> {
            String before = settings.defaultProfile();
            t.click(translated("keybindprofilesplus.edit.is_default") + ": " + translated("options.off"));
            t.check("edit: the switch makes this the default profile", PROFILE_A.equals(settings.defaultProfile()));
            settings.setDefaultProfile(before);
        });
    }

    private void contentsFlow() {
        // Real mouse clicks, one per step so the rows are laid out again in between.
        t.step("contents: click a group label", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            contents.setExpanded("options/video", false);
        });
        t.step("contents: click a group label (2)", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.mouseClick(contents.hitPoint("options/video", false), 0);
            t.check("contents: clicking a group label expands it", contents.isExpanded("options/video"));
        });
        t.step("contents: click a check box", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.mouseClick(contents.hitPoint("opt:ao", true), 0);
            t.check("contents: clicking a check box ticks the item", contents.isChecked("opt:ao"));
        });
        t.step("contents: click an item label", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.mouseClick(contents.hitPoint("opt:ao", false), 0);
            t.check("contents: clicking the item again unticks it", !contents.isChecked("opt:ao"));
        });
        t.step("contents: click a group check box", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.mouseClick(contents.hitPoint("keys/minecraft:multiplayer", true), 0);
            t.check("contents: clicking a group check box ticks the whole group and does not expand it",
                    contents.checkedCount(true) == 5 && !contents.isExpanded("keys/minecraft:multiplayer"));
        });
        t.step("contents: click the group check box again", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.mouseClick(contents.hitPoint("keys/minecraft:multiplayer", true), 0);
            t.check("contents: clicking it again unticks the group", contents.checkedCount(true) == 1);
            contents.setExpanded("options/video", false);
        });
        t.step("contents: tick more items", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.check("contents: starts with 1 key and 2 settings", contents.checkedCount(true) == 1 && contents.checkedCount(false) == 2);
            t.check("contents: tick brightness", contents.setChecked("opt:gamma", true));
            t.check("contents: tick the whole movement group", contents.setChecked("keys/minecraft:movement", true));
            t.check("contents: untick auto-jump", contents.setChecked("opt:autoJump", false));
            t.check("contents: 7 movement keys and 2 settings ticked, got " + contents.checkedCount(true) + " and " + contents.checkedCount(false),
                    contents.checkedCount(true) == 7 && contents.checkedCount(false) == 2);
            contents.setQuery("sensitivity");
            t.check("contents: search narrows the tree, rows=" + contents.visibleRowCount(), contents.visibleRowCount() > 0 && contents.visibleRowCount() < 10);
        });
        t.shot("en_contents_search");
        t.step("contents: save", SCREEN_SETTLE_TICKS, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            contents.setQuery("");
            t.click(translated("gui.done"));
            t.check("contents: Done returns to the edit screen", t.isScreen(ProfileEditScreen.class));
            Map<String, String> keys = service.profiles().get(PROFILE_A);
            Map<String, String> saved = service.getProfileOptions(PROFILE_A);
            t.check("contents: profile saves the 7 movement keys", keys.size() == 7 && keys.containsKey("key.sneak"));
            t.check("contents: the previously saved jump key is kept", PROFILE_A_KEY.equals(keys.get(TEST_BINDING_ID)));
            t.check("contents: profile saves fov and brightness, not auto-jump",
                    saved.size() == 2 && saved.containsKey("fov") && saved.containsKey("gamma") && !saved.containsKey("autoJump"));
            t.bind(TEST_BINDING_ID, TEST_KEY);
        });
        t.step("contents: reopen", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.contents.open"));
            t.screen(ProfileContentsScreen.class).setExpanded("keys/minecraft:movement", true);
        });
        t.shot("en_contents_saved_differs_from_now");
        t.step("contents: use current values", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.contents.recapture"));
            t.click(translated("gui.done"));
            t.check("contents: use-current-values stored the live jump key", TEST_KEY.equals(service.profiles().get(PROFILE_A).get(TEST_BINDING_ID)));
        });
    }

    /**
     * The acceptance scenario for partial profiles: a new profile that saves only FOV must change
     * FOV and nothing else when applied.
     */
    private void newProfileFlow() {
        String[] sensitivityBefore = new String[1];
        t.step("new profile: a name is required", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            t.check("new profile: every key binding is ticked to begin with, no other setting",
                    contents.checkedCount(true) == client().options.allKeys.length && contents.checkedCount(false) == 0);
            contents.save();
            t.check("new profile: Done without a name is refused with a message", t.isScreen(ProfileContentsScreen.class) && contents.errorText() != null);
            contents.setProfileName(PROFILE_C.toUpperCase(java.util.Locale.ROOT));
            contents.save();
            t.check("new profile: an existing name (in other capitals) is refused", t.isScreen(ProfileContentsScreen.class) && contents.errorText() != null);
        });
        t.step("new profile: only FOV", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            contents.setProfileName(PROFILE_FOV_ONLY);
            contents.setChecked("keys", false);
            contents.setChecked("opt:fov", true);
            contents.setExpanded("options/video", true);
            t.check("new profile: nothing but FOV is ticked", contents.checkedCount(true) == 0 && contents.checkedCount(false) == 1);
        });
        t.shot("en_new_profile_fov_only");
        t.step("new profile: create", SCREEN_SETTLE_TICKS, () -> {
            t.check("new profile: no \"use current values\" button, it would do nothing here", !t.hasWidget(translated("keybindprofilesplus.contents.recapture")));
            t.click(translated("keybindprofilesplus.new.create"));
            t.check("new profile: created and selected on the main screen",
                    t.isScreen(KeyBindProfileScreen.class) && PROFILE_FOV_ONLY.equals(t.screen(KeyBindProfileScreen.class).selectedProfile()));
            t.check("new profile: it saves FOV and nothing else", service.profiles().containsKey(PROFILE_FOV_ONLY)
                    && service.profiles().get(PROFILE_FOV_ONLY).isEmpty() && service.getProfileOptions(PROFILE_FOV_ONLY).keySet().equals(java.util.Set.of("fov")));

            sensitivityBefore[0] = SelfTestRunner.liveOption("mouseSensitivity");
            String otherSensitivity = sensitivityBefore[0].equals("0.25") ? "0.75" : "0.25";
            GameOptionsBridge.apply(client().options, Map.of("fov", "1.0", "mouseSensitivity", otherSensitivity));
            t.bind(TEST_BINDING_ID, PROFILE_A_KEY);
        });
        t.step("new profile: apply it", SCREEN_SETTLE_TICKS, () -> {
            String sensitivityNow = SelfTestRunner.liveOption("mouseSensitivity");
            t.click(translated("keybindprofilesplus.apply"));
            if (t.isScreen(ApplyConfirmScreen.class)) {
                t.pass("the confirm screen is shown for a profile that only saves a game setting");
                t.screen(ApplyConfirmScreen.class).confirm(false);
            } else {
                t.fail("expected the confirm screen");
            }
            t.check("acceptance: FOV changed back to the saved value", logic.originalFov.equals(SelfTestRunner.liveOption("fov")));
            t.check("acceptance: mouse sensitivity, which the profile does not save, stayed as it was",
                    sensitivityNow.equals(SelfTestRunner.liveOption("mouseSensitivity")) && !sensitivityNow.equals(sensitivityBefore[0]));
            t.check("acceptance: key bindings, which the profile does not save, stayed as they were",
                    PROFILE_A_KEY.equals(SelfTestRunner.binding(TEST_BINDING_ID).getBoundKeyTranslationKey()));
            GameOptionsBridge.apply(client().options, Map.of("mouseSensitivity", sensitivityBefore[0]));
            t.bind(TEST_BINDING_ID, TEST_KEY);
        });
        t.step("new profile: delete it from the main screen", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.delete"));
            t.check("delete: asks for confirmation", t.isScreen(ConfirmScreen.class) && service.profiles().containsKey(PROFILE_FOV_ONLY));
        });
        t.step("delete: no", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.no"));
            t.check("delete: answering No keeps the profile", t.isScreen(KeyBindProfileScreen.class) && service.profiles().containsKey(PROFILE_FOV_ONLY));
            t.click(translated("keybindprofilesplus.delete"));
        });
        t.step("delete: yes", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.yes"));
            t.check("delete: answering Yes deletes it", t.isScreen(KeyBindProfileScreen.class) && !service.profiles().containsKey(PROFILE_FOV_ONLY)
                    && !logic.profileFile(PROFILE_FOV_ONLY).exists());
            t.screen(KeyBindProfileScreen.class).select(PROFILE_A);
        });
    }

    private void importFlow() {
        String[] imported = new String[1];
        t.step("import: a damaged code", 3, () -> {
            ImportScreen screen = t.screen(ImportScreen.class);
            screen.setCode(shareCode.substring(0, shareCode.length() - 6));
            t.check("import: a cut-off code is refused as damaged", screen.problem() == ShareCode.Problem.CORRUPTED && !screen.canImport());
        });
        t.step("import: the real code", 3, () -> {
            ImportScreen screen = t.screen(ImportScreen.class);
            screen.setCode(shareCode.substring(0, 30) + "\n  " + shareCode.substring(30));
            t.check("import: the code copied earlier is accepted", screen.problem() == null && screen.canImport());
            t.check("import: the name field is filled in, moved aside because the profile already exists",
                    t.hasWidget(translated("keybindprofilesplus.profile_name")));
        });
        t.shot("en_import_valid");
        t.step("import: confirm", SCREEN_SETTLE_TICKS, () -> {
            ImportScreen screen = t.screen(ImportScreen.class);
            screen.setName(PROFILE_A);
            t.check("import: an existing name disables Import", !screen.canImport());
            imported[0] = PROFILE_A + " (2)";
            screen.setName(imported[0]);
            t.click(translated("keybindprofilesplus.import.confirm"));
            t.check("import: back on the main screen with the new profile selected",
                    t.isScreen(KeyBindProfileScreen.class) && imported[0].equals(t.screen(KeyBindProfileScreen.class).selectedProfile()));
            t.check("import: the imported profile has exactly the same contents",
                    service.profiles().get(PROFILE_A).equals(service.profiles().get(imported[0]))
                            && service.getProfileOptions(PROFILE_A).equals(service.getProfileOptions(imported[0])));
            service.reloadProfiles();
            t.check("import: ... also after reading it back from its file",
                    service.profiles().get(PROFILE_A).equals(service.profiles().get(imported[0]))
                            && service.getProfileOptions(PROFILE_A).equals(service.getProfileOptions(imported[0])));
            service.deleteProfile(imported[0]);
        });
        t.step("import: reopen the main screen", SCREEN_SETTLE_TICKS, () -> {
            client().setScreen(new KeyBindProfileScreen(null));
        });
        t.step("main: select " + PROFILE_A, 2, () -> t.screen(KeyBindProfileScreen.class).select(PROFILE_A));
    }

    private void settingsFlow() {
        ModSettings settings = KeyBindProfilesPlus.settings();
        t.step("settings: ask-before-applying switch", 3, () -> {
            boolean before = settings.confirmApply();
            t.click(translated("keybindprofilesplus.confirm.toggle") + ": " + translated(before ? "options.on" : "options.off"));
            t.check("settings: the switch turns asking " + (before ? "off" : "on"), settings.confirmApply() != before);
            settings.setConfirmApply(before);
        });
        t.step("settings: auto-switch and back-to-default switches", 3, () -> {
            boolean autoBefore = settings.autoSwitch();
            boolean returnBefore = settings.returnToDefault();
            t.click(translated("keybindprofilesplus.server.auto_switch_toggle") + ": " + translated(autoBefore ? "options.on" : "options.off"));
            t.click(translated("keybindprofilesplus.settings.return_to_default") + ": " + translated(returnBefore ? "options.on" : "options.off"));
            t.check("settings: both switches work", settings.autoSwitch() != autoBefore && settings.returnToDefault() != returnBefore);
            settings.setAutoSwitch(autoBefore);
            settings.setReturnToDefault(returnBefore);
        });
        t.step("settings: replace-the-vanilla-screen switch", 3, () -> {
            KeybindsScreen vanilla = new KeybindsScreen(t.homeScreen, client().options);
            t.check("settings: the vanilla Key Binds screen is replaced to begin with", settings.replaceKeyBinds()
                    && KeyOverviewScreen.replacementFor(vanilla) instanceof KeyOverviewScreen);
            t.check("settings: opened from the main screen there is no extra 'Manage Profiles' row", !t.hasWidget(translated("keybindprofilesplus.open")));
            t.click(translated("keybindprofilesplus.settings.replace_key_binds") + ": " + translated("options.on"));
            t.check("settings: the switch turns the replacement off", !settings.replaceKeyBinds() && KeyOverviewScreen.replacementFor(vanilla) == vanilla);
            settings.setReplaceKeyBinds(true);
        });
        t.step("settings: default profile picker", 3, () -> {
            settings.setDefaultProfile(null);
            client().setScreen(new SettingsScreen(new KeyBindProfileScreen(null), service));
        });
        t.step("settings: default profile picker (2)", 3, () -> {
            t.click(translated("keybindprofilesplus.settings.default_profile") + ": " + translated("keybindprofilesplus.settings.default_profile.none"));
            String first = service.profiles().keySet().stream().min(String.CASE_INSENSITIVE_ORDER).orElse(null);
            t.check("settings: the picker moves to the first profile (" + first + ")", first != null && first.equals(settings.defaultProfile()));
            settings.setDefaultProfile(null);
        });
    }

    private void rulesFlow() {
        t.step("rules: all rules in one place", 3, () -> {
            ServerRulesScreen rules = t.screen(ServerRulesScreen.class);
            ruleBaseline = rules.ruleCount();
            t.check("rules: the rules of " + PROFILE_C + " are listed (" + ruleBaseline + " rules in total)", ruleBaseline >= 2
                    && t.hasWidget(translated("keybindprofilesplus.rules.row", "example.org", PROFILE_C))
                    && t.hasWidget(translated("keybindprofilesplus.rules.row", "*.selftest.example", PROFILE_C)));
            rules.addRule("play.selftest.example:25570", PROFILE_A);
            t.check("rules: a rule can be added to any profile from here",
                    List.of("play.selftest.example:25570").equals(service.getProfileAutoSwitchServers(PROFILE_A)) && rules.ruleCount() == ruleBaseline + 1);
            rules.addRule("has space", PROFILE_A);
            t.check("rules: an invalid rule is refused", rules.ruleCount() == ruleBaseline + 1);
        });
        t.shot("en_server_rules_three");
        t.step("rules: remove", 3, () -> {
            ServerRulesScreen rules = t.screen(ServerRulesScreen.class);
            rules.removeRule("play.selftest.example:25570", PROFILE_A);
            t.check("rules: and removed again", service.getProfileAutoSwitchServers(PROFILE_A) == null && rules.ruleCount() == ruleBaseline);
        });
    }

    private void vanillaKeyBinds(String tag, boolean english) {
        t.open("vanilla key binds screen", () -> new KeybindsScreen(t.homeScreen, client().options));
        t.step("vanilla: buttons and conflict markers", () -> {
            t.check("vanilla Key Binds screen has the manage button", t.hasWidget(translated("keybindprofilesplus.open")));
            t.check("vanilla Key Binds screen has the compare button", t.hasWidget(translated("keybindprofilesplus.compare.open_short")));
            String hardKey = SelfTestRunner.keyLabel("key.inventory");
            String softKey = SelfTestRunner.keyLabel("key.loadToolbarActivator");
            String debugKey = SelfTestRunner.keyLabel("key.advancements");
            t.check("vanilla list: the real conflict on " + hardKey + " is marked", t.hasWidget("[ " + hardKey + " ]"));
            t.check("vanilla list: the possible conflict on " + softKey + " is marked", t.hasWidget("[ " + softKey + " ]"));
            t.check("vanilla list: sharing " + debugKey + " with an F3 combination is not marked",
                    !t.hasWidget("[ " + debugKey + " ]") && t.hasWidget(debugKey));
            t.check("vanilla list: a key that clashes with a Meteor module is marked", t.hasWidget("[ " + SelfTestRunner.keyLabel("key.smoothCamera") + " ]"));
            t.check("vanilla list: combinations are shown as such", t.hasWidget("Ctrl + F16"));
            KeyConflicts.Summary summary = KeyConflicts.summarize(client().options);
            t.check("vanilla list: summary counts them (" + summary.hard() + " conflicts, " + summary.soft() + " possible)",
                    summary.hard() >= 3 && summary.soft() >= 2);
        });
        t.shot(tag + "_vanilla_keybinds");
        t.step("vanilla: compare button", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.compare.open_short"));
            t.check("vanilla: it opens the compare screen against the current settings",
                    t.isScreen(ProfileCompareScreen.class) && t.screen(ProfileCompareScreen.class).rightSide() == null);
        });
        t.step("vanilla: compare done", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("vanilla: compare returns to the Key Binds screen", t.isScreen(KeybindsScreen.class));
        });
        t.step("vanilla: manage button", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.open"));
            t.check("vanilla: it opens the main screen", t.isScreen(KeyBindProfileScreen.class));
        });
        t.step("vanilla: back", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("gui.done"));
            t.check("vanilla: Done returns to the Key Binds screen, which still leads back to where it came from",
                    t.isScreen(KeybindsScreen.class) && t.screen(KeybindsScreen.class).parent == t.homeScreen);
        });
    }

    // ------------------------------------------------------------------ applying for real

    /** Clicks through Apply with and without the confirm screen. */
    void applyFlow() {
        ModSettings settings = KeyBindProfilesPlus.settings();
        KeyBinding drop = SelfTestRunner.binding("key.drop");
        String[] dropBefore = new String[1];

        t.open("main screen", () -> new KeyBindProfileScreen(null));
        t.step("apply: prepare", 2, () -> {
            t.screen(KeyBindProfileScreen.class).select(PROFILE_A);
            service.setProfileContents(PROFILE_A, Map.of(TEST_BINDING_ID, PROFILE_A_KEY), Map.of("autoJump", logic.profileAutoJump, "fov", PROFILE_FOV));
            logic.makeGameDifferFromProfileA();
            dropBefore[0] = KeyCombos.valueOf(drop);
            t.bind("key.drop", "key.keyboard.f17");
            settings.setConfirmApply(true);
        });
        t.step("apply: confirm screen", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.apply"));
            t.check("apply: confirm screen is shown", t.isScreen(ApplyConfirmScreen.class));
        });
        t.step("apply: confirm", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.confirm.apply"));
            t.check("apply: back on the main screen", t.isScreen(KeyBindProfileScreen.class));
            t.check("apply: saved key applied", PROFILE_A_KEY.equals(SelfTestRunner.binding(TEST_BINDING_ID).getBoundKeyTranslationKey()));
            t.check("apply: saved auto-jump applied", logic.profileAutoJump.equals(SelfTestRunner.liveOption("autoJump")));
            t.check("apply: saved fov applied", PROFILE_FOV.equals(SelfTestRunner.liveOption("fov")));
            t.check("apply: fov really changed in the game", Math.abs(client().options.getFov().getValue() - 90) <= 1);
            t.check("apply: a key the profile does not save is left alone", "key.keyboard.f17".equals(drop.getBoundKeyTranslationKey()));
            t.check("apply: profile became current", PROFILE_A.equals(service.getCurrentProfile()));
            t.check("apply: nothing left to change", service.previewApply(PROFILE_A).isEmpty());
            t.check("apply: confirm setting untouched", settings.confirmApply());
            t.check("apply: options.txt has the new fov", logic.optionsFileContains("fov:" + PROFILE_FOV));
        });
        t.step("apply: again (nothing to change, no dialog)", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.apply"));
            t.check("apply: no confirm screen when nothing would change", t.isScreen(KeyBindProfileScreen.class));
        });
        t.step("apply: a double click on the row applies too", SCREEN_SETTLE_TICKS, () -> {
            logic.makeGameDifferFromProfileA();
            KeyBindProfileScreen main = t.screen(KeyBindProfileScreen.class);
            int[] point = main.hitPoint(PROFILE_A);
            client().currentScreen.mouseClicked(new net.minecraft.client.gui.Click(point[0], point[1], new net.minecraft.client.input.MouseInput(0, 0)), true);
            t.check("apply: double click asks like the button does", t.isScreen(ApplyConfirmScreen.class));
        });
        t.step("apply: with do-not-ask-again", SCREEN_SETTLE_TICKS, () -> {
            if (t.isScreen(ApplyConfirmScreen.class)) {
                t.click(translated("keybindprofilesplus.confirm.dont_ask"));
                t.click(translated("keybindprofilesplus.confirm.apply"));
            }
            t.check("apply: do-not-ask-again is remembered", !settings.confirmApply());
        });
        t.step("apply: without asking", SCREEN_SETTLE_TICKS, () -> {
            logic.makeGameDifferFromProfileA();
            t.click(translated("keybindprofilesplus.apply"));
            t.check("apply: applied directly, no confirm screen", t.isScreen(KeyBindProfileScreen.class)
                    && PROFILE_A_KEY.equals(SelfTestRunner.binding(TEST_BINDING_ID).getBoundKeyTranslationKey()));
            settings.reload();
            t.check("apply: the choice is stored on disk", !settings.confirmApply());
        });
        t.step("apply: switch asking back on in the settings screen", SCREEN_SETTLE_TICKS, () -> {
            client().setScreen(new SettingsScreen(client().currentScreen, service));
        });
        t.step("apply: switch asking back on (2)", SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.confirm.toggle") + ": " + translated("options.off"));
            t.check("apply: the settings screen turns asking back on", settings.confirmApply());
            t.bind("key.drop", dropBefore[0]);
            logic.makeGameDifferFromProfileA();
            client().setScreen(t.homeScreen);
        });
    }
}
