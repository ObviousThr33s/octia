package com.serenity.octia.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The codex types are pure Java with no Minecraft on the classpath, so they
 * are tested here rather than as GameTests. In-world behaviour belongs in
 * {@code src/main/java/.../gametest}; this is the fast gate.
 */
class NotationTest {

    /**
     * The sign KEG placed at (-112, 67, -149), exactly as its four message
     * lines read.
     *
     * <p>This is the anchor of the whole codex: the notation was authored
     * in-world, so the world is the source of truth and the docs are a
     * description of it. If this test ever fails, the code is wrong - not
     * the sign.
     */
    private static final String ACT_ONE_SIGN = """
            ACT ONE
            MILESTONE 0
            START HERE
            SEEK KEG""";

    @Test
    @DisplayName("the ACT ONE sign round-trips unchanged")
    void actOneSignRoundTrips() {
        Notation n = Notation.parse(ACT_ONE_SIGN);

        assertEquals(Act.ONE, n.act());
        assertEquals(0, n.milestone());
        assertEquals("START HERE", n.subject());
        assertEquals("KEG", n.seek().target());
        assertFalse(n.seek().isAddressed(), "the sign carries no scope");

        assertEquals(ACT_ONE_SIGN, n.block());
    }

    @Test
    @DisplayName("a sign's blank lines do not change the parse")
    void blankSignLinesAreDropped() {
        // A Minecraft sign always stores four messages; unused ones are "".
        // A block copied off a sign must parse the same as one typed by hand.
        String withBlanks = "\nACT ONE\n\nMILESTONE 0\nSTART HERE\n\nSEEK KEG\n";
        assertEquals(Notation.parse(ACT_ONE_SIGN), Notation.parse(withBlanks));
    }

    @Test
    @DisplayName("the composite block carries an artifact as its subject")
    void compositeBlockCarriesArtifact() {
        Notation n = Notation.parse("""
                ACT ONE
                MILESTONE 0
                OCTIOID_[0.1.0.A.C.T.1]_build
                SEEK KEG |ALL|""");

        ArtifactId a = n.artifact().orElseThrow();
        assertEquals("OCTIOID", a.name());
        assertEquals(0, a.major());
        assertEquals(1, a.minor());
        assertEquals(0, a.patch());
        assertEquals(List.of("A", "C", "T", "1"), a.flags());
        assertEquals("build", a.type());

        assertTrue(n.seek().isAddressed());
        assertEquals(Scope.ALL, n.seek().scope());
    }

    @Test
    @DisplayName("a directive subject is not mistaken for an artifact")
    void directiveIsNotAnArtifact() {
        assertTrue(Notation.parse(ACT_ONE_SIGN).artifact().isEmpty());
    }

    @Test
    @DisplayName("acts are spelled, never numeric")
    void actsAreSpelled() {
        assertEquals(Act.ONE, Act.parse("ACT ONE"));
        assertEquals(Act.FIVE, Act.parse("five"));
        // "ACT 1" would be a quiet corruption of the form, so it throws.
        assertThrows(IllegalArgumentException.class, () -> Act.parse("ACT 1"));
    }

    @Test
    @DisplayName("acts and era layers share one ordering")
    void actsMapToEras() {
        assertEquals(5, Act.values().length, "five acts, five eras");
        assertEquals("Infinite City", Act.ONE.era());
        assertEquals("Modern World", Act.FIVE.era());
        for (Act act : Act.values()) {
            assertEquals(act.ordinal() + 1, act.index());
        }
    }

    @Test
    @DisplayName("SEEK is the only call verb")
    void seekIsTheOnlyVerb() {
        assertEquals("KEG", Seek.parse("SEEK KEG").target());
        assertThrows(IllegalArgumentException.class, () -> Seek.parse("FIND KEG"));
        assertThrows(IllegalArgumentException.class, () -> Seek.parse("GET KEG"));
    }

    @Test
    @DisplayName("scope keeps both readings available")
    void scopeKeepsBothReadings() {
        Scope s = Scope.parse("|SEEK|SAGE|DEVOPS|ALL|USERS|");

        // Cardinality - the divisor reading in the Mobius note.
        assertEquals(5, s.size());
        // Selector - the alternation reading.
        assertTrue(s.contains("DEVOPS"));
        assertEquals(List.of("SEEK", "SAGE", "DEVOPS", "ALL", "USERS"), s.members());

        // Outer pipes are part of the form and are always emitted.
        assertEquals("|SEEK|SAGE|DEVOPS|ALL|USERS|", s.toNotation());
        assertEquals(s, Scope.parse("SEEK|SAGE|DEVOPS|ALL|USERS"));
    }

    @Test
    @DisplayName("the filesystem form is derived, not a second notation")
    void filesystemFormIsDerived() {
        // Minecraft sanitised the authored level name into the folder name.
        // Both must resolve to the same artifact.
        ArtifactId authored = ArtifactId.parse("SERENITY_[0.0.0.S.A.F.E].project");
        ArtifactId onDisk = ArtifactId.parse("SERENITY_[0_0_0_S_A_F_E]_project");

        assertEquals(authored, onDisk);
        assertEquals("SAFE", authored.flagWord());
        assertEquals("SERENITY_[0_0_0_S_A_F_E]_project", authored.toFilesystemName());
        assertEquals("SERENITY_[0.0.0.S.A.F.E]_project", authored.toNotation());
    }

    @Test
    @DisplayName("milestone is zero-based and rejects negatives")
    void milestoneIsZeroBased() {
        // MILESTONE 0 is the entry, not the first achievement.
        assertEquals(0, Notation.parse(ACT_ONE_SIGN).milestone());
        assertThrows(IllegalArgumentException.class,
                () -> Notation.of(Act.ONE, -1, "NOWHERE", Seek.of("KEG")));
    }

    @Test
    @DisplayName("a block is exactly four lines")
    void blockIsFourLines() {
        assertThrows(IllegalArgumentException.class,
                () -> Notation.parse("ACT ONE\nMILESTONE 0\nSEEK KEG"));
        assertThrows(IllegalArgumentException.class,
                () -> Notation.parse("ACT ONE\nMILESTONE 0\nA\nB\nSEEK KEG"));
    }
}
