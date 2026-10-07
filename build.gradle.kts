plugins {
    // Applies fabric-loom-remap up to 1.21.11 and fabric-loom on 26.1+ (unobfuscated)
    id("dev.kikugie.loom-back-compat")
}

fun prop(name: String): String = project.property(name).toString()

val mc = sc.current.version
val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21
// The dev client's game folder: 1.21.11 keeps the project's own run/ (it holds the test profiles),
// the other versions use versions/<version>/run.
val gameDir = if (mc == "1.21.11") rootProject.file("run") else file("run")

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base { archivesName.set(prop("mod.id")) }

loom {
    accessWidenerPath = sc.process(rootProject.file("src/main/resources/keybindprofilesplus.accesswidener"), "build/processed.accesswidener")

    // Tells the dev client that the compiled classes and the resources are one mod. Without it the
    // classes only become visible once the game is loading, too late for the early config importer
    // (a language adapter, created before the mods' mixins are read). Release jars are not affected.
    mods {
        register("keybindprofilesplus") {
            sourceSet(sourceSets["main"])
        }
    }

    runs {
        named("client") {
            runDir(gameDir.relativeTo(projectDir).path)
        }
        // Dev client that runs the automated self-test and quits (see tools/selftest.ps1).
        register("selfTest") {
            client()
            configName = "Self Test"
            vmArg("-Dkbp.selftest=true")
            runDir(gameDir.relativeTo(projectDir).path)
            ideConfigGenerated(false)
        }
    }
}

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")

    // Mod Menu: only its API is compiled against (the configure button in the mod list). It is also
    // put into the dev client so the self-test can check that button; the released jar does not need it.
    modCompileOnly("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
    modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")

    // Other mods whose own hotkeys are listed and edited (external/): Meteor Client, the malilib family and
    // Inventory Profiles Next.
    // They are reached by reflection on their own class and method names (which are the same in every
    // environment), so the mod needs none of them to build or to run.
    //
    // For the dev client (and with it the self-test) tools/prepare-dev-mods.ps1 puts copies of malilib,
    // Litematica, Meteor and IPN for each Minecraft version into libs/<version>/, which is not committed.
    // Without them the dev client simply runs without those mods.
    for (dir in listOf("malilib", "meteor", "ipn")) {
        val folder = rootProject.file("libs/$mc/$dir")
        if (folder.isDirectory) {
            modLocalRuntime(fileTree(folder) { include("*.jar") })
        }
    }
}

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    withSourcesJar()

    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain { languageVersion.set(JavaLanguageVersion.of(requiredJava.majorVersion)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava.majorVersion.toInt())
}

tasks.processResources {
    val props = mapOf(
        "version" to prop("mod.version"),
        "minecraft_version" to prop("mod.mc_compat"),
        "loader_compat" to prop("mod.loader_compat"),
        "java_compat" to prop("mod.java_compat"),
        "mixin_java" to "JAVA_${requiredJava.majorVersion}",
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
}

tasks.named<Jar>("jar") {
    val baseName = prop("mod.id")
    inputs.property("archivesName", baseName)

    // GPLv3 for this mod, plus the MIT notice of the upstream code it is based on.
    from(listOf(rootProject.file("LICENSE"), rootProject.file("LICENSE-upstream-MIT"))) {
        rename { "${it}_$baseName" }
    }
}

// Collects the release jars of all versions in build/libs/<mod version>/
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
}

// Development tool: runs the mod config scan on a game folder and writes a report
// (gradlew :<version>:scanConfigs -Pinstance=<game folder>). Not part of the mod.
tasks.register<JavaExec>("scanConfigs") {
    group = "verification"
    description = "Scans a game folder for mod config files and writes build/config-scan/report.txt"
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.autyism.keybindprofilesplus.selftest.ConfigScanTool")
    args = listOf(
        (project.findProperty("instance") ?: gameDir.absolutePath).toString(),
        layout.buildDirectory.file("config-scan/report.txt").get().asFile.absolutePath,
        layout.buildDirectory.file("config-scan/cache.json").get().asFile.absolutePath,
    )
}
