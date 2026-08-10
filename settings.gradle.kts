pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
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
