package com.serenity.octia.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.serenity.octia.codex.Scope.Endian;
import com.serenity.octia.codex.Scope.Reading;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pipes, after 2026-08-28.
 *
 * <p>docs/NOTATION.md 3 stood open for a long time on whether {@code |A|B|C|} is
 * a selector or a count. It is both, and which one depends on who is reading.
 * These tests pin that resolution so it cannot quietly collapse back into one
 * answer - which is the failure the old javadoc was written to prevent.
 */
@DisplayName("a scope is read by somebody, from an end")
class ScopeTest {

    private static final Scope THREE = Scope.of("SEEK", "SAGE", "DEVOPS");

    @Test
    @DisplayName("simplex divides by one however wide the scope is written")
    void simplexDividesByOne() {
        // The whole point of the resolution. A simplex reader occupies one
        // channel, so the written width attenuates nothing for them.
        assertEquals(1, THREE.divisor(Reading.SIMPLEX));
        assertEquals(1, Scope.of("A", "B", "C", "D", "E", "F").divisor(Reading.SIMPLEX));
        assertEquals(1, Scope.ALL.divisor(Reading.SIMPLEX));
    }

    @Test
    @DisplayName("multiplex divides by the count")
    void multiplexDividesBySize() {
        assertEquals(3, THREE.divisor(Reading.MULTIPLEX));
        assertEquals(THREE.size(), THREE.divisor(Reading.MULTIPLEX));
        assertEquals(1, Scope.ALL.divisor(Reading.MULTIPLEX), "ALL holds one member, not a population");
    }

    @Test
    @DisplayName("the two readings disagree exactly when the scope is wider than one")
    void theReadingsDivergeOnlyWhenWide() {
        assertNotEquals(THREE.divisor(Reading.SIMPLEX), THREE.divisor(Reading.MULTIPLEX));
        Scope one = Scope.of("KEG");
        assertEquals(one.divisor(Reading.SIMPLEX), one.divisor(Reading.MULTIPLEX));
    }

    @Test
    @DisplayName("a divisor needs a reading, and a read needs an end")
    void nullsAreRefused() {
        // Refused rather than defaulted. A silently-defaulted reading would put
        // the wrong denominator in the aberration and nothing would say so.
        assertThrows(IllegalArgumentException.class, () -> THREE.divisor(null));
        assertThrows(IllegalArgumentException.class, () -> THREE.read(null));
    }

    @Test
    @DisplayName("endian picks which member a simplex reader is on")
    void endianSelects() {
        assertEquals("SEEK", THREE.selected(Endian.LEFT));
        assertEquals("DEVOPS", THREE.selected(Endian.RIGHT));
    }

    @Test
    @DisplayName("endian turns the reading order around, and nothing else")
    void endianReadsBothWays() {
        assertEquals(List.of("SEEK", "SAGE", "DEVOPS"), THREE.read(Endian.LEFT));
        assertEquals(List.of("DEVOPS", "SAGE", "SEEK"), THREE.read(Endian.RIGHT));
        // The notation is unchanged by which end you read from - that is what
        // makes endian a property of the reader rather than of the scope.
        assertEquals("|SEEK|SAGE|DEVOPS|", THREE.toNotation());
    }

    @Test
    @DisplayName("a multiplex reader cannot observe the endian at all")
    void multiplexIsBlindToEndian() {
        assertEquals(THREE.divisor(Reading.MULTIPLEX), THREE.reversed().divisor(Reading.MULTIPLEX));
        assertEquals(THREE.size(), THREE.reversed().size());
    }

    @Test
    @DisplayName("cycling walks the scope through its own channels and returns")
    void cycleRotates() {
        Scope once = THREE.cycle();
        assertEquals("|SAGE|DEVOPS|SEEK|", once.toNotation());
        assertEquals("|DEVOPS|SEEK|SAGE|", once.cycle().toNotation());
        // Three members, three steps, back where it started.
        assertEquals(THREE, once.cycle().cycle());
    }

    @Test
    @DisplayName("cycling a single channel selects itself rather than throwing")
    void cycleOfOne() {
        assertEquals(Scope.ALL, Scope.ALL.cycle());
        assertEquals("|ALL|", Scope.ALL.cycle().toNotation());
    }

    @Test
    @DisplayName("reversed is a scope, not a view, and round-trips")
    void reversedRoundTrips() {
        assertEquals("|DEVOPS|SAGE|SEEK|", THREE.reversed().toNotation());
        assertEquals(THREE, THREE.reversed().reversed());
        assertEquals(THREE.reversed(), Scope.parse("|DEVOPS|SAGE|SEEK|"));
    }

    @Test
    @DisplayName("every cycle of a scope holds the same members")
    void cyclingPreservesMembership() {
        Scope s = THREE;
        for (int i = 0; i < 3; i++) {
            s = s.cycle();
            for (String m : THREE.members()) {
                assertTrue(s.contains(m), m + " fell out of the scope after cycling");
            }
        }
    }
}
