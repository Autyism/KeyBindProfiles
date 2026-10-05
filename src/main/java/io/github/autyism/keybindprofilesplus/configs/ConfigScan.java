package io.github.autyism.keybindprofilesplus.configs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Goes through a game folder and sorts every file into "settings of mod X", "data mod X keeps per
 * world or server", "settings of a mod that is not installed" or "not settings at all" (login data,
 * caches, maps, backups, files of other kinds).
 *
 * <p><b>Which mod owns a name</b> is decided by points. What the mods' code says about file names
 * ({@link BytecodeEvidence}): 100 for the exact name handed to a file call, 90 for a name made by a
 * pattern of the mod, 80 when the code uses the name without its extension, 75 for a fixed start of
 * the name, 60 for a name handed to a file method of a library, 40 for a name handed to some other
 * method of a config class; a name the code of three or more mods uses is an everyday word and
 * counts 40 at most. Plus how much the name resembles the mod's id
 * or name: 70 for the same name, 55 for the id followed by a separator, 40 to 45 for one being the
 * start of the other. The mod with the most points owns a top-level entry of the config folder when
 * it reaches 55. In the game folder itself, where launchers and other programs keep folders too, the
 * mod must also name it in its code or be called exactly like it.</p>
 *
 * <p><b>Inside a mod's folder</b> everything belongs to that mod, with two exceptions: an add-on that
 * keeps its own file there (Meteor add-ons do) gets that file when its code names it exactly, the
 * name is not an everyday word ("settings", "config", "waypoints"...), no other mod's code uses the
 * same name and the folder's owner does not name it itself; and when several mods share a folder
 * (Xaero's minimap and world map), a subfolder goes to the one whose code names it.</p>
 *
 * <p>This mod itself is never an owner of anything but its own folder, although its code names the
 * files of other mods (it edits Meteor's and malilib's hotkeys). Its profiles and settings are offered
 * like any mod's settings; its working files (exports, a waiting import, the scan cache) never.</p>
 *
 * <p>Add-ons that have no files for their settings are named with the mod that saves them: a Meteor
 * add-on's modules extend Meteor's {@code Module} and are saved in Meteor's {@code modules.nbt}.</p>
 */
public final class ConfigScan {
    public enum Kind {
        /** Settings of an installed mod. */
        SETTINGS,
        /** Data an installed mod keeps for one world or one server. */
        PER_WORLD,
        /** Settings no installed mod uses (left behind by a removed or switched-off mod). */
        ORPHAN
    }

    /**
     * Why a file is never exported: login data, not a settings file type, too big for settings, broken,
     * a cache or log, a backup, a dated record or export, state of this computer (fingerprints,
     * update checks, analytics), this mod's own files, or a folder no installed mod uses.
     */
    public enum Reason { LOGIN_DATA, NOT_SETTINGS, TOO_LARGE, BROKEN, CACHE, BACKUP, DATED, STATE, OWN_FILES, UNKNOWN }

    /**
     * A file that can be exported. {@code owners} are mod ids; {@code byCode}: the owner's code names
     * it; {@code sure}: the owner is certain (otherwise the file is only offered, not ticked).
     */
    public record Found(String path, long size, List<String> owners, Kind kind, String detail, boolean byCode, boolean sure) {
    }

    /** A file (or a folder of files) that is never exported, and why. */
    public record Skipped(String path, int files, long size, Reason reason, String detail, List<String> owners) {
    }

    /** {@code addOns}: mod id -> the installed add-ons whose settings that mod saves in its files. */
    public record Result(List<Found> files, List<Skipped> skipped, Map<String, ModInfo> mods, Map<String, List<String>> addOns) {
        public List<Found> files(Kind kind) {
            return files.stream().filter(f -> f.kind() == kind).toList();
        }

        public ModInfo mod(String id) {
            return mods.get(id);
        }
    }

    private enum Place { CONFIG_TOP, GAME_TOP, INSIDE }

    private static final int OWNER_THRESHOLD = 55;
    private static final int CODE_THRESHOLD = 60;
    private static final int EXACT = 90;
    private static final int SAME_NAME = 70;
    private static final int MAX_DEPTH = 12;
    private static final int MAX_FILES = 300_000;
    private static final int ATTRIBUTION_DEPTH = 3;
    /** Dates and times in a name: records and exports made at that moment, not settings. */
    private static final Pattern DATED = Pattern.compile("(19|20)\\d{2}[-_.]\\d{2}[-_.]\\d{2}|(19|20)\\d{6}[-_]\\d{4,6}|(^|[^0-9])\\d{13}($|[^0-9])");
    /** What this computer did or is, not what the player chose. */
    private static final Pattern STATE = Pattern.compile(
            "fingerprint|analytics|telemetry|statistic|(^|[_-])stats($|[_-])|updater|update[-_]?check|last[-_]?(seen|run|login|update|played|version)"
                    + "|first[-_]?(run|launch|start)|install[-_]?id|machine[-_]?id|hwid");
    private static final Pattern DOCUMENT = Pattern.compile("readme|read_me|license|licence|changelog|credits|notice|about");
    private static final Set<String> CACHE_NAMES = Set.of(
            "cache", "caches", "temp", "tmp", "logs", "log", "crash", "crashes", "crash-reports", "dumps", "recordings",
            "replays", "screenshots", "thumbnails", "history", "downloads", "trash", "recycle");
    /** Everyday names many mods use for their own files; they never move a file to another mod. */
    private static final Set<String> GENERIC_NAMES = Set.copyOf(List.of(
            "config", "configs", "configuration", "settings", "setting", "options", "option", "client", "common", "server", "main",
            "default", "defaults", "data", "readme", "global", "general", "profile", "profiles", "keybinds", "keybindings", "keys",
            "hud", "huds", "module", "modules", "gui", "theme", "themes", "friends", "macros", "waypoints", "stashes", "lib", "libs",
            "library", "storage", "state", "values", "preferences", "prefs", "user", "users", "players", "player", "accounts", "history",
            "info", "metadata", "version", "versions", "list", "lists", "groups", "presets", "layouts", "colors", "colours", "sounds",
            "textures", "fonts", "lang", "translations", "rules", "filters", "blacklist", "whitelist", "schematics", "export", "exports",
            "import", "imports", "temp", "backup", "cache", "log", "logs", "world", "worlds", "servers", "maps", "map", "minimap",
            "worldmap", "icons", "images", "assets", "resources", "scripts", "plugins", "addons", "extensions", "features", "tweaks",
            "misc", "other", "test", "tests", "debug", "dev", "notes", "changelog", "license", "credits", "mods", "mod", "hotkeys",
            "binds", "bindings", "cfg", "options", "profiles", "highlights", "markers", "toggles", "ui", "screens", "render", "client-config"));

    /** Words that follow a mod's name in the names of its own files. */
    private static final Set<String> SUFFIX_WORDS = Set.of(
            "client", "common", "server", "config", "configs", "configuration", "options", "settings", "mixins", "mixin",
            "properties", "prefs", "preferences", "core", "main", "general", "global", "user", "local", "hud", "gui", "keybinds",
            "keys", "hotkeys", "profiles", "profile", "default", "defaults", "filters", "rules", "colors", "colours", "theme",
            "themes", "data", "v1", "v2", "v3", "new", "custom", "overrides", "categories", "blacklist", "whitelist", "list");

    private final Path gameDir;
    private final Path configDir;
    private final List<Candidate> candidates;
    private final Map<String, Candidate> candidatesById = new HashMap<>();
    private final Map<String, ModInfo> modsById = new LinkedHashMap<>();
    /** How many installed mods' code uses each name. */
    private final Map<String, Integer> nameUsers = new HashMap<>();
    private final WorldNames worlds;
    private final String ownModId;
    private final List<Found> found = new ArrayList<>();
    private final List<Skipped> skippedFiles = new ArrayList<>();
    private int visited;

    public ConfigScan(Path gameDir, Path configDir, List<ModInfo> mods, WorldNames worlds, String ownModId) {
        this.gameDir = gameDir.toAbsolutePath().normalize();
        this.configDir = configDir.toAbsolutePath().normalize();
        this.worlds = worlds;
        this.ownModId = ownModId;
        List<Candidate> list = new ArrayList<>();
        for (ModInfo mod : mods) {
            modsById.putIfAbsent(mod.id(), mod);
            if (mod.id().equals(ownModId) || candidatesById.containsKey(mod.id())) {
                continue;
            }
            Candidate candidate = new Candidate(mod, nameUsers);
            list.add(candidate);
            candidatesById.put(mod.id(), candidate);
            if (mod.installed()) {
                Set<String> names = new HashSet<>(mod.evidence().strong());
                names.addAll(mod.evidence().medium());
                names.addAll(mod.evidence().weak());
                names.forEach(name -> nameUsers.merge(name, 1, Integer::sum));
            }
        }
        this.candidates = List.copyOf(list);
    }

    public Result scan() {
        scanConfigFolder();
        scanGameFolder();
        learnWorlds();
        likeTheirNeighbours();
        found.sort(Comparator.comparing(Found::path, String.CASE_INSENSITIVE_ORDER));
        return new Result(List.copyOf(found), aggregate(skippedFiles), Map.copyOf(modsById), addOns());
    }

    /** Who owns a file at this path in this game, without looking at the disk (used for imports). */
    public record PathOwner(List<String> installed, List<String> disabled, boolean sure, boolean own) {
    }

    public PathOwner ownerOf(String path) {
        if (ConfigRules.isOwnWorkingFile(path) || path.equalsIgnoreCase(ConfigRules.OWN_DIR)) {
            return new PathOwner(List.of(), List.of(), true, true);
        }
        if (ConfigRules.isOwnFile(path)) {
            return new PathOwner(List.of(ownModId), List.of(), true, false);
        }
        String[] parts = path.split("/");
        boolean inConfig = parts[0].equals("config") && parts.length > 1;
        int top = inConfig ? 1 : 0;
        Attribution owner = attribute(parts[top], top == parts.length - 1, inConfig ? Place.CONFIG_TOP : Place.GAME_TOP);
        for (int i = top + 1, depth = 1; i < parts.length && depth <= ATTRIBUTION_DEPTH; i++, depth++) {
            owner = ownerInside(parts[i], i == parts.length - 1, owner);
        }
        return new PathOwner(owner.installed().stream().map(ModInfo::id).toList(), owner.disabled().stream().map(ModInfo::id).toList(),
                owner.sure(), false);
    }

    /** The world or server a path belongs to in this game, or null (never for this mod's profiles, whatever they are called). */
    public String perWorldReason(String path) {
        if (ConfigRules.isOwnFile(path)) {
            return null;
        }
        return worlds.perWorldReason(path.startsWith(ConfigRules.CONFIG_PREFIX) ? path.substring(ConfigRules.CONFIG_PREFIX.length()) : path);
    }

    // ------------------------------------------------------------------ per-world data, second look

    /**
     * Worlds that no longer exist in the saves folder still left data behind. Their names show in the
     * data itself: "litematica_X_dim_overworld.json" or a folder X with "DIM-1" / "dim%0" inside means
     * X is a world. Files of those worlds are per-world data too.
     */
    private void learnWorlds() {
        Set<String> learned = new HashSet<>();
        List<String> paths = new ArrayList<>();
        found.forEach(f -> paths.add(f.path()));
        skippedFiles.forEach(s -> paths.add(s.path()));
        for (int k = 0; k < paths.size(); k++) {
            String[] parts = paths.get(k).split("/");
            String last = ConfigRules.stem(parts[parts.length - 1]);
            int dim = last.toLowerCase(Locale.ROOT).indexOf("_dim_");
            if (dim > 0) {
                List<String> owners = k < found.size() ? found.get(k).owners() : List.of();
                learned.add(stripOwnerPrefix(last.substring(0, dim), owners));
            }
            for (int i = 1; i < parts.length; i++) {
                if (WorldNames.DIMENSION_FOLDER.matcher(parts[i]).matches()) {
                    learned.add(parts[i - 1]);
                }
            }
        }
        learned.removeIf(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.length() < 3 || GENERIC_NAMES.contains(lower) || GENERIC_NAMES.contains(Candidate.normalize(lower))
                    || modsById.containsKey(lower) || lower.startsWith("multiplayer_") || lower.equals("null");
        });
        if (learned.isEmpty()) {
            return;
        }
        WorldNames more = worlds.with(learned);
        for (int i = 0; i < found.size(); i++) {
            Found f = found.get(i);
            if (ConfigRules.isOwnFile(f.path())) {
                continue;
            }
            String path = f.path().startsWith(ConfigRules.CONFIG_PREFIX) ? f.path().substring(ConfigRules.CONFIG_PREFIX.length()) : f.path();
            if (f.kind() == Kind.SETTINGS) {
                String reason = more.perWorldReason(path);
                if (reason != null) {
                    found.set(i, new Found(f.path(), f.size(), f.owners(), Kind.PER_WORLD, reason, f.byCode(), f.sure()));
                }
            } else if (f.kind() == Kind.PER_WORLD && worlds.knownWorld(path) == null) {
                // Its world is known now: show it rather than the kind of name the file was recognised by ("dim%0").
                String known = more.knownWorld(path);
                if (known != null) {
                    found.set(i, new Found(f.path(), f.size(), f.owners(), Kind.PER_WORLD, known, f.byCode(), f.sure()));
                }
            }
        }
    }

    private static String stripOwnerPrefix(String name, List<String> owners) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (String owner : owners) {
            for (String variant : new String[]{owner, owner.replace('-', '_'), owner.replace('_', '-')}) {
                String lower = variant.toLowerCase(Locale.ROOT);
                if (lowerName.startsWith(lower + "_") || lowerName.startsWith(lower + "-")) {
                    return name.substring(lower.length() + 1);
                }
            }
        }
        return name;
    }

    /**
     * In a folder where most files are per-world data (Meteor's waypoints, one file per world), the
     * remaining files of the same kind are too, even when their world is not known by name.
     */
    private void likeTheirNeighbours() {
        Map<String, List<Integer>> byFolder = new LinkedHashMap<>();
        for (int i = 0; i < found.size(); i++) {
            String path = found.get(i).path();
            byFolder.computeIfAbsent(path.substring(0, Math.max(0, path.lastIndexOf('/'))), k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> folder : byFolder.values()) {
            List<Found> files = folder.stream().map(found::get).filter(f -> f.kind() != Kind.ORPHAN).toList();
            List<Found> perWorld = files.stream().filter(f -> f.kind() == Kind.PER_WORLD).toList();
            if (perWorld.size() < 2 || perWorld.size() * 10 < files.size() * 6) {
                continue;
            }
            Set<String> extensions = perWorld.stream().map(f -> ConfigRules.extension(f.path())).collect(Collectors.toSet());
            for (int index : folder) {
                Found f = found.get(index);
                String stem = Candidate.normalize(ConfigRules.stem(f.path().substring(f.path().lastIndexOf('/') + 1)));
                if (f.kind() == Kind.SETTINGS && extensions.contains(ConfigRules.extension(f.path())) && !GENERIC_NAMES.contains(stem)) {
                    found.set(index, new Found(f.path(), f.size(), f.owners(), Kind.PER_WORLD, "", f.byCode(), f.sure()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ add-ons

    /**
     * Add-ons whose settings another mod saves in its own files: the add-on depends on that mod and
     * extends its module or HUD element classes (a Meteor add-on's modules are saved in Meteor's
     * modules.nbt, a QoL Bundle add-on's module in qolbundle.json). Only mods that own files here.
     */
    private Map<String, List<String>> addOns() {
        Set<String> owning = new HashSet<>();
        found.stream().filter(f -> f.kind() != Kind.ORPHAN).forEach(f -> owning.addAll(f.owners()));
        Map<String, List<String>> out = new HashMap<>();
        for (ModInfo addOn : modsById.values()) {
            if (!addOn.installed() || addOn.evidence().parents().isEmpty()) {
                continue;
            }
            for (String hostId : addOn.depends()) {
                ModInfo host = modsById.get(hostId);
                if (host != null && host.installed() && !host.rootId().equals(addOn.rootId()) && owning.contains(host.id())
                        && extendsClassesOf(addOn, host)) {
                    out.computeIfAbsent(host.id(), k -> new ArrayList<>()).add(addOn.id());
                }
            }
        }
        out.replaceAll((host, list) -> list.stream().sorted().toList());
        return Map.copyOf(out);
    }

    private static boolean extendsClassesOf(ModInfo addOn, ModInfo host) {
        for (String parent : addOn.evidence().parents()) {
            for (String pkg : host.packages()) {
                if (parent.startsWith(pkg.replace('.', '/') + "/")) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ walking

    private void scanConfigFolder() {
        for (Path entry : list(configDir)) {
            String name = entry.getFileName().toString();
            String path = ConfigRules.CONFIG_PREFIX + name;
            boolean file = Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS);
            if (ConfigRules.isOwnWorkingFile(path)) {
                skipTree(entry, path, Reason.OWN_FILES, "", List.of(ownModId));
                continue;
            }
            if (ConfigRules.isOwnFile(path)) {
                scanOwnFolder(entry, path);
                continue;
            }
            Attribution owner = attribute(name, file, Place.CONFIG_TOP);
            if (file) {
                classify(entry, path, owner);
            } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                walk(entry, path, owner, 1);
            }
        }
    }

    /** This mod's folder: its profiles and settings are offered like any mod's, its working files never. */
    private void scanOwnFolder(Path dir, String path) {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            skipTree(dir, path, Reason.OWN_FILES, "", List.of(ownModId));
            return;
        }
        ModInfo own = modsById.computeIfAbsent(ownModId, id -> new ModInfo(id, "KeyBind Profiles+", "", Set.of(), true, null, null));
        Attribution owner = new Attribution(List.of(own), List.of(), 200, 100, SAME_NAME, false, null, true);
        for (Path child : list(dir)) {
            String childPath = path + "/" + child.getFileName();
            if (ConfigRules.isOwnWorkingFile(childPath) || !Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                skipTree(child, childPath, Reason.OWN_FILES, "", List.of(ownModId));
            } else {
                classify(child, childPath, owner);
            }
        }
    }

    private void scanGameFolder() {
        for (Path entry : list(gameDir)) {
            if (entry.toAbsolutePath().normalize().equals(configDir)) {
                continue;
            }
            String name = entry.getFileName().toString();
            boolean file = Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS);
            boolean dir = Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
            if (name.startsWith(".") || (dir && ConfigRules.isProtectedTopLevel(name)) || (file && ConfigRules.isVanillaRootFile(name))) {
                continue;
            }
            Attribution owner = attribute(name, file, Place.GAME_TOP);
            if (owner.installed().isEmpty() && owner.disabled().isEmpty()) {
                // A folder nobody owns as a whole ("data") may still hold a file only one mod's code names.
                boolean rescued = dir && rescueDistinctive(entry, name, 1);
                if (!rescued && (dir || ConfigRules.isConfigExtension(name))) {
                    skippedFiles.add(new Skipped(name + (dir ? "/" : ""), dir ? -1 : 1, dir ? -1 : size(entry), Reason.UNKNOWN, "", List.of()));
                }
                continue;
            }
            if (file) {
                classify(entry, name, owner);
            } else if (dir) {
                walk(entry, name, owner, 1);
            }
        }
    }

    /**
     * Classifies the files below an unowned folder whose exact name only one installed mod's code uses
     * (and that are no everyday names). True when at least one was found.
     */
    private boolean rescueDistinctive(Path dir, String path, int depth) {
        if (depth > 2) {
            return false;
        }
        boolean any = false;
        for (Path child : list(dir)) {
            String name = child.getFileName().toString();
            String childPath = path + "/" + name;
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                any |= rescueDistinctive(child, childPath, depth + 1);
                continue;
            }
            String lname = name.toLowerCase(Locale.ROOT);
            Attribution owner = attribute(name, true, Place.INSIDE);
            if (owner.installed().size() == 1 && owner.code() >= 100 && nameUsers.getOrDefault(lname, 0) == 1
                    && !GENERIC_NAMES.contains(Candidate.normalize(ConfigRules.stem(lname)))) {
                classify(child, childPath, owner);
                any = true;
            }
        }
        return any;
    }

    private void walk(Path dir, String path, Attribution owner, int depth) {
        if (depth > MAX_DEPTH || visited > MAX_FILES) {
            return;
        }
        if (isBackupName(dir.getFileName().toString(), owner)) {
            skipTree(dir, path, Reason.BACKUP, "", owner.ids());
            return;
        }
        for (Path child : list(dir)) {
            String name = child.getFileName().toString();
            String childPath = path + "/" + name;
            boolean file = Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS);
            Attribution childOwner = depth <= ATTRIBUTION_DEPTH ? ownerInside(name, file, owner) : owner;
            if (file) {
                classify(child, childPath, childOwner);
            } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                walk(child, childPath, childOwner, depth + 1);
            }
        }
    }

    /** Who owns an entry inside a folder that belongs to {@code parent} (see the class description). */
    private Attribution ownerInside(String name, boolean file, Attribution parent) {
        Attribution child = attribute(name, file, Place.INSIDE);
        if (child.installed().isEmpty()) {
            return parent;
        }
        if (child.ids().equals(parent.ids())) {
            // The owner of a folder that is only "probably" its own names this file exactly, and nobody else does.
            String exact = name.toLowerCase(Locale.ROOT);
            String stem = file ? ConfigRules.stem(exact) : exact;
            if (!parent.sure() && child.code() >= 100 && nameUsers.getOrDefault(exact, 0) == 1
                    && !GENERIC_NAMES.contains(stem) && !GENERIC_NAMES.contains(Candidate.normalize(stem))) {
                return new Attribution(parent.installed(), parent.disabled(), parent.score(), child.code(), parent.name(), parent.byPattern(),
                        parent.capture(), true);
            }
            return parent;
        }
        Set<String> parentIds = new HashSet<>(parent.ids());
        Set<String> childIds = new HashSet<>(child.ids());
        String lname = name.toLowerCase(Locale.ROOT);
        String lstem = file ? ConfigRules.stem(lname) : lname;
        // Several mods share the folder: the subfolder goes to those among them whose code names it.
        if (!parent.installed().isEmpty() && parentIds.containsAll(childIds) && child.code() >= CODE_THRESHOLD) {
            return child;
        }
        // An add-on's own file in another mod's folder.
        if (child.installed().size() != 1 || child.code() < EXACT || GENERIC_NAMES.contains(Candidate.normalize(lstem))
                || GENERIC_NAMES.contains(lstem)) {
            return parent;
        }
        if (!child.byPattern() && nameUsers.getOrDefault(lname, 0) + nameUsers.getOrDefault(lstem, 0) > 1) {
            return parent;
        }
        ModInfo addOn = child.installed().get(0);
        for (ModInfo owner : parent.installed()) {
            Candidate own = candidatesById.get(owner.id());
            if (own != null && own.score(lname, lstem).code() > 0) {
                return parent;
            }
            if (owner.rootId().equals(addOn.rootId())) {
                return parent;
            }
        }
        return child;
    }

    private void classify(Path file, String path, Attribution owner) {
        visited++;
        String name = file.getFileName().toString();
        long size = size(file);
        List<String> ids = owner.ids();
        // This mod knows its own files: a profile may be called anything ("2b2t", "Backup Binds", "Login").
        boolean own = ConfigRules.isOwnFile(path);
        if (ConfigRules.isBackupOrTemp(name) || (!own && isBackupName(insidePart(path), owner))) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.BACKUP, "", ids));
            return;
        }
        if (!ConfigRules.isConfigExtension(name)) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.NOT_SETTINGS, ConfigRules.extension(name), ids));
            return;
        }
        String stem = ConfigRules.stem(name).toLowerCase(Locale.ROOT);
        if (!own && DOCUMENT.matcher(stem).matches()) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.NOT_SETTINGS, "text", ids));
            return;
        }
        String cache = own ? null : cacheSegment(path, owner);
        if (cache != null) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.CACHE, cache, ids));
            return;
        }
        if (!own && DATED.matcher(stem).find()) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.DATED, "", ids));
            return;
        }
        Matcher state = STATE.matcher(stem);
        if (!own && state.find()) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.STATE, state.group().replaceAll("[^a-z]", ""), ids));
            return;
        }
        if (size > ConfigRules.MAX_FILE_BYTES) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.TOO_LARGE, "", ids));
            return;
        }
        String secretName = own ? null : SecretDetector.secretName(path);
        if (secretName != null) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.LOGIN_DATA, secretName, ids));
            return;
        }
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.BROKEN, "unreadable", ids));
            return;
        }
        String problem = contentProblem(name, data);
        if (problem != null) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.BROKEN, problem, ids));
            return;
        }
        String secret = SecretDetector.secretContent(name, data);
        if (secret != null) {
            skippedFiles.add(new Skipped(path, 1, size, Reason.LOGIN_DATA, secret, ids));
            return;
        }
        if (owner.installed().isEmpty()) {
            String hint = owner.disabled().stream().map(ModInfo::name).collect(Collectors.joining(", "));
            found.add(new Found(path, size, owner.disabled().stream().map(ModInfo::id).toList(), Kind.ORPHAN, hint, owner.byCode(), false));
            return;
        }
        String world = own ? null : worlds.perWorldReason(path.startsWith(ConfigRules.CONFIG_PREFIX) ? path.substring(ConfigRules.CONFIG_PREFIX.length()) : path);
        if (world == null && owner.capture() != null) {
            world = worlds.reasonFor(owner.capture());
        }
        if (world == null && !own) {
            // One file with the data of every world ("server:play.example.org": [...], "save:New World": [...]).
            world = worlds.perWorldByContent(name, data);
        }
        found.add(new Found(path, size, ids, world != null ? Kind.PER_WORLD : Kind.SETTINGS, world == null ? "" : world, owner.byCode(), owner.sure()));
    }

    /** The path below its top-level entry ("config/x/a/b.json" -> "a/b.json"): the entry itself is the mod's name. */
    private static String insidePart(String path) {
        String rest = path.startsWith(ConfigRules.CONFIG_PREFIX) ? path.substring(ConfigRules.CONFIG_PREFIX.length()) : path;
        int slash = rest.indexOf('/');
        return slash < 0 ? "" : rest.substring(slash + 1);
    }

    /** Null when the content fits the file type, else what is wrong with it. */
    static String contentProblem(String fileName, byte[] data) {
        return ConfigRules.contentProblem(fileName, data);
    }

    /**
     * The part of the path that marks a cache, log or similar, or null. A word that is part of the
     * owning mod's own name ("Cached Foo", "Simple Backups") does not count.
     */
    private static String cacheSegment(String path, Attribution owner) {
        for (String part : path.toLowerCase(Locale.ROOT).split("/")) {
            String stem = ConfigRules.stem(part);
            if ((CACHE_NAMES.contains(stem) || stem.contains("cache")) && !ownerNameContains(owner, "cache")) {
                return part;
            }
        }
        return null;
    }

    private static boolean isBackupName(String path, Attribution owner) {
        String lower = path.toLowerCase(Locale.ROOT);
        boolean looksLikeBackup = lower.contains("backup") || lower.contains("_bak") || lower.contains("-bak") || lower.endsWith(".bak");
        return looksLikeBackup && !ownerNameContains(owner, "backup");
    }

    private static boolean ownerNameContains(Attribution owner, String word) {
        for (ModInfo mod : owner.installed().isEmpty() ? owner.disabled() : owner.installed()) {
            if (mod.id().toLowerCase(Locale.ROOT).contains(word) || mod.name().toLowerCase(Locale.ROOT).contains(word)) {
                return true;
            }
        }
        return false;
    }

    private void skipTree(Path entry, String path, Reason reason, String detail, List<String> owners) {
        if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            skippedFiles.add(new Skipped(path, 1, size(entry), reason, detail, owners));
            return;
        }
        int[] count = new int[1];
        long[] bytes = new long[1];
        try (var stream = Files.walk(entry, MAX_DEPTH)) {
            stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).forEach(p -> {
                count[0]++;
                bytes[0] += size(p);
            });
        } catch (IOException | RuntimeException ignored) {
            // counted as far as possible
        }
        skippedFiles.add(new Skipped(path + "/", count[0], bytes[0], reason, detail, owners));
    }

    /** Many skipped files of one top-level entry for one reason become one line. */
    private static List<Skipped> aggregate(List<Skipped> skipped) {
        Map<String, List<Skipped>> byGroup = new LinkedHashMap<>();
        for (Skipped s : skipped) {
            byGroup.computeIfAbsent(topLevel(s.path()) + "|" + s.reason(), k -> new ArrayList<>()).add(s);
        }
        List<Skipped> out = new ArrayList<>();
        for (List<Skipped> group : byGroup.values()) {
            if (group.size() == 1) {
                out.add(group.get(0));
                continue;
            }
            Skipped first = group.get(0);
            int files = group.stream().mapToInt(s -> Math.max(0, s.files())).sum();
            long bytes = group.stream().mapToLong(s -> Math.max(0, s.size())).sum();
            String detail = group.stream().map(Skipped::detail).filter(d -> !d.isEmpty()).distinct().limit(4).collect(Collectors.joining(", "));
            out.add(new Skipped(topLevel(first.path()) + "/", files, bytes, first.reason(), detail, first.owners()));
        }
        out.sort(Comparator.comparing(Skipped::path, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** "config/xaero/a/b" -> "config/xaero", "baritone/x/y" -> "baritone". */
    private static String topLevel(String path) {
        String[] parts = path.split("/");
        if (parts[0].equals("config") && parts.length > 1) {
            return "config/" + parts[1];
        }
        return parts[0];
    }

    private static List<Path> list(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            stream.forEach(out::add);
        } catch (IOException | RuntimeException ignored) {
            // an unreadable folder has nothing to export
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ attribution

    /** The owners found for one name, how sure, and the part of the name a pattern of the owner left open. */
    record Attribution(List<ModInfo> installed, List<ModInfo> disabled, int score, int code, int name, boolean byPattern, String capture,
                       boolean sure) {
        List<String> ids() {
            return (installed.isEmpty() ? disabled : installed).stream().map(ModInfo::id).toList();
        }

        boolean byCode() {
            return code > 0;
        }
    }

    private static final Attribution NOBODY = new Attribution(List.of(), List.of(), 0, 0, 0, false, null, false);

    /** The mods that own a file or folder of this name in this kind of place. */
    Attribution attribute(String name, boolean file, Place place) {
        String lname = name.toLowerCase(Locale.ROOT);
        String lstem = file ? ConfigRules.stem(lname) : lname;
        int bestInstalled = 0;
        int bestDisabled = 0;
        List<ModInfo> installed = new ArrayList<>();
        List<ModInfo> disabled = new ArrayList<>();
        Score best = null;
        for (Candidate candidate : candidates) {
            Score score = candidate.score(lname, lstem);
            if (!eligible(score, place)) {
                continue;
            }
            int points = score.total();
            if (candidate.mod.installed()) {
                if (points > bestInstalled) {
                    bestInstalled = points;
                    installed.clear();
                    best = score;
                }
                if (points == bestInstalled) {
                    installed.add(candidate.mod);
                }
            } else {
                if (points > bestDisabled) {
                    bestDisabled = points;
                    disabled.clear();
                }
                if (points == bestDisabled) {
                    disabled.add(candidate.mod);
                }
            }
        }
        // A switched-off mod whose code names the file beats an installed mod that only has a similar name.
        if (!installed.isEmpty() && !disabled.isEmpty() && bestDisabled >= bestInstalled + 20 && best.code() == 0) {
            installed.clear();
        }
        if (!installed.isEmpty()) {
            // In the game folder a name the mod's code uses but that looks nothing like the mod is only offered.
            boolean sure = switch (place) {
                case CONFIG_TOP -> best.code() > 0 || best.name() >= OWNER_THRESHOLD;
                case GAME_TOP -> best.name() > 0;
                case INSIDE -> true;
            };
            return new Attribution(List.copyOf(installed), List.of(), bestInstalled, best.code(), best.name(), best.byPattern(), best.capture(), sure);
        }
        if (!disabled.isEmpty()) {
            return new Attribution(List.of(), List.copyOf(disabled), bestDisabled, 0, 0, false, null, false);
        }
        return NOBODY;
    }

    private static boolean eligible(Score score, Place place) {
        return switch (place) {
            case CONFIG_TOP -> score.total() >= OWNER_THRESHOLD;
            case GAME_TOP -> score.total() >= OWNER_THRESHOLD && (score.code() >= CODE_THRESHOLD || score.name() >= SAME_NAME);
            case INSIDE -> score.code() >= CODE_THRESHOLD;
        };
    }

    record Score(int code, int name, boolean byPattern, String capture) {
        int total() {
            return code + name;
        }
    }

    /** A mod with its evidence prepared for quick matching. */
    static final class Candidate {
        final ModInfo mod;
        /** How many installed mods' code uses each name (shared, filled while the candidates are made). */
        final Map<String, Integer> users;
        final List<Pattern> patterns = new ArrayList<>();
        final List<String> prefixes = new ArrayList<>();
        final List<String> names = new ArrayList<>();

        Candidate(ModInfo mod, Map<String, Integer> users) {
            this.mod = mod;
            this.users = users;
            BytecodeEvidence.Evidence evidence = mod.evidence();
            for (String pattern : evidence.patterns()) {
                if (isSpecific(pattern)) {
                    StringBuilder regex = new StringBuilder();
                    for (String part : pattern.split("\u0001", -1)) {
                        if (regex.length() > 0) {
                            regex.append("(.+?)");
                        }
                        regex.append(Pattern.quote(part));
                    }
                    patterns.add(Pattern.compile(regex.toString()));
                }
            }
            for (String token : evidence.strong()) {
                if (token.length() >= 5 && (token.endsWith("_") || token.endsWith("-"))) {
                    prefixes.add(token);
                }
            }
            names.addAll(mod.aliases());
            names.add(mod.name().toLowerCase(Locale.ROOT));
        }

        /** A pattern names something particular when its fixed text (without extension and separators) has 3+ letters. */
        static boolean isSpecific(String pattern) {
            String fixed = pattern.replace("\u0001", " ");
            fixed = fixed.replaceAll("\\.(json5?|jsonc|hjson|toml|properties|cfg|conf|config|ini|txt|ya?ml|xml|s?nbt|dat)\\b", " ");
            return fixed.replaceAll("[^\\p{L}\\p{N}]", "").length() >= 3;
        }

        Score score(String lname, String lstem) {
            BytecodeEvidence.Evidence evidence = mod.evidence();
            int code = 0;
            boolean byPattern = false;
            String capture = null;
            String token = null;
            if (evidence.strong().contains(lname)) {
                code = 100;
                token = lname;
            } else if (!lstem.equals(lname) && lstem.length() >= 3 && evidence.strong().contains(lstem)) {
                code = 80;
                token = lstem;
            }
            if (code < 90) {
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(lname);
                    if (matcher.matches()) {
                        code = 90;
                        byPattern = true;
                        capture = matcher.groupCount() > 0 ? matcher.group(1) : null;
                        break;
                    }
                }
            }
            if (code < 75) {
                for (String prefix : prefixes) {
                    if (lname.startsWith(prefix) && lname.length() > prefix.length()) {
                        code = 75;
                        byPattern = true;
                        capture = ConfigRules.stem(lname.substring(prefix.length()));
                        break;
                    }
                }
            }
            if (code < 60 && (evidence.medium().contains(lname) || (lstem.length() >= 3 && evidence.medium().contains(lstem)))) {
                code = 60;
                token = evidence.medium().contains(lname) ? lname : lstem;
            }
            if (code < 40 && (evidence.weak().contains(lname) || (lstem.length() >= 3 && evidence.weak().contains(lstem)))) {
                code = 40;
                token = evidence.weak().contains(lname) ? lname : lstem;
            }
            // A name the code of many mods uses is an everyday word - unless it is (part of) this mod's own name
            // (the folder "xaero" of Xaero's Minimap, World Map and their library).
            int name = similarity(lstem);
            if (token != null && !byPattern && name == 0 && users.getOrDefault(token, 0) >= 3) {
                code = Math.min(code, 40);
            }
            return new Score(code, name, byPattern, capture);
        }

        int similarity(String lstem) {
            String stem = normalize(lstem);
            if (stem.length() < 2) {
                return 0;
            }
            int best = 0;
            for (String name : names) {
                String normalized = normalize(name);
                if (normalized.length() < 3) {
                    continue;
                }
                if (stem.equals(normalized)) {
                    best = Math.max(best, SAME_NAME);
                } else if (startsWithSeparated(lstem, name)) {
                    // "creativecore-client" is Creative Core's; "litematica_printer" is another mod's.
                    best = Math.max(best, onlySuffixWords(lstem, name) ? 55 : 35);
                } else if (stem.startsWith(normalized) && normalized.length() >= 4) {
                    best = Math.max(best, 45);
                } else if (normalized.startsWith(stem) && stem.length() >= 4) {
                    best = Math.max(best, 40);
                }
            }
            return best;
        }

        /** After the mod's name, only words like "client", "common", "options" follow. */
        static boolean onlySuffixWords(String text, String prefix) {
            for (String variant : new String[]{prefix, prefix.replace('-', '_'), prefix.replace('_', '-'), normalize(prefix)}) {
                if (variant.length() >= 3 && text.length() > variant.length() + 1 && text.startsWith(variant)) {
                    for (String word : text.substring(variant.length() + 1).split("[-_. ]+")) {
                        if (!word.isEmpty() && !SUFFIX_WORDS.contains(word)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        /** "litematica_localhost" starts with "litematica" followed by a separator (also with - and _ swapped). */
        static boolean startsWithSeparated(String text, String prefix) {
            for (String variant : new String[]{prefix, prefix.replace('-', '_'), prefix.replace('_', '-'), normalize(prefix)}) {
                if (variant.length() >= 3 && text.length() > variant.length() && text.startsWith(variant)
                        && "-_. ".indexOf(text.charAt(variant.length())) >= 0) {
                    return true;
                }
            }
            return false;
        }

        static String normalize(String text) {
            StringBuilder out = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = Character.toLowerCase(text.charAt(i));
                if (Character.isLetterOrDigit(c)) {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }
}
