package com.serenity.octia.lightwell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.serenity.octia.tower.TowerPlan;

/**
 * A pyramid dug downward: an inverted stepped well that houses people the way a
 * data centre houses racks, and gets daylight to every one of them.
 *
 * <p><b>The shape, and why this one.</b> A tower stacks floors into the sky and
 * every floor has an outside wall. Invert it and that stops being true: a room
 * underground touches no sky at all, which is why cellars are storerooms and not
 * dwellings. The form that solves it is old and real. A stepwell - Chand Baori
 * at Abhaneri is the clearest - is an inverted stepped pyramid cut into the
 * ground, and it is bright at the bottom. The modern restatement is the
 * Earthscraper: an inverted pyramid under a city square, capped at grade, with
 * floors ringing a central void whose whole purpose is to carry light down.
 * This builds that: an envelope that narrows with depth, floors as rings, and
 * one shaft that does <em>not</em> narrow.
 *
 * <p><b>The shaft is the entire point, so it is the one thing that does not
 * taper.</b> Everything else steps inward as it goes down - that is what makes
 * the envelope a pyramid. If the shaft tapered with it, the well would close to
 * a point and the lowest floors would be exactly the windowless cellar the form
 * exists to avoid. So {@code shaft} is constant from grade to apex, and the
 * pyramid is what happens to the <em>outside</em>. Depth is therefore not a
 * parameter: the structure ends where the narrowing envelope meets the shaft it
 * cannot cross, and {@link #levels()} reports where that fell.
 *
 * <p><b>No Minecraft on the imports, and none wanted.</b> The same line
 * {@link TowerPlan} draws, for the same reason: the gates run headless, so
 * nothing about how this looks can ever be defended by CI, but that a given set
 * of measurements yields exactly these blocks is a pure function testable in
 * milliseconds. Everything uncertain is pushed to the other side of the line on
 * purpose.
 *
 * <p><b>It borrows the vocabulary and not the frame.</b> {@link TowerPlan.Cell}
 * is used as-is, because two block palettes in one mod would drift and the one
 * that drifted would be the one nobody was looking at. The coordinate frame is
 * <em>not</em> borrowed: a tower's {@code y} counts up from its bottom row, and
 * this counts down from grade. Reusing a record whose documented axes point the
 * other way is how a subtle inversion bug is born, so {@link Block} is its own
 * type with its own frame written on it.
 *
 * <p><b>Measurements, not a drawing.</b> A tower is typed by a person and is
 * therefore untrusted input. A lightwell is four numbers, so the caps below are
 * about arithmetic rather than vocabulary: the block count is computed
 * analytically and refused <em>before</em> anything is allocated in proportion
 * to it. A set of measurements that will not resolve is refused whole, and the
 * refusal names the nearest measurement that would have worked.
 */
public final class LightwellPlan {

    /**
     * The widest the mouth may be at grade.
     *
     * <p>Odd on purpose, and the same order as {@link TowerPlan#MAX_WIDTH}. A
     * lightwell needs a centre for the shaft to sit on, so every horizontal
     * measurement here is odd; 65 is the tower's ceiling plus the one block that
     * gives it a middle.
     */
    public static final int MAX_MOUTH = 65;

    /** The narrowest useful shaft: a centre column and one block either side. */
    public static final int MIN_SHAFT = 3;

    /**
     * Blocks in a storey, floor slab included.
     *
     * <p>Two is a crawlspace and is refused. Eight is the point past which the
     * rings stop reading as floors and start reading as a quarry.
     */
    public static final int MIN_STOREY = 3;
    public static final int MAX_STOREY = 8;

    /**
     * The default step: one bay in from every side, per level.
     *
     * <p>{@link TowerPlan#BAY} is already this mod's module - five is what
     * {@code ArchFeature} is built on, so a terrace exactly one bay deep is in
     * the same measure as the arches on the threads. Unlike the tower's, this
     * one <em>is</em> enforced arithmetically, because the envelope has to reach
     * the shaft exactly rather than approximately.
     */
    public static final int DEFAULT_INSET = TowerPlan.BAY;

