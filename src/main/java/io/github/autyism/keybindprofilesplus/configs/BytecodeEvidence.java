package io.github.autyism.keybindprofilesplus.configs;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds out which file and folder names a mod really uses, by reading its compiled code.
 *
 * <p>A name only counts when the code hands it to something that opens or locates a file: a text
 * that goes into {@code new File(...)}, {@code Path.resolve(...)}, {@code Path.of(...)} and their
 * relatives is a <em>strong</em> sign (the mod reads or writes a file of that name); a text that goes
 * into a method whose name speaks of files ({@code loadConfigFile("x")}, {@code getConfigPath("x")})
 * or that names a config file in an annotation ({@code @Config(name = "x")}) is a <em>medium</em> sign;
 * a text that goes into another method of a config class ({@code MidnightConfig.init("x", ...)},
 * {@code ConfigManager.load("x")}) is only a <em>weak</em> sign - such classes also take the names of
 * single options, which are no files at all, and their constructors are not counted. The same text
 * used for anything else - most of all {@code isModLoaded("jei")} in compatibility code - says
 * nothing, so a mod is never taken for the owner of another mod's folder just because it checks
 * whether that mod is installed.</p>
 *
 * <p>Texts that already look like a file ({@code "sodium-options.json"}, {@code "config/xaero"}) count
 * wherever they appear. Names built at run time ({@code "litematica_" + world + ".json"}) are kept as
 * patterns, which also tells which files are kept per world or per server. Constants read from fields
 * are followed through the whole mod.</p>
 *
 * <p>It also notes the module and HUD element classes of other mods that this mod's classes extend:
 * an add-on whose modules extend Meteor's {@code Module} has its settings saved by Meteor.</p>
 */
