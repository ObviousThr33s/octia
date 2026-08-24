plugins {
    // No version here on purpose. A plugins {} block in a build script takes a
    // literal, which is how the Loom pin came to be written twice; the one in
    // settings.gradle.kts resolves loom_version from gradle.properties instead,
    // so the pin and its reasoning stay in the one file that claims to own them.
    id("fabric-loom")
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

    // The codex types, and crew's pure-logic ones (Order, SeatName), need no
    // world to exercise, so they get plain JUnit. In-world behaviour is tested
    // by @GameTest instead - see docs/DEVOPS.md for why that split is
    // deliberate.
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

            // What the crew bench is allowed to expect of this machine.
            //
            // CrewBenchGameTest refuses to guess: it asserts the online contract
            // when this says "required" and the offline tender contract when it
            // says "absent", and an unrecognised value is an error rather than a
            // quiet skip. Its own default is "required", which is the right
            // default for a test written at a desk with LM Studio open and the
            // wrong one for a gate.
            //
            // "absent" here, because OCTIA.md standing order 2 says the gates run
            // with no display, no Mojang account and no secrets - and a local
            // model on 127.0.0.1 is the same class of thing. A gate that cannot
            // go green on a clean checkout is not a gate. Pass -PoctiaBench=required
            // (tools/verify.ps1 -Bench) to check the network side instead.
            vmArg("-Doctia.crew.network=" + (findProperty("octiaBench") ?: "absent"))

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

// How tools/new-world.ps1 tells the server to generate and then stop.
//
// This was once done by writing `stop` to the server console over a connected
// stdin, and it was a mistake twice over. Declaring standardInput made the
// Gradle client forward stdin for the entire build and hang waiting for an EOF
// no script sends - a ten minute verify that had already passed all 29 tests.
// And the line itself arrived with a byte-order mark on the front, so the
// server read "<BOM>stop" and kept running. Properties cross the same three
// process boundaries with nothing to encode. See HeadlessRun.
tasks.withType<JavaExec>().matching { it.name == "runWorldgen" }.configureEach {
    if (project.hasProperty("octiaExit")) {
        jvmArgs("-Doctia.worldgen.exit=true")
    }
    if (project.hasProperty("octiaRadius")) {
        jvmArgs("-Doctia.worldgen.radius=${project.property("octiaRadius")}")
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

// ---- The door -------------------------------------------------------------
// The third way in, beside SEEK.cmd at the repo root and the desktop shortcut
// that `tools/frontdoor.ps1 -Install` writes. All three call the same script.
//
// Registered lazily and wired to nothing. `tasks.register` does not configure
// the task unless somebody asks for it by name, which is what keeps a
// powershell.exe command line out of CI's way - verify.yml runs on Linux and
// would choke the moment this was realised there.
//
// It must never become a dependency of `build` or `check`. Those two are the
// gates; the gates run with no display, no account and no window, and this
// opens a window. A doorbell, not a dependency.
//
// The door itself still does not build against Loom and still knows nothing
// about Minecraft - see the header of tools/frontdoor.ps1 for why it lives
// outside the source set and caches its own jar.
tasks.register<Exec>("door") {
    group = "octia"
    description = "Open the OCTIA front door. Compiles it first if it is stale."
    commandLine(
        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", layout.projectDirectory.file("tools/frontdoor.ps1").asFile.absolutePath,
    )
}
