package io.github.autyism.keybindprofilesplus.selftest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyism.keybindprofilesplus.external.ExternalBinding;
import io.github.autyism.keybindprofilesplus.external.ExternalKeys;
import io.github.autyism.keybindprofilesplus.keys.KeyCombo;
import io.github.autyism.keybindprofilesplus.keys.KeyCombos;
import io.github.autyism.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyism.keybindprofilesplus.keys.KeyLabels;
import io.github.autyism.keybindprofilesplus.keys.KeyOrigins;
import io.github.autyism.keybindprofilesplus.keys.KeySource;
import io.github.autyism.keybindprofilesplus.keys.KeySourceResolver;
import io.github.autyism.keybindprofilesplus.notification.ProfileNoticeHud;
import io.github.autyism.keybindprofilesplus.options.GameOptionsBridge;
import io.github.autyism.keybindprofilesplus.options.OptionCatalog;
import io.github.autyism.keybindprofilesplus.profile.ProfileChange;
import io.github.autyism.keybindprofilesplus.profile.ProfileComparison;
import io.github.autyism.keybindprofilesplus.profile.ProfileNames;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.profile.ShareCode;
import io.github.autyism.keybindprofilesplus.server.ServerAutoSwitchController;
import io.github.autyism.keybindprofilesplus.server.ServerProfileMatcher;
import io.github.autyism.keybindprofilesplus.storage.LegacyOptions;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import io.github.autyism.keybindprofilesplus.storage.ProfileFileStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
//? if >=26.3 {
/*import io.github.autyism.keybindprofilesplus.input.SdlKeys;
import org.lwjgl.sdl.SDLScancode;
*///?}

/**
 * The part of the self-test that needs no screen: every rule and calculation of the mod is fed
 * known inputs and its answers are checked. Screens are covered by {@link ScreenChecks}.
 */
final class LogicChecks {
    static final String PROFILE_PREFIX = "selftest_";
    static final String PROFILE_A = PROFILE_PREFIX + "a";
    static final String PROFILE_B = PROFILE_PREFIX + "b";
    static final String PROFILE_C = PROFILE_PREFIX + "c";
    static final String TEST_BINDING_ID = "key.jump";
    static final String TEST_KEY = "key.keyboard.j";
    static final String PROFILE_A_KEY = "key.keyboard.k";
    static final String PROFILE_FOV = "0.5";
    static final String DEMO_MOD_BINDING = "key.fabric-api.selftest_demo";
    static final String DEMO_SCREEN_BINDING = "key.fabric-api.selftest_inventory_sort";
    static final String DEMO_UNKNOWN_BINDING = "key.selftest.unknown_demo";
    static final String MOD_MENU_BINDING = "key.modmenu.open_menu";
    /** Stands for an F3 combination added by a mod, like Language Reload's F3+J: a mod key in the game's Debug category. */
    static final String DEMO_DEBUG_BINDING = "key.debug.selftestReloadLanguages";
    //? if >=1.21.11 {
    static final int DEMO_BINDINGS = 4;
    //?} else
    /*static final int DEMO_BINDINGS = 3;*/
    private static final String LANG_PATH = "assets/" + KeyBindProfilesPlus.MOD_ID + "/lang/";

    private final SelfTestRunner t;
    private final ProfileService service;

    String originalTestKey;
    String originalAutoJump;
    String originalFov;
    String profileAutoJump;
    Path fixtureDirectory;

    LogicChecks(SelfTestRunner runner, ProfileService service) {
        this.t = runner;
        this.service = service;
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    // ------------------------------------------------------------------ identity and files

    void identity() {
        var mod = FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID);
        t.check("mod is loaded as '" + KeyBindProfilesPlus.MOD_ID + "'", mod.isPresent());
        mod.ifPresent(container -> {
            t.check("mod name is 'KeyBind Profiles+'", "KeyBind Profiles+".equals(container.getMetadata().getName()));
            t.check("mod license is GPL", container.getMetadata().getLicense().stream().anyMatch(license -> license.startsWith("GPL-3.0")));
        });
        t.check("upstream mod id is not loaded alongside", !FabricLoader.getInstance().isModLoaded("keybindprofiles"));
        t.check("profiles live in config/keybindprofilesplus",
                service.profilesDirectory().getPath().replace('\\', '/').endsWith("config/keybindprofilesplus"));
        t.check("title is translated", "KeyBind Profiles+".equals(SelfTestRunner.translated("keybindprofilesplus.title")));
        KeyMapping open = KeyMapping.get("key.keybindprofilesplus.open");
        //? if >=26.2 {
        /*t.check("the open key exists and defaults to I (O is the game's Friends key)", open != null && open.getDefaultKey().getName().equals("key.keyboard.i"));
        *///?} else
        t.check("the open key exists and defaults to O", open != null && open.getDefaultKey().getName().equals("key.keyboard.o"));
    }

    void migration() {
        try {
            File root = Files.createTempDirectory("kbp_selftest_migration").toFile();
            File legacy = new File(root, "keybindprofiles");
            File target = new File(root, "keybindprofilesplus");
            t.check("migration: nothing to do without an old folder", ProfileFileStore.migrateLegacyDirectory(legacy, target) == 0 && !target.exists());

            Files.createDirectories(legacy.toPath());
            Files.writeString(new File(legacy, "Old.kbp").toPath(), "{\"name\":\"Old\",\"keybindings\":{}}");
            Files.writeString(new File(legacy, "current_profile.txt").toPath(), "Old");
            t.check("migration: copies the old folder", ProfileFileStore.migrateLegacyDirectory(legacy, target) == 2
                    && new File(target, "Old.kbp").isFile() && new File(target, "current_profile.txt").isFile());
            t.check("migration: leaves the old folder in place", new File(legacy, "Old.kbp").isFile());

            Files.writeString(new File(legacy, "Later.kbp").toPath(), "{}");
            t.check("migration: runs only once", ProfileFileStore.migrateLegacyDirectory(legacy, target) == 0 && !new File(target, "Later.kbp").exists());
            deleteRecursively(root.toPath());
        } catch (IOException e) {
            t.fail("migration check could not use a temp folder: " + e);
        }

        t.check("old 'open' key is carried over", "key.keyboard.p".equals(LegacyOptions.findLegacyOpenKey(
                List.of("fov:0.0", "key_key.keybindprofiles.open:key.keyboard.p", "key_key.jump:key.keyboard.space"))));
        t.check("old 'open' key is ignored once the new entry exists", LegacyOptions.findLegacyOpenKey(
                List.of("key_key.keybindprofiles.open:key.keyboard.p", "key_key.keybindprofilesplus.open:key.keyboard.o")) == null);
    }

