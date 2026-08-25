package com.serenity.octia.entity;

import com.serenity.octia.world.OctiaWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.material.Fluids;

/**
 * A void squid: the thing that is already down there.
 *
 * <p><b>Why the mod's first creature is not a threat.</b> The ruins are always
 * empty - {@code HabitationGameTest.nobodyIsEverHome} is the law and it is not
 * being repealed here. Nothing this class does invents an inhabitant: a squid
 * has no home, builds nothing, guards nothing and was not left by anybody. It is
 * fauna in a place that has no ground, which is the one kind of life that can
 * exist in this world without contradicting what the ruins say about it.
 *
 * <p><b>It is found, never met.</b> The order was <i>seek them out rather then
 * they find you</i>, and every absence below is that sentence: no goal, no
 * target selector, no path navigation in use, no attack, no follow, no flee, no
 * sound. A squid does not know a player exists. You go to the underside of your
 * island, look over the lip, and either one is down there or one is not. That
 * asymmetry is the whole encounter, and it is enforced by {@link #registerGoals}
 * being empty rather than by anything being tuned to zero.
 *
 * <p><b>Ink is the point.</b> {@code docs/PALETTE.md} reserves one colour of the
 * eleven - {@code i}, ink - and says why: <i>ink means writing, and writing has
 * a source in this world - the squid.</i> {@code OctiaLoot} already spends ink
 * as the thing an old barrel keeps when the food has gone. Until now that source
 * was a claim in a comment. It drops {@code minecraft:ink_sac} through
 * {@code data/octia/loot_table/entities/void_squid.json}, and that table is what
 * closes the loop between the palette, the loot and the animal.
 *
 * <p><b>One rule decides where a squid may be, and it is {@link #opensTo}.</b>
 * Five blocks of air with no fluid in the column, and no fluid touching it
 * sideways. Everything else falls out of that single sentence: it is never in a
 * cave, never on the ground, never in water, never inside the two-block air seal
 * over a watershed pool, and never within reach of the continent's underside. It
 * is not a list of prohibitions with a rule for each, because a list is what
 * grows a gap.
 *
 * <p><b>Save-safety, stated rather than assumed.</b> The 8/23 flight loaded
 * 2,690 chunks at 33 metres a second and stalled the autosave, and no mechanic
 * in this mod may make fast horizontal travel worth doing. A squid drifts at
 * {@link VoidSquidDrift#DRIFT}, 0.6 blocks a second - slower than walking away
 * from it. It takes no passengers ({@link #canAddPassenger}), cannot be leashed
 * ({@link #canBeLeashed}) and cannot be pushed ({@link #isPushable}), so there
 * is no way to be carried by one or to move one anywhere. It loads nothing: the
 * thirteen block reads a tick are all within two blocks of an entity that is
 * itself inside a ticking chunk, and {@link #opensTo} answers no rather than
 * reading through an unloaded one at all.
 *
 * <p><b>Silent on purpose.</b> There are no {@code SoundEvent}s in this mod and
 * this does not add one - not even a pitch-shifted vanilla tone in the
 * {@code EraEcho} register, which was the obvious cheap cue. A noise is a
 * creature announcing itself, and the order was that you seek them out. A squid
 * that could be heard from a ledge would be finding you.
 */
public class VoidSquid extends FlyingMob {

    /** Health. Low, because nothing here is a fight - two good hits and it is ink. */
    private static final double HEALTH = 6.0;  // provisional - owner tunes by walking the world

    /**
     * Ticks spent in a cell it may not occupy.
     *
     * <p>Deliberately not saved. A squid reloaded into somewhere wrong - because
     * a player walled it in and logged out, or because a chunk came back
     * different - gets its grace period again rather than vanishing on the first
     * tick after a load, and nothing is stored on disk to go stale.
     */
    private int adrift;

    public VoidSquid(EntityType<? extends VoidSquid> type, Level level) {
        super(type, level);
        // Belt and braces. FlyingMob.travel applies no gravity at all, so this
        // changes nothing today; it is here so that a base class that starts
        // applying gravity on some future version bump cannot quietly drop every
        // squid in the world out of the bottom of it.
        setNoGravity(true);
    }

