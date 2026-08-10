plugins {
    id("fabric-loom") version "1.11.8"
}

// ---- Identity, read from gradle.properties -------------------------------
// Nothing here hardcodes the mod's name. See docs/NAMING.md.
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modDescription = property("mod_description") as String
val modLicense = property("mod_license") as String
val modSources = property("mod_sources") as String
val modPackage = property("mod_package") as String
val modMainClass = property("mod_main_class") as String

val minecraftVersion = property("minecraft_version") as String
val fabricLoaderVersion = property("fabric_loader_version") as String
val fabricApiVersion = property("fabric_api_version") as String
val javaVersion = (property("java_version") as String).toInt()

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName = modId
}

repositories {
    mavenCentral()
}

dependencies {
    // Loom's configurations are addressed by name — the Kotlin DSL does not
    // reliably generate type-safe accessors for them.
    // Sources are written against Mojang-official names (mojmap), not yarn.
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"(loom.officialMojangMappings())
    "modImplementation"("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // The codex types are pure Java with no Minecraft on the classpath, so
    // they get plain JUnit. In-world behaviour is tested by @GameTest instead
    // - see docs/DEVOPS.md for why that split is deliberate.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    // Ship sources alongside the jar; this is a commons project and the
    // sources jar costs nothing to produce.
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}

// ---- Headless in-world tests ---------------------------------------------
// Minecraft's own GameTest framework, driven by fabric-gametest-api-v1. This
// boots a real dedicated server, runs every @GameTest, writes a JUnit XML
// report, and exits non-zero on failure — so it works identically on a laptop
// and in CI, with no display and no account.
loom {
    runs {
        create("gametest") {
            server()
            name = "Game Test"
            source(sourceSets.main.get())
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=" +
                    layout.buildDirectory.file("test-results/gametest/report.xml").get().asFile.absolutePath)
            runDir("build/gametest")
        }

        // A real dedicated server, used to generate worlds headlessly so that
        // "does this actually look right in a world" stops costing a human at a
        // keyboard. Driven by tools/new-world.ps1.
        //
        // Its own runDir, deliberately: the default would be run/, which holds
        // the client's options.txt, its saves and its logs. And under run/
        // rather than build/, because build/ is what `gradlew clean` deletes and
        // this directory holds the one file the user has to accept by hand.
        create("worldgen") {
            server()
            name = "Worldgen"
            source(sourceSets.main.get())
            runDir("run/worldgen")
        }
    }
}

// The generator script tells the server to `stop` once spawn has been written.
// Without a connected stdin the only way to end the run is to kill the JVM,
// which races the final chunk save - the one thing a world generator must not do.
//
// Gated behind -PoctiaStdin, and the gate is not optional. Declaring
// standardInput = System.`in` unconditionally makes the Gradle client forward
// stdin for the WHOLE build and then wait for an EOF that a scripted caller
// never sends, so every other task hangs after finishing. That cost a ten
// minute verify run that had already printed "All 29 required tests passed".
if (project.hasProperty("octiaStdin")) {
    tasks.withType<JavaExec>().matching { it.name == "runWorldgen" }.configureEach {
        standardInput = System.`in`
    }
}

// ---- fabric.mod.json is generated, never edited by hand -------------------
// Every placeholder must sit INSIDE a JSON string. Loom parses the raw source
// file at configure time to discover the mod id, so a placeholder in a bare
// value position (e.g. "authors": [${mod_authors}]) makes the file invalid
// JSON and Loom logs "Failed to parse fabric.mod.json". The build still
// succeeds, which is what makes the mistake easy to keep.
tasks.processResources {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_version" to version.toString(),
        "mod_description" to modDescription,
        "mod_license" to modLicense,
        "mod_sources" to modSources,
        "mod_package" to modPackage,
        "mod_entrypoint" to "$modPackage.$modMainClass",
        "minecraft_version" to minecraftVersion,
        "fabric_loader_version" to fabricLoaderVersion,
        "java_version" to javaVersion.toString(),
    )

    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