    /**
     * The most blocks a well may come to.
     *
     * <p>Chosen the way {@link TowerPlan#MAX_CELLS} was: the number past which
     * this stops being a structure and becomes a chunk rewrite.
     *
     * <p><b>It had to be measured to be worth having.</b> The first number here
     * was 200,000, and it was useless: the other caps already bound the largest
     * possible well - mouth 65, shaft 3, inset 1, storey 8 - to <b>77,674</b>
     * blocks, so the ceiling could never fire. A cap that cannot bite is not a
     * safeguard, it is a comment that looks like one, and the test asking it to
     * refuse is the thing that found it. This number sits below that maximum on
     * purpose, so the largest wells are genuinely refused and the guard is real.
     */
    public static final int MAX_BLOCKS = 65_536;

    /**
     * The brightest the engine goes.
     *
     * <p>Used only by {@link #daylight()}, which is advisory. See the honesty
     * note on that method about what is and is not verified here.
     */
    public static final int SKY = 15;

    /**
     * One block to place, in the well's own frame.
     *
     * <p><b>Read the axes.</b> {@code x} and {@code z} are measured from the
     * centre of the shaft and are freely negative - the well is symmetrical
     * about its middle and there is no left column to count from. {@code y} is
     * zero at <em>grade</em> and goes <em>negative downward</em>.
     *
     * <p>That last one is deliberate and is the opposite of {@link TowerPlan},
     * where {@code y} is zero at the bottom and counts up. The reason: a well is
     * dug from a surface the caller already knows, and it does not know where
     * the apex will land until the geometry resolves. Anchoring at the apex
     * would make every caller compute the depth first in order to place the
     * origin, which is arithmetic that gets done wrong exactly once.
     */
    public record Block(int x, int y, int z, TowerPlan.Cell cell) {
    }

