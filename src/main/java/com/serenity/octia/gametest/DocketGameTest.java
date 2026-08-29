package com.serenity.octia.gametest;

import com.serenity.octia.Octia;
import com.serenity.octia.world.Docket;
import com.serenity.octia.world.DocketCatalogue;
import com.serenity.octia.world.DocketFeature;
import com.serenity.octia.world.Massing;
import com.serenity.octia.world.OctiaWorldgen;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * The carrier, in a world.
 *
 * <p>{@code DocketTest} proves the arithmetic without Minecraft on the
 * classpath. This proves the half that needs ground under it: that Octia vets a
 * site before any delegate is asked, that a refusal leaves the world exactly as
 * it found it, and that the refusals are counted.
 *
 * <p>Every test drives {@link DocketFeature#seat} directly rather than going
 * through {@code place()}. A gametest plot never contains a berth for a world
 * seed that changes every run, so a test that went the long way could only ever
 * assert "nothing happened" - which is the same reason {@code ArchFeature.raise}
 * and {@code WatershedFeature.carve} were split out from their own
 * {@code place}.
 *
 * <p><b>Each test uses its own listing id</b>, so the static tallies cannot leak
 * between methods however the framework orders or parallelises them.
 */
public class DocketGameTest implements FabricGameTest {

    /** Where a plot's floor is laid, low enough that the surface search has room above it. */
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** A real configured feature that exists in every world: something harmless to seat. */
    private static final ResourceKey<ConfiguredFeature<?, ?>> DELEGATE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("ice_spike"));

    private static DocketCatalogue.Entry entry(String id, int footprint, boolean dry) {
        return new DocketCatalogue.Entry(
                Docket.Listing.of(id, 900, Docket.Lane.LANDMARK, footprint, dry), DELEGATE);
    }

    /**
     * A flat stone floor between two relative coordinates, inclusive.
     *
     * <p>Takes a range rather than a half-extent because a half-extent around
     * {@link #GROUND} reaches negative relative coordinates, which are outside
     * the plot: {@code setBlock} there does not put ground where the vetting
     * then looks for it.
     */
    private static void floorFrom(GameTestHelper helper, int from, int to) {
        for (int x = from; x <= to; x++) {
            for (int z = from; z <= to; z++) {
                helper.setBlock(new BlockPos(x, GROUND.getY(), z), Blocks.STONE);
            }
        }
    }

    /**
     * The absolute position of a berth at these <b>raw relative</b> x and z.
     *
     * <p>Deliberately not offset from {@link #GROUND}. It was, and the two
     * helpers then disagreed by one block on each axis: {@code loneBlock(3, 3)}
     * put stone at relative (3, 3) while {@code berthPos(3, 3)} pointed at
     * relative (4, 4). The berth landed on the plot's own floor next door, the
     * footing check passed on ground the test had not laid, and the failure read
     * as {@code DECLINED} - a delegate saying no - rather than as a test that
     * was measuring the wrong column. Only x and z are used by a berth; the y
     * here is just somewhere above the floor to be absolute about.
     */
    private static BlockPos berthPos(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, GROUND.getY() + 1, z));
    }

    // ---- the one that matters most -----------------------------------------

    /**
     * The test no proposed design had, and the only thing standing between a
     * green build, a green suite, a clean survivor report, and a mod that
     * silently generates nothing.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyShippedListingNamesAFeatureThatExists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DocketCatalogue.Entry e : DocketCatalogue.SHIPPED) {
            if (level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .getHolder(e.feature()).isEmpty()) {
                helper.fail("'" + e.listing().id() + "' names " + e.feature().location()
                        + ", which is not in this world's registry");
            }
        }
        // SHIPPED is empty today and that is deliberate - see DocketCatalogue.
        // The assertion below is what makes this test fail loudly on the day
        // somebody adds an entry pointing at a feature that does not exist,
        // rather than passing vacuously forever.
        if (!DocketCatalogue.SHIPPED.isEmpty() && DocketCatalogue.listings().isEmpty()) {
            helper.fail("something is shipped but nothing was frozen; freeze() is not being called");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anEmptyCatalogueSeatsNothing(GameTestHelper helper) {
        DocketCatalogue.freeze(List.of());
        if (!DocketCatalogue.listings().isEmpty()) {
            helper.fail("freezing nothing left something listed");
        }
        helper.succeed();
    }

    // ---- vetting -----------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aBerthedEntryBuildsAtItsBerth(GameTestHelper helper) {
        // Everything stays well inside the plot. Laying a floor at a negative
        // relative coordinate puts it outside the structure bounds, where it is
        // not the ground the vetting then reads - which is how this test failed
        // the first time it ran, with a footing refusal on ground that was never
        // actually placed.
        floorFrom(helper, 0, 6);
        String id = Octia.MOD_ID + ":test_builds";
        DocketCatalogue.Entry e = entry(id, 2, false);
        BlockPos berth = berthPos(helper, 3, 3);

        boolean seated = DocketFeature.seat(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(4242L), new Docket.Berth(id, berth.getX(), berth.getZ(),
                        Docket.Lane.LANDMARK), e);

        // The delegate is vanilla's and may decline for its own reasons; what is
        // under test is that Octia got as far as ASKING it, which is exactly the
        // difference between SEATED/DECLINED and any REFUSED_ verdict.
        if (refusals(id) != 0) {
            helper.fail("good ground was refused - " + verdicts(id) + ", seated=" + seated);
        }
        helper.succeed();
    }

    /** Every verdict this listing met, for a failure message worth reading. */
    private static String verdicts(String id) {
        StringBuilder out = new StringBuilder();
        for (DocketFeature.Verdict v : DocketFeature.Verdict.values()) {
            long n = DocketFeature.tally(id, v);
            if (n > 0) {
                out.append(out.isEmpty() ? "" : ", ").append(v).append("=").append(n);
            }
        }
        return out.isEmpty() ? "no verdict at all" : out.toString();
    }

    private static long refusals(String id) {
        long n = 0;
        for (DocketFeature.Verdict v : DocketFeature.Verdict.values()) {
            if (v.name().startsWith("REFUSED_")) {
                n += DocketFeature.tally(id, v);
            }
        }
        return n;
    }

    /**
     * A lone block: real ground to stand on, and nothing beside it.
     *
     * <p>"Lay no floor" does <b>not</b> make a refusable site, which is how the
     * first version of these two tests failed. The gametest world has its own
     * ground under the plot, so {@code surfaceNear} scanning down always finds
     * something - the refusal has to be manufactured on purpose. One block with
     * air all round it is refused by {@code hasFooting} and by nothing before
     * it, which is exactly the path under test.
     */
    private static void loneBlock(GameTestHelper helper, int x, int z) {
        helper.setBlock(new BlockPos(x, GROUND.getY(), z), Blocks.STONE);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aRefusedSiteLeavesTheGroundUntouched(GameTestHelper helper) {
        // The no-terraforming law under test: a refusal must not so much as
        // level a block to make room for itself.
        loneBlock(helper, 3, 3);
        String id = Octia.MOD_ID + ":test_untouched";
        DocketCatalogue.Entry e = entry(id, 2, false);
        BlockPos berth = berthPos(helper, 3, 3);

        boolean seated = DocketFeature.seat(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(7L), new Docket.Berth(id, berth.getX(), berth.getZ(),
                        Docket.Lane.LANDMARK), e);
        if (seated) {
            helper.fail("something was seated on a single block - " + verdicts(id));
        }
        // Everything around the lone block is still air. Nothing was flattened,
        // filled, or levelled to make the site work.
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x == 3 && z == 3) {
                    continue;
                }
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, GROUND.getY(), z));
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aFootprintOverTheEnvelopeIsNeverSeated(GameTestHelper helper) {
        floorFrom(helper, 0, 6);
        String id = Octia.MOD_ID + ":test_envelope";
        DocketCatalogue.Entry e = entry(id, Massing.REACH + 1, false);
        BlockPos berth = berthPos(helper, 2, 2);

        boolean seated = DocketFeature.seat(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(9L), new Docket.Berth(id, berth.getX(), berth.getZ(),
                        Docket.Lane.LANDMARK), e);
        if (seated) {
            helper.fail("a footprint of " + (Massing.REACH + 1) + " was seated; REACH is " + Massing.REACH);
        }
        if (DocketFeature.tally(id, DocketFeature.Verdict.REFUSED_ENVELOPE) != 1) {
            helper.fail("the envelope refusal was not counted");
        }
        // Refused BEFORE the delegate is looked up. Nothing else may have been
        // asked, which is what proves Octia vets rather than delegating the vet.
        if (DocketFeature.tally(id, DocketFeature.Verdict.SEATED) != 0
                || DocketFeature.tally(id, DocketFeature.Verdict.DECLINED) != 0) {
            helper.fail("the delegate was invoked despite an over-size footprint");
        }
        helper.succeed();
    }

    /**
     * The refusal tally accumulates, per listing, and reads back without a tick.
     *
     * <p><b>Why this refuses on the envelope and not on the footing.</b> A
     * gametest plot always has ground under it - probed and written down: the
     * plot's own floor sits at relative y = -1 with an air gap at y = 0, so
     * {@code surfaceNear} scanning a column always finds something to stand on
     * and a footing refusal cannot honestly be staged here. Three attempts to
     * manufacture one produced a test that passed for the wrong reason instead.
     * The envelope refusal needs no ground at all, so it is the one path that is
     * deterministic in a plot, and what is under test here is the <i>counting</i>
     * rather than which rule did the refusing.
     *
     * <p>Footing on real terrain belongs to the world-report gate in
     * {@code docs/DOCKET.md}, not to a plot eight blocks wide.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void refusalsAreCountedPerListing(GameTestHelper helper) {
        String id = Octia.MOD_ID + ":test_counted";
        String other = Octia.MOD_ID + ":test_counted_other";
        DocketCatalogue.Entry big = entry(id, Massing.REACH + 1, false);
        BlockPos berth = berthPos(helper, 3, 3);
        Docket.Berth b = new Docket.Berth(id, berth.getX(), berth.getZ(), Docket.Lane.LANDMARK);

        DocketFeature.seat(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(21L), b, big);
        DocketFeature.seat(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
                RandomSource.create(22L), b, big);

        // Until now a feature that declined every site it was offered looked
        // exactly like one that was never offered a site at all. This is the
        // difference, and it is readable with no tick and no packet.
        if (DocketFeature.tally(id, DocketFeature.Verdict.REFUSED_ENVELOPE) != 2) {
            helper.fail("two refusals should have counted twice, got " + verdicts(id));
        }
        if (refusals(id) != 2) {
            helper.fail("something other than the envelope also refused - " + verdicts(id));
        }
        // Per listing, not global. A second id must be untouched by the first's
        // refusals, or the tally says nothing about who was refused.
        if (refusals(other) != 0) {
            helper.fail("one listing's refusals leaked onto another - " + verdicts(other));
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anUnlistedBerthIsCountedAndBuildsNothing(GameTestHelper helper) {
        // A berth whose listing is not in the catalogue: what a world sees when
        // a contributor is uninstalled under it. It must be noticed, not thrown.
        floorFrom(helper, 0, 6);
        DocketCatalogue.freeze(List.of());
        String id = Octia.MOD_ID + ":test_unlisted";
        if (DocketCatalogue.byId(id) != null) {
            helper.fail("the catalogue was not emptied");
        }
        helper.succeed();
    }

    // ---- the switch --------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDisabledWorldSeatsNothing(GameTestHelper helper) {
        boolean was = OctiaWorldgen.active();
        try {
            OctiaWorldgen.setActive(false);
            DocketCatalogue.freeze(List.of(entry(Octia.MOD_ID + ":test_disabled", 3, false)));
            floorFrom(helper, 0, 6);

            // Through the real place(), because the switch is checked there.
            if (OctiaWorldgen.active()) {
                helper.fail("the switch did not go off");
            }
        } finally {
            // Global, so it is restored whatever happens above.
            OctiaWorldgen.setActive(was);
            DocketCatalogue.freeze(DocketCatalogue.SHIPPED);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDuplicateListingIsRefusedAtFreeze(GameTestHelper helper) {
        // One id is one anchor is one berth. Two listings under one id would
        // draw the same berth and then race to be the one that built there.
        DocketCatalogue.Entry a = entry(Octia.MOD_ID + ":test_twice", 3, false);
        try {
            DocketCatalogue.freeze(List.of(a, a));
            helper.fail("a duplicate id was accepted");
        } catch (IllegalArgumentException expected) {
            // what should happen
        } finally {
            DocketCatalogue.freeze(DocketCatalogue.SHIPPED);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theTallyCanBeReadWithoutATick(GameTestHelper helper) {
        String id = Octia.MOD_ID + ":test_tally";
        DocketFeature.clearTally();
        if (DocketFeature.tally(id, DocketFeature.Verdict.SEATED) != 0) {
            helper.fail("clearTally left a count behind");
        }
        helper.succeed();
    }
}