    /**
     * What a squid is made of.
     *
     * <p>{@code MOVEMENT_SPEED} is zero and {@code FOLLOW_RANGE} is zero, and
     * both are statements rather than tunings. Nothing moves a squid but
     * {@link VoidSquidDrift}, and there is nothing for it to follow because it
     * has no goals to follow with. An attribute set to zero is a claim a later
     * reader can check; an attribute left at its default is a claim nobody made.
     */
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    /**
     * Empty, and this method is the point of the class.
     *
     * <p>Every reason a mob has to approach a player lives in a goal, and there
     * are none. No {@code NearestAttackableTarget}, no {@code Panic}, no
     * {@code LookAtPlayer}, no {@code RandomStroll} - not even the harmless ones,
     * because a squid that turned to look at you would be finding you. The
     * default path navigation the base class builds is never asked for a path.
     */
    @Override
    protected void registerGoals() {
    }

    /**
     * How many goals are registered, so a test can assert the absence rather
     * than trust it.
     *
     * <p>The {@code ObeliskFeature.across()} precedent: a test that reads a
     * behaviour off the class cannot go stale the way a test that assumes one
     * can. If somebody adds a goal to {@link #registerGoals} the count moves and
     * {@code VoidSquidGameTest.itNeverHuntsAnything} says so, which is the only
     * warning that "it never hunts" is no longer true.
     */
    public int goalsRegistered() {
        return goalSelector.getAvailableGoals().size() + targetSelector.getAvailableGoals().size();
    }

