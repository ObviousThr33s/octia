package com.serenity.octia.gametest;

import com.serenity.octia.Octia;
import com.serenity.octia.entity.OctiaEntities;
import com.serenity.octia.entity.VoidSquid;
import com.serenity.octia.entity.VoidSquidDrift;
import com.serenity.octia.world.OctiaWorldgen;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * The void squid, asserted as decisions rather than as particles.
 *
 * <p><b>Two things this suite cannot do, said first so nobody writes a test that
 * only appears to do them.</b> A plot sits wherever the test runner put it and
 * the vertical band is absolute world Y, so <em>no position in a plot is ever in
 * the band</em>: the band's own arithmetic is {@code VoidSquidDriftTest}, and
 * what is checked here is the behaviour that follows from being outside it. And
 * because a plot is outside the band, {@code VoidSquid.spawnRules} answers false
 * in a plot whatever the world switch says, so only the switch's "off" direction
 * can be shown - {@link #theSwitchGatesTheSpawn} shows that one and claims
 * nothing more.
 *
 * <p><b>Corrected [2026-08-24]: the emphasised sentence above is false, and
 * {@link #itStaysInsideItsBand} failed on it.</b> A plot sits wherever the
 * runner put it, and on the run that caught this it was put at world Y -60 -
 * which places {@link #AIR} at Y -56, two blocks under
 * {@link VoidSquidDrift#BAND_FLOOR}. A squid there recovers upward at
 * {@link VoidSquidDrift#RECOVER}, reaches the floor in forty ticks, and spends
 * the rest of a {@link #WATCH} inside the band bobbing. So a plot's airspace
 * can reach the band, and no test here may assume which side of the band a
 * squid is on for the whole of its watch - only which side it is on this tick.
 * The wrong text is kept because corrections are new entries. What survives of
 * it is the part that was never about altitude: the band's own arithmetic still
 * belongs to {@code VoidSquidDriftTest}, which walks the whole band in
 * milliseconds, where this suite reaches at most the two blocks over the floor
 * that a recovery happens to cross.
 *
 * <p><b>No floor, except where a floor is the thing under test.</b> The plot
 * stays at its minimum for the drift tests, the {@code SailRigGameTest}
 * discipline - a generous floor writes into the next test. The pool tests build
 * exactly one sealed one-deep bowl, three blocks across, and nothing else.
 *
 * <p><b>Nothing here hard-codes a heading.</b> A squid's wander comes from its
 * own UUID, which is different on every run, so every assertion below has to
 * hold for every direction it could have chosen. That is why the drift tests
 * assert bounds and refusals rather than positions.
 *
 * <p><b>Failures are raised with {@link GameTestHelper#fail(String)} and not by
 * throwing an {@code AssertionError}, and that is a correction rather than a
 * style preference.</b> {@code GameTestInfo.tickInternal} and
 * {@code GameTestInfo.startTest} both wrap the test in {@code catch (Exception)},
 * so an {@code Error} escapes the framework instead of failing the test:
 * verified in the 1.21.1 bytecode, where both exception tables name
 * {@code java/lang/Exception} and nothing wider. {@code helper.fail} throws
 * {@code GameTestAssertException}, which is a {@code RuntimeException} and is
 * therefore caught and reported. Several older suites in this tree throw
 * {@code AssertionError} from inside a {@code runAfterDelay} callback; that path
 * has never fired, because a green suite never runs its own failure branch. It
 * is recorded here and left where it is - corrections are new entries, and those
 * files belong to other lanes.
 */
public class VoidSquidGameTest implements FabricGameTest {

    /** Somewhere in the plot's own airspace, clear of the plot's bedrock floor. */
    private static final BlockPos AIR = new BlockPos(3, 4, 3);

    /** How long the drift is watched. Sixty ticks is three seconds of it. */
    private static final int WATCH = 60;

    /** The middle of the test pool, at the water's own level. */
    private static final BlockPos MIRROR = new BlockPos(3, 1, 3);

    /** Where a squid dropped into the seal starts, and where one over it starts. */
    private static final BlockPos INSIDE_SEAL = new BlockPos(3, 3, 3);
    private static final BlockPos OVER_POOL = new BlockPos(3, 5, 3);

    /**
     * Runs a sampler on each of the next {@code ticks} ticks, then succeeds.
     *
     * <p>Every callback is scheduled up front and each one is a distinct object,
     * and both halves of that matter. {@code GameTestInfo} keys its pending work
     * on the {@code Runnable} itself - {@code Object2LongMap<Runnable>} - so two
     * scheduled samplers that were the same object would collide and only the
     * last tick would ever be sampled. And they are all filed before the first
     * one runs, because the framework walks that map with an iterator and
     * removes as it goes: scheduling from inside a callback would be inserting
     * into a fastutil map mid-iteration.
     *
     * <p>An explicit {@code new Runnable} rather than a lambda for exactly that
     * first reason. A fresh object per loop is guaranteed by the language here;
     * for a lambda it is only guaranteed by the implementation.
     */
    private static void watch(GameTestHelper helper, int ticks, Runnable sample) {
        for (int tick = 1; tick <= ticks; tick++) {
            helper.runAfterDelay(tick, new Runnable() {
                @Override
                public void run() {
                    sample.run();
                }
            });
        }
        helper.runAfterDelay(ticks + 1, helper::succeed);
    }

    /**
     * A sealed one-deep bowl, built in the order {@code WatershedFeature.Plan}
     * writes in: all the solid first, then the water.
     *
     * <p>That order is not decoration. {@code RuinGround.put}'s javadoc sets out
     * why - a write into a live level runs block logic whatever flag it carries -
     * so a source placed before its rim exists is a source that has somewhere to
     * go for one tick. Building it the other way round would make this test's own
     * fixture leak, and the leak would look like the mechanic failing.
     *
     * <p>The result is the shape {@code WatershedGameTest.everyPoolIsSealed}
     * proves the real feature makes: one water cell, solid under it, solid on all
     * four sides at its own level, and two blocks of air over it. The liner is
     * three across rather than one, which is also true of the real bowl and is
     * what closes the corners - see {@code VoidSquid.steer} on the diagonal.
     */
    private static void pool(GameTestHelper helper) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(MIRROR.offset(dx, -1, dz), Blocks.ANDESITE);
                if (dx != 0 || dz != 0) {
                    helper.setBlock(MIRROR.offset(dx, 0, dz), Blocks.POLISHED_ANDESITE);
                }
            }
        }
        helper.setBlock(MIRROR, Blocks.WATER);
    }

    /** The three cells a squid must never be in: the mirror and its two air blocks. */
    private static boolean sealed(GameTestHelper helper, BlockPos absolute) {
        for (int dy = 0; dy <= 2; dy++) {
            if (helper.absolutePos(MIRROR.above(dy)).equals(absolute)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The registered type is ours, under the path this mod names, in its
     * category, and one can be put in a world.
     *
     * <p>The registry is asked by path rather than the constant being asked for
     * its path, which is the direction that catches something else claiming the
     * name - the {@code SailRigGameTest.theSailRigIsRegistered} discipline
     * applied to the mod's first entity type.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theVoidSquidIsRegistered(GameTestHelper helper) {
        EntityType<?> byPath = BuiltInRegistries.ENTITY_TYPE
                .get(Octia.id(OctiaEntities.VOID_SQUID_PATH));
        if (byPath != OctiaEntities.VOID_SQUID) {
            helper.fail("octia:void_squid is registered to something else");
        }
        if (OctiaEntities.VOID_SQUID.getCategory() != MobCategory.AMBIENT) {
            helper.fail("the void squid left MobCategory.AMBIENT, which is its spawn cap - see "
                    + "the note in OctiaEntities on why the water categories are permanently "
                    + "full on this terrain");
        }

        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, AIR);
        if (squid.getType() != OctiaEntities.VOID_SQUID) {
            helper.fail("a spawned squid reports a different type");
        }
        helper.assertEntityPresent(OctiaEntities.VOID_SQUID, AIR, 2.0);
        helper.succeed();
    }

    /**
     * The ink table exists at the path the game derives, and it loaded.
     *
     * <p>{@code LootGameTest}'s standing complaint, applied to an animal: a
     * missing table does not crash, it resolves to {@code LootTable.EMPTY}, and
     * the only symptom is a squid that gives up nothing. The key is read off the
     * entity type rather than written out here, so this also proves the shipped
     * file is at the path {@code EntityType} actually asks for.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theInkTableLoaded(GameTestHelper helper) {
        LootTable table = helper.getLevel().getServer().reloadableRegistries()
                .getLootTable(OctiaEntities.VOID_SQUID.getDefaultLootTable());
        if (table == LootTable.EMPTY) {
            helper.fail("the void squid's loot table did not load. A malformed or misplaced "
                    + "table resolves to EMPTY and drops nothing, silently - check "
                    + "data/octia/loot_table/entities/void_squid.json");
        }
        helper.succeed();
    }

    /**
     * It ticks in open air with no fluid and is still there afterwards.
     *
     * <p>The plainest thing that can be asked of the mod's first creature, and
     * it is not trivial: a mob with no gravity, no goals and no navigation in use
     * is a shape nothing else in this tree has, and every one of those absences
     * is a way for a tick to throw.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void itDriftsInOpenAirAndSurvives(GameTestHelper helper) {
        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, AIR);
        ServerLevel level = helper.getLevel();

        watch(helper, WATCH, () -> {
            if (squid.isRemoved() || !squid.isAlive()) {
                helper.fail("the squid did not survive open air");
            }
            if (!level.getFluidState(squid.blockPosition()).isEmpty()) {
                helper.fail("the squid ended a tick in fluid at " + squid.blockPosition());
            }
        });
    }

    /**
     * It never acquires a target, with a player standing in it.
     *
     * <p>Two assertions and they are different claims. The target getter staying
     * null says no target was acquired on this run; the goal count staying zero
     * says there is nothing in the class that could ever acquire one. The second
     * is the one that survives somebody adding a goal in a hurry.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void itNeverHuntsAnything(GameTestHelper helper) {
        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, AIR);

        Player bait = helper.makeMockPlayer(GameType.SURVIVAL);
        bait.moveTo(helper.absoluteVec(new Vec3(3.5, 4.0, 3.5)));

        watch(helper, WATCH, () -> {
            if (squid.getTarget() != null) {
                helper.fail("the squid took a target: " + squid.getTarget());
            }
            if (squid.goalsRegistered() != 0) {
                helper.fail("the squid has " + squid.goalsRegistered() + " goals. It is supposed "
                        + "to have none - it does not hunt, follow, flee or look, and "
                        + "registerGoals is where that is enforced");
            }
        });
    }

    /**
     * It never leaves the band, whichever side of the band it is on this tick.
     *
     * <p>The squid's own altitude is read every tick and the assertion is chosen
     * from it - which is what makes this test hold wherever the runner puts a
     * plot, and hold through a crossing rather than only on one side of one.
     * These are {@code VoidSquidDrift.rise}'s three clauses, asked of a real
     * entity in the same order it asks them. Above the ceiling the only legal
     * behaviour is to sink and never rise, and that is the charter's clause
     * verbatim: it never climbs above the band's ceiling. Below the floor, the
     * mirror of it. Inside, it stays inside.
     *
     * <p>The altitude a tick is judged by is the one the tick was decided from.
     * {@code VoidSquid.steer} reads {@code getY()} at the top of an
     * {@code aiStep}, which is the position the previous move left; the sampler
     * runs at one fixed point in the tick, so consecutive samples are
     * consecutive positions and {@code was} is exactly the number {@code rise}
     * was handed to produce {@code now}. That holds whether the sampler runs
     * before or after the entity ticks, which is not something this suite gets
     * to choose.
     *
     * <p>"Never rises" is stronger than "ends up lower", and it is the one worth
     * pinning: a squid that oscillated its way down would satisfy the weaker
     * form while still climbing on half its ticks. It is also why the refusal in
     * {@code VoidSquidDrift.refuse} zeroes a component instead of pushing back -
     * a squid stopped by the plot's own bedrock stops sinking, and stopping is
     * not rising.
     *
     * <p><b>Corrected [2026-08-24]. The clause used to be picked once, from
     * {@code helper.absolutePos(AIR).getY()}, and held for the whole watch.</b>
     * That is the bug the run at plot Y -60 found, and it was a bug in this test
     * and not in the animal. {@link #AIR} was then Y -56, two blocks under the
     * floor, so the squid rose at {@link VoidSquidDrift#RECOVER} for forty ticks,
     * arrived at -53.95 - which is {@code BAND_FLOOR + RECOVER}, one step past
     * the floor and inside the band - and bobbed. The first downward half of that
     * bob was reported as "a squid below the band's floor sank, from
     * -53.95000000000012 to -53.96732050807581". Both of those altitudes are
     * inside the band, and the step between them is 0.01732050807569, which is
     * {@link VoidSquidDrift#BOB} times sin at the 240-tick phase: the bob,
     * exactly, and the behaviour {@code rise} is written to produce. The old
     * assertion went on applying the below-the-floor clause to a squid that was
     * no longer below the floor. Judging each tick by the squid's own altitude
     * fixes that without loosening anything - it is the stronger claim, because
     * it now covers the crossing tick as well as the ticks either side of it,
     * and it is why the unit harness stayed green while the server went red:
     * {@code VoidSquidDriftTest} always asked {@code rise} about the y it was
     * about to move from.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void itStaysInsideItsBand(GameTestHelper helper) {
        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, AIR);
        double[] last = {squid.getY()};

        watch(helper, WATCH, () -> {
            double was = last[0];
            double now = squid.getY();
            if (was > VoidSquidDrift.BAND_CEILING) {
                if (now > was + 1.0E-9) {
                    helper.fail("a squid above the band's ceiling climbed, from "
                            + was + " to " + now);
                }
            } else if (was < VoidSquidDrift.BAND_FLOOR) {
                if (now < was - 1.0E-9) {
                    helper.fail("a squid below the band's floor sank, from "
                            + was + " to " + now);
                }
            } else if (now > VoidSquidDrift.BAND_CEILING + 1.0E-6
                    || now < VoidSquidDrift.BAND_FLOOR - 1.0E-6) {
                // The slack is rounding, not leniency: the clamp aims at an edge
                // exactly and the physics adds the delta in floating point. The
                // band is stated in whole blocks.
                helper.fail("a squid inside the band left it at " + now);
            }
            last[0] = now;
        });
    }

    /**
     * Killed, it gives up ink.
     *
     * <p>The one thing this animal is for, in the {@code docs/PALETTE.md} sense:
     * the ramp reserves one colour for writing and names the squid as where
     * writing comes from, and this is that claim made real.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void itDropsInkOnDeath(GameTestHelper helper) {
        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, AIR);
        squid.kill();

        helper.runAfterDelay(2, () -> {
            helper.assertItemEntityPresent(Items.INK_SAC, AIR, 4.0);
            helper.succeed();
        });
    }

    /**
     * A sealed pool's own cells are cells no squid may occupy.
     *
     * <p>Asked of the rule rather than of an animal, because this is the claim
     * that has to hold before any drift is considered: the water cell and both
     * blocks of its forced air answer no, and the cell one above the seal answers
     * yes. That last one is the design and not a leak - the order was that it is
     * <i>seen from the mirror, never in it</i>, so the squid has to be able to
     * hold station just over the water and nowhere lower.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aSealedPoolIsClosedToSquid(GameTestHelper helper) {
        pool(helper);
        ServerLevel level = helper.getLevel();

        for (int dy = 0; dy <= 2; dy++) {
            if (VoidSquid.opensTo(level, helper.absolutePos(MIRROR.above(dy)))) {
                helper.fail("a sealed pool cell is open to a squid, " + dy + " over the mirror. "
                        + "The one-deep seal is water with two blocks of forced air over it, and "
                        + "none of the three is a place for this animal");
            }
        }
        if (!VoidSquid.opensTo(level, helper.absolutePos(MIRROR.above(3)))) {
            helper.fail("the block over a sealed pool's seal is closed to a squid. It has to be "
                    + "open, or nothing can ever be seen from the mirror");
        }
        helper.succeed();
    }

    /**
     * A squid drifting over a pool never gets into it.
     *
     * <p>It starts in open air over the seal and is left to drift for
     * {@link #WATCH} ticks with nothing steering it. The plot is above the band
     * so the drift is pure descent, which is the hardest case there is: this is
     * a squid actively trying to go down into the pool, refused every tick by
     * the cell below it not being one it may occupy.
     *
     * <p><b>Corrected [2026-08-24]: the paragraph above assumes a plot above the
     * band, and that is the same wrong premise that broke
     * {@link #itStaysInsideItsBand}.</b> On a plot at world Y -60,
     * {@link #OVER_POOL} is Y -55 - below {@link VoidSquidDrift#BAND_FLOOR}, not
     * above the ceiling - so the squid rises away from the pool and this test
     * passes without ever putting the refusal under load. The assertion is right
     * either way and is left exactly as it is; what is not true is the sentence
     * claiming this is always the hardest case. Which case it is depends on where
     * the runner put the plot, and on the run recorded here it was the easy one.
     * The descent case is held meanwhile by {@code VoidSquid.steer}'s geometry
     * argument and by {@link #noSquidStaysInASealedPool}, which does not care
     * which way a squid was drifting when it got in.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void noSquidDriftsIntoASealedPool(GameTestHelper helper) {
        pool(helper);
        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, OVER_POOL);

        watch(helper, WATCH, () -> {
            if (sealed(helper, squid.blockPosition())) {
                helper.fail("a squid got inside a sealed pool at " + squid.blockPosition());
            }
        });
    }

    /**
     * A squid put inside a sealed pool leaves, and leaves nothing behind.
     *
     * <p>Placement is the case the drift cannot cover - a command, a player
     * walling one in, a chunk that came back different - and the answer is that
     * it withdraws. Two things are asserted about the withdrawal and the second
     * matters as much as the first: it happens even though
     * {@code GameTestHelper.spawn} marked this squid persistent, because the rule
     * about where a squid may be has no exemption in it; and no ink is left,
     * because a creature you can farm by boxing in is a creature that stops
     * being found in the void.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void noSquidStaysInASealedPool(GameTestHelper helper) {
        pool(helper);
        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, INSIDE_SEAL);

        int grace = VoidSquidDrift.WITHDRAW_TICKS + 10;
        for (int tick = 1; tick <= grace; tick++) {
            helper.runAfterDelay(tick, new Runnable() {
                @Override
                public void run() {
                    if (helper.absolutePos(MIRROR).equals(squid.blockPosition())) {
                        helper.fail("a squid moved into the mirror itself");
                    }
                }
            });
        }
        helper.runAfterDelay(grace + 1, () -> {
            if (!squid.isRemoved()) {
                helper.fail("a squid sealed in a pool was still there after " + grace
                        + " ticks; it withdraws after " + VoidSquidDrift.WITHDRAW_TICKS);
            }
            helper.assertItemEntityNotPresent(Items.INK_SAC, MIRROR, 6.0);
            helper.succeed();
        });
    }

    /** Nothing rides one, and nothing leads one. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void itCarriesNobody(GameTestHelper helper) {
        VoidSquid squid = helper.spawn(OctiaEntities.VOID_SQUID, AIR);

        Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
        rider.moveTo(helper.absoluteVec(new Vec3(3.5, 4.0, 3.5)));

        if (rider.startRiding(squid)) {
            helper.fail("a player mounted a void squid");
        }
        if (squid.isVehicle() || !squid.getPassengers().isEmpty() || rider.isPassenger()) {
            helper.fail("a void squid took a passenger");
        }
        if (squid.canBeLeashed()) {
            helper.fail("a void squid can be leashed, so it can be walked home");
        }
        if (squid.isPushable()) {
            helper.fail("a void squid can be shoved, so it can be herded");
        }
        helper.succeed();
    }

    /**
     * The world switch reaches the spawn.
     *
     * <p>Only the "off" direction is provable here, and the class note says why:
     * a plot is never in the band, so the "on" answer in a plot is false for a
     * reason that has nothing to do with the switch. Off means no, and that is
     * what a save with Octia disabled is promised - left as vanilla found it.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSwitchGatesTheSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctiaWorldgen.setActive(false);

        boolean allowed = VoidSquid.spawnRules(OctiaEntities.VOID_SQUID, level,
                MobSpawnType.NATURAL, helper.absolutePos(AIR), level.getRandom());

        // Put it back before anything else runs - this flag is global.
        OctiaWorldgen.setActive(true);

        if (allowed) {
            helper.fail("a void squid was allowed to spawn in a world with Octia switched off");
        }
        helper.succeed();
    }

    /**
     * Both halves of the one rule, switched on and off again in isolation.
     *
     * <p>{@link VoidSquid#opensTo} is not "no water", it is a clear column with
     * no fluid touching it, and the seal tests above would pass against a rule
     * that only ever looked down. So a fluid to the side is shown to close a cell
     * whose own column is perfectly clear, and solid two below is shown to close
     * a cell that is not touching it. The water is taken away in the same tick it
     * was placed, so the plot is handed on as it was found - a stray source in a
     * floorless plot would pour into whatever runs next.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theClearanceRuleIsWalked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos over = helper.absolutePos(new BlockPos(3, 2, 3));

        if (!VoidSquid.opensTo(level, over)) {
            helper.fail("open plot air is closed to a squid, so nothing else here can be told "
                    + "apart");
        }

        helper.setBlock(new BlockPos(4, 2, 3), Blocks.WATER);
        if (VoidSquid.opensTo(level, over)) {
            helper.fail("a squid may sit beside open water; the rule is only looking up and down");
        }
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
        if (!VoidSquid.opensTo(level, over)) {
            helper.fail("the cell did not re-open once the water was taken away");
        }

        helper.setBlock(new BlockPos(3, 0, 3), Blocks.ANDESITE);
        if (VoidSquid.opensTo(level, over)) {
            helper.fail("a squid may stand two blocks over solid ground; the clearance is not "
                    + "being applied downward");
        }
        helper.succeed();
    }
}
