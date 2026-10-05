package io.github.autyism.keybindprofilesplus.selftest;

import io.github.autyism.keybindprofilesplus.configs.BytecodeEvidence;
import io.github.autyism.keybindprofilesplus.configs.ConfigArchive;
import io.github.autyism.keybindprofilesplus.configs.ConfigImport;
import io.github.autyism.keybindprofilesplus.configs.ConfigImportApplier;
import io.github.autyism.keybindprofilesplus.configs.ConfigRules;
import io.github.autyism.keybindprofilesplus.configs.ConfigScan;
import io.github.autyism.keybindprofilesplus.configs.ModConfigs;
import io.github.autyism.keybindprofilesplus.configs.ModInfo;
import io.github.autyism.keybindprofilesplus.configs.SecretDetector;
import io.github.autyism.keybindprofilesplus.configs.WorldNames;
import io.github.autyism.keybindprofilesplus.gui.ConfigExportScreen;
import io.github.autyism.keybindprofilesplus.gui.ConfigImportScreen;
import io.github.autyism.keybindprofilesplus.gui.ModConfigsScreen;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static io.github.autyism.keybindprofilesplus.selftest.SelfTestRunner.translated;

/**
 * Checks of the mod config export / import: the rules, the reading of mod code, the sorting of a
 * made-up game folder with known answers, export -> import -> undo on throwaway folders, the import
 * staged by the harness before this start, the scan of the real development game folder, and the
 * screens. Nothing outside run/selftest_configs and the files it creates itself is changed.
 */
final class ConfigChecks {
    /** Written by tools/selftest.ps1 as a staged import before the game starts. */
    static final String EARLY_FILE = "config/kbp_selftest_early.json";
    static final String EARLY_SOURCE = "selftest_early.zip";
    private static final String SANDBOX_ARCHIVE = "selftest_sandbox.zip";

    private final SelfTestRunner t;
    private final Path sandbox;
    private final List<Path> createdArchives = new ArrayList<>();
    private CompletableFuture<ModConfigs.Context> realScan;
    private Path sandboxArchive;

    ConfigChecks(SelfTestRunner runner) {
        this.t = runner;
        this.sandbox = FabricLoader.getInstance().getGameDir().resolve("selftest_configs");
    }

    // ------------------------------------------------------------------ the import staged before this start

    void earlyImport() {
        Path file = ConfigImportApplier.resolve(EARLY_FILE, ModConfigs.gameDir(), ModConfigs.configDir());
        ConfigImport.LastResult result = ModConfigs.lastResult();
        if (result == null || !EARLY_SOURCE.equals(result.source())) {
            t.check("early import: the harness staged an import before this start (run tools\\selftest.ps1)", false);
            return;
        }
        try {
            t.check("early import: the staged file was written before the mods started", Files.isRegularFile(file)
                    && Files.readString(file).contains("early-import"));
        } catch (IOException e) {
            t.check("early import: written file readable", false);
        }
        t.check("early import: result says 1 file written, nothing skipped (" + result.written() + ", " + result.skipped() + ")",
                result.written() == 1 && result.skipped().isEmpty() && result.errors().isEmpty());
        t.check("early import: the staged file is gone", ModConfigs.pendingSource() == null);
        t.check("early import: a backup lists the created file for undo", result.backup() != null
                && Files.isRegularFile(ModConfigs.exportsDir().resolve(result.backup())) && listsRemoval(ModConfigs.exportsDir().resolve(result.backup()), EARLY_FILE));
        try {
            Files.deleteIfExists(file);
            if (result.backup() != null) {
                Files.deleteIfExists(ModConfigs.exportsDir().resolve(result.backup()));
            }
        } catch (IOException e) {
            t.check("early import: test files removed", false);
        }
    }

