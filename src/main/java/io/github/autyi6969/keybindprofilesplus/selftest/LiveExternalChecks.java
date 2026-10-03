package io.github.autyi6969.keybindprofilesplus.selftest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyi6969.keybindprofilesplus.KeyBindProfilesPlus;
import io.github.autyi6969.keybindprofilesplus.external.ExternalBinding;
import io.github.autyi6969.keybindprofilesplus.external.ExternalKeys;
import io.github.autyi6969.keybindprofilesplus.gui.ApplyConfirmScreen;
import io.github.autyi6969.keybindprofilesplus.gui.KeyOverviewScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileCompareScreen;
import io.github.autyi6969.keybindprofilesplus.gui.ProfileContentsScreen;
import io.github.autyi6969.keybindprofilesplus.keys.KeyCombo;
import io.github.autyi6969.keybindprofilesplus.keys.KeyConflicts;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileChange;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileComparison;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import io.github.autyi6969.keybindprofilesplus.profile.ShareCode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.autyi6969.keybindprofilesplus.selftest.LogicChecks.PROFILE_PREFIX;
import static io.github.autyi6969.keybindprofilesplus.selftest.SelfTestRunner.SCREEN_SETTLE_TICKS;

/**
 * Changing the hotkeys of other mods for real: the dev client runs with malilib + Litematica and
 * Meteor (put into libs/ by tools/prepare-dev-mods.ps1). Rebinding through the Key Binds screen with
 * real clicks and key events, checking that the mod's own object changed and that the mod saved
 * its config file, conflicts with edited values, and a profile that saves, shares and re-applies
 * them. Every hotkey touched is put back at the end. Without those mods in the dev client the
 * checks are skipped (logged), not failed.
 */
final class LiveExternalChecks {
    static final String PROFILE_LIVE = PROFILE_PREFIX + "live_external";
    private static final String LITEMATICA_HOTKEY = "openGuiMainMenu";
    private static final String LITEMATICA_ID = "ext:malilib:litematica/" + LITEMATICA_HOTKEY;
    private static final String METEOR_MODULE = "auto-totem";
    private static final String METEOR_ID = "ext:meteor:" + METEOR_MODULE;

    private final SelfTestRunner t;
    private final LogicChecks logic;
    private final ProfileService service;
    private final Map<String, String> originals = new LinkedHashMap<>();
    private boolean litematica;
    private boolean meteor;

