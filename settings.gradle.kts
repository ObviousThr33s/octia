pluginManagement {
    // A host that is MISSING an artifact is not the problem this solves - Gradle
    // already walks on to the next repository for a 404. A host that is DOWN is a
    // different failure: the build either dies at plugin resolution or sits on a
    // connect timeout. docs/TRAJECTORY.traj XV is that failure, written down - the
    // beamline work went unverified because maven.fabricmc.net could not be reached
    // from this machine, and there was no second address to try.
    //
    // Fabric publishes the same artifacts on three hosts. Checked 2026-08-28:
    // fabric-loader-0.19.3.jar is 1976502 bytes and fabric-loom-1.11.8.jar is
    // 1159795 bytes on all three, so these are mirrors and not three spellings of
    // one machine. Borrowed from Turnip-Labs/bta-example-mod, which solved it first.
    //
    // Bounded on purpose: a probe that can hang has only replaced one stall with
    // another. Local functions because pluginManagement must be the first block in
    // a settings script - nothing, declarations included, may precede it.
    fun isRepoHealthy(url: String): Boolean = try {
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        conn.instanceFollowRedirects = true
        try { conn.responseCode in 200..399 } finally { conn.disconnect() }
    } catch (_: Exception) {
        false
    }

    fun repoUrlWithFallbacks(vararg candidates: String): String {
        val log = org.gradle.api.logging.Logging.getLogger("octia.settings")
        for (url in candidates) {
            if (isRepoHealthy(url)) {
                if (url != candidates.first()) {
                    log.lifecycle("octia: ${candidates.first()} did not answer; using $url")
                }
                return url
            }
        }
        // Nothing answered, which usually means this machine is offline rather than
        // Fabric being down. Hand back the primary so Gradle reports the real failure
        // against the address everyone recognises, not a mirror nobody expected.
        log.warn("octia: no Fabric maven answered a HEAD within 2s; using ${candidates.first()}")
        return candidates.first()
    }

    repositories {
        maven(
            repoUrlWithFallbacks(
                "https://maven.fabricmc.net/",
                "https://maven2.fabricmc.net/",
                "https://maven3.fabricmc.net/",
            )
        ) { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }

    // The Loom pin, resolved rather than repeated. gradle.properties calls
    // itself the single control panel and docs/UPGRADING.md offers
    // `1.11.8 -> 1.12.7` as a free move - but build.gradle.kts used to carry
    // the same number as a literal, so editing the property alone changed
    // nothing and said so silently. A plugins {} block in a BUILD script needs
    // a literal; this one, in settings, can read a property, so the version
    // lives in exactly one place and the free move is one line after all.
    plugins {
        id("fabric-loom") version providers.gradleProperty("loom_version").get()
    }
}

plugins {
    // This machine ships JDK 24 and no 21. The foojay resolver lets Gradle
    // fetch the pinned JDK 21 toolchain itself instead of failing the build
    // or silently compiling against the wrong release.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Deliberately NOT read from gradle.properties: rootProject.name feeds the
// IDE project name and the .gradle cache path, and Gradle resolves it before
// the properties are applied in some import paths. tools/rename-mod.ps1
// rewrites this line along with everything else.
rootProject.name = "octia"