public final class BytecodeEvidence {
    /** How many instructions a text may travel before it reaches a file call. */
    private static final int WINDOW = 12;
    private static final String STRING = "Ljava/lang/String;";
    private static final char PLACEHOLDER = '\u0001';
    private static final Pattern FORMAT_PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd]|\\{}");
    private static final Pattern FILE_LIKE = Pattern.compile(
            "(?i)(^|[/\\\\])[^/\\\\\\s]{1,100}\\.(json5?|jsonc|hjson|toml|properties|cfg|conf|config|ini|txt|ya?ml|xml|s?nbt|options|settings|prefs)$");
    private static final Pattern LIBRARY_METHOD = Pattern.compile("(?i).*(file|path|dir|folder|json|toml|propert|yaml|nbt).*");
    private static final Pattern LIBRARY_OWNER = Pattern.compile("(?i).*(config|setting|option|storage|persist|preference).*");
    private static final Pattern NOT_A_FILE_METHOD = Pattern.compile(
            "(?i).*(loaded|modcontainer|translat|log|warn|error|info|debug|trace|print|equals|contains|matches|startswith|endswith|"
                    + "format|parse|identifier|lang|text|message|tooltip|comment|description|category|keybind|command|permission|"
                    + "property$|getenv|setstring|getstring|putstring|addproperty|getasstring|tag|cache).*");
    private static final Set<String> EXCLUDED_LIBRARY_OWNERS = Set.of(
            "java/", "javax/", "jdk/", "sun/", "kotlin/", "kotlinx/", "org/slf4j/", "org/apache/logging/", "org/apache/commons/lang",
            "com/google/gson/", "com/google/common/", "com/mojang/", "net/minecraft/", "it/unimi/", "org/objectweb/",
            "org/spongepowered/", "com/llamalad7/", "net/fabricmc/", "org/lwjgl/", "io/netty/", "org/joml/", "com/electronwill/");

    /** Simple names of classes whose subclasses another mod saves the settings of (Meteor's and QoL Bundle's modules). */
    private static final Pattern HOSTED_PARENT = Pattern.compile("(Module|HudElement)$");

    /**
     * Everything one mod's code says about file names; {@code parents}: module and HUD element classes
     * of other mods that its classes extend (internal names, "a/b/Module").
     */
    public record Evidence(Set<String> strong, Set<String> medium, Set<String> weak, Set<String> patterns, Set<String> parents) {
        public static final Evidence EMPTY = new Evidence(Set.of(), Set.of(), Set.of(), Set.of());

        public Evidence(Set<String> strong, Set<String> medium, Set<String> weak, Set<String> patterns) {
            this(strong, medium, weak, patterns, Set.of());
        }

        public boolean isEmpty() {
            return strong.isEmpty() && medium.isEmpty() && weak.isEmpty() && patterns.isEmpty();
        }
    }

    private enum Strength { STRONG, MEDIUM, WEAK }

    private final Set<String> strong = new HashSet<>();
    private final Set<String> medium = new HashSet<>();
    private final Set<String> weak = new HashSet<>();
    private final Set<String> patterns = new HashSet<>();
    /** "owner.field" -> text assigned to it somewhere in the mod. */
    private final Map<String, String> fieldTexts = new HashMap<>();
    /** Fields read right before a file call, and how strong that call was. */
    private final Map<String, Strength> fieldUses = new HashMap<>();
    private final Set<String> definedClasses = new HashSet<>();
    private final Set<String> superClasses = new HashSet<>();
    private int classes;

    /** Reads one class file. Broken or unusual class files are skipped. */
    public void addClass(byte[] classFile) {
        try {
            new ClassReader(classFile).accept(new ClassScan(), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes++;
        } catch (RuntimeException e) {
            // A class ASM cannot read says nothing about files.
        }
    }

    public int classCount() {
        return classes;
    }

    /** The result, once every class of the mod was read. */
    public Evidence build() {
        fieldUses.forEach((field, strength) -> {
            String text = fieldTexts.get(field);
            if (text != null) {
                record(text, strength);
            }
        });
        weak.removeAll(medium);
        weak.removeAll(strong);
        medium.removeAll(strong);
        Set<String> parents = new HashSet<>(superClasses);
        parents.removeAll(definedClasses);
        return new Evidence(Set.copyOf(strong), Set.copyOf(medium), Set.copyOf(weak), Set.copyOf(patterns), Set.copyOf(parents));
    }

    // ------------------------------------------------------------------ recording

    private void record(String text, Strength strength) {
        String value = FORMAT_PLACEHOLDER.matcher(text).replaceAll(String.valueOf(PLACEHOLDER));
        for (String part : value.split("[/\\\\]")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (!usableName(name)) {
                continue;
            }
            if (name.indexOf(PLACEHOLDER) >= 0) {
                patterns.add(name);
            } else {
                (strength == Strength.STRONG ? strong : strength == Strength.MEDIUM ? medium : weak).add(name);
            }
        }
    }

    private static boolean usableName(String name) {
        if (name.length() < 2 || name.length() > 120 || name.chars().filter(c -> c == ' ').count() > 3) {
            return false;
        }
        boolean letterOrDigit = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 && c != PLACEHOLDER || "<>:\"|?*\n\r\t".indexOf(c) >= 0) {
                return false;
            }
            letterOrDigit |= Character.isLetterOrDigit(c);
        }
        return letterOrDigit && !name.equals(String.valueOf(PLACEHOLDER));
    }

    // ------------------------------------------------------------------ class reading

    private final class ClassScan extends ClassVisitor {
        private String className;

        ClassScan() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            className = name;
            definedClasses.add(name);
            if (superName != null && HOSTED_PARENT.matcher(superName.substring(Math.max(superName.lastIndexOf('/'), superName.lastIndexOf('$')) + 1)).find()) {
                superClasses.add(superName);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (value instanceof String text && STRING.equals(descriptor)) {
                fieldTexts.putIfAbsent(className + "." + name, text);
            }
            return null;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return configAnnotation(descriptor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodScan();
        }
    }

    /** {@code @Config(name = "x")} of AutoConfig, owo-lib and friends names the config file. */
    private AnnotationVisitor configAnnotation(String descriptor) {
        String simpleName = descriptor.substring(descriptor.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (!simpleName.contains("config")) {
            return null;
        }
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                if (value instanceof String text && name != null && name.matches("name|value|file|fileName|path|id|filename")) {
                    record(text, FILE_LIKE.matcher(text).find() ? Strength.STRONG : Strength.MEDIUM);
                }
            }
        };
    }

    /** One pending text on its way through a method. */
    private record Pending(String text, String field, int age) {
        Pending older() {
            return new Pending(text, field, age + 1);
        }
    }

    private final class MethodScan extends MethodVisitor {
        private final List<Pending> pending = new ArrayList<>();
        private String lastText;

        MethodScan() {
            super(Opcodes.ASM9);
        }

        private void step() {
            pending.replaceAll(Pending::older);
            pending.removeIf(p -> p.age() > WINDOW);
        }

        private void other() {
            lastText = null;
            step();
        }

        private void flush(Strength strength) {
            for (Pending p : pending) {
                if (p.text() != null) {
                    record(p.text(), strength);
                } else {
                    fieldUses.merge(p.field(), strength, (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
                }
            }
            pending.clear();
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return configAnnotation(descriptor);
        }

        @Override
        public void visitLdcInsn(Object value) {
            step();
            if (value instanceof String text) {
                if (FILE_LIKE.matcher(text).find() && !isResourcePath(text)) {
                    record(text, Strength.STRONG);
                }
                pending.add(new Pending(text, null, 0));
                lastText = text;
            } else {
                lastText = null;
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            boolean string = STRING.equals(descriptor);
            if (string && (opcode == Opcodes.PUTSTATIC || opcode == Opcodes.PUTFIELD) && lastText != null) {
                fieldTexts.putIfAbsent(owner + "." + name, lastText);
            }
            step();
            lastText = null;
            if (string && (opcode == Opcodes.GETSTATIC || opcode == Opcodes.GETFIELD)) {
                pending.add(new Pending(null, owner + "." + name, 0));
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            lastText = null;
            switch (kind(owner, name, descriptor)) {
                case FILE -> flush(Strength.STRONG);
                case LIBRARY -> flush(Strength.MEDIUM);
                case CONFIG_CLASS -> flush(Strength.WEAK);
                case PASS -> {
                    // The text goes on as part of a longer text.
                }
                case OTHER -> {
                    if (descriptor.substring(0, descriptor.indexOf(')')).contains(STRING)) {
                        // The texts were arguments of something unrelated to files.
                        pending.clear();
                    }
                }
            }
            step();
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
            lastText = null;
            if (bootstrap.getOwner().equals("java/lang/invoke/StringConcatFactory") && arguments.length > 0 && arguments[0] instanceof String recipe) {
                // "a" + x + ".json": the recipe keeps the fixed parts and marks the others with \u0001.
                StringBuilder text = new StringBuilder();
                int constant = 1;
                for (int i = 0; i < recipe.length(); i++) {
                    char c = recipe.charAt(i);
                    if (c == '\u0002' && constant < arguments.length) {
                        text.append(arguments[constant++]);
                    } else {
                        text.append(c);
                    }
                }
                pending.add(new Pending(text.toString(), null, 0));
            }
            step();
        }

        @Override
        public void visitInsn(int opcode) {
            other();
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            other();
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            other();
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            other();
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            other();
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            other();
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            other();
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            other();
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            other();
        }
    }

    enum CallKind { FILE, LIBRARY, CONFIG_CLASS, PASS, OTHER }

    static CallKind kind(String owner, String name, String descriptor) {
        String params = descriptor.substring(1, descriptor.indexOf(')'));
        boolean takesText = params.contains(STRING);
        switch (owner) {
            case "java/io/File", "java/io/FileReader", "java/io/FileWriter", "java/io/FileInputStream", "java/io/FileOutputStream",
                 "java/io/RandomAccessFile", "java/io/PrintWriter", "java/util/Scanner", "java/util/logging/FileHandler" -> {
                if (name.equals("<init>") && takesText) {
                    return CallKind.FILE;
                }
            }
            case "java/nio/file/Path" -> {
                if ((name.equals("resolve") || name.equals("resolveSibling") || name.equals("of")) && takesText) {
                    return CallKind.FILE;
                }
            }
            case "java/nio/file/Paths", "java/nio/file/FileSystem", "org/apache/commons/io/FileUtils" -> {
                if ((name.equals("get") || name.equals("getPath") || name.equals("getFile")) && takesText) {
                    return CallKind.FILE;
                }
            }
            case "java/lang/StringBuilder", "java/lang/StringBuffer" -> {
                return CallKind.PASS;
            }
            case "java/lang/String" -> {
                if (name.equals("concat") || name.equals("valueOf") || name.equals("format") || name.equals("formatted")
                        || name.startsWith("to") || name.equals("trim") || name.equals("strip") || name.equals("replace")
                        || name.equals("intern") || name.equals("join")) {
                    return CallKind.PASS;
                }
            }
            case "kotlin/jvm/internal/Intrinsics" -> {
                if (name.equals("stringPlus")) {
                    return CallKind.PASS;
                }
            }
            default -> {
            }
        }
        if (owner.startsWith("kotlin/io/FilesKt") && (name.equals("resolve") || name.equals("resolveSibling")) && takesText) {
            return CallKind.FILE;
        }
        if (takesText && !isExcludedOwner(owner) && !NOT_A_FILE_METHOD.matcher(name).matches()) {
            if (LIBRARY_METHOD.matcher(name).matches()) {
                return CallKind.LIBRARY;
            }
            String simpleOwner = owner.substring(owner.lastIndexOf('/') + 1);
            // The constructors of config classes take the names of single options.
            if (!name.equals("<init>") && LIBRARY_OWNER.matcher(simpleOwner).matches()) {
                return CallKind.CONFIG_CLASS;
            }
        }
        return CallKind.OTHER;
    }

    /**
     * Files inside the mod's own jar (assets, data packs, mixin configurations, class path resources)
     * look like file names too, but are never on disk in the game folder.
     */
    static boolean isResourcePath(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('\\', '/');
        return lower.startsWith("/") || lower.startsWith("assets/") || lower.startsWith("data/") || lower.startsWith("meta-inf/")
                || lower.contains("mixins") || lower.contains("refmap") || lower.endsWith("fabric.mod.json") || lower.endsWith("quilt.mod.json")
                || lower.endsWith("pack.mcmeta") || lower.endsWith(".accesswidener") || lower.contains("/lang/") || lower.startsWith("lang/");
    }

    private static boolean isExcludedOwner(String owner) {
        for (String excluded : EXCLUDED_LIBRARY_OWNERS) {
            if (owner.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }
}