    /**
     * Whether a void squid may occupy this block position.
     *
     * <p>The one rule. {@link VoidSquidDrift#CLEARANCE} blocks of air above and
     * below, air here, no fluid in any of them, and no fluid in the four
     * horizontal neighbours. The chunk has to be here already, for
     * {@code EraEcho}'s reason: asking for a blockstate loads a chunk, and a mod
     * that generates terrain because an animal drifted past is not a cheap mod.
     * An unloaded neighbour answers no, so a squid at the edge of what is loaded
     * stops rather than reading ground that is not there yet.
     *
     * <p>Static and given the level rather than reading {@code this}, because
     * the spawn placement asks the same question about a position with no squid
     * in it, and two spellings of one rule is how the seal and the spawn drift
     * apart.
     */
    public static boolean opensTo(LevelReader level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        for (int dy = -VoidSquidDrift.CLEARANCE; dy <= VoidSquidDrift.CLEARANCE; dy++) {
            BlockPos at = pos.above(dy);
            if (!level.getBlockState(at).isAir()) {
                return false;
            }
            if (!level.getFluidState(at).is(Fluids.EMPTY)) {
                return false;
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(pos.relative(side)).is(Fluids.EMPTY)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether one may be born here.
     *
     * <p>Three conjuncts and they are separate on purpose. The switch, because a
     * save with Octia off is left as vanilla found it and a creature is nearer
     * to terrain than to an item - and because on ordinary terrain the band is
     * solid deepslate, so the only positions that would pass are big caves,
     * which is not where this belongs. The band, because that is the vertical
     * slice under the continent this animal lives in. And {@link #opensTo},
     * because that is where it may be at all.
     *
     * <p>Only the third can be asserted in a gametest plot: a plot sits wherever
     * the test runner put it and the band is absolute world Y, so no position in
     * a plot is ever in the band. {@code VoidSquidDriftTest} holds the band and
     * {@code VoidSquidGameTest} holds the rest, and this note is here so nobody
     * later writes a plot test that passes because it never got past conjunct
     * two.
     *
     * <p><b>Corrected [2026-08-24]: "no position in a plot is ever in the band"
     * is false.</b> The runner put a plot at world Y -60, which is close enough
     * to {@link VoidSquidDrift#BAND_FLOOR} that a squid spawned in it recovered
     * into the band inside three seconds - it is what broke
     * {@code VoidSquidGameTest.itStaysInsideItsBand}, and the correction is
     * written out there. The rest of this note stands and is why it is kept: the
     * band is still {@code VoidSquidDriftTest}'s to hold, because a plot reaches
     * at most the two or three blocks of the band nearest wherever it landed,
     * and a test that happens to be near an edge is not a test of a band.
     * Conjunct two can now answer true in a plot, so a plot test that means to
     * exercise conjunct three must place itself rather than assume it.
     */
    public static boolean spawnRules(EntityType<VoidSquid> type, ServerLevelAccessor level,
                                     MobSpawnType reason, BlockPos pos, RandomSource random) {
        return OctiaWorldgen.active()
                && VoidSquidDrift.inBand(pos.getY())
                && opensTo(level, pos);
    }

    /**
     * One tick of drift, decided before the base class travels with it.
     *
     * <p>The order matters and it is why this overrides {@code aiStep} rather
     * than {@code serverAiStep}: {@code LivingEntity.aiStep} truncates every
     * velocity component under {@link VoidSquidDrift#TRUNCATION} and then calls
     * {@code travel}, so the vector has to be in place before {@code super} runs
     * or it is a tick late for the whole of its life.
     */
    @Override
    public void aiStep() {
        if (!level().isClientSide) {
            steer();
        }
        super.aiStep();
    }

    /**
     * The decision: withdraw from somewhere wrong, or drift and refuse.
     *
     * <p>Three neighbours are asked about and not twenty-six: the cell each
     * velocity component points at, one per axis. That leaves one shape
     * unasked - a diagonal step whose two axis neighbours are both open while
     * the corner between them is not - and it is written down rather than
     * closed, because closing it would mean stopping a squid dead at every
     * corner instead of letting it slide past one.
     *
     * <p><b>Why it cannot open a sealed pool.</b> A watershed bowl is a ring:
     * every water cell has solid at its own level on all four sides and a liner
     * under it, so at any height where a sealed cell is closed, all four of its
     * axis neighbours are closed too - and a diagonal step needs two open ones.
     * {@code VoidSquidGameTest.noSquidDriftsIntoASealedPool} rides that
     * geometry rather than trusting it. Everywhere else the gap is bounded by
     * {@link #withdraw}: a squid that ends up somewhere it may not be leaves,
     * whatever put it there.
     */
    private void steer() {
        Level level = level();
        BlockPos here = blockPosition();

        if (!opensTo(level, here)) {
            withdraw();
            return;
        }
        adrift = 0;

        VoidSquidDrift.Drift want =
                VoidSquidDrift.drift(mark(), level.getGameTime(), getY());
        VoidSquidDrift.Drift may = VoidSquidDrift.refuse(want,
                opensTo(level, here.offset(VoidSquidDrift.step(want.x()), 0, 0)),
                opensTo(level, here.offset(0, VoidSquidDrift.step(want.y()), 0)),
                opensTo(level, here.offset(0, 0, VoidSquidDrift.step(want.z()))));

        setDeltaMovement(may.x(), may.y(), may.z());
    }

    /**
     * What happens to a squid that is somewhere it may not be: after
     * {@link VoidSquidDrift#WITHDRAW_TICKS} it is simply not there.
     *
     * <p><b>It is a removal and not a death, and the difference is the whole
     * reason it is written this way.</b> A creature that dies when you wall it
     * in is a creature you farm by walling it in, and the ink would come out of
     * a box instead of out of the void. {@code discard} drops nothing, awards
     * nothing and fires no death, so boxing one in gets you an empty box.
     *
     * <p>It ignores persistence deliberately, which is why this is not routed
     * through {@code checkDespawn}. A squid held in place by a name tag or by a
     * gametest's own {@code setPersistenceRequired} is still a squid in the
     * wrong place, and the rule about where a squid may be does not have an
     * exemption in it. {@code VoidSquidGameTest.noSquidStaysInASealedPool} is
     * exactly that case.
     *
     * <p>While the clock runs the squid holds still rather than thrashing: there
     * is no direction that is reliably out, since the way it came in may have
     * been closed behind it.
     */
    private void withdraw() {
        setDeltaMovement(0.0, 0.0, 0.0);
        adrift++;
        if (adrift > VoidSquidDrift.WITHDRAW_TICKS) {
            discard();
        }
    }

    /**
     * A squid's own number, for its heading.
     *
     * <p>The UUID rather than the entity id, because the entity id is handed out
     * fresh on every load and a squid would turn a different way every time the
     * chunk came back. The UUID is saved with the entity, so a squid keeps the
     * wander it had.
     */
    private long mark() {
        return getUUID().getLeastSignificantBits();
    }

    /**
     * Nothing rides a squid. It is 0.6 blocks a second and it would still be a
     * vehicle, and the save-safety law is about what a mechanic invites, not
     * about how fast it is.
     */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    /** No lead. A hidden friend that can be dragged home is neither. */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /**
     * Nothing shoves it. Being pushable is how a player would herd one into a
     * pool, or into a wall to make it withdraw, and both are ways of reaching a
     * creature that is supposed to be reached only by going to it.
     */
    @Override
    public boolean isPushable() {
        return false;
    }
}