    private static boolean listsRemoval(Path backup, String path) {
        try (ZipFile zip = new ZipFile(backup.toFile())) {
            ZipEntry remove = zip.getEntry(ConfigImportApplier.REMOVE_LIST);
            return remove != null && new String(zip.getInputStream(remove).readAllBytes(), StandardCharsets.UTF_8).lines().anyMatch(path::equals);
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ rules

    void rules() {
        t.check("rules: '../x.json', absolute paths and drive letters are refused",
                ConfigRules.normalize("../x.json") == null && ConfigRules.normalize("/etc/x.json") == null
                        && ConfigRules.normalize("C:\\x.json") == null && ConfigRules.normalize("config/a/../../x.json") == null);
        t.check("rules: Windows-reserved and odd names are refused", ConfigRules.normalize("config/con.json") == null
                && ConfigRules.normalize("config/a?.json") == null && ConfigRules.normalize("config/x. ") == null);
        t.check("rules: backslashes are read as folders", "config/sub/x.json".equals(ConfigRules.normalize("config\\sub\\x.json")));
        t.check("rules: only settings files outside worlds, mods and this mod may be written",
                ConfigRules.isWritableTarget("config/x.json") && ConfigRules.isWritableTarget("meteor-client/modules.nbt")
                        && !ConfigRules.isWritableTarget("mods/x.json") && !ConfigRules.isWritableTarget("saves/w/level.json")
                        && !ConfigRules.isWritableTarget("config/x.jar") && !ConfigRules.isWritableTarget("options.txt")
                        && !ConfigRules.isWritableTarget("config/keybindprofilesplus/settings.json")
                        && !ConfigRules.isWritableTarget("config/x.json.bak") && !ConfigRules.isWritableTarget("config/accounts.json"));

        t.check("secrets: account files by name", SecretDetector.secretName("meteor-client/accounts.nbt") != null
                && SecretDetector.secretName("config/viafabricplus/accounts.json") != null && SecretDetector.secretName("config/author.json") == null);
        t.check("secrets: a token in JSON", SecretDetector.secretContent("x.json",
                "{\"refreshToken\": \"M.C543_BAY.0.U.-Abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG\"}".getBytes()) != null);
        t.check("secrets: a password in properties", SecretDetector.secretContent("x.properties", "proxy.password=hunter2\n".getBytes()) != null);
        t.check("secrets: a JWT anywhere", SecretDetector.secretContent("x.txt",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U".getBytes()) != null);
        t.check("secrets: settings that only mention such words are no secrets", SecretDetector.secretContent("x.json",
                "{\"showTokenCount\": true, \"tokenColor\": \"#ffffff\", \"passwordLength\": 12, \"hideSessionTimer\": \"on\"}".getBytes()) == null);
        t.check("secrets: a token in NBT", SecretDetector.secretContent("x.nbt", nbt("token", "M.C543_BAY.0.U.-Abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG")) != null);
        t.check("secrets: plain NBT settings are no secret", SecretDetector.secretContent("x.nbt", nbt("name", "Freecam")) == null);
    }

    /** A minimal NBT file: a compound with one text tag. */
    private static byte[] nbt(String key, String value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(8);
            out.writeUTF(key);
            out.writeUTF(value);
            out.writeByte(0);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ reading mod code

    void bytecode() {
        BytecodeEvidence collector = new BytecodeEvidence();
        collector.addClass(generatedClass());
        BytecodeEvidence.Evidence evidence = collector.build();
        t.check("code: a name handed to new File(...) counts", evidence.strong().contains("file-folder"));
        t.check("code: a name read from a field and handed to Path.resolve counts", evidence.strong().contains("field-folder"));
        t.check("code: a name handed to a file method of a library counts", evidence.medium().contains("library-file"));
        t.check("code: a name handed to another method of a config class counts a little", evidence.weak().contains("library-config"));
        t.check("code: option names handed to a config class's constructor do not count",
                !evidence.weak().contains("option-name") && !evidence.medium().contains("option-name"));
        t.check("code: a name built as \"pattern_\" + x + \".json\" is a pattern", evidence.patterns().contains("pattern_\u0001.json"));
        t.check("code: a name only used in isModLoaded(...) does not count (" + evidence + ")",
                !evidence.strong().contains("compat-only") && !evidence.medium().contains("compat-only"));
        t.check("code: a log message does not count", !evidence.strong().contains("just-a-message") && !evidence.medium().contains("just-a-message"));
        t.check("code: a file name in a text counts wherever it is", evidence.strong().contains("written-anywhere.toml"));
        t.check("code: files inside the jar (assets, mixin configs) do not count",
                !evidence.strong().contains("en_us.json") && !evidence.strong().contains("generated.mixins.json"));
    }

    /** A class with one method for each way a mod may use a text. */
    private static byte[] generatedClass() {
        String name = "kbp/selftest/Generated";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_STATIC, "FOLDER", "Ljava/lang/String;", null, null).visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn("field-folder");
        mv.visitFieldInsn(Opcodes.PUTSTATIC, name, "FOLDER", "Ljava/lang/String;");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC, "compat", "()Z", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/fabricmc/loader/api/FabricLoader", "getInstance", "()Lnet/fabricmc/loader/api/FabricLoader;", true);
        mv.visitLdcInsn("compat-only");
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/fabricmc/loader/api/FabricLoader", "isModLoaded", "(Ljava/lang/String;)Z", true);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC, "file", "(Ljava/io/File;)Ljava/io/File;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "java/io/File");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn("file-folder");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/io/File;Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC, "field", "(Ljava/nio/file/Path;)Ljava/nio/file/Path;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETSTATIC, name, "FOLDER", "Ljava/lang/String;");
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;", true);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC, "library", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn("library-config");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/ConfigManager", "load", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC, "libraryFile", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn("library-file");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Storage", "loadJsonFile", "(Ljava/lang/String;)V", false);
        mv.visitTypeInsn(Opcodes.NEW, "com/example/ConfigDouble");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("option-name");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/example/ConfigDouble", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC, "pattern", "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/Path;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                "pattern_\u0001.json");
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;", true);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC, "texts", "(Lorg/slf4j/Logger;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn("just-a-message");
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", "info", "(Ljava/lang/String;)V", true);
        mv.visitLdcInsn("written-anywhere.toml");
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn("assets/generated/lang/en_us.json");
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn("generated.mixins.json");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------ sorting a made-up game folder

    private static final String HUGE = "x".repeat((int) ConfigRules.MAX_FILE_BYTES + 10);

    /** The made-up mods: alpha (code names its files), beta (only its name), epsilon (name only), zeta (an add-on), gamma (switched off). */
    private static List<ModInfo> sandboxMods(boolean withBeta) {
        List<ModInfo> mods = new ArrayList<>();
        mods.add(new ModInfo("alpha", "Alpha", "1.0", Set.of(), true,
                new BytecodeEvidence.Evidence(Set.of("alpha.json", "alpha", "alpha-data"), Set.of(), Set.of(), Set.of("alpha_\u0001.json")), null));
        if (withBeta) {
            mods.add(new ModInfo("beta", "Beta", "2.0", Set.of(), true, BytecodeEvidence.Evidence.EMPTY, null));
        }
        mods.add(new ModInfo("epsilon", "Epsilon", "1.0", Set.of(), true, BytecodeEvidence.Evidence.EMPTY, null));
        mods.add(new ModInfo("zeta", "Zeta Addon", "1.0", Set.of(), true,
                new BytecodeEvidence.Evidence(Set.of("zeta-config.nbt", "settings.json"), Set.of(), Set.of(), Set.of()), null));
        mods.add(new ModInfo("gamma", "Gamma", "1.0", Set.of(), false, BytecodeEvidence.Evidence.EMPTY, null));
        // Three mods whose code uses the everyday name "common".
        for (String id : List.of("lib-one", "lib-two", "lib-three")) {
            mods.add(new ModInfo(id, id, "1.0", Set.of(), true, new BytecodeEvidence.Evidence(Set.of("common"), Set.of(), Set.of(), Set.of()), null));
        }
        mods.add(new ModInfo("keybindprofilesplus", "KeyBind Profiles+", "0", Set.of(), true,
                new BytecodeEvidence.Evidence(Set.of("alpha.json", "settings.json", "keybindprofilesplus"), Set.of(), Set.of(), Set.of()), null));
        return mods;
    }

    private void writeSandbox(Path game) throws IOException {
        LogicChecks.deleteRecursively(game);
        write(game, "config/alpha.json", "{\"zoom\": 2}");
        write(game, "config/alpha/sub.toml", "speed = 3");
        write(game, "config/alpha/alpha_play.example.org.json", "{\"placements\": []}");
        write(game, "config/alpha/alpha_World One.json", "{\"placements\": []}");
        write(game, "config/alpha/alpha_Gone World.json", "{\"placements\": []}");
        write(game, "config/alpha/alpha_Gone World_dim_minecraft_overworld.json", "{}");
        write(game, "config/alpha/backup/old.json", "{}");
        write(game, "config/alpha/material_list_2024-01-02_10.11.12.json", "{}");
        write(game, "config/alpha/fingerprint.json", "{}");
        write(game, "config/beta-client.json", "{\"hud\": true}");
        write(game, "config/common.json", "{}");
        write(game, "config/beta_printer.json", "{}");
        write(game, "config/gamma.json", "{}");
        write(game, "config/accounts.json", "{}");
        write(game, "config/delta.json", "{\"refreshToken\": \"M.C543_BAY.0.U.-Abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG\"}");
        write(game, "config/cachething/cache.json", "{}");
        write(game, "config/big.json", HUGE);
        write(game, "config/binary.json", "\0\0\0");
        write(game, "config/keybindprofilesplus/x.json", "{}");
        write(game, "config/readme.txt", "hello");
        write(game, "alpha-data/state.json", "{\"open\": true}");
        write(game, "alpha-data/zeta-config.nbt", nbt("mode", "fast"));
        write(game, "alpha-data/settings.json", "{}");
        write(game, "alpha-data/waypoints/play.example.org.nbt", nbt("x", "1"));
        write(game, "epsilon/epsilon.json", "{}");
        write(game, "launcher-stuff/profile.json", "{}");
        Files.createDirectories(game.resolve("saves/World One"));
        // servers.dat with one server: {servers: [{ip: "play.example.org:25570"}]}
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(9);
        out.writeUTF("servers");
        out.writeByte(10);
        out.writeInt(1);
        out.writeByte(8);
        out.writeUTF("ip");
        out.writeUTF("play.example.org:25570");
        out.writeByte(0);
        out.writeByte(0);
        Files.write(game.resolve("servers.dat"), bytes.toByteArray());
    }

    private static void write(Path game, String path, String text) throws IOException {
        write(game, path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path game, String path, byte[] data) throws IOException {
        Path file = game.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
    }

    private ConfigScan scanner(Path game, boolean withBeta) {
        return new ConfigScan(game, game.resolve("config"), sandboxMods(withBeta), WorldNames.of(game), "keybindprofilesplus");
    }

    void classification() {
        Path game = sandbox.resolve("source");
        try {
            writeSandbox(game);
        } catch (IOException e) {
            t.check("sorting: test folder written (" + e + ")", false);
            return;
        }
        ConfigScan.Result result = scanner(game, true).scan();
        Map<String, ConfigScan.Found> found = result.files().stream().collect(Collectors.toMap(ConfigScan.Found::path, f -> f));
        expect(found, "config/alpha.json", ConfigScan.Kind.SETTINGS, "alpha", true);
        expect(found, "config/alpha/sub.toml", ConfigScan.Kind.SETTINGS, "alpha", true);
        expect(found, "config/alpha/alpha_play.example.org.json", ConfigScan.Kind.PER_WORLD, "alpha", true);
        expect(found, "config/alpha/alpha_World One.json", ConfigScan.Kind.PER_WORLD, "alpha", true);
        expect(found, "config/alpha/alpha_Gone World.json", ConfigScan.Kind.PER_WORLD, "alpha", true);
        expect(found, "config/beta-client.json", ConfigScan.Kind.SETTINGS, "beta", true);
        expect(found, "alpha-data/state.json", ConfigScan.Kind.SETTINGS, "alpha", true);
        expect(found, "alpha-data/zeta-config.nbt", ConfigScan.Kind.SETTINGS, "zeta", true);
        expect(found, "alpha-data/settings.json", ConfigScan.Kind.SETTINGS, "alpha", true);
        expect(found, "alpha-data/waypoints/play.example.org.nbt", ConfigScan.Kind.PER_WORLD, "alpha", true);
        expect(found, "epsilon/epsilon.json", ConfigScan.Kind.SETTINGS, "epsilon", true);
        ConfigScan.Found common = found.get("config/common.json");
        t.check("sorting: a name the code of many mods uses is nobody's in particular", common != null && common.kind() == ConfigScan.Kind.ORPHAN);
        ConfigScan.Found gamma = found.get("config/gamma.json");
        t.check("sorting: config/gamma.json belongs to the switched-off Gamma", gamma != null && gamma.kind() == ConfigScan.Kind.ORPHAN
                && gamma.detail().equals("Gamma"));
        ConfigScan.Found printer = found.get("config/beta_printer.json");
        t.check("sorting: beta_printer.json is not Beta's (another word follows the name)", printer != null && printer.kind() == ConfigScan.Kind.ORPHAN);
        expectSkipped(result, "config/accounts.json", ConfigScan.Reason.LOGIN_DATA);
        expectSkipped(result, "config/delta.json", ConfigScan.Reason.LOGIN_DATA);
        expectSkipped(result, "config/cachething", ConfigScan.Reason.CACHE);
        expectSkipped(result, "config/big.json", ConfigScan.Reason.TOO_LARGE);
        expectSkipped(result, "config/binary.json", ConfigScan.Reason.BROKEN);
        expectSkipped(result, "config/keybindprofilesplus", ConfigScan.Reason.OWN_FILES);
        expectSkipped(result, "config/readme.txt", ConfigScan.Reason.NOT_SETTINGS);
        expectSkipped(result, "config/alpha", ConfigScan.Reason.BACKUP);
        expectSkipped(result, "config/alpha", ConfigScan.Reason.DATED);
        expectSkipped(result, "config/alpha", ConfigScan.Reason.STATE);
        expectSkipped(result, "launcher-stuff", ConfigScan.Reason.UNKNOWN);
        t.check("sorting: this mod never owns other mods' files", result.files().stream().noneMatch(f -> f.owners().contains("keybindprofilesplus")));
        t.check("sorting: nothing secret is offered", result.files().stream().noneMatch(f -> f.path().contains("accounts") || f.path().contains("delta")));
    }

    private void expect(Map<String, ConfigScan.Found> found, String path, ConfigScan.Kind kind, String owner, boolean sure) {
        ConfigScan.Found f = found.get(path);
        t.check("sorting: " + path + " is " + kind + " of " + owner + (f == null ? " (not found)" : " (" + f.kind() + " " + f.owners() + " sure=" + f.sure() + ")"),
                f != null && f.kind() == kind && f.owners().contains(owner) && f.sure() == sure);
    }

    private void expectSkipped(ConfigScan.Result result, String pathStart, ConfigScan.Reason reason) {
        List<ConfigScan.Skipped> matching = result.skipped().stream().filter(s -> s.path().startsWith(pathStart) && s.reason() == reason).toList();
        t.check("sorting: " + pathStart + " is not exported: " + reason, !matching.isEmpty());
    }

    // ------------------------------------------------------------------ export -> import -> undo

    void roundTrip() {
        Path source = sandbox.resolve("source");
        Path target = sandbox.resolve("target");
        Path exports = sandbox.resolve("exports");
        try {
            LogicChecks.deleteRecursively(target);
            LogicChecks.deleteRecursively(exports);
            write(target, "config/alpha.json", "{\"zoom\": 1}");
            write(target, "config/beta-client.json", "{\"hud\": true}");
            Files.createDirectories(target.resolve("saves"));
        } catch (IOException e) {
            t.check("round trip: folders prepared (" + e + ")", false);
            return;
        }
        ConfigScan.Result result = scanner(source, true).scan();
        List<ConfigScan.Found> chosen = new ArrayList<>(result.files().stream()
                .filter(f -> List.of("config/alpha.json", "config/alpha/sub.toml", "config/beta-client.json").contains(f.path())).toList());
        // Even if login data was ticked somehow, it must not end up in the file.
        chosen.add(new ConfigScan.Found("config/delta.json", 10, List.of("alpha"), ConfigScan.Kind.SETTINGS, "", true, true));
        Path archive;
        try {
            archive = ConfigArchive.export(exports, result, chosen, source, source.resolve("config"), "1.21.11", "test", LocalDateTime.now());
        } catch (IOException e) {
            t.check("round trip: export written (" + e + ")", false);
            return;
        }
        sandboxArchive = archive;
        ConfigArchive.Archive read = ConfigArchive.read(archive);
        t.check("export: the chosen files are in the file (" + read.files().keySet() + ")", read.problem() == null
                && read.files().keySet().equals(Set.of("config/alpha.json", "config/alpha/sub.toml", "config/beta-client.json")));
        t.check("export: login data never gets into an export", !read.files().containsKey("config/delta.json"));
        t.check("export: the manifest names the owners and their versions", read.entries().get("config/alpha.json").owners().contains("alpha")
                && read.mods().containsKey("alpha") && "1.0".equals(read.mods().get("alpha").version()));

        // Into a game without Beta.
        ConfigScan targetScan = scanner(target, false);
        Map<String, ModInfo> targetMods = sandboxMods(false).stream().collect(Collectors.toMap(ModInfo::id, m -> m));
        ConfigImport.Plan plan = ConfigImport.plan(read, target, target.resolve("config"), targetScan, targetMods, "1.21.11");
        t.check("import: a file that is different here is replaced", status(plan, "config/alpha.json") == ConfigImport.Status.CHANGE);
        t.check("import: a missing file is new", status(plan, "config/alpha/sub.toml") == ConfigImport.Status.NEW);
        t.check("import: a file of a mod that is not installed here is only offered", status(plan, "config/beta-client.json") == ConfigImport.Status.MOD_MISSING
                && !plan.item("config/beta-client.json").suggested());

        Path ownDir = ConfigImportApplier.ownDir(target, target.resolve("config"));
        try {
            ConfigImport.stage(plan, plan.items().stream().filter(ConfigImport.Item::suggested).toList(), ownDir);
        } catch (IOException e) {
            t.check("import: staged (" + e + ")", false);
            return;
        }
        ConfigImportApplier.Outcome outcome = ConfigImportApplier.applyPending(target, target.resolve("config"));
        t.check("import: the staged files are written on the next start (" + outcome + ")", outcome != null && outcome.written() == 2
                && outcome.errors().isEmpty() && read(target, "config/alpha.json").equals("{\"zoom\": 2}") && read(target, "config/alpha/sub.toml").equals("speed = 3"));
        t.check("import: the file not chosen is left alone", read(target, "config/beta-client.json").equals("{\"hud\": true}"));

        // Undo with the backup.
        Path backup = outcome == null || outcome.backup() == null ? null : ownDir.resolve(ConfigImportApplier.EXPORTS_DIR).resolve(outcome.backup());
        t.check("import: a backup was made", backup != null && Files.isRegularFile(backup));
        if (backup != null) {
            ConfigArchive.Archive undo = ConfigArchive.read(backup);
            ConfigImport.Plan undoPlan = ConfigImport.plan(undo, target, target.resolve("config"), targetScan, targetMods, "1.21.11");
            t.check("undo: the replaced file comes back, the created file goes away",
                    status(undoPlan, "config/alpha.json") == ConfigImport.Status.CHANGE && status(undoPlan, "config/alpha/sub.toml") == ConfigImport.Status.DELETE);
            try {
                ConfigImport.stage(undoPlan, undoPlan.items().stream().filter(ConfigImport.Item::suggested).toList(), ownDir);
            } catch (IOException e) {
                t.check("undo: staged (" + e + ")", false);
            }
            ConfigImportApplier.Outcome undone = ConfigImportApplier.applyPending(target, target.resolve("config"));
            t.check("undo: everything is as before the import", undone != null && read(target, "config/alpha.json").equals("{\"zoom\": 1}")
                    && !Files.exists(target.resolve("config/alpha/sub.toml")));
        }

        hostileArchive(target, targetScan, targetMods);
    }

    /** An archive made to do harm: every entry must be refused. */
    private void hostileArchive(Path target, ConfigScan targetScan, Map<String, ModInfo> targetMods) {
        Path file = sandbox.resolve("exports/hostile.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String[] entry : new String[][]{{"files/../escape.json", "{}"}, {"files/config/../../escape2.json", "{}"}, {"files/mods/evil.json", "{}"},
                    {"files/config/keybindprofilesplus/settings.json", "{}"}, {"files/config/accounts.json", "{}"}, {"files/config/zeros.json", "\0\0"},
                    {"files/options.txt", "x"}, {"files/config/evil.jar", "x"}}) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            t.check("hostile: file written (" + e + ")", false);
            return;
        }
        ConfigImport.Plan plan = ConfigImport.plan(ConfigArchive.read(file), target, target.resolve("config"), targetScan, targetMods, "1.21.11");
        t.check("hostile: every entry of a harmful file is refused (" + plan.items().stream().map(i -> i.path() + "=" + i.status() + ":" + i.detail()).toList() + ")",
                plan.items().size() == 8 && plan.items().stream().allMatch(i -> i.status() == ConfigImport.Status.REJECTED));
        // And even if such a file got staged directly, the early importer refuses it.
        Path ownDir = ConfigImportApplier.ownDir(target, target.resolve("config"));
        try {
            Files.createDirectories(ownDir);
            Files.copy(file, ownDir.resolve(ConfigImportApplier.PENDING_FILE), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            t.check("hostile: staged (" + e + ")", false);
            return;
        }
        ConfigImportApplier.Outcome outcome = ConfigImportApplier.applyPending(target, target.resolve("config"));
        t.check("hostile: the early importer writes none of it (" + outcome + ")", outcome != null && outcome.written() == 0
                && !Files.exists(sandbox.resolve("escape.json")) && !Files.exists(target.resolve("escape.json")) && !Files.exists(target.resolve("mods/evil.json")));
    }

    private static ConfigImport.Status status(ConfigImport.Plan plan, String path) {
        ConfigImport.Item item = plan.item(path);
        return item == null ? null : item.status();
    }

    private static String read(Path game, String path) {
        try {
            return Files.readString(game.resolve(path));
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ the real development game folder

    void startRealScan() {
        realScan = ModConfigs.scan();
    }

    boolean realScanDone() {
        return realScan != null && realScan.isDone();
    }

    void realScan() {
        ModConfigs.Context context = realScan.getNow(null);
        if (context == null) {
            t.check("real scan: finished", false);
            return;
        }
        ConfigScan.Result result = context.result();
        SelfTestRunner.log("real scan: " + context.mods().size() + " mods, " + result.files().size() + " files offered, " + result.skipped().size() + " not exported");
        result.files().forEach(f -> SelfTestRunner.log("real scan: " + f.kind() + " " + f.path() + " " + f.owners() + (f.sure() ? "" : " (probably)")));
        result.skipped().forEach(s -> SelfTestRunner.log("real scan: skipped " + s.reason() + " " + s.path() + " " + s.detail()));
        Map<String, ConfigScan.Found> found = result.files().stream().collect(Collectors.toMap(ConfigScan.Found::path, f -> f));
        if (Files.exists(ModConfigs.configDir().resolve("modmenu.json"))) {
            expect(found, "config/modmenu.json", ConfigScan.Kind.SETTINGS, "modmenu", true);
        }
        if (Files.exists(ModConfigs.configDir().resolve("fabric/indigo-renderer.properties"))) {
            expect(found, "config/fabric/indigo-renderer.properties", ConfigScan.Kind.SETTINGS, "fabric-renderer-indigo", true);
        }
        if (FabricLoader.getInstance().isModLoaded("litematica") && Files.exists(ModConfigs.configDir().resolve("litematica.json"))) {
            expect(found, "config/litematica.json", ConfigScan.Kind.SETTINGS, "litematica", true);
        }
        if (FabricLoader.getInstance().isModLoaded("meteor-client") && Files.exists(ModConfigs.gameDir().resolve("meteor-client/accounts.nbt"))) {
            t.check("real scan: Meteor's accounts are never exported", result.skipped().stream()
                    .anyMatch(s -> s.reason() == ConfigScan.Reason.LOGIN_DATA && s.path().startsWith("meteor-client")));
        }
        t.check("real scan: nothing of this mod's own folder is offered", result.files().stream()
                .noneMatch(f -> f.path().startsWith("config/keybindprofilesplus/") || f.path().startsWith("config/keybindprofiles/")));
        t.check("real scan: this mod owns no other files", result.files().stream().noneMatch(f -> f.owners().contains("keybindprofilesplus")));
    }

    // ------------------------------------------------------------------ screens

    void screens(boolean english, String tag) {
        t.step("configs: settings -> mod configs", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> {
            t.click(translated("keybindprofilesplus.configs.open"));
            t.check("configs: the mod configs screen opens from the settings", t.isScreen(ModConfigsScreen.class));
        });
        if (english) {
            t.step("configs: put the test export into the exports folder", () -> {
                try {
                    Files.createDirectories(ModConfigs.exportsDir());
                    Path copy = ModConfigs.exportsDir().resolve(SANDBOX_ARCHIVE);
                    Files.copy(sandboxArchive, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    createdArchives.add(copy);
                    t.screen(ModConfigsScreen.class).refresh();
                } catch (IOException | RuntimeException e) {
                    t.check("configs: test export copied (" + e + ")", false);
                }
            });
            t.step("configs: settle", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> {
            });
        }
        t.shot(tag + "_configs");
        t.step("configs: choose and export", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> t.click(translated("keybindprofilesplus.configs.export.open")));
        t.stepUntil("configs: the mods are read", () -> {
        }, () -> t.isScreen(ConfigExportScreen.class) && t.screen(ConfigExportScreen.class).isReady(), 20 * 120);
        t.step("configs: open the Mod Menu group", 3, () -> {
            ConfigExportScreen screen = t.screen(ConfigExportScreen.class);
            screen.setExpanded(ConfigExportScreen.modId("modmenu"), true);
            screen.setExpanded("never", true);
            t.check("configs: the export tree lists Mod Menu's settings ticked", !english || screen.isChecked(ConfigExportScreen.fileId("config/modmenu.json")));
        });
        t.shot(tag + "_configs_export");
        if (!english) {
            t.step("configs: back", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.cancel")));
            t.step("configs: done", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.done")));
            return;
        }
        t.step("configs: export only Mod Menu's settings", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> {
            ConfigExportScreen screen = t.screen(ConfigExportScreen.class);
            screen.selectOnly(List.of("config/modmenu.json"));
            t.check("configs: one file ticked", screen.checkedPaths().equals(List.of("config/modmenu.json")));
            Path file = screen.export();
            if (file != null) {
                createdArchives.add(file);
            }
            t.check("configs: export file written and back on the mod configs screen", file != null && Files.isRegularFile(file) && t.isScreen(ModConfigsScreen.class));
        });
        t.shot("en_configs_after_export");
        t.step("configs: open the import preview of the test export", SelfTestRunner.SCREEN_SETTLE_TICKS,
                () -> t.screen(ModConfigsScreen.class).openImport(ModConfigs.exportsDir().resolve(SANDBOX_ARCHIVE)));
        t.stepUntil("configs: the import is worked out", () -> {
        }, () -> t.isScreen(ConfigImportScreen.class) && t.screen(ConfigImportScreen.class).isReady(), 20 * 120);
        t.step("configs: the made-up mods are not installed here", 3, () -> {
            ConfigImportScreen screen = t.screen(ConfigImportScreen.class);
            ConfigImport.Plan plan = screen.plan();
            t.check("configs: every file of the test export is for a missing mod and not ticked",
                    plan.items().stream().allMatch(i -> i.status() == ConfigImport.Status.MOD_MISSING) && screen.checkedPaths().isEmpty());
            screen.setExpanded("missing", true);
            screen.setExpanded("missing/alpha", true);
        });
        t.shot("en_configs_import");
        t.step("configs: tick one and stage it", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> {
            ConfigImportScreen screen = t.screen(ConfigImportScreen.class);
            screen.setChecked("file:config/alpha/sub.toml", true);
            t.check("configs: staged for the next start", screen.confirm() && ModConfigs.pendingSource() != null && ModConfigs.pendingCount() == 1);
        });
        t.shot("en_configs_pending");
        t.step("configs: cancel the staged import", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> {
            t.screen(ModConfigsScreen.class).cancelPending();
            t.check("configs: nothing staged any more", ModConfigs.pendingSource() == null);
        });
        t.step("configs: done", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> t.click(translated("gui.done")));
    }

    void smallScreens() {
        t.open("mod configs (small)", () -> new ModConfigsScreen(t.homeScreen));
        t.shot("small_configs");
        t.step("small: export tree", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> t.screen(ModConfigsScreen.class).openExport());
        t.stepUntil("small: the mods are read", () -> {
        }, () -> t.isScreen(ConfigExportScreen.class) && t.screen(ConfigExportScreen.class).isReady(), 20 * 120);
        t.step("small: export tree settles", 3, () -> {
        });
        t.shot("small_configs_export");
        t.step("small: leave", SelfTestRunner.SCREEN_SETTLE_TICKS, () -> SelfTestRunner.client().setScreen(t.homeScreen));
    }

    // ------------------------------------------------------------------ cleanup

    void cleanup() {
        try {
            for (Path archive : createdArchives) {
                Files.deleteIfExists(archive);
            }
            ModConfigs.cancelPending();
            LogicChecks.deleteRecursively(sandbox);
        } catch (IOException e) {
            SelfTestRunner.log("configs: cleanup incomplete: " + e);
        }
        SelfTestRunner.log("configs: test files removed " + Arrays.toString(createdArchives.stream().map(p -> p.getFileName().toString()).toArray()));
    }
}
