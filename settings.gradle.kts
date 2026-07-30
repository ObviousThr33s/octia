pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
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
rootProject.name = "octioid"
