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
