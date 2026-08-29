package com.serenity.octia.codex;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The release names itself in KEG notation, and this is what stops that name
 * from being a third source of truth.
 *
 * <p>{@code mod_act}, {@code mod_milestone} and {@code mod_flags} live in
 * {@code gradle.properties}, which docs/NAMING.md calls the single control
 * panel. Two things downstream render them without going near this package:
 * {@code tools/release-card.ps1} in PowerShell and the title step of
 * {@code .github/workflows/release.yml} in sh. Neither can import
 * {@link Notation}, so neither can be stopped from writing something the codex
 * would reject - a numeric act, a negative milestone, a flag with a space in
 * it. This test is where that gets caught, at {@code gradlew build}, before a
 * tag exists.
 *
 * <p>The values arrive as system properties set by {@code tasks.test} rather
 * than being read off disk here. A test that parsed {@code gradle.properties}
 * itself would be a second parser for a file the build already parses, and
 * would depend on a working directory nobody chose deliberately.
 */
@DisplayName("the release names itself in notation the codex accepts")
class ReleaseNotationTest {

    private static String required(String key) {
        String value = System.getProperty(key);
        // Not assumeTrue. A missing property means tasks.test stopped passing it
        // and the check is silently gone - which is exactly the failure mode
        // docs/DEVOPS.md records for a gametest class missing from the
        // fabric-gametest list, where the only symptom was the count.
        assertTrue(value != null && !value.isBlank(),
                key + " was not set; tasks.test in build.gradle.kts should pass it");
        return value.trim();
    }

    private static String act() {
        return required("octia.release.act");
    }

    private static String milestone() {
        return required("octia.release.milestone");
    }

    private static String flags() {
        return required("octia.release.flags");
    }

    /** The version triple, with any SemVer prerelease cut off, as the card does. */
    private static String triple() {
        return required("octia.release.version").split("-", 2)[0];
    }

    /** The artifact id exactly as tools/release-card.ps1 assembles it. */
    private static String artifactId() {
        return required("octia.release.name").toUpperCase() + "_[" + triple() + "." + flags() + "]_build";
    }

    @Test
    @DisplayName("mod_act is a spelled act, never a number")
    void actParses() {
        Act parsed = Act.parse(act());
        assertEquals("ACT " + act().toUpperCase(), parsed.toNotation());
    }

    @Test
    @DisplayName("mod_milestone is zero-based and not negative")
    void milestoneIsZeroBased() {
        int n = assertDoesNotThrow(() -> Integer.parseInt(milestone()),
                "MILESTONE takes a number, and mod_milestone says '" + milestone() + "'");
        assertTrue(n >= 0, "MILESTONE 0 is the entry, so a milestone is never negative: " + n);
    }

    @Test
    @DisplayName("the artifact id parses, and echoes back unchanged")
    void artifactRoundTrips() {
        ArtifactId parsed = ArtifactId.parse(artifactId());
        assertEquals(artifactId(), parsed.toNotation());
        assertEquals("build", parsed.type());
    }

    @Test
    @DisplayName("mod_version's triple survives into the bracket intact")
    void tripleSurvives() {
        ArtifactId parsed = ArtifactId.parse(artifactId());
        assertEquals(triple(), parsed.major() + "." + parsed.minor() + "." + parsed.patch(),
                "the bracket takes major.minor.patch, so mod_version must open with one");
    }

    @Test
    @DisplayName("the flags are the dots taken out of mod_flags, and nothing else")
    void flagsAreTheProperty() {
        ArtifactId parsed = ArtifactId.parse(artifactId());
        assertEquals(List.of(flags().split("\\.")), parsed.flags());
        // flagWord() only reads as a word while every flag is one character.
        // ArtifactId's own comment says so; this is that condition, asserted.
        assertEquals(flags().replace(".", ""), parsed.flagWord());
    }

    @Test
    @DisplayName("the four-line block the card prints round-trips through the parser")
    void blockRoundTrips() {
        String block = "ACT " + act().toUpperCase()
                + "\nMILESTONE " + milestone()
                + "\n" + artifactId()
                + "\nSEEK KEG |ALL|";

        Notation parsed = Notation.parse(block);
        assertEquals(block, parsed.block());
        assertEquals(Act.parse(act()), parsed.act());
        assertEquals(Integer.parseInt(milestone()), parsed.milestone());
        assertTrue(parsed.artifact().isPresent(),
                "the third line is an artifact here, not a bare directive");
        assertEquals("KEG", parsed.seek().target());
    }
}