    /** Raised when measurements do not make a well. Refused whole, never in part. */
    public static final class Malformed extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        Malformed(String message) {
            super(message);
        }
    }

    private final int mouth;
    private final int shaft;
    private final int inset;
    private final int storey;
    private final int levels;

    private LightwellPlan(int mouth, int shaft, int inset, int storey, int levels) {
        this.mouth = mouth;
        this.shaft = shaft;
        this.inset = inset;
        this.storey = storey;
        this.levels = levels;
    }

    /** A well on the default step and a three-block storey. */
    public static LightwellPlan of(int mouth, int shaft) {
        return of(mouth, shaft, DEFAULT_INSET, MIN_STOREY);
    }

    /**
     * Resolves a set of measurements into a well.
     *
     * <p>Everything is checked before anything is built, and the block count is
     * derived arithmetically rather than by generating and counting - the whole
     * point of a cap is to refuse work before doing it.
     *
     * @param mouth  width at grade, odd, at most {@link #MAX_MOUTH}
     * @param shaft  width of the light shaft, odd, at least {@link #MIN_SHAFT},
     *               and constant all the way down
     * @param inset  how far each level steps in on every side
     * @param storey blocks from one floor to the next
     * @throws Malformed if a measurement is out of range, if a width is even, if
     *                   the envelope does not reach the shaft exactly, or if the
     *                   well would exceed {@link #MAX_BLOCKS}
     */
    public static LightwellPlan of(int mouth, int shaft, int inset, int storey) {
        if (mouth < MIN_SHAFT || mouth > MAX_MOUTH) {
            throw new Malformed("mouth is " + mouth + "; the range is " + MIN_SHAFT
                    + " to " + MAX_MOUTH);
        }
        if (shaft < MIN_SHAFT) {
            throw new Malformed("shaft is " + shaft + "; the floor is " + MIN_SHAFT
                    + ", which is a centre column and one block either side");
        }
        if (shaft > mouth) {
            throw new Malformed("shaft is " + shaft + " and the mouth is only " + mouth
                    + "; a well cannot be narrower than the light it carries");
        }
        // Odd on both, so the shaft has a centre column to stand on. An even
        // width has no middle, and a well built off-centre by half a block is
        // wrong in a way that is very hard to see once it is in the ground.
        if (mouth % 2 == 0) {
            throw new Malformed("mouth is " + mouth + ", which is even and so has no centre;"
                    + " try " + (mouth - 1) + " or " + (mouth + 1));
        }
        if (shaft % 2 == 0) {
            throw new Malformed("shaft is " + shaft + ", which is even and so has no centre;"
                    + " try " + (shaft - 1) + " or " + (shaft + 1));
        }
        if (inset < 1) {
            throw new Malformed("inset is " + inset + "; a level that steps in by nothing"
                    + " never reaches the shaft and the well has no bottom");
        }
        if (storey < MIN_STOREY || storey > MAX_STOREY) {
            throw new Malformed("storey is " + storey + "; the range is " + MIN_STOREY
                    + " to " + MAX_STOREY);
        }

        int mouthHalf = (mouth - 1) / 2;
        int shaftHalf = (shaft - 1) / 2;
        int span = mouthHalf - shaftHalf;

        // The envelope must land ON the shaft, not straddle it. A well whose
        // last step overshoots would have its lowest floor narrower than the
        // light it is built around, which is not a smaller floor - it is a
        // floor with the shaft cut through its outer wall.
        if (span % inset != 0) {
            int over = span % inset;
            throw new Malformed("a mouth of " + mouth + " with a shaft of " + shaft
                    + " leaves " + span + " to step down, which " + inset
                    + " does not divide; the nearest mouths that work are "
                    + (mouth - 2 * over) + " and " + (mouth + 2 * (inset - over)));
        }

        int levels = span / inset + 1;
        long projected = projectedBlocks(mouthHalf, shaftHalf, inset, storey, levels);
        if (projected > MAX_BLOCKS) {
            throw new Malformed("that well is " + projected + " blocks; the ceiling is "
                    + MAX_BLOCKS);
        }

        return new LightwellPlan(mouth, shaft, inset, storey, levels);
    }

    /**
     * How many blocks the well will come to, counted rather than generated.
     *
     * <p>This exists so {@link #of} can refuse an oversized well without first
     * building it. {@code LightwellPlanTest} asserts it agrees exactly with
     * {@link #blocks()} for a spread of measurements, because a cap that
     * disagrees with the thing it is capping is worse than no cap.
     */
    private static long projectedBlocks(int mouthHalf, int shaftHalf, int inset,
            int storey, int levels) {
        long total = 0;
        for (int i = 0; i < levels; i++) {
            int outer = mouthHalf - i * inset;
            boolean apex = (i == levels - 1);
            long side = 2L * outer + 1L;
            long hole = 2L * shaftHalf + 1L;

            // the floor: a ring everywhere but the apex, where the well bottoms out
            total += apex ? side * side : side * side - hole * hole;

            // the retaining wall, one storey less the floor slab it stands on.
            // Never at the apex - see blocks(), where the reason is written.
            if (outer > shaftHalf) {
                total += (8L * outer) * (storey - 1L);
            }

            // the kerb at the lip of the shaft. Only where the ring is at least
            // two deep: at exactly one deep the lip and the retaining wall are
            // the same ring of cells, and placing both writes every one of them
            // twice. See the note in blocks().
            if (!apex && outer > shaftHalf + 1) {
                total += 8L * (shaftHalf + 1L);
            }
        }
        // the anchorage at the bottom
        return total + 1;
    }

    /**
     * How many blocks this well comes to, without building it.
     *
     * <p>The same count {@link #of} refuses against, exposed because a caller
     * deciding whether to place a well wants it before committing to the list.
     * {@code LightwellPlanTest} asserts it agrees exactly with
     * {@link #blocks()}: two derivations of one number, and if they ever
     * disagree the generated blocks are the truth and this is the bug.
     */
    public long projectedBlocks() {
        return projectedBlocks((mouth - 1) / 2, (shaft - 1) / 2, inset, storey, levels);
    }

    /** Width at grade. */
    public int mouth() {
        return mouth;
    }

    /** Width of the shaft, the same at every level. */
    public int shaft() {
        return shaft;
    }

    /** How far each level steps in, on every side. */
    public int inset() {
        return inset;
    }

    /** Blocks from one floor to the next. */
    public int storey() {
        return storey;
    }

    /** How many floors the envelope resolved to, apex included. */
    public int levels() {
        return levels;
    }

    /** How far below grade the apex floor sits, as a positive number of blocks. */
    public int depth() {
        return levels * storey;
    }

    /** Half-width of the envelope at a level, zero at grade. */
    public int outerHalf(int level) {
        return (mouth - 1) / 2 - level * inset;
    }

    /**
     * How deep the walkable ring is at a level, in blocks.
     *
     * <p>Zero at the apex, which is the definition of the apex: the envelope has
     * arrived at the shaft and there is no ring left, only the floor of the well.
     */
    public int ringWidth(int level) {
        return outerHalf(level) - (shaft - 1) / 2;
    }

    /** The y of a level's floor slab. Negative: see {@link Block}. */
    public int floorY(int level) {
        return -(level + 1) * storey;
    }

    /**
     * Every block the well puts in the world.
     *
     * <p>Only what the well <em>adds</em> is listed. Nothing is emitted for the
     * volume it hollows out, for the same reason no ruin in this mod carves: the
     * caller writes what it is given, so a well dropped on ground that is
     * already hollow leaves the hollow alone. A caller that wants the earth
     * removed has to remove it, and that is deliberately its decision rather
     * than this one's.
     *
     * <p>Ordering is top level first, then floor, wall, kerb within a level.
     * That is the order it would be dug in, which is the order that looks right
     * if anything is ever watching it happen.
     */
    public List<Block> blocks() {
        int shaftHalf = (shaft - 1) / 2;
        List<Block> out = new ArrayList<>();

        for (int i = 0; i < levels; i++) {
            int outer = outerHalf(i);
            int y = floorY(i);
            boolean apex = (i == levels - 1);

            // 1. the floor. A ring around the shaft, except at the apex where
            //    the shaft finally has a bottom to land on.
            for (int x = -outer; x <= outer; x++) {
                for (int z = -outer; z <= outer; z++) {
                    boolean overShaft = Math.max(Math.abs(x), Math.abs(z)) <= shaftHalf;
                    if (overShaft && !apex) {
                        continue;
                    }
                    out.add(new Block(x, y, z, TowerPlan.Cell.FRAME));
                }
            }

            // 2. the retaining wall, holding the earth off this floor. It stops
            //    one short of the floor above, which lays its own slab.
            //
            //    NOT AT THE APEX, and this is the one place the geometry can
            //    quietly betray the whole design. At the apex the envelope has
            //    arrived at the shaft, so outer == shaftHalf, and a wall there
            //    would stand ON the shaft's own boundary ring - throttling the
            //    well at the bottom, which is precisely the windowless cellar
            //    this form exists to avoid. It is also unnecessary: below the
            //    lowest terrace nothing is hollowed out except the shaft
            //    itself, so the shaft is a bore through undisturbed earth and
            //    has nothing to retain. Caught by the shaft-is-clear test, not
            //    by reading.
            if (outer > shaftHalf) {
                for (int h = 1; h < storey; h++) {
                    for (int x = -outer; x <= outer; x++) {
                        for (int z = -outer; z <= outer; z++) {
                            if (Math.max(Math.abs(x), Math.abs(z)) != outer) {
                                continue;
                            }
                            out.add(new Block(x, y + h, z, TowerPlan.Cell.FRAME));
                        }
                    }
                }
            }

            // 3. the kerb at the lip, so a floor does not end in open air. One
            //    block, because anything taller is a wall and would shade the
            //    ring from the very light the shaft is carrying to it.
            //
            //    Skipped where the ring is only one deep, because there the lip
            //    and the retaining wall are the same ring of cells and placing
            //    both writes all of them twice. Found by arithmetic before it
            //    was found by the test: at inset five the case never arises, so
            //    the obvious well would have hidden it.
            if (!apex && outer > shaftHalf + 1) {
                int lip = shaftHalf + 1;
                for (int x = -lip; x <= lip; x++) {
                    for (int z = -lip; z <= lip; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != lip) {
                            continue;
                        }
                        out.add(new Block(x, y + 1, z, TowerPlan.Cell.FRAME));
                    }
                }
            }
        }

        // 4. the anchorage, at the bottom of the shaft. Exactly one, and at the
        //    apex rather than at grade: a well is entered from the top and
        //    arrived at from the bottom, and the bottom is the one place in the
        //    structure that every level can see.
        out.add(new Block(0, floorY(levels - 1) + 1, 0, TowerPlan.Cell.CORE));

        return Collections.unmodifiableList(out);
    }

    // ---- Daylight ------------------------------------------------------

    /** What is wrong with a well's light, and where. Advisory, never a refusal. */
    public record Finding(int level, int estimatedLight, String what) {
    }

    /**
     * How much sky light is likely to reach the outer wall of a level.
     *
     * <p>The shaft is open to the sky, so the column inside it is at
     * {@link #SKY}. Light spreads sideways under the floor above, losing a level
     * per block, so a ring {@code w} deep has roughly {@code SKY - w} at its
     * outer wall. That is the whole model, and it is why ring width is the only
     * thing that matters: a well can be as deep as it likes and stay bright, and
     * a shallow well with wide floors will be dark at the edges.
     */
    public int estimatedLightAtWall(int level) {
        return Math.max(0, SKY - ringWidth(level));
    }

    /**
     * Every level whose outer edge will not be lit, and why.
     *
     * <p><b>Advisory on purpose.</b> {@link #of} refuses measurements that are
     * not a well; this reports wells that are dim. Refusing a building over its
     * light would be this class ruling on the author's intent, and a dark lower
     * gallery may be exactly what somebody wants.
     *
     * <p><b>What is verified and what is not, stated rather than left to be
     * discovered.</b> The thresholds are {@link TowerPlan#GROWTH_LIGHT} and
     * {@link TowerPlan#SURVIVAL_LIGHT}, which were read out of the 1.21.1 jar
     * with {@code javap} and are cited on those fields. The falloff model here -
     * full strength straight down an open shaft, one level lost per block
     * sideways - is <b>NOT</b> verified against the jar. It is the documented
     * behaviour of the light engine and it matches every well built by hand, but
     * AGENTS.md rule V says to open the artifact when a version-dependent detail
     * matters, and nobody has opened {@code LightEngine} for this. Anyone who
     * needs it exact should look there before trusting a number.
     *
     * <p>Like {@link TowerPlan#agronomy()}, this is a necessary condition and not
     * a sufficient one. It measures a straight horizontal run and does not know
     * that light is a flood fill which opaque blocks stop dead, so the real light
     * at a wall is <em>never better</em> than this says and is usually worse. A
     * checker that quietly overpromises is worse than no checker.
     */
    public List<Finding> daylight() {
        List<Finding> found = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            if (ringWidth(i) == 0) {
                continue; // the apex has no ring to light
            }
            int lit = estimatedLightAtWall(i);
            if (lit == 0) {
                found.add(new Finding(i, lit, "the outer wall is beyond the reach of the shaft;"
                        + " this ring is " + ringWidth(i) + " deep and light carries " + SKY));
            } else if (lit < TowerPlan.SURVIVAL_LIGHT) {
                found.add(new Finding(i, lit, "under " + TowerPlan.SURVIVAL_LIGHT
                        + " at the outer wall; dim enough to be a cellar rather than a room"));
            } else if (lit < TowerPlan.GROWTH_LIGHT) {
                found.add(new Finding(i, lit, "lit, but under " + TowerPlan.GROWTH_LIGHT
                        + ", so nothing will grow against the outer wall"));
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** Whether every ring in the well is lit well enough to grow something. */
    public boolean fullyLit() {
        return daylight().isEmpty();
    }

    /**
     * The widest ring that still reaches {@link TowerPlan#GROWTH_LIGHT} at its
     * outer wall. A well drawn on this step is bright to its edges at every
     * level, however deep it goes.
     */
    public static int brightestRing() {
        return SKY - TowerPlan.GROWTH_LIGHT;
    }

    /** A section through the well, drawn the way a person would sketch it. */
    public List<String> section() {
        int shaftHalf = (shaft - 1) / 2;
        int mouthHalf = (mouth - 1) / 2;
        List<String> rows = new ArrayList<>(levels);
        for (int i = 0; i < levels; i++) {
            int outer = outerHalf(i);
            StringBuilder row = new StringBuilder();
            for (int x = -mouthHalf; x <= mouthHalf; x++) {
                int d = Math.abs(x);
                if (d > outer) {
                    row.append(' ');
                } else if (d <= shaftHalf) {
                    row.append(TowerPlan.Cell.EMPTY.glyph());
                } else {
                    row.append(TowerPlan.Cell.FRAME.glyph());
                }
            }
            rows.add(row.toString());
        }
        return Collections.unmodifiableList(rows);
    }

    @Override
    public String toString() {
        return "LightwellPlan[mouth=" + mouth + " shaft=" + shaft
                + " levels=" + levels + " depth=" + depth() + "]";
    }
}