    LiveExternalChecks(SelfTestRunner runner, LogicChecks logic, ProfileService service) {
        this.t = runner;
        this.logic = logic;
        this.service = service;
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    void register() {
        t.step("live external: the running mods", this::start);
        litematicaOnScreen();
        meteorOnScreen();
        t.step("live external: conflicts between edited hotkeys", this::conflicts);
        profileFlow();
        t.step("live external: put everything back", this::restore);
    }

    // ------------------------------------------------------------------ setup

    private void start() {
        // The real mods instead of the fixture files used so far.
        ExternalKeys.setEnvironmentForTesting(null);
        litematica = FabricLoader.getInstance().isModLoaded("litematica");
        meteor = FabricLoader.getInstance().isModLoaded("meteor-client");
        SelfTestRunner.log("live external: litematica=" + litematica + " meteor=" + meteor
                + (litematica && meteor ? "" : " (run tools/prepare-dev-mods.ps1 to have them in the dev client; their checks are skipped)"));

        List<ExternalBinding> all = ExternalKeys.all();
        if (litematica) {
            t.check("live external: malilib hotkeys come from the running malilib", ExternalKeys.isLive("malilib"));
            long editable = all.stream().filter(binding -> binding.editable() && binding.sourceId().equals("litematica")).count();
            t.check("live external: all of Litematica's hotkeys are listed and editable, also the unbound ones (" + editable + ")", editable > 50
                    && all.stream().anyMatch(binding -> binding.sourceId().equals("litematica") && binding.editable() && binding.unbound()));
            t.check("live external: malilib's own hotkeys are there too",
                    all.stream().anyMatch(binding -> binding.editable() && binding.sourceId().equals("malilib")));
            ExternalBinding open = ExternalKeys.find(LITEMATICA_ID);
            t.check("live external: Litematica's 'open main menu' hotkey is found with its default M ("
                    + (open == null ? "missing" : open.value() + " / default " + open.defaultValue()) + ")", open != null && "M".equals(open.defaultValue()));
            if (open != null) {
                originals.put(LITEMATICA_ID, open.value());
            }
            ExternalBinding corner = all.stream().filter(binding -> "ext:malilib:litematica/toolPlaceCorner1".equals(binding.hotkeyId())).findFirst().orElse(null);
            t.check("live external: the tool key on the left mouse button still counts as 'only in a special situation'",
                    corner != null && corner.when() == ExternalBinding.When.SITUATIONAL);
        }
        if (meteor) {
            t.check("live external: Meteor's binds come from the running Meteor", ExternalKeys.isLive("meteor"));
            long modules = all.stream().filter(binding -> binding.editable() && binding.hotkeyId().startsWith("ext:meteor:")).count();
            t.check("live external: every Meteor module is listed and editable (" + modules + ")", modules > 100);
            ExternalBinding totem = ExternalKeys.find(METEOR_ID);
            t.check("live external: Auto Totem is found", totem != null && totem.sourceId().equals("meteor") && totem.when() == ExternalBinding.When.IN_GAME);
            if (totem != null) {
                originals.put(METEOR_ID, totem.value());
            }
            t.check("live external: key bind settings inside modules are listed too",
                    all.stream().anyMatch(binding -> binding.editable() && binding.hotkeyId().startsWith("ext:meteor:") && binding.hotkeyId().contains("/")));
        }
    }

    // ------------------------------------------------------------------ Litematica through the Key Binds screen

    private void litematicaOnScreen() {
        t.open("live external: key binds screen", () -> new KeyOverviewScreen(t.homeScreen));
        t.step("live external: Litematica: search", 2, () -> {
            if (litematica) {
                t.screen(KeyOverviewScreen.class).setQuery("open gui main menu");
            }
        });
        t.step("live external: Litematica: click the key button", 2, () -> {
            if (!litematica) {
                return;
            }
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(LITEMATICA_ID), 0);
            t.check("live external: a click on a Litematica hotkey's button makes it wait for a key", LITEMATICA_ID.equals(keys.waitingFor()));
        });
        t.step("live external: Litematica: Ctrl + F19", 2, () -> {
            if (!litematica) {
                return;
            }
            t.sendKey(InputUtil.GLFW_KEY_LEFT_CONTROL, true, KeyCombo.CTRL);
            t.sendKey(InputUtil.GLFW_KEY_F19, true, KeyCombo.CTRL);
            t.sendKey(InputUtil.GLFW_KEY_F19, false, KeyCombo.CTRL);
            t.sendKey(InputUtil.GLFW_KEY_LEFT_CONTROL, false, 0);
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            ExternalBinding open = ExternalKeys.find(LITEMATICA_ID);
            t.check("live external: Litematica's own hotkey object now has LEFT_CONTROL,F19 (" + (open == null ? "?" : open.value()) + ")",
                    open != null && "LEFT_CONTROL,F19".equals(open.value()) && keys.waitingFor() == null);
            t.check("live external: Litematica saved it to its own config file", "LEFT_CONTROL,F19".equals(malilibFileValue(LITEMATICA_HOTKEY)));
            t.check("live external: the list shows Ctrl + F19 (" + keys.externalKeyText(LITEMATICA_ID) + ")", "Ctrl + F19".equals(keys.externalKeyText(LITEMATICA_ID)));
        });
        t.shot("en_keybinds_litematica_rebound");
        t.step("live external: Litematica: reset", 2, () -> {
            if (!litematica) {
                return;
            }
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.check("live external: Reset puts Litematica's default back", keys.resetExternal(LITEMATICA_ID)
                    && "M".equals(ExternalKeys.find(LITEMATICA_ID).value()) && "M".equals(malilibFileValue(LITEMATICA_HOTKEY)));
        });
        t.step("live external: Litematica: Escape", 2, () -> {
            if (!litematica) {
                return;
            }
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(LITEMATICA_ID), 0);
            t.sendKey(InputUtil.GLFW_KEY_ESCAPE, true, 0);
            t.sendKey(InputUtil.GLFW_KEY_ESCAPE, false, 0);
            ExternalBinding open = ExternalKeys.find(LITEMATICA_ID);
            t.check("live external: Escape leaves the Litematica hotkey without a key, saved as such, screen still open",
                    open != null && open.unbound() && "".equals(malilibFileValue(LITEMATICA_HOTKEY)) && t.isScreen(KeyOverviewScreen.class));
        });
    }

    // ------------------------------------------------------------------ Meteor through the Key Binds screen

    private void meteorOnScreen() {
        t.step("live external: Meteor: search", 2, () -> {
            if (meteor) {
                t.screen(KeyOverviewScreen.class).setQuery("auto totem");
            }
        });
        t.step("live external: Meteor: click the key button", 2, () -> {
            if (!meteor) {
                return;
            }
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(METEOR_ID), 0);
            t.check("live external: a click on a Meteor module's button makes it wait for a key", METEOR_ID.equals(keys.waitingFor()));
        });
        t.step("live external: Meteor: F20", 2, () -> {
            if (!meteor) {
                return;
            }
            t.sendKey(InputUtil.GLFW_KEY_F20, true, 0);
            t.sendKey(InputUtil.GLFW_KEY_F20, false, 0);
            ExternalBinding totem = ExternalKeys.find(METEOR_ID);
            t.check("live external: Meteor's own key bind object now has F20 (" + (totem == null ? "?" : totem.value()) + ")",
                    totem != null && "key.keyboard.f20".equals(totem.value()));
            t.check("live external: Meteor saved it to modules.nbt", meteorFileValue(METEOR_MODULE) == InputUtil.GLFW_KEY_F20);
        });
        t.shot("en_keybinds_meteor_rebound");
        t.step("live external: Meteor refuses the left mouse button", 2, () -> {
            if (!meteor) {
                return;
            }
            KeyOverviewScreen keys = t.screen(KeyOverviewScreen.class);
            t.mouseClick(keys.keyButtonPoint(METEOR_ID), 0);
            t.check("live external: waiting again", METEOR_ID.equals(keys.waitingFor()));
            t.mouseClick(new int[]{5, 5}, 0);
            t.check("live external: binding a module to the left mouse button is refused, as in Meteor itself",
                    "key.keyboard.f20".equals(ExternalKeys.find(METEOR_ID).value()) && keys.waitingFor() == null);
            String status = keys.statusText();
            t.check("live external: ... and the screen says so (" + status + ")", status != null && status.contains("Auto Totem"));
            keys.setQuery("");
        });
        t.shot("en_keybinds_meteor_refused");
    }

    // ------------------------------------------------------------------ conflicts

    private void conflicts() {
        if (!meteor) {
            return;
        }
        KeyBinding jump = SelfTestRunner.binding("key.jump");
        String jumpBefore = jump.getBoundKeyTranslationKey();
        try {
            t.bind("key.jump", "key.keyboard.f20");
            ExternalKeys.refresh();
            t.check("live external: a game key on the Meteor module's new key (F20) is a conflict",
                    KeyConflicts.conflictsOf(jump, client().options).stream().anyMatch(conflict -> conflict.external() != null
                            && METEOR_ID.equals(conflict.external().hotkeyId()) && conflict.level() == KeyConflicts.Level.HARD));
            t.bind("key.jump", jumpBefore);
            if (litematica) {
                ExternalKeys.setValue(ExternalKeys.find(LITEMATICA_ID), "F20");
                ExternalBinding totem = ExternalKeys.find(METEOR_ID);
                t.check("live external: a Meteor module and a Litematica hotkey on the same key are reported against each other",
                        KeyConflicts.conflictsOf(totem, client().options).stream().anyMatch(conflict -> conflict.external() != null
                                && LITEMATICA_ID.equals(conflict.external().hotkeyId()) && conflict.level() == KeyConflicts.Level.HARD));
                ExternalKeys.setValue(ExternalKeys.find(LITEMATICA_ID), "");
            }
        } finally {
            t.bind("key.jump", jumpBefore);
        }
    }

    // ------------------------------------------------------------------ profiles

    private void profileFlow() {
        t.step("live external: a profile saves them", () -> {
            if (!litematica && !meteor) {
                return;
            }
            set(LITEMATICA_ID, "F21", litematica);
            set(METEOR_ID, "key.keyboard.f22", meteor);
            service.saveProfile(PROFILE_LIVE, client().options.allKeys);
            Map<String, String> saved = service.profiles().get(PROFILE_LIVE);
            t.check("live external: the profile holds the other mods' hotkeys next to the game's key bindings",
                    (!litematica || "F21".equals(saved.get(LITEMATICA_ID))) && (!meteor || "key.keyboard.f22".equals(saved.get(METEOR_ID))));
            try {
                ShareCode.Content back = ShareCode.decode(ShareCode.encode(new ShareCode.Content(PROFILE_LIVE, saved, Map.of())));
                t.check("live external: a share code carries them", back.keyBindings().equals(saved));
            } catch (ShareCode.InvalidShareCodeException e) {
                t.fail("live external: share code could not be read back: " + e.problem());
            }

            set(LITEMATICA_ID, "LEFT_ALT,F23", litematica);
            set(METEOR_ID, "key.keyboard.unknown", meteor);
            List<ProfileChange> changes = service.previewApply(PROFILE_LIVE);
            long external = changes.stream().filter(change -> change.kind() == ProfileChange.Kind.EXTERNAL).count();
            t.check("live external: the apply preview lists them as other mods' hotkeys (" + changes.stream()
                            .filter(change -> change.kind() == ProfileChange.Kind.EXTERNAL).map(ProfileChange::id).toList() + ")",
                    external == (litematica ? 1 : 0) + (meteor ? 1 : 0));
            ProfileComparison.Result compared = ProfileComparison.compare(service, client().options, PROFILE_LIVE, null);
            t.check("live external: the comparison with the current keys shows them as different",
                    compared.rows().stream().filter(row -> row.id().startsWith(ExternalKeys.ID_PREFIX) && row.state() == ProfileComparison.State.DIFFERENT)
                            .count() == external);
            client().setScreen(new ApplyConfirmScreen(t.homeScreen, PROFILE_LIVE, changes, KeyBindProfilesPlus.settings(), () -> {
            }));
        });
        t.shot("en_apply_confirm_other_mods");
        t.open("live external: compare with the current keys", () -> new ProfileCompareScreen(t.homeScreen, service, PROFILE_LIVE, null));
        t.step("live external: only the differences", 3, () -> t.click(SelfTestRunner.translated("keybindprofilesplus.compare.only_differences") + ": "
                + SelfTestRunner.translated("options.off")));
        t.shot("en_compare_other_mods");
        t.step("live external: applying the profile sets them back", () -> {
            if (!litematica && !meteor) {
                return;
            }
            service.applyProfile(PROFILE_LIVE);
            if (litematica) {
                t.check("live external: Litematica's hotkey is F21 again, in the mod and in its file",
                        "F21".equals(ExternalKeys.find(LITEMATICA_ID).value()) && "F21".equals(malilibFileValue(LITEMATICA_HOTKEY)));
            }
            if (meteor) {
                t.check("live external: Meteor's Auto Totem is F22 again, in Meteor and in modules.nbt",
                        "key.keyboard.f22".equals(ExternalKeys.find(METEOR_ID).value()) && meteorFileValue(METEOR_MODULE) == InputUtil.GLFW_KEY_F22);
            }
            List<String> left = service.previewApply(PROFILE_LIVE).stream().filter(change -> change.kind() == ProfileChange.Kind.EXTERNAL)
                    .map(change -> change.id() + " " + change.from().getString() + " -> " + change.to().getString()).toList();
            t.check("live external: nothing left to apply " + left, left.isEmpty());
        });
        t.open("live external: what the profile saves", () -> new ProfileContentsScreen(t.homeScreen, service, PROFILE_LIVE, name -> {
        }));
        t.step("live external: the contents tree", 3, () -> {
            ProfileContentsScreen contents = t.screen(ProfileContentsScreen.class);
            contents.setExpanded("keys", false);
            contents.setExpanded("options", false);
            if (litematica) {
                contents.setExpanded("external/Litematica", true);
                t.check("live external: the contents tree has the other mods' hotkeys, ticked",
                        contents.isChecked("key:" + LITEMATICA_ID) && contents.isExpanded("external"));
            }
        });
        t.shot("en_contents_other_mods");
    }

    // ------------------------------------------------------------------ cleanup

    private void restore() {
        if (!originals.isEmpty()) {
            ExternalKeys.applyValues(originals);
            Map<String, String> now = ExternalKeys.currentValues();
            boolean back = originals.entrySet().stream().allMatch(entry -> entry.getValue().equals(now.get(entry.getKey())));
            t.check("live external: every hotkey touched is back on its value from before (" + originals + ")", back);
            if (litematica) {
                t.check("live external: ... also in Litematica's file", originals.get(LITEMATICA_ID).equals(malilibFileValue(LITEMATICA_HOTKEY)));
            }
        }
        service.deleteProfile(PROFILE_LIVE);
        // The screens that follow are photographed with the fixture files again.
        ExternalKeys.setEnvironmentForTesting(logic.fixtureEnvironment());
    }

    private void set(String id, String value, boolean when) {
        if (when) {
            ExternalBinding binding = ExternalKeys.find(id);
            if (binding == null || !ExternalKeys.setValue(binding, value)) {
                t.fail("live external: could not set " + id + " to " + value);
            }
        }
    }

    // ------------------------------------------------------------------ reading the mods' files back

    /** The keys of one Litematica hotkey as written in config/litematica.json, or null. */
    private static String malilibFileValue(String hotkeyName) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("litematica.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return find(JsonParser.parseReader(reader), hotkeyName);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String find(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.get(name) instanceof JsonObject hotkey && hotkey.get("keys") != null) {
            return hotkey.get("keys").getAsString();
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String found = find(entry.getValue(), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The key code of a Meteor module's bind as written in meteor-client/modules.nbt; -2 when not found. */
    private static int meteorFileValue(String module) {
        Path file = FabricLoader.getInstance().getGameDir().resolve("meteor-client").resolve("modules.nbt");
        try {
            NbtCompound root = NbtIo.read(file);
            if (root == null) {
                return -2;
            }
            for (NbtCompound tag : root.getListOrEmpty("modules").streamCompounds().toList()) {
                if (module.equals(tag.getString("name", ""))) {
                    return tag.getCompound("keybind").map(keybind -> keybind.getInt("value", -2)).orElse(-2);
                }
            }
        } catch (IOException | RuntimeException e) {
            return -2;
        }
        return -2;
    }
}