    void languageFiles() {
        Map<String, String> english = readLanguage("en_us");
        t.check("en_us.json is readable (" + english.size() + " entries)", !english.isEmpty());

        for (String code : List.of("zh_cn", "ru_ru")) {
            Map<String, String> other = readLanguage(code);
            Set<String> missing = new TreeSet<>(english.keySet());
            missing.removeAll(other.keySet());
            Set<String> unknown = new TreeSet<>(other.keySet());
            unknown.removeAll(english.keySet());
            Set<String> placeholderMismatch = new TreeSet<>();
            for (Map.Entry<String, String> entry : other.entrySet()) {
                String source = english.get(entry.getKey());
                if (source != null && countPlaceholders(source) != countPlaceholders(entry.getValue())) {
                    placeholderMismatch.add(entry.getKey());
                }
            }

            if (code.equals("zh_cn")) {
                t.check("zh_cn.json has every en_us entry" + (missing.isEmpty() ? "" : ", missing " + missing), missing.isEmpty());
            } else {
                // Russian is kept from upstream; entries it lacks fall back to English in game.
                SelfTestRunner.log(code + ".json lacks " + missing.size() + " entries (they fall back to English)");
            }
            t.check(code + ".json has no entries unknown to en_us" + (unknown.isEmpty() ? "" : ": " + unknown), unknown.isEmpty());
            t.check(code + ".json keeps the %s placeholders" + (placeholderMismatch.isEmpty() ? "" : ", wrong in " + placeholderMismatch), placeholderMismatch.isEmpty());
        }

        String notice = ProfileNoticeHud.messageFor("X").getString();
        t.check("HUD notice is translated, not hardcoded Russian: '" + notice + "'",
                "Profile \"X\" applied".equals(notice) && notice.chars().noneMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC));
    }

    void keyNames() {
        keyName("key.keyboard.keypad.5", "Num 5");
        keyName("key.keyboard.keypad.0", "Num 0");
        keyName("key.keyboard.keypad.add", "Num +");
        keyName("key.keyboard.keypad.divide", "Num /");
        keyName("key.keyboard.keypad.enter", "Num Enter");
        keyName("key.keyboard.5", "5");
        keyName("key.keyboard.enter", "Enter");

        // Bindings and categories a mod gave no translation for are shown as words, never as the bare id.
        label("a translated binding keeps the game's name", KeyLabels.name("key.jump").getString(), "Jump");
        label("an untranslated binding", KeyLabels.name(DEMO_MOD_BINDING).getString(), "Selftest Demo");
        label("an untranslated binding in camel case", KeyLabels.name("key.somemod.toggleFreeCam").getString(), "Toggle Free Cam");
        label("an untranslated binding with nothing but one word", KeyLabels.name("zoom").getString(), "Zoom");
        label("an untranslated category", KeyLabels.category(SelfTestRunner.binding(DEMO_UNKNOWN_BINDING).getCategory()).getString(), "Selftestmod Demo");
        label("a vanilla category", KeyLabels.category(KeyMapping.Category.MOVEMENT).getString(), "Movement");
        settingsAreReadable("en_us");
    }

    /** No game setting may show up in the contents tree under its internal name or with a raw value. */
    private void settingsAreReadable(String language) {
        Map<String, GameOptionsBridge.Entry> settings = GameOptionsBridge.readAll(client().options);
        for (GameOptionsBridge.Entry entry : settings.values()) {
            if (OptionCatalog.isOffered(entry.key())) {
                String name = entry.name().getString();
                String value = entry.describe(entry.rawValue()).getString();
                if (name.isBlank() || name.equals(entry.key()) || name.contains("options.") || "true".equals(value) || "false".equals(value) || value.startsWith(name + ":") || value.startsWith(name + "：")) {
                    t.fail(language + ": setting \"" + entry.key() + "\" is shown as \"" + name + "\" = \"" + value + "\"");
                }
            }
        }
        t.pass(language + ": every offered game setting has a readable name and value");
    }

    private void label(String what, String actual, String expected) {
        t.check("labels: " + what + " reads \"" + expected + "\" (got \"" + actual + "\")", expected.equals(actual));
    }

    void chineseTexts() {
        keyName("key.keyboard.keypad.5", "小键盘 5");
        t.check("zh_cn: manage button reads 管理档案", "管理档案".equals(SelfTestRunner.translated("keybindprofilesplus.open")));
        t.check("zh_cn: HUD notice reads 已应用档案“X”", "已应用档案“X”".equals(ProfileNoticeHud.messageFor("X").getString()));
        t.check("zh_cn: a combination reads Ctrl + 小键盘 5", "Ctrl + 小键盘 5".equals(KeyCombo.describe("ctrl+key.keyboard.keypad.5").getString()));
        settingsAreReadable("zh_cn");
    }

    private void keyName(String translationKey, String expected) {
        String actual = InputConstants.getKey(translationKey).getDisplayName().getString();
        t.check("key name " + translationKey + " -> '" + expected + "'" + (expected.equals(actual) ? "" : " but was '" + actual + "'"), expected.equals(actual));
    }

    // ------------------------------------------------------------------ sources

    void keySources() {
        KeySourceResolver resolver = new KeySourceResolver(client().options);
        source(resolver, "key.jump", KeySource.Kind.VANILLA, "minecraft");
        source(resolver, "key.hotbar.1", KeySource.Kind.VANILLA, "minecraft");
        //? if >=1.21.11
        source(resolver, "key.debug.reloadChunk", KeySource.Kind.VANILLA, "minecraft");

        KeyMapping own = SelfTestRunner.binding("key.keybindprofilesplus.open");
        SelfTestRunner.log("key source: creator of the open key = " + KeyOrigins.creatorClassOf(own) + ", mod root paths = "
                + FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID).map(mod -> mod.getRootPaths().toString()).orElse("?"));
        t.check("key source: the code that created a binding is traced to its mod",
                KeyOrigins.modOf(own).map(mod -> mod.getMetadata().getId()).orElse("").equals(KeyBindProfilesPlus.MOD_ID));
        source(resolver, "key.keybindprofilesplus.open", KeySource.Kind.MOD, KeyBindProfilesPlus.MOD_ID);

        // The demo bindings had their creator forgotten on purpose, so these use the naming rules.
        source(resolver, DEMO_MOD_BINDING, KeySource.Kind.MOD, "fabric-api");
        source(resolver, DEMO_UNKNOWN_BINDING, KeySource.Kind.UNKNOWN, "selftestmod");

        int vanilla = 0;
        for (KeyMapping binding : client().options.keyMappings) {
            if (resolver.resolve(binding).isVanilla()) {
                vanilla++;
            }
        }
        // Mod Menu is in the dev client (for its configure button) and registers one key binding of its own:
        // a real mod jar, so this is the one place where tracing the creating class is checked for real.
        KeyMapping modMenuKey = KeyMapping.get(MOD_MENU_BINDING);
        if (modMenuKey != null) {
            KeySource modMenu = resolver.resolve(modMenuKey);
            t.check("key source: Mod Menu's own key binding is traced to Mod Menu (" + modMenu.description().getString() + ")",
                    modMenu.kind() == KeySource.Kind.MOD && "modmenu".equals(modMenu.modId()) && KeyOrigins.creatorClassOf(modMenuKey) != null);
        }
        // Other mods in the dev client (Mod Menu, Meteor, ...) register bindings of their own: each must be traced to its mod.
        int otherMods = 0;
        for (KeyMapping binding : client().options.keyMappings) {
            String creator = KeyOrigins.modOf(binding).map(mod -> mod.getMetadata().getId()).orElse("minecraft");
            if (!creator.equals("minecraft") && !creator.equals(KeyBindProfilesPlus.MOD_ID)) {
                otherMods++;
                KeySource traced = resolver.resolve(binding);
                if (traced.kind() != KeySource.Kind.MOD || !creator.equals(traced.modId())) {
                    t.fail("key source: " + binding.getName() + " was created by " + creator + " but is labelled " + traced.description().getString());
                }
            }
        }
        t.check("key sources: every binding except the mod ones is vanilla (" + vanilla + " of " + client().options.keyMappings.length
                        + ", " + otherMods + " from other mods in the dev client)",
                vanilla == client().options.keyMappings.length - DEMO_BINDINGS - 1 - otherMods);
    }

    private void source(KeySourceResolver resolver, String bindingId, KeySource.Kind kind, String modId) {
        KeySource source = resolver.resolve(SelfTestRunner.binding(bindingId));
        t.check("key source: " + bindingId + " -> " + kind + " " + modId + " (label '" + source.label().getString() + "')",
                source.kind() == kind && modId.equals(source.modId()));
    }

    // ------------------------------------------------------------------ conflicts

    void conflicts() {
        Map<String, String> before = SelfTestRunner.currentKeyValues();
        ModSettings settings = KeyBindProfilesPlus.settings();
        // Only the game's own bindings here: the other mods in the dev client (IPN, Meteor...) have their own checks.
        ExternalKeys.setEnvironmentForTesting(new FixtureEnvironment(null, false, List.of()));
        try {
            for (KeyMapping binding : client().options.keyMappings) {
                KeyCombos.bind(binding, binding.getDefaultKey(), 0);
            }
            KeyMapping.resetMapping();
            KeyConflicts.Summary defaults = KeyConflicts.summarize(client().options);
            t.check("conflicts: the default key bindings have none (" + defaults.hard() + " / " + defaults.soft() + ")", defaults.isEmpty());

            t.bind("key.jump", "key.keyboard.g");
            conflict("a normal key on G conflicts with Quick Actions (also G), never with F3+G", "key.jump", KeyConflicts.Level.HARD, 1, "general");
            t.bind("key.jump", "key.keyboard.b");
            conflict("a normal key on B and F3+B (show hitboxes) do not conflict", "key.jump", KeyConflicts.Level.NONE, 0, null);
            t.bind("key.jump", "key.keyboard.1");
            conflict("jump on 1 conflicts with hotbar slot 1 but not with F3+1", "key.jump", KeyConflicts.Level.HARD, 1, "general");
            t.bind("key.jump", "key.keyboard.space");

            t.bind("key.drop", "key.keyboard.e");
            conflict("two keys used during play on the same key is a real conflict", "key.drop", KeyConflicts.Level.HARD, 1, "general");
            conflict("... seen from the other binding too", "key.inventory", KeyConflicts.Level.HARD, 1, "general");
            KeyConflicts.Summary oneClash = KeyConflicts.summarize(client().options);
            t.check("conflicts: summary counts both bindings of the pair", oneClash.hard() == 2 && oneClash.soft() == 0);
            t.bind("key.drop", "key.keyboard.q");

            t.bind("key.sprint", "key.keyboard.x");
            conflict("sprint on X and the Creative-only load-hotbar key is a possible conflict", "key.sprint", KeyConflicts.Level.SOFT, 1, "creative");
            t.bind("key.sprint", "key.keyboard.left.control");

            t.bind("key.spectatorOutlines", "key.mouse.middle");
            conflict("two Spectator-only keys clash, pick block (never in Spectator) does not", "key.spectatorOutlines", KeyConflicts.Level.HARD, 1, "spectator_both");
            t.bind("key.spectatorOutlines", "key.keyboard.space");
            conflict("a Spectator-only key and jump is a possible conflict", "key.spectatorOutlines", KeyConflicts.Level.SOFT, 1, "spectator");
            t.bind("key.spectatorOutlines", "key.keyboard.unknown");

            //? if >=1.21.11 {
            t.bind("key.debug.reloadChunk", "key.keyboard.b");
            conflict("two F3 combinations on the same key conflict", "key.debug.reloadChunk", KeyConflicts.Level.HARD, 1, "debug");
            t.bind("key.debug.reloadChunk", "key.keyboard.a");

            t.bind("key.jump", "key.keyboard.f3");
            conflict("a normal key on F3 itself conflicts with the debug keys", "key.jump", KeyConflicts.Level.HARD, 2, "general");
            t.bind("key.jump", "key.keyboard.space");
            //?}

            t.bind(DEMO_MOD_BINDING, "key.keyboard.q");
            conflict("a mod key used during play on the same key as a vanilla one conflicts", DEMO_MOD_BINDING, KeyConflicts.Level.HARD, 1, "general");
            t.bind(DEMO_MOD_BINDING, "key.keyboard.keypad.5");

            // A key that only works inside screens against a key used during play.
            KeySourceResolver sources = KeyConflicts.sources(client().options);
            KeyMapping screenKey = SelfTestRunner.binding(DEMO_SCREEN_BINDING);
            t.check("conflicts: a mod key named after the inventory is taken to work only in screens",
                    KeyConflicts.scopeOf(screenKey, sources) == KeyConflicts.Scope.SCREEN_ONLY);
            t.bind(DEMO_SCREEN_BINDING, "key.keyboard.space");
            conflict("a screen-only key and a key used during play is only a possible conflict", DEMO_SCREEN_BINDING, KeyConflicts.Level.SOFT, 1, "screen");
            conflict("... seen from the in-game key too", "key.jump", KeyConflicts.Level.SOFT, 1, "screen");
            t.bind(DEMO_SCREEN_BINDING, "key.keyboard.1");
            conflict("a screen-only key on a key that vanilla also uses in inventories is a real conflict", DEMO_SCREEN_BINDING, KeyConflicts.Level.HARD, 1, "screen_vanilla");
            t.bind(DEMO_SCREEN_BINDING, "key.keyboard.space");

            settings.setScopeOverride(DEMO_SCREEN_BINDING, KeyConflicts.OVERRIDE_GENERAL);
            conflict("the player can overrule the guess: now active during play, it is a real conflict", DEMO_SCREEN_BINDING, KeyConflicts.Level.HARD, 1, "general");
            settings.setScopeOverride(DEMO_SCREEN_BINDING, null);
            t.bind(DEMO_SCREEN_BINDING, "key.keyboard.unknown");

            t.bind(DEMO_MOD_BINDING, "key.keyboard.space");
            settings.setScopeOverride(DEMO_MOD_BINDING, KeyConflicts.OVERRIDE_SCREEN);
            conflict("... and the other way round: marked screen-only, it is only a possible conflict", DEMO_MOD_BINDING, KeyConflicts.Level.SOFT, 1, "screen");
            settings.setScopeOverride(DEMO_MOD_BINDING, KeyConflicts.OVERRIDE_SITUATIONAL);
            conflict("... marked 'only in a special situation', it is no conflict at all", DEMO_MOD_BINDING, KeyConflicts.Level.NONE, 0, null);
            conflict("... seen from the vanilla key too", "key.jump", KeyConflicts.Level.NONE, 0, null);
            t.check("conflicts: ... but the tooltip can still say what shares the key",
                    KeyConflicts.sharedWithoutConflict(SelfTestRunner.binding("key.jump"), client().options).size() == 1
                            && KeyConflicts.describeShared(KeyConflicts.sharedWithoutConflict(SelfTestRunner.binding("key.jump"), client().options)).size() == 2);
            t.check("conflicts: clicking through the choices goes automatic -> play -> screens -> special situation -> automatic",
                    KeyConflicts.OVERRIDE_GENERAL.equals(KeyConflicts.nextOverride(null))
                            && KeyConflicts.OVERRIDE_SCREEN.equals(KeyConflicts.nextOverride(KeyConflicts.OVERRIDE_GENERAL))
                            && KeyConflicts.OVERRIDE_SITUATIONAL.equals(KeyConflicts.nextOverride(KeyConflicts.OVERRIDE_SCREEN))
                            && KeyConflicts.nextOverride(KeyConflicts.OVERRIDE_SITUATIONAL) == null);
            settings.setScopeOverride(DEMO_MOD_BINDING, null);
            conflict("... and back to a real conflict once the override is removed", DEMO_MOD_BINDING, KeyConflicts.Level.HARD, 1, "general");
            settings.reload();
            t.check("conflicts: overrides are stored in settings.json and removed again", settings.scopeOverride(DEMO_MOD_BINDING) == null);
        } finally {
            settings.setScopeOverride(DEMO_SCREEN_BINDING, null);
            settings.setScopeOverride(DEMO_MOD_BINDING, null);
            before.forEach(t::bind);
            ExternalKeys.setEnvironmentForTesting(null);
        }
    }

    void conflict(String what, String bindingId, KeyConflicts.Level level, int count, String reason) {
        List<KeyConflicts.Conflict> conflicts = KeyConflicts.conflictsOf(SelfTestRunner.binding(bindingId), client().options);
        boolean ok = KeyConflicts.worst(conflicts) == level && conflicts.size() == count
                && (reason == null || conflicts.stream().anyMatch(conflict -> conflict.reason().equals(reason)));
        StringBuilder found = new StringBuilder();
        for (KeyConflicts.Conflict conflict : conflicts) {
            found.append(" [").append(conflict.level()).append(" ").append(conflict.otherId()).append(" ").append(conflict.reason()).append("]");
        }
        t.check("conflicts: " + what + (ok ? "" : " - got" + found), ok);
    }

    // ------------------------------------------------------------------ combinations

    void combos() {
        KeyCombo parsed = KeyCombo.parse("Shift+ctrl+key.keyboard.x");
        t.check("combos: text form is normalised (" + parsed.encode() + ")", parsed.encode().equals("ctrl+shift+key.keyboard.x"));
        t.check("combos: shown as Ctrl + Shift + X", KeyCombo.describe("ctrl+shift+key.keyboard.x").getString().equals("Ctrl + Shift + X"));
        t.check("combos: a plain key stays a plain key", KeyCombo.parse("key.keyboard.x").isPlain() && KeyCombo.describe("key.keyboard.x").getString().equals("X"));
        t.check("combos: numpad names work inside a combination", KeyCombo.describe("alt+key.keyboard.keypad.5").getString().equals("Alt + Num 5"));

        Map<String, String> before = SelfTestRunner.currentKeyValues();
        KeyMapping plain = SelfTestRunner.binding("key.advancements");
        KeyMapping combo = SelfTestRunner.binding("key.socialInteractions");
        InputConstants.Key f15 = InputConstants.getKey("key.keyboard.f15");
        int[] held = {0};
        KeyCombos.setHeldModifiersForTesting(() -> held[0]);
        try {
            t.bind("key.advancements", "key.keyboard.f15");
            t.bind("key.socialInteractions", "ctrl+key.keyboard.f15");
            t.check("combos: the binding reports its combination", KeyCombos.valueOf(combo).equals("ctrl+key.keyboard.f15")
                    && combo.getTranslatedKeyMessage().getString().equals("Ctrl + F15"));
            t.check("combos: a combination is never the default", !combo.isDefault());

            SelfTestRunner.drainPressed(plain);
            SelfTestRunner.drainPressed(combo);
            held[0] = 0;
            KeyMapping.click(f15);
            boolean plainOnBare = SelfTestRunner.drainPressed(plain);
            boolean comboOnBare = SelfTestRunner.drainPressed(combo);
            t.check("combos: F15 alone triggers the plain binding, not Ctrl + F15", plainOnBare && !comboOnBare);

            held[0] = KeyCombo.CTRL;
            KeyMapping.click(f15);
            boolean plainOnCtrl = SelfTestRunner.drainPressed(plain);
            boolean comboOnCtrl = SelfTestRunner.drainPressed(combo);
            t.check("combos: Ctrl + F15 triggers the combination, not the plain binding", comboOnCtrl && !plainOnCtrl);

            held[0] = KeyCombo.CTRL | KeyCombo.SHIFT;
            KeyMapping.click(f15);
            t.check("combos: extra modifiers held still trigger Ctrl + F15", SelfTestRunner.drainPressed(combo) && !SelfTestRunner.drainPressed(plain));

            held[0] = KeyCombo.CTRL;
            KeyMapping.set(f15, true);
            t.check("combos: held state follows the same rule", combo.isDown() && !plain.isDown());
            KeyMapping.set(f15, false);
            t.check("combos: releasing the key releases every binding on it", !combo.isDown() && !plain.isDown());

            t.check("combos: screen key checks respect modifiers",
                    //? if >=26.3 {
                    /*combo.matches(new KeyEvent(InputConstants.KEY_F15, 0, io.github.autyism.keybindprofilesplus.input.SdlKeys.toSdlModifiers(KeyCombo.CTRL)))
                            && !plain.matches(new KeyEvent(InputConstants.KEY_F15, 0, io.github.autyism.keybindprofilesplus.input.SdlKeys.toSdlModifiers(KeyCombo.CTRL)))
                    *///?} else
                    combo.matches(new KeyEvent(InputConstants.KEY_F15, 0, KeyCombo.CTRL)) && !plain.matches(new KeyEvent(InputConstants.KEY_F15, 0, KeyCombo.CTRL))
                            && plain.matches(new KeyEvent(InputConstants.KEY_F15, 0, 0)) && !combo.matches(new KeyEvent(InputConstants.KEY_F15, 0, 0)));

            // A key without any combination on it keeps the vanilla behaviour whatever is held.
            KeyMapping jump = SelfTestRunner.binding(TEST_BINDING_ID);
            SelfTestRunner.drainPressed(jump);
            held[0] = KeyCombo.CTRL | KeyCombo.ALT;
            KeyMapping.click(jump.key);
            t.check("combos: bindings on other keys are untouched by held modifiers", SelfTestRunner.drainPressed(jump));

            conflict("X and Ctrl + X on the same key do not conflict", "key.socialInteractions", KeyConflicts.Level.NONE, 0, null);
            t.bind("key.playerlist", "ctrl+key.keyboard.f15");
            conflict("two bindings on Ctrl + F15 conflict", "key.socialInteractions", KeyConflicts.Level.HARD, 1, "general");
            t.bind("key.playerlist", "key.keyboard.f16");
            t.bind("key.sprint", "key.keyboard.left.control");
            conflict("a combination next to a binding on its modifier key (sprint on Ctrl) is not reported", "key.socialInteractions", KeyConflicts.Level.NONE, 0, null);

            File combosFile = new File(service.profilesDirectory(), "combos.json");
            boolean written = false;
            try {
                written = combosFile.isFile() && Files.readString(combosFile.toPath()).contains("\"key.socialInteractions\": \"ctrl+key.keyboard.f15\"");
            } catch (IOException e) {
                t.fail("combos: could not read combos.json: " + e);
            }
            t.check("combos: saved to combos.json", written);
            KeyCombos.load(service.profilesDirectory());
            t.check("combos: read back from combos.json", KeyCombos.valueOf(combo).equals("ctrl+key.keyboard.f15"));

            String comboProfile = PROFILE_PREFIX + "combo";
            service.saveProfile(comboProfile, client().options.keyMappings);
            t.check("combos: stored in a profile", "ctrl+key.keyboard.f15".equals(service.profiles().get(comboProfile).get("key.socialInteractions")));
            combo.setKey(InputConstants.getKey("key.keyboard.f16"));
            KeyMapping.resetMapping();
            t.check("combos: rebinding the vanilla way drops the modifiers", KeyCombos.valueOf(combo).equals("key.keyboard.f16"));
            t.check("combos: the profile preview shows the combination",
                    service.previewApply(comboProfile).stream().anyMatch(change -> change.to().getString().equals("Ctrl + F15")));
            service.applyProfile(comboProfile);
            held[0] = KeyCombo.CTRL;
            SelfTestRunner.drainPressed(combo);
            KeyMapping.click(f15);
            t.check("combos: applying the profile restores a working combination",
                    KeyCombos.valueOf(combo).equals("ctrl+key.keyboard.f15") && SelfTestRunner.drainPressed(combo));
            SelfTestRunner.drainPressed(plain);
            service.deleteProfile(comboProfile);
        } finally {
            KeyCombos.setHeldModifiersForTesting(null);
            before.forEach(t::bind);
        }
        t.check("combos: test bindings were put back", SelfTestRunner.currentKeyValues().equals(before));
    }

    //? if >=26.3 {
    /*// 26.3 reads keys through SDL. Saved key names keep the meaning they had before (GLFW), so profiles,
    // share codes and combos.json from another version bind the same physical keys here, and the reverse.
    void crossVersionKeys() {
        Map<String, String> before = SelfTestRunner.currentKeyValues();
        KeyMapping binding = SelfTestRunner.binding("key.advancements");
        try {
            t.bind("key.advancements", "key.keyboard.keypad.decimal");
            t.check("sdl keys: a saved numpad dot binds the numpad dot (" + binding.key.getName() + ")",
                    binding.key.getType() == InputConstants.Type.KEYSYM && binding.key.getValue() == SDLScancode.SDL_SCANCODE_KP_PERIOD);
            t.check("sdl keys: ... and is saved under the name the other versions use (" + KeyCombos.valueOf(binding) + ")",
                    KeyCombos.valueOf(binding).equals("key.keyboard.keypad.decimal"));
            t.bind("key.advancements", "ctrl+key.keyboard.menu");
            t.check("sdl keys: Ctrl + the saved menu key binds Ctrl + the context menu key (" + KeyCombos.valueOf(binding) + ")",
                    binding.key.getValue() == SDLScancode.SDL_SCANCODE_APPLICATION && KeyCombos.valueOf(binding).equals("ctrl+key.keyboard.menu"));
            binding.setKey(InputConstants.Type.KEYSYM.getOrCreate(SDLScancode.SDL_SCANCODE_KP_DECIMAL));
            KeyMapping.resetMapping();
            t.check("sdl keys: SDL's own keypad decimal key, unknown to the other versions, is saved by its number (" + KeyCombos.valueOf(binding) + ")",
                    KeyCombos.valueOf(binding).equals("key.keyboard.220"));
            t.bind("key.advancements", "key.keyboard.220");
            t.check("sdl keys: ... and read back as that key", binding.key.getValue() == SDLScancode.SDL_SCANCODE_KP_DECIMAL);
            t.bind("key.advancements", "key.keyboard.a");
            t.check("sdl keys: ordinary keys keep their names",
                    binding.key.getValue() == SDLScancode.SDL_SCANCODE_A && KeyCombos.valueOf(binding).equals("key.keyboard.a"));

            String profile = PROFILE_PREFIX + "sdl";
            service.saveProfile(profile, client().options.keyMappings);
            service.setProfileContents(profile, Map.of("key.advancements", "alt+key.keyboard.keypad.decimal", "key.socialInteractions", "key.mouse.4"), Map.of());
            service.applyProfile(profile);
            KeyMapping social = SelfTestRunner.binding("key.socialInteractions");
            t.check("sdl keys: a profile from another version applies to the same keys and buttons",
                    binding.key.getValue() == SDLScancode.SDL_SCANCODE_KP_PERIOD && KeyCombos.modifiersOf(binding) == KeyCombo.ALT
                            && social.key.getType() == InputConstants.Type.MOUSE && social.key.getValue() == InputConstants.MOUSE_BUTTON_4);
            service.saveProfile(profile, client().options.keyMappings);
            t.check("sdl keys: saving it again writes the same names",
                    "alt+key.keyboard.keypad.decimal".equals(service.profiles().get(profile).get("key.advancements"))
                            && "key.mouse.4".equals(service.profiles().get(profile).get("key.socialInteractions")));
            service.deleteProfile(profile);
        } finally {
            before.forEach(t::bind);
        }
        t.check("sdl keys: test bindings were put back", SelfTestRunner.currentKeyValues().equals(before));

        // Numbers below are GLFW's: what files written before 26.3 hold.
        t.check("sdl keys: GLFW key codes from old files become the same keys",
                SdlKeys.keyOfGlfwCode(65).getValue() == SDLScancode.SDL_SCANCODE_A
                        && SdlKeys.keyOfGlfwCode(330).getValue() == SDLScancode.SDL_SCANCODE_KP_PERIOD
                        && SdlKeys.keyOfGlfwCode(341).getValue() == SDLScancode.SDL_SCANCODE_LCTRL
                        && SdlKeys.keyOfGlfwCode(348).getValue() == SDLScancode.SDL_SCANCODE_APPLICATION
                        && SdlKeys.keyOfGlfwCode(290).getValue() == SDLScancode.SDL_SCANCODE_F1);
        t.check("sdl keys: GLFW mouse buttons from old files become the same buttons",
                SdlKeys.mouseButtonOfGlfw(0) == InputConstants.MOUSE_BUTTON_LEFT && SdlKeys.mouseButtonOfGlfw(1) == InputConstants.MOUSE_BUTTON_RIGHT
                        && SdlKeys.mouseButtonOfGlfw(2) == InputConstants.MOUSE_BUTTON_MIDDLE && SdlKeys.mouseButtonOfGlfw(3) == InputConstants.MOUSE_BUTTON_4
                        && SdlKeys.mouseButtonOfGlfw(4) == InputConstants.MOUSE_BUTTON_5);
        t.check("sdl keys: malilib's 26.3 key names are saved as the names it wrote before, and back",
                SdlKeys.malilibToStored("LEFT_CONTROL,KP_PERIOD").equals("LEFT_CONTROL,KP_DECIMAL")
                        && SdlKeys.storedToMalilib("LEFT_CONTROL,KP_DECIMAL").equals("LEFT_CONTROL,KP_PERIOD")
                        && SdlKeys.malilibToStored("RETURN,LEFT_GUI").equals("ENTER,LEFT_SUPER"));
        t.check("sdl keys: GLFW key names (malilib, libIPN) are the same keys",
                SdlKeys.keyOfGlfwName("KP_DECIMAL").getValue() == SDLScancode.SDL_SCANCODE_KP_PERIOD
                        && SdlKeys.keyOfGlfwName("KP_PERIOD").getValue() == SDLScancode.SDL_SCANCODE_KP_PERIOD
                        && SdlKeys.keyOfGlfwName("LEFT_CONTROL").getValue() == SDLScancode.SDL_SCANCODE_LCTRL
                        && SdlKeys.keyOfGlfwName("GRAVE_ACCENT").getValue() == SDLScancode.SDL_SCANCODE_GRAVE);
        t.check("sdl keys: Ctrl / Shift / Alt of SDL events become this mod's bits and back",
                SdlKeys.toKbpModifiers(SdlKeys.toSdlModifiers(KeyCombo.ALL)) == KeyCombo.ALL
                        && SdlKeys.toKbpModifiers(InputConstants.MOD_CONTROL) == KeyCombo.CTRL
                        && SdlKeys.toKbpModifiers(InputConstants.MOD_SHIFT | InputConstants.MOD_ALT) == (KeyCombo.SHIFT | KeyCombo.ALT));
    }
    *///?}

    // ------------------------------------------------------------------ share codes and names

    void shareCodes() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("key.jump", "key.keyboard.space");
        keys.put("key.socialInteractions", "ctrl+key.keyboard.f15");
        Map<String, String> options = new LinkedHashMap<>();
        options.put("fov", "0.5");
        ShareCode.Content content = new ShareCode.Content("My 档案", keys, options);
        String code = ShareCode.encode(content);
        t.check("share: a code is one line starting with KBP1- (" + code.length() + " characters)",
                code.startsWith(ShareCode.PREFIX) && !code.contains("\n") && !code.contains(" "));
        t.check("share: decoding gives back exactly what was encoded", content.equals(decodeOrNull(code)));
        t.check("share: line breaks and spaces from pasting are ignored",
                content.equals(decodeOrNull("  " + code.substring(0, 20) + "\n" + code.substring(20) + " \r\n")));

        shareProblem("", ShareCode.Problem.EMPTY);
        shareProblem("   ", ShareCode.Problem.EMPTY);
        shareProblem("hello world", ShareCode.Problem.NOT_A_CODE);
        shareProblem("KBP9-AAAA", ShareCode.Problem.UNSUPPORTED_VERSION);
        shareProblem(code.substring(0, code.length() - 9), ShareCode.Problem.CORRUPTED);
        shareProblem(ShareCode.PREFIX + "!!!not base64!!!", ShareCode.Problem.CORRUPTED);
        shareProblem(ShareCode.PREFIX + "AAAAAAAAAAAAAAAA", ShareCode.Problem.CORRUPTED);
        char swapped = code.charAt(code.length() - 5) == 'A' ? 'B' : 'A';
        shareProblem(code.substring(0, code.length() - 5) + swapped + code.substring(code.length() - 4), ShareCode.Problem.CORRUPTED);
        shareProblem(ShareCode.encode(new ShareCode.Content("empty", Map.of(), Map.of())), ShareCode.Problem.INVALID_CONTENT);

        // Whatever gets pasted, decoding must end in a profile or a tidy refusal, never in a crash.
        Random random = new Random(20261001L);
        int crashes = 0;
        for (int i = 0; i < 400; i++) {
            char[] mangled = code.toCharArray();
            int edits = 1 + random.nextInt(4);
            for (int e = 0; e < edits; e++) {
                mangled[random.nextInt(mangled.length)] = (char) (33 + random.nextInt(94));
            }
            String text = new String(mangled, 0, 1 + random.nextInt(mangled.length));
            try {
                ShareCode.decode(text);
            } catch (ShareCode.InvalidShareCodeException expected) {
                // A refusal is the correct outcome.
            } catch (RuntimeException e) {
                crashes++;
            }
        }
        t.check("share: 400 damaged codes are all refused cleanly", crashes == 0);
    }

    private static ShareCode.Content decodeOrNull(String code) {
        try {
            return ShareCode.decode(code);
        } catch (ShareCode.InvalidShareCodeException e) {
            return null;
        }
    }

    private void shareProblem(String text, ShareCode.Problem expected) {
        ShareCode.Problem actual = null;
        try {
            ShareCode.decode(text);
        } catch (ShareCode.InvalidShareCodeException e) {
            actual = e.problem();
        } catch (RuntimeException e) {
            t.fail("share: decoding threw " + e);
            return;
        }
        t.check("share: refused as " + expected + (actual == expected ? "" : " but was " + actual), actual == expected);
    }

    void profileNames() {
        t.check("names: ordinary names are fine", ProfileNames.validate("PvP 2") == null && ProfileNames.validate("建筑") == null);
        t.check("names: blank, too long and file-name-breaking names are refused",
                ProfileNames.validate(" ") != null && ProfileNames.validate("x".repeat(33)) != null && ProfileNames.validate("a/b") != null
                        && ProfileNames.validate("what?") != null && ProfileNames.validate("con") != null && ProfileNames.validate("dot.") != null);
        t.check("names: a free name is found next to existing ones",
                ProfileNames.firstFree("Test", Set.of("test", "Test (2)")).equals("Test (3)") && ProfileNames.firstFree("New", Set.of("Test")).equals("New"));
    }

    // ------------------------------------------------------------------ server rules

    void serverRules() {
        ServerProfileMatcher.Location server = ServerProfileMatcher.Location.server("play.example.org");
        ServerProfileMatcher.Location otherPort = ServerProfileMatcher.Location.server("Play.Example.org:25566");
        ServerProfileMatcher.Location single = ServerProfileMatcher.Location.singleplayer();
        ServerProfileMatcher.Location lan = new ServerProfileMatcher.Location(ServerProfileMatcher.Kind.LAN, "192.168.1.20:51234");
        ServerProfileMatcher.Location ipv6 = ServerProfileMatcher.Location.server("[2001:db8::1]:25570");

        int exact = ServerProfileMatcher.score(server, "play.example.org:25565");
        int host = ServerProfileMatcher.score(server, "play.example.org");
        int subdomain = ServerProfileMatcher.score(server, "example.org");
        int wildcard = ServerProfileMatcher.score(server, "*.example.org");
        int anywhere = ServerProfileMatcher.score(server, "*");
        t.check("server rules: exact > host > subdomain > wildcard > * (" + exact + " " + host + " " + subdomain + " " + wildcard + " " + anywhere + ")",
                exact > host && host > subdomain && subdomain > wildcard && wildcard > anywhere && anywhere > 0);
        t.check("server rules: a different port does not match an exact rule", ServerProfileMatcher.score(server, "play.example.org:25566") == 0);
        t.check("server rules: a rule without a port matches any port", ServerProfileMatcher.score(otherPort, "play.example.org") > 0);
        t.check("server rules: case, scheme and path are ignored", ServerProfileMatcher.score(server, "HTTP://Play.Example.ORG/lobby") == host);
        t.check("server rules: a trailing dot is ignored", ServerProfileMatcher.score(server, "play.example.org.") == host);
        t.check("server rules: unrelated hosts do not match",
                ServerProfileMatcher.score(server, "other.org") == 0 && ServerProfileMatcher.score(server, "ample.org") == 0);
        t.check("server rules: a longer wildcard is more specific",
                ServerProfileMatcher.score(server, "play.*.org") > ServerProfileMatcher.score(server, "*.org"));
        t.check("server rules: singleplayer matches only singleplayer",
                ServerProfileMatcher.score(single, "singleplayer") > 0 && ServerProfileMatcher.score(server, "singleplayer") == 0
                        && ServerProfileMatcher.score(single, "example.org") == 0);
        t.check("server rules: * also covers singleplayer", ServerProfileMatcher.score(single, "*") > 0);
        t.check("server rules: lan matches LAN worlds only",
                ServerProfileMatcher.score(lan, "lan") > 0 && ServerProfileMatcher.score(server, "lan") == 0 && ServerProfileMatcher.score(lan, "realms") == 0);
        t.check("server rules: IPv6 with and without port",
                ServerProfileMatcher.score(ipv6, "[2001:db8::1]:25570") > ServerProfileMatcher.score(ipv6, "2001:db8::1")
                        && ServerProfileMatcher.score(ipv6, "2001:db8::1") > 0 && ServerProfileMatcher.score(ipv6, "[2001:db8::1]:25571") == 0);

        Map<String, List<String>> rules = new LinkedHashMap<>();
        rules.put("anywhere", List.of("*"));
        rules.put("host", List.of("example.org"));
        rules.put("exact", List.of("mc.other.net", "play.example.org"));
        Set<String> all = Set.of("anywhere", "host", "exact");
        t.check("server rules: the most specific rule wins", matched(server, rules, all).equals("exact"));
        t.check("server rules: a deleted profile is skipped", matched(server, rules, Set.of("anywhere", "host")).equals("host"));
        t.check("server rules: * catches everything else", matched(ServerProfileMatcher.Location.server("mc.elsewhere.net"), rules, all).equals("anywhere"));
        t.check("server rules: equal rules go to the first name",
                matched(server, Map.of("b", List.of("play.example.org"), "A", List.of("play.example.org")), Set.of("A", "b")).equals("A"));
        t.check("server rules: nothing matches without rules", ServerProfileMatcher.findBestMatch(server, Map.of(), Set.of()) == null);

        t.check("server rules: kinds are recognised",
                ServerProfileMatcher.ruleKind("*").equals("anywhere") && ServerProfileMatcher.ruleKind("Singleplayer").equals("singleplayer")
                        && ServerProfileMatcher.ruleKind("*.example.org").equals("wildcard") && ServerProfileMatcher.ruleKind("example.org").equals("host")
                        && ServerProfileMatcher.ruleKind("example.org:25565").equals("exact"));
        t.check("server rules: blank and spaced rules are rejected",
                ServerProfileMatcher.validate("  ") != null && ServerProfileMatcher.validate("my server") != null && ServerProfileMatcher.validate("example.org") == null);
        t.check("server rules: the same rule in another profile is noticed",
                ServerProfileMatcher.profilesUsingRule("Example.org", rules, "exact").equals(List.of("host")));
    }

    private static String matched(ServerProfileMatcher.Location location, Map<String, List<String>> rules, Set<String> existing) {
        ServerProfileMatcher.Match match = ServerProfileMatcher.findBestMatch(location, rules, existing);
        return match == null ? "" : match.profile();
    }

    /** Leaving a world: back to the default profile only when the player asked for it. */
    void leaveDefault() {
        ModSettings settings = KeyBindProfilesPlus.settings();
        ServerAutoSwitchController controller = KeyBindProfilesPlus.autoSwitchController();
        String defaultBefore = settings.defaultProfile();
        boolean returnBefore = settings.returnToDefault();
        try {
            service.applyProfile(PROFILE_C);
            settings.setDefaultProfile(PROFILE_A);
            settings.setReturnToDefault(false);
            t.check("leave: nothing happens while 'back to default' is off", controller.onLeave() == null && PROFILE_C.equals(service.getCurrentProfile()));

            settings.setReturnToDefault(true);
            t.check("leave: with it on, leaving applies the default profile", PROFILE_A.equals(controller.onLeave()) && PROFILE_A.equals(service.getCurrentProfile()));
            t.check("leave: already on the default profile, nothing is applied again", controller.onLeave() == null);

            service.applyProfile(PROFILE_C);
            settings.setDefaultProfile(null);
            t.check("leave: without a default profile nothing happens", controller.onLeave() == null && PROFILE_C.equals(service.getCurrentProfile()));

            settings.setDefaultProfile(PROFILE_A);
            t.check("leave: renaming the default profile keeps it the default",
                    KeyBindProfilesPlus.renameProfile(PROFILE_A, PROFILE_A + "x") && (PROFILE_A + "x").equals(settings.defaultProfile())
                            && KeyBindProfilesPlus.renameProfile(PROFILE_A + "x", PROFILE_A) && PROFILE_A.equals(settings.defaultProfile()));
            settings.reload();
            t.check("leave: both settings are stored in settings.json", PROFILE_A.equals(settings.defaultProfile()) && settings.returnToDefault());
        } finally {
            settings.setDefaultProfile(defaultBefore);
            settings.setReturnToDefault(returnBefore);
            service.applyProfile(PROFILE_C);
            makeGameDifferFromProfileA();
        }
    }

    // ------------------------------------------------------------------ external keys (Meteor, malilib)

    /**
     * Builds fake Meteor and malilib config files in a temp folder, reads them, and leaves them in
     * place as the "installed mods" for the screens that follow.
     */
    void externalKeys() {
        try {
            fixtureDirectory = Files.createTempDirectory("kbp_selftest_external");
            writeFixtures(fixtureDirectory);
        } catch (IOException e) {
            t.fail("external: could not create fixture files: " + e);
            return;
        }
        Map<Path, String> hashesBefore = hashes(fixtureDirectory);
        Map<Path, Long> timesBefore = modifiedTimes(fixtureDirectory);

        ExternalKeys.setEnvironmentForTesting(new FixtureEnvironment(fixtureDirectory, false, List.of()));
        t.check("external: without Meteor or malilib installed nothing is listed, even if their files exist", ExternalKeys.all().isEmpty());

        ExternalKeys.setEnvironmentForTesting(fixtureEnvironment());
        List<ExternalBinding> all = ExternalKeys.all();
        external(all, "Meteor", "Auto Totem", "Z", true);
        external(all, "Meteor", "Auto Totem / Swap Key", "Right Shift", true);
        external(all, "Meteor", "Free Look", "Ctrl + K", true);
        external(all, "Meteor", "Mouse Module", "Button 4", true);
        t.check("external: Meteor modules without a key are left out", all.stream().noneMatch(binding -> binding.name().equals("Unbound Module")));
        external(all, "Meteor profile: pvp", "Auto Totem", "Y", false);
        external(all, "Litematica", "Open Gui Main Menu", "M", true);
        external(all, "Litematica", "Toggle All Rendering", "M + R", true);
        external(all, "Litematica", "Execute Operation", "Ctrl + Num 5", true);
        external(all, "Litematica", "Pick Block Last", "Middle Button", true);
        external(all, "Litematica", "Tool Place Corner 1", "Left Button", true);
        external(all, "Litematica", "Tool Place Corner 2", "Right Button", true);
        external(all, "Litematica", "Rerender Schematic", "F3 + M", true);
        external(all, "Meteor", "Light Overlay", "B", true);
        external(all, "Meteor", "Auto Eat", "J", true);
        external(all, "Meteor", "Anti Afk", "Ctrl + H", true);
        external(all, "Meteor", "Anti Afk / Pause Key", "Button 5", true);
        t.check("external: a Meteor key bind in the newer format without a key is left out, one that needs the Windows key is shown but not compared",
                all.stream().noneMatch(binding -> binding.name().equals("New Unbound"))
                        && all.stream().anyMatch(binding -> binding.name().equals("Super Module") && binding.key() == null
                        && binding.keyText().getString().equals("Super + N")));
        t.check("external: malilib hotkeys without keys are left out", all.stream().noneMatch(binding -> binding.name().equals("Unbound One")));
        external(all, "Item Scroller", "Crafting Features", "Num -", true);
        external(all, "Item Scroller", "Modifier Move Everything", "Left Alt", true);
        t.check("external: malilib's own 'GUI only' marking is understood",
                all.stream().filter(binding -> binding.sourceId().equals("itemscroller")).allMatch(binding -> binding.when() == ExternalBinding.When.SCREEN_ONLY)
                        && all.stream().filter(binding -> binding.sourceId().equals("litematica")).noneMatch(binding -> binding.when() == ExternalBinding.When.SCREEN_ONLY));

        // Hotkeys that only act in a situation of the mod's own must be recognised as such.
        when(all, "Litematica", "Tool Place Corner 1", ExternalBinding.When.SITUATIONAL);
        when(all, "Litematica", "Tool Place Corner 2", ExternalBinding.When.SITUATIONAL);
        when(all, "Litematica", "Tool Select Elements", ExternalBinding.When.SITUATIONAL);
        when(all, "Litematica", "Tool Select Modifier Block 2", ExternalBinding.When.SITUATIONAL);
        when(all, "Litematica", "Operation Mode Change Modifier", ExternalBinding.When.SITUATIONAL);
        when(all, "Litematica", "Pick Block Last", ExternalBinding.When.SITUATIONAL);
        when(all, "Litematica", "Open Gui Main Menu", ExternalBinding.When.IN_GAME);
        when(all, "Litematica", "Layer Next", ExternalBinding.When.IN_GAME);
        when(all, "Meteor", "Auto Totem", ExternalBinding.When.IN_GAME);
        when(all, "Meteor", "Auto Totem / Swap Key", ExternalBinding.When.SITUATIONAL);
        t.check("external: without a word in its file, an Item Scroller hotkey counts as screen-only, its config key as in-game; a bare modifier as a hold key",
                malilibWhen("itemscroller", "keyDropStack", false, false) == ExternalBinding.When.SCREEN_ONLY
                        && malilibWhen("itemscroller", "openConfigGui", false, false) == ExternalBinding.When.IN_GAME
                        && malilibWhen("tweakeroo", "flexibleBlockPlacementOffset", true, false) == ExternalBinding.When.SITUATIONAL
                        && malilibWhen("somemod", "doSomething", false, true) == ExternalBinding.When.SITUATIONAL
                        && malilibWhen("somemod", "doSomething", false, false) == ExternalBinding.When.IN_GAME);

        Map<String, String> before = SelfTestRunner.currentKeyValues();
        ModSettings settings = KeyBindProfilesPlus.settings();
        try {
            // Default keys, so the outcome does not depend on the layout the dev client happens to have.
            for (KeyMapping binding : client().options.keyMappings) {
                KeyCombos.bind(binding, binding.getDefaultKey(), 0);
            }
            // Litematica ships with its tool on the mouse buttons and its hold-keys on Shift / Ctrl: by design, so no conflict.
            conflict("Litematica's tool key on the left mouse button is no conflict with attack", "key.attack", KeyConflicts.Level.NONE, 0, null);
            conflict("... nor the one on the right mouse button with use", "key.use", KeyConflicts.Level.NONE, 0, null);
            conflict("... nor the ones on the middle mouse button with pick block", "key.pickItem", KeyConflicts.Level.NONE, 0, null);
            conflict("... nor its hold-key on Left Shift with sneak", "key.sneak", KeyConflicts.Level.NONE, 0, null);
            conflict("... nor its hold-key on Left Control with sprint", "key.sprint", KeyConflicts.Level.NONE, 0, null);
            ExternalBinding corner1 = all.stream().filter(binding -> binding.name().equals("Tool Place Corner 1")).findFirst().orElseThrow();
            List<Component> sharedWithAttack = KeyConflicts.sharedWithoutConflict(SelfTestRunner.binding("key.attack"), client().options);
            t.check("external: the attack key's tooltip still says what shares the left mouse button " + sharedWithAttack.stream().map(Component::getString).toList(),
                    sharedWithAttack.size() == 1 && sharedWithAttack.get(0).getString().contains("Tool Place Corner 1"));
            t.check("external: ... and seen from Litematica's side: no conflict, attack named as sharing the key",
                    KeyConflicts.conflictsOf(corner1, client().options).isEmpty()
                            && KeyConflicts.sharedWithoutConflict(corner1, client().options).size() == 1);
            settings.setScopeOverride(KeyConflicts.overrideKey(corner1), KeyConflicts.OVERRIDE_GENERAL);
            conflict("the player can overrule that: counted as used during play, the tool key clashes with attack", "key.attack", KeyConflicts.Level.HARD, 1, "external");
            settings.setScopeOverride(KeyConflicts.overrideKey(corner1), null);
            conflict("... and removing the choice makes it no conflict again", "key.attack", KeyConflicts.Level.NONE, 0, null);

            // The case from real play: Language Reload adds F3+J as a key binding of its own in the Debug
            // category, and a Meteor module sits on J. An F3 combination stays one whoever registered it.
            // (Before 1.21.11 the debug keys were no key bindings, so a mod could not add one.)
            //? if >=1.21.11 {
            KeyMapping modDebugKey = SelfTestRunner.binding(DEMO_DEBUG_BINDING);
            KeySourceResolver sources = KeyConflicts.sources(client().options);
            t.check("external: a mod's key binding in the Debug category counts as an F3 combination, and cannot be re-labelled by hand",
                    !sources.isVanilla(DEMO_DEBUG_BINDING) && KeyConflicts.scopeOf(modDebugKey, sources) == KeyConflicts.Scope.DEBUG_COMBO
                            && !KeyConflicts.canOverrideScope(modDebugKey, sources));
            conflict("a mod's F3+J and a Meteor module on J do not conflict", DEMO_DEBUG_BINDING, KeyConflicts.Level.NONE, 0, null);
            //?}
            ExternalBinding autoEat = all.stream().filter(binding -> binding.name().equals("Auto Eat")).findFirst().orElseThrow();
            t.check("external: ... seen from the Meteor module's side neither", KeyConflicts.conflictsOf(autoEat, client().options).isEmpty());
            t.bind("key.jump", "key.keyboard.j");
            conflict("a normal key on J still clashes with that Meteor module, and only with it", "key.jump", KeyConflicts.Level.HARD, 1, "external");
            t.bind("key.jump", "key.keyboard.space");

            // F3 combinations against keys of other mods.
            ExternalBinding lightOverlay = all.stream().filter(binding -> binding.name().equals("Light Overlay")).findFirst().orElseThrow();
            //? if >=1.21.11
            conflict("an F3 combination (F3+B) and a Meteor module on B do not conflict", "key.debug.showHitboxes", KeyConflicts.Level.NONE, 0, null);
            t.check("external: ... seen from the Meteor module's side neither",
                    KeyConflicts.conflictsOf(lightOverlay, client().options).isEmpty());
            t.check("external: a mod's chord that starts with F3 (F3 + M) is not compared with single keys",
                    all.stream().filter(binding -> binding.name().equals("Rerender Schematic")).allMatch(binding -> binding.key() == null));
            t.bind("key.jump", "key.keyboard.b");
            conflict("a normal key on B does clash with that Meteor module (and still not with F3+B)", "key.jump", KeyConflicts.Level.HARD, 1, "external");

            t.bind("key.jump", "ctrl+key.keyboard.h");
            conflict("a Meteor key bind in the newer format (Ctrl + H) is compared like the others", "key.jump", KeyConflicts.Level.HARD, 1, "external");
            t.bind("key.jump", "key.keyboard.space");
            t.bind(DEMO_SCREEN_BINDING, "key.keyboard.b");
            conflict("a key that only works in screens never meets a Meteor module, which is ignored while a screen is open",
                    DEMO_SCREEN_BINDING, KeyConflicts.Level.NONE, 0, null);
            t.bind(DEMO_SCREEN_BINDING, "key.keyboard.unknown");

            t.bind("key.jump", "key.keyboard.z");
            conflict("a Meteor module on Z and a game key on Z are reported", "key.jump", KeyConflicts.Level.HARD, 1, "external");
            ExternalBinding autoTotem = all.stream().filter(binding -> binding.name().equals("Auto Totem") && binding.active()).findFirst().orElseThrow();
            t.check("external: ... and seen from the Meteor side too",
                    KeyConflicts.conflictsOf(autoTotem, client().options).stream().anyMatch(conflict -> conflict.otherId().equals("key.jump")));
            t.bind("key.jump", "key.keyboard.y");
            conflict("a key only used by a Meteor profile that is not loaded is not reported", "key.jump", KeyConflicts.Level.NONE, 0, null);
            t.bind("key.jump", "ctrl+key.keyboard.k");
            conflict("combinations are compared as a whole (Ctrl + K against Meteor's Ctrl + K)", "key.jump", KeyConflicts.Level.HARD, 1, "external");
            t.bind("key.jump", "key.keyboard.k");
            conflict("... K alone does not clash with Ctrl + K", "key.jump", KeyConflicts.Level.NONE, 0, null);
            t.bind("key.jump", "key.keyboard.keypad.subtract");
            conflict("a malilib hotkey that only works in screens is a possible conflict with a play key", "key.jump", KeyConflicts.Level.SOFT, 1, "external_partial");
            t.bind("key.jump", "key.keyboard.r");
            conflict("a chord of two ordinary keys (M + R) is not compared with single keys", "key.jump", KeyConflicts.Level.NONE, 0, null);
        } finally {
            before.forEach(t::bind);
        }

        ExternalKeys.refresh();
        ExternalKeys.all();
        t.check("external: the mods' files are byte-for-byte unchanged after being read", hashesBefore.equals(hashes(fixtureDirectory)));
        t.check("external: their modification times are unchanged too", timesBefore.equals(modifiedTimes(fixtureDirectory)));
    }

    ExternalKeys.Environment fixtureEnvironment() {
        return new FixtureEnvironment(fixtureDirectory, true,
                List.of(new ExternalKeys.MalilibMod("litematica", "Litematica"), new ExternalKeys.MalilibMod("itemscroller", "Item Scroller")));
    }

    /** What the fixture files look like right now: used again at the very end of the run. */
    void checkFixturesUntouched(Map<Path, String> hashesAtStart) {
        t.check("external: after the whole run the mods' files are still unchanged", hashesAtStart.equals(hashes(fixtureDirectory)));
    }

    Map<Path, String> fixtureHashes() {
        return hashes(fixtureDirectory);
    }

    void removeFixtures() {
        ExternalKeys.setEnvironmentForTesting(null);
        if (fixtureDirectory != null) {
            try {
                deleteRecursively(fixtureDirectory);
            } catch (IOException e) {
                SelfTestRunner.log("could not remove " + fixtureDirectory + ": " + e);
            }
        }
    }

    private void when(List<ExternalBinding> all, String group, String name, ExternalBinding.When expected) {
        ExternalBinding.When actual = all.stream().filter(binding -> binding.group().getString().equals(group) && binding.name().equals(name))
                .map(ExternalBinding::when).findFirst().orElse(null);
        t.check("external: " + group + " / " + name + " is in use " + expected + (actual == expected ? "" : " but was " + actual), actual == expected);
    }

    private static ExternalBinding.When malilibWhen(String modId, String name, boolean bareModifier, boolean bareMouseClick) {
        return ExternalKeys.malilibWhen(modId, name, bareModifier, bareMouseClick);
    }

    private void external(List<ExternalBinding> all, String group, String name, String keyText, boolean active) {
        ExternalBinding found = null;
        for (ExternalBinding binding : all) {
            if (binding.group().getString().equals(group) && binding.name().equals(name)) {
                found = binding;
            }
        }
        String actual = found == null ? "not listed" : found.keyText().getString() + (found.active() ? "" : " (inactive)");
        t.check("external: " + group + " / " + name + " -> " + keyText + (active ? "" : " (inactive)")
                        + (found != null && found.keyText().getString().equals(keyText) && found.active() == active ? "" : " but was " + actual),
                found != null && found.keyText().getString().equals(keyText) && found.active() == active);
    }

    private static void writeFixtures(Path root) throws IOException {
        Path meteor = Files.createDirectories(root.resolve("meteor-client"));
        ListTag modules = new ListTag();
        CompoundTag autoTotem = meteorModule("auto-totem", true, 90, 0);
        CompoundTag setting = new CompoundTag();
        setting.putString("name", "swap-key");
        setting.put("value", meteorKeybind(true, 344, 0));
        ListTag settingList = new ListTag();
        settingList.add(setting);
        CompoundTag group = new CompoundTag();
        group.putString("name", "General");
        group.put("settings", settingList);
        ListTag groups = new ListTag();
        groups.add(group);
        CompoundTag settings = new CompoundTag();
        settings.put("groups", groups);
        autoTotem.put("settings", settings);
        modules.add(autoTotem);
        modules.add(meteorModule("free-look", true, 75, 2));
        modules.add(meteorModule("unbound-module", true, -1, 0));
        modules.add(meteorModule("mouse-module", false, 3, 0));
        modules.add(meteorModule("light-overlay", true, 66, 0));
        modules.add(meteorModule("auto-eat", true, 74, 0));
        // The same things the way newer Meteor versions write them: key names and modifier names.
        CompoundTag antiAfk = meteorModule("anti-afk", "key.keyboard.h", "CONTROL");
        CompoundTag pauseKey = new CompoundTag();
        pauseKey.putString("name", "pause-key");
        pauseKey.put("value", meteorKeybind("key.mouse.5"));
        ListTag pauseSettings = new ListTag();
        pauseSettings.add(pauseKey);
        CompoundTag pauseGroup = new CompoundTag();
        pauseGroup.putString("name", "General");
        pauseGroup.put("settings", pauseSettings);
        ListTag pauseGroups = new ListTag();
        pauseGroups.add(pauseGroup);
        CompoundTag antiAfkSettings = new CompoundTag();
        antiAfkSettings.put("groups", pauseGroups);
        antiAfk.put("settings", antiAfkSettings);
        modules.add(antiAfk);
        modules.add(meteorModule("new-unbound", "key.keyboard.unknown"));
        modules.add(meteorModule("super-module", "key.keyboard.n", "SUPER"));
        CompoundTag rootTag = new CompoundTag();
        rootTag.putString("name", "modules");
        rootTag.put("modules", modules);
        NbtIo.write(rootTag, meteor.resolve("modules.nbt"));

        Path pvp = Files.createDirectories(meteor.resolve("profiles").resolve("pvp"));
        ListTag pvpModules = new ListTag();
        pvpModules.add(meteorModule("auto-totem", true, 89, 0));
        CompoundTag pvpRoot = new CompoundTag();
        pvpRoot.put("modules", pvpModules);
        NbtIo.write(pvpRoot, pvp.resolve("modules.nbt"));

        Path config = Files.createDirectories(root.resolve("config"));
        Files.writeString(config.resolve("litematica.json"), """
                {
                  "Generic": { "pickBlockEnabled": true, "toolItem": "minecraft:stick" },
                  "Hotkeys": {
                    "openGuiMainMenu": { "keys": "M" },
                    "toggleAllRendering": { "keys": "M,R" },
                    "executeOperation": { "keys": "LEFT_CONTROL,KP_5" },
                    "unboundOne": { "keys": "" },
                    "pickBlockLast": { "keys": "BUTTON_3" },
                    "toolPlaceCorner1": { "keys": "BUTTON_1" },
                    "toolPlaceCorner2": { "keys": "BUTTON_2" },
                    "toolSelectElements": { "keys": "BUTTON_3" },
                    "toolSelectModifierBlock2": { "keys": "LEFT_SHIFT" },
                    "operationModeChangeModifier": { "keys": "LEFT_CONTROL" },
                    "rerenderSchematic": { "keys": "F3,M" },
                    "layerNext": { "keys": "PAGE_UP" }
                  }
                }
                """);
        Files.writeString(config.resolve("itemscroller.json"), """
                {
                  "Hotkeys": {
                    "modifierMoveEverything": { "keys": "LEFT_ALT", "settings": { "activate_on": "PRESS", "context": "GUI" } }
                  },
                  "Toggles": {
                    "craftingFeatures": { "enabled": true, "hotkey": { "keys": "KP_SUBTRACT", "settings": { "context": "GUI" } } }
                  }
                }
                """);
    }

    private static CompoundTag meteorModule(String name, boolean isKey, int value, int modifiers) {
        CompoundTag module = new CompoundTag();
        module.putString("name", name);
        module.put("keybind", meteorKeybind(isKey, value, modifiers));
        module.putBoolean("toggleOnKeyRelease", false);
        module.putBoolean("chatFeedback", true);
        module.putBoolean("favorite", false);
        module.put("settings", new CompoundTag());
        module.putBoolean("active", false);
        return module;
    }

    private static CompoundTag meteorModule(String name, String keyName, String... modifierNames) {
        CompoundTag module = meteorModule(name, true, -1, 0);
        module.put("keybind", meteorKeybind(keyName, modifierNames));
        return module;
    }

    private static CompoundTag meteorKeybind(String keyName, String... modifierNames) {
        CompoundTag keybind = new CompoundTag();
        keybind.putString("key", keyName);
        ListTag modifiers = new ListTag();
        for (String modifier : modifierNames) {
            modifiers.add(net.minecraft.nbt.StringTag.valueOf(modifier));
        }
        keybind.put("modifiers", modifiers);
        return keybind;
    }

    private static CompoundTag meteorKeybind(boolean isKey, int value, int modifiers) {
        CompoundTag keybind = new CompoundTag();
        keybind.putBoolean("isKey", isKey);
        keybind.putInt("value", value);
        keybind.putInt("modifiers", modifiers);
        return keybind;
    }

    private record FixtureEnvironment(Path gameDirectory, boolean hasMeteor, List<ExternalKeys.MalilibMod> malilibMods) implements ExternalKeys.Environment {
    }

    private static Map<Path, String> hashes(Path root) {
        Map<Path, String> hashes = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                hashes.put(root.relativize(path), HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path))));
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            hashes.put(root, "unreadable: " + e);
        }
        return hashes;
    }

    private static Map<Path, Long> modifiedTimes(Path root) {
        Map<Path, Long> times = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                times.put(root.relativize(path), Files.getLastModifiedTime(path).toMillis());
            }
        } catch (IOException e) {
            times.put(root, -1L);
        }
        return times;
    }

    // ------------------------------------------------------------------ profiles

    void profileCreate() {
        KeyMapping jump = SelfTestRunner.binding(TEST_BINDING_ID);
        originalTestKey = jump.saveString();

        service.saveProfile(PROFILE_A, client().options.keyMappings);
        t.check("create " + PROFILE_A + ": in memory", service.profiles().containsKey(PROFILE_A));
        t.check("create " + PROFILE_A + ": file written", profileFile(PROFILE_A).isFile());
        t.check("create " + PROFILE_A + ": the file is readable JSON with the key bindings in it", readableProfileFile(PROFILE_A));
        t.check("create " + PROFILE_A + ": stores " + TEST_BINDING_ID + "=" + originalTestKey,
                originalTestKey.equals(service.profiles().get(PROFILE_A).get(TEST_BINDING_ID)));

        t.bind(TEST_BINDING_ID, TEST_KEY);
        service.saveProfile(PROFILE_B, client().options.keyMappings);
        t.check("create " + PROFILE_B + ": stores " + TEST_BINDING_ID + "=" + TEST_KEY,
                TEST_KEY.equals(service.profiles().get(PROFILE_B).get(TEST_BINDING_ID)));
        t.check("create " + PROFILE_B + ": file written", profileFile(PROFILE_B).isFile());
        t.check("create: a second profile with the same name in other capitals is refused",
                !service.createProfile(PROFILE_B.toUpperCase(java.util.Locale.ROOT), Map.of("key.jump", "key.keyboard.space"), Map.of()));
    }

    void profileApply() {
        KeyMapping jump = SelfTestRunner.binding(TEST_BINDING_ID);

        service.applyProfile(PROFILE_A);
        t.check("apply " + PROFILE_A + ": " + TEST_BINDING_ID + " back to " + originalTestKey, originalTestKey.equals(jump.saveString()));
        t.check("apply " + PROFILE_A + ": becomes current profile", PROFILE_A.equals(service.getCurrentProfile()));

        service.applyProfile(PROFILE_B);
        t.check("apply " + PROFILE_B + ": " + TEST_BINDING_ID + " is " + TEST_KEY, TEST_KEY.equals(jump.saveString()));
        t.check("apply " + PROFILE_B + ": becomes current profile", PROFILE_B.equals(service.getCurrentProfile()));
        t.check("apply " + PROFILE_B + ": options.txt updated", optionsFileContains("key_" + TEST_BINDING_ID + ":" + TEST_KEY));
    }

    void profileRename() {
        KeyMapping jump = SelfTestRunner.binding(TEST_BINDING_ID);
        service.setProfileHotkey(PROFILE_B, List.of("key.keyboard.keypad.5", "key.keyboard.f6"));
        service.setProfileAutoSwitchServers(PROFILE_B, List.of("example.org", "*.selftest.example"));

        t.check("rename " + PROFILE_B + " -> " + PROFILE_C + ": accepted", service.renameProfile(PROFILE_B, PROFILE_C));
        t.check("rename: old name gone", !service.profiles().containsKey(PROFILE_B) && !profileFile(PROFILE_B).exists());
        t.check("rename: new name present", service.profiles().containsKey(PROFILE_C) && profileFile(PROFILE_C).isFile());
        t.check("rename: current profile follows", PROFILE_C.equals(service.getCurrentProfile()));
        t.check("rename: hotkey kept", List.of("key.keyboard.keypad.5", "key.keyboard.f6").equals(service.getProfileHotkey(PROFILE_C)));
        t.check("rename: servers kept", List.of("example.org", "*.selftest.example").equals(service.getProfileAutoSwitchServers(PROFILE_C)));
        t.check("rename: live key bindings untouched", TEST_KEY.equals(jump.saveString()));
        t.check("rename onto an existing name is refused", !service.renameProfile(PROFILE_C, PROFILE_A));
    }

    void profileReload() {
        service.reloadProfiles();
        t.check("reload: " + PROFILE_A + " read back", service.profiles().containsKey(PROFILE_A)
                && originalTestKey.equals(service.profiles().get(PROFILE_A).get(TEST_BINDING_ID)));
        t.check("reload: " + PROFILE_C + " read back", service.profiles().containsKey(PROFILE_C)
                && TEST_KEY.equals(service.profiles().get(PROFILE_C).get(TEST_BINDING_ID)));
        t.check("reload: hotkey read back", List.of("key.keyboard.keypad.5", "key.keyboard.f6").equals(service.getProfileHotkey(PROFILE_C)));
        t.check("reload: servers read back", List.of("example.org", "*.selftest.example").equals(service.getProfileAutoSwitchServers(PROFILE_C)));
    }

    void profileDelete() {
        for (String name : new ArrayList<>(service.profiles().keySet())) {
            if (name.startsWith(PROFILE_PREFIX)) {
                service.deleteProfile(name);
                t.check("delete: " + name + " removed from memory and disk", !service.profiles().containsKey(name) && !profileFile(name).exists());
            }
        }
        t.check("delete: current profile cleared", service.getCurrentProfile() == null);
    }

    /**
     * Turns selftest_a into a partial profile: it saves only the jump key plus two game settings.
     * Everything else must be left alone when it is applied.
     */
    void profileContents() {
        Map<String, GameOptionsBridge.Entry> options = GameOptionsBridge.readAll(client().options);
        t.check("options: the game settings can be listed (" + options.size() + " entries)", options.size() > 60);
        t.check("options: fov, mouse sensitivity and auto-jump are listed",
                options.containsKey("fov") && options.containsKey("mouseSensitivity") && options.containsKey("autoJump"));
        t.check("options: key bindings are not listed as settings", options.keySet().stream().noneMatch(key -> key.startsWith("key_")));
        t.check("options: language and resource packs are never offered",
                !OptionCatalog.isOffered("lang") && !OptionCatalog.isOffered("resourcePacks") && OptionCatalog.isOffered("fov"));
        int uncategorized = 0;
        for (String key : options.keySet()) {
            if (OptionCatalog.isOffered(key) && OptionCatalog.categoryOf(key) == OptionCatalog.Category.OTHER) {
                uncategorized++;
                SelfTestRunner.log("options: not in a named group: " + key);
            }
        }
        t.check("options: every offered setting has a named group (" + uncategorized + " in Other)", uncategorized == 0);

        originalAutoJump = options.get("autoJump").rawValue();
        originalFov = options.get("fov").rawValue();
        profileAutoJump = "true".equals(originalAutoJump) ? "false" : "true";
        t.check("options: values are shown without repeating the name (fov 0.5 -> 90)", "90".equals(options.get("fov").describe(PROFILE_FOV).getString()));
        GameOptionsBridge.Entry chunkFade = options.get("chunkSectionFadeInTime");
        if (chunkFade != null) {
            String shown = chunkFade.describe(chunkFade.rawValue()).getString();
            t.check("options: shortened labels are stripped too (chunk fade shown as [" + shown + "])", !shown.contains(":"));
        }

        service.setProfileContents(PROFILE_A, Map.of(TEST_BINDING_ID, PROFILE_A_KEY), Map.of("autoJump", profileAutoJump, "fov", PROFILE_FOV));
        t.check("contents: profile now saves 1 key binding", service.profiles().get(PROFILE_A).size() == 1);
        t.check("contents: profile now saves 2 settings", service.getProfileOptions(PROFILE_A).size() == 2);
        service.reloadProfiles();
        t.check("contents: settings survive a reload from disk",
                PROFILE_FOV.equals(service.getProfileOptions(PROFILE_A).get("fov")) && profileAutoJump.equals(service.getProfileOptions(PROFILE_A).get("autoJump")));

        List<ProfileChange> changes = service.previewApply(PROFILE_A);
        for (ProfileChange change : changes) {
            SelfTestRunner.log("preview: " + change.kind() + " " + change.name().getString() + ": " + change.from().getString() + " -> " + change.to().getString());
        }
        t.check("preview: exactly the 3 saved items would change, got " + changes.size(), changes.size() == 3);
        t.check("preview: 1 key binding and 2 settings",
                changes.stream().filter(change -> change.kind() == ProfileChange.Kind.KEY_BINDING).count() == 1
                        && changes.stream().filter(change -> change.kind() == ProfileChange.Kind.OPTION).count() == 2);
    }

    void comparison() {
        int savedByC = service.profiles().get(PROFILE_C).size();
        ProfileComparison.Result partial = ProfileComparison.compare(service, client().options, PROFILE_A, PROFILE_C);
        t.check("compare: " + PROFILE_A + " vs " + PROFILE_C + " differ only in the jump key, got " + partial.different(), partial.different() == 1);
        t.check("compare: items saved by one side only are counted separately (" + partial.oneSided() + ")", partial.oneSided() == savedByC - 1 + 2);

        ProfileComparison.Result live = ProfileComparison.compare(service, client().options, PROFILE_C, null);
        t.check("compare: the applied profile matches the current settings", live.different() == 0 && live.oneSided() == 0);
        t.bind(TEST_BINDING_ID, PROFILE_A_KEY);
        t.check("compare: changing a key in the game shows up as 1 difference",
                ProfileComparison.compare(service, client().options, PROFILE_C, null).different() == 1);
        t.bind(TEST_BINDING_ID, TEST_KEY);

        // Settings count as well when both sides save them.
        service.createProfile(PROFILE_PREFIX + "d", Map.of(TEST_BINDING_ID, PROFILE_A_KEY), Map.of("fov", "1.0", "autoJump", profileAutoJump));
        ProfileComparison.Result withSettings = ProfileComparison.compare(service, client().options, PROFILE_A, PROFILE_PREFIX + "d");
        t.check("compare: a setting both profiles save with different values is a difference (fov), an equal one is not (auto-jump)",
                withSettings.different() == 1 && withSettings.rows().stream().anyMatch(row -> !row.keyBinding() && row.id().equals("fov")
                        && row.state() == ProfileComparison.State.DIFFERENT)
                        && withSettings.rows().stream().anyMatch(row -> row.id().equals("autoJump") && row.state() == ProfileComparison.State.SAME));
        service.deleteProfile(PROFILE_PREFIX + "d");
    }

    /** Puts the live game back into a state where applying selftest_a changes exactly its saved items. */
    void makeGameDifferFromProfileA() {
        t.bind(TEST_BINDING_ID, TEST_KEY);
        GameOptionsBridge.apply(client().options, Map.of("autoJump", originalAutoJump, "fov", originalFov));
    }

    // ------------------------------------------------------------------ helpers

    File profileFile(String name) {
        return new File(service.profilesDirectory(), name + ".kbp");
    }

    private boolean readableProfileFile(String name) {
        try (Reader reader = Files.newBufferedReader(profileFile(name).toPath(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return name.equals(json.get("name").getAsString()) && json.getAsJsonObject("keybindings").has(TEST_BINDING_ID);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    boolean optionsFileContains(String line) {
        try {
            return Files.readAllLines(new File(client().gameDirectory, "options.txt").toPath(), StandardCharsets.UTF_8).contains(line);
        } catch (IOException e) {
            return false;
        }
    }

    private static Map<String, String> readLanguage(String code) {
        Map<String, String> entries = new LinkedHashMap<>();
        Path path = FabricLoader.getInstance().getModContainer(KeyBindProfilesPlus.MOD_ID)
                .flatMap(mod -> mod.findPath(LANG_PATH + code + ".json")).orElse(null);
        if (path == null) {
            return entries;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
        } catch (IOException | RuntimeException e) {
            KeyBindProfilesPlus.LOGGER.error(SelfTestRunner.LOG_PREFIX + "could not read language file " + code, e);
        }
        return entries;
    }

    private static int countPlaceholders(String text) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf("%s", index)) >= 0) {
            count++;
            index += 2;
        }
        return count;
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
