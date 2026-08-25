package com.serenity.octia.world;

import java.util.Optional;

import com.serenity.octia.ship.ShipCoreBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * The signs that somebody lived here, scattered over a ruin after it is built.
 *
 * <p>A derelict is a shape. A hearth beside it, a barrel with someone's things
 * in it and a bed nobody folded is a <i>place</i>, and the difference is most of
 * what makes a structure worth walking to. The palette is taken from the ACT ONE
 * settlement in world 0 rather than invented: andesite and timber, an unreasonable
 * number of light sources, fences, signage, and cobweb and moss doing the ageing.
 *
 * <p><b>Every prop is rolled on its own.</b> There is no checklist and nothing
 * here tries to place one of each. A ruin with a cold hearth and nothing else
 * reads far better than one carrying a hearth, a bed, a barrel, a workbench and
 * a path, which reads as a shop. {@link RuinAge#presenceInEight()} is the whole
 * density control.
 *
 * <p><b>Nothing may ever touch a hull.</b> {@link ShipCoreBlock#hullIntact} needs
 * all eight of a core's horizontal neighbours to be frame panels, so a torch
 * dropped into one of those eight slots would unmoor the ship - a mooring lost
 * to decoration, which is the kind of bug that gets found months later by
 * someone counting marks on a map. {@link #free} refuses any position adjacent
 * to a core, and refuses to overwrite anything that is not air.
 *
 * <p><b>No entities, ever.</b> These are things left behind. Beds and hearths
 * imply a person; the person is gone, and the emptiness is the point.
 *
 * <p><b>[2026-08-24] The paths keep their word, and the pot is no longer
 * empty.</b> Both changes answer one sentence the owner said at the close of the
 * {@code [0_6_8]} playtest, standing on a ruin's radiating dirt: <i>where do
 * these paths go?</i> They went nowhere - {@link #path} rolled a cardinal at
 * random and walked it - and the ANCIENT pot beside them was a bare
 * {@code DECORATED_POT} that broke into four bricks while four pottery sherds
 * sat unused in {@code archaeology/ruin.json}. The old behaviour is written down
 * on {@link #path} rather than removed, because a correction here is a new
 * entry.
 */
public final class Habitation {

    /** How far from the anchor props may be scattered. */
    private static final int SPREAD_MIN = 2;
    private static final int SPREAD_MAX = 5;

    /**
     * How many strides an aimed path runs before the ruin stops maintaining it.
     *
     * <p>Unchanged from the roll this replaced, deliberately. Only the heading
     * moved, and the envelope a path may write in is what keeps it inside the
     * chunk that owns the site - provisional - owner tunes by walking the world.
     */
    private static final int PATH_STEPS_MIN = 3;
    private static final int PATH_STEPS_MAX = 6;

    /**
     * How many arms a crossroads puts out, and how far each one runs.
     *
     * <p>Three is the fewest that cannot be mistaken for a path with a kink in
     * it, and four is every cardinal, which is the most a crossroads can be. The
     * arms are short because an arm is not a journey: it is the site saying
     * <i>and that way, and that way</i>, and a long arm would just be four paths
     * that each promise somewhere and deliver nowhere. Provisional - owner tunes
     * by walking the world.
     */
    private static final int ARMS_MIN = 3;
    private static final int ARMS_MAX = 4;
    private static final int ARM_STEPS_MIN = 2;
    private static final int ARM_STEPS_MAX = 3;

    /**
     * The sherds an ANCIENT pot can carry.
     *
     * <p>These four and no others, because these four are exactly what
     * {@code data/octia/loot_table/archaeology/ruin.json} already drops out of a
     * ruin's digs. A pot made of the same four is the same find twice - brushed
     * out of the gravel, or standing whole beside it - and that is the entire
     * reason the list is not longer.
     *
     * <p><b>They mean nothing, and that is a ruling rather than an oversight.</b>
     * {@code docs/MYSTERIES.md} threw out a design that assigned these marks
     * private meanings: <i>the marks are vanilla's, the mod cannot redefine
     * them, and a cipher needs a key, and a key is a wiki.</i> So the faces are
     * rolled and nothing reads them back. Anyone who later wants a pot to say
     * something has to answer that paragraph first.
     */
    private static final Item[] SHERDS = {
            Items.FRIEND_POTTERY_SHERD,
            Items.HEARTBREAK_POTTERY_SHERD,
            Items.HOWL_POTTERY_SHERD,
            Items.DANGER_POTTERY_SHERD,
    };

    private Habitation() {
    }

    /**
     * Dresses one ruin, letting the lattice decide where its path goes.
     *
     * @param anchor the middle of the ruin; props ring it
     * @param age    how long ago the people left
     */
    public static void dress(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        dress(level, random, anchor, age, null);
    }

    /**
     * Dresses one ruin, with the path aimed by hand.
     *
     * <p>This exists for a caller that already knows where its site's path ought
     * to run and should not have to re-derive it. A derelict seated against
     * something it came from is the case: the wreck knows what it is lying
     * beside, and the lattice does not.
     *
     * <p><b>A target in the anchor's own column is not an error.</b> It is a site
     * with nowhere to point, and it puts out the crossroads - the same answer
     * {@link Mystery#toward} gives for a position standing on its node, for the
     * same reason. One rule covers both, so there is no special case to get
     * wrong. A caller that does not want stubs must not hand this its own
     * column.
     *
     * @param pathTarget the block the path should run toward, or null to ask the
     *                   lattice
     */
    public static void dress(WorldGenLevel level, RandomSource random, BlockPos anchor,
                             RuinAge age, BlockPos pathTarget) {
        hearth(level, random, anchor, age);
        light(level, random, anchor, age);
        rest(level, random, anchor, age);
        store(level, random, anchor, age);
        work(level, random, anchor, age);
        wear(level, random, anchor, age);
        path(level, random, anchor, age, pathTarget);
    }

    /**
     * Which of the eight ring positions a site's path leaves on, or <b>null</b>
     * when the site is a crossroads.
     *
     * <p>Pure, and public for the reason {@code ObeliskFeature.across()} is: a
     * gametest cannot stand its plot on a lattice node, so the decision has to be
     * askable without a world or it goes untested. Nothing in the world reads
     * this except {@link #path}.
     *
     * <p>Null carries one meaning in both branches - <i>there is nowhere to
     * point from here</i> - and {@link #path} answers it one way.
     */
    public static Mystery.Mark aim(long seed, BlockPos anchor, BlockPos pathTarget) {
        if (pathTarget == null) {
            return Mystery.toward(seed, anchor.getX(), anchor.getZ());
        }
        return Mystery.markFor(pathTarget.getX() - anchor.getX(),
                pathTarget.getZ() - anchor.getZ());
    }

    /** Whether this prop happens at all. */
    private static boolean rolls(RandomSource random, RuinAge age) {
        return random.nextInt(8) < age.presenceInEight();
    }

    /**
     * A fire, or what is left of one.
     *
     * <p>The only prop that is nearly always present, because a hearth is the
     * one thing every occupied place has and its state says the age out loud
     * without anybody having to read a sign.
     */
    private static void hearth(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        BlockPos spot = spot(level, random, anchor);
        if (spot == null) {
            return;
        }
        switch (age) {
            case RECENT -> put(level, spot, Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(CampfireBlock.LIT, true));
            case WEATHERED -> put(level, spot, Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(CampfireBlock.LIT, false));
            // Gone out long enough ago that the fire itself is gone. Soul soil
            // reads as scorched ground without needing a block called ash.
            case ANCIENT -> {
                put(level, spot, Blocks.SOUL_SOIL.defaultBlockState());
                // The neighbour did not go through spot, so it asks its own
                // footing. put stays untouched: its contract is air-and-not-hull.
                if (random.nextBoolean() && settled(level, spot.relative(Direction.EAST))) {
                    put(level, spot.relative(Direction.EAST), Blocks.SOUL_SOIL.defaultBlockState());
                }
            }
        }
    }

    /**
     * Light, failing with age.
     *
     * <p>Torches burn out and lichen does not, so what lights an old ruin is
     * the thing that grew rather than the thing that was lit. Lichen needs a
     * face to cling to, which is why it is tried against the ruin itself.
     */
    private static void light(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        if (!rolls(random, age)) {
            return;
        }
        BlockPos spot = spot(level, random, anchor);
        if (spot == null) {
            return;
        }
        switch (age) {
            case RECENT -> put(level, spot, Blocks.TORCH.defaultBlockState());
            case WEATHERED -> put(level, spot, random.nextBoolean()
                    ? Blocks.TORCH.defaultBlockState()
                    : Blocks.GLOW_LICHEN.defaultBlockState());
            case ANCIENT -> put(level, spot, Blocks.GLOW_LICHEN.defaultBlockState());
        }
    }

    /**
     * Somewhere someone slept.
     *
     * <p>A bed is two blocks and both halves have to land, or it renders as a
     * broken half-bed - which reads as a bug rather than as a ruin. Ancient
     * ruins get wool instead: the frame is long gone and only the stuffing is
     * left, which says the same thing more quietly.
     */
    private static void rest(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        if (!rolls(random, age)) {
            return;
        }
        BlockPos foot = spot(level, random, anchor);
        if (foot == null) {
            return;
        }

        if (age == RuinAge.ANCIENT) {
            put(level, foot, Blocks.WHITE_WOOL.defaultBlockState());
            return;
        }

        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos head = foot.relative(facing);
        // Both halves of a bed rest on ground, and spot only vouched for the foot.
        if (!free(level, head) || !settled(level, head)) {
            return;
        }

        BlockState bed = Blocks.WHITE_BED.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing);
        put(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
        put(level, head, bed.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /**
     * Somebody's things, still where they were put.
     *
     * <p>Belongings rather than treasure, because these were people and not
     * dragons: bread, a torch, a worn tool, and a panel off the hull. The age
     * picks the table, so a recent barrel still holds something fresh and a
     * weathered one holds what did not rot. See {@link OctiaLoot}.
     */
    private static void store(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        if (!rolls(random, age)) {
            return;
        }
        BlockPos spot = spot(level, random, anchor);
        if (spot == null) {
            return;
        }

        if (age == RuinAge.ANCIENT) {
            // The barrel is gone; what it held is not worth having. The pot
            // itself is, which is what sherds is for.
            put(level, spot, Blocks.DECORATED_POT.defaultBlockState());
            sherds(level, random, spot);
            return;
        }

        put(level, spot, Blocks.BARREL.defaultBlockState());
        if (level.getBlockEntity(spot) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(age == RuinAge.RECENT
                    ? OctiaLoot.RUIN_STORE
                    : OctiaLoot.RUIN_STORE_OLD);
            chest.setLootTableSeed(random.nextLong());
        }
    }

    /**
     * Puts somebody's pottery on the pot that outlived them.
     *
     * <p><b>Why this is worth doing at all.</b> A bare decorated pot breaks into
     * four bricks, which is the same nothing a player would get from a pot they
     * made themselves out of four bricks - so the one container an ANCIENT ruin
     * has was, on being found and broken, exactly as informative as the ground it
     * stood on. A pot with faces on it is the opposite: it is the only object in
     * these ruins that carries a picture somebody chose, and it is the only thing
     * here that survives being carried home.
     *
     * <p><b>The write is the pattern {@code RuinGround.dig} proved.</b> Put the
     * block down, ask the level for the block entity it just made, set the data
     * on that. There is no public setter for a pot's faces in 1.21.1 - the
     * {@code decorations} field is private and the only door in is
     * {@code setFromItem}, which applies the {@code POT_DECORATIONS} data
     * component off an item stack. {@code createDecoratedPotItem} builds exactly
     * that stack, so the two together are the sanctioned route rather than a
     * reach through a mixin.
     *
     * <p><b>No setChanged, on purpose, and it matches the dig.</b> The chunk is
     * already unsaved from the {@code setBlock} that made the pot, and
     * {@code BlockEntity.setChanged} on a generation worker walks back into
     * {@code ServerLevel} to ask which chunk it is in - the same read
     * {@code RuinGround.clearOfStructures} goes out of its way to scope. One
     * write, no round trip.
     *
     * <p>At least one face always carries a sherd, and the rest are coin tosses.
     * A pot with one sherd and three bare faces is the common case and it should
     * be: these people were leaving, not decorating.
     */
    private static void sherds(WorldGenLevel level, RandomSource random, BlockPos pot) {
        if (!(level.getBlockEntity(pot) instanceof DecoratedPotBlockEntity jar)) {
            return;
        }
        // Which face is certain is rolled first, so that the guarantee costs one
        // draw rather than a retry loop that could in principle never end.
        int certain = random.nextInt(4);
        PotDecorations faces = new PotDecorations(
                face(random, certain == 0),
                face(random, certain == 1),
                face(random, certain == 2),
                face(random, certain == 3));
        jar.setFromItem(DecoratedPotBlockEntity.createDecoratedPotItem(faces));
    }

    /** One face of a pot: a sherd, or the brick that was always there. */
    private static Optional<Item> face(RandomSource random, boolean certain) {
        if (!certain && !random.nextBoolean()) {
            return Optional.empty();
        }
        return Optional.of(SHERDS[random.nextInt(SHERDS.length)]);
    }

    /** A job somebody was in the middle of. */
    private static void work(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        if (!rolls(random, age)) {
            return;
        }
        BlockPos spot = spot(level, random, anchor);
        if (spot == null) {
            return;
        }
        put(level, spot, switch (age) {
            case RECENT -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case WEATHERED -> Blocks.FLETCHING_TABLE.defaultBlockState();
            case ANCIENT -> Blocks.CAULDRON.defaultBlockState();
        });
    }

    /** What the years did. Always tried, and heaviest at the far end. */
    private static void wear(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        int strands = switch (age) {
            case RECENT -> random.nextInt(2);
            case WEATHERED -> 1 + random.nextInt(2);
            case ANCIENT -> 2 + random.nextInt(3);
        };
        for (int i = 0; i < strands; i++) {
            BlockPos spot = looseSpot(level, random, anchor);
            if (spot == null) {
                continue;
            }
            put(level, spot, age == RuinAge.RECENT
                    ? Blocks.COBWEB.defaultBlockState()
                    : random.nextBoolean()
                        ? Blocks.COBWEB.defaultBlockState()
                        : Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    /**
     * The ground worn down by somebody walking the same line.
     *
     * <p>Written into the block <em>below</em> a free surface rather than onto
     * it, because a path is the ground being different, not something sitting on
     * the ground. Ancient ruins get none: the whole point of a path is that it
     * is kept, and nobody has walked this one in a long time.
     *
     * <p><b>[2026-08-24] It used to lie.</b> The heading was
     * {@code Direction.Plane.HORIZONTAL.getRandomDirection(random)} - four ruins
     * in a valley put out four paths that agreed about nothing and led to
     * nothing, and the owner asked the only question that shape can prompt:
     * <i>where do these paths go?</i> The honest answers were "nowhere" and
     * "four different nowheres". A path that is dressing is fine; a path that
     * looks like evidence and is not is a lie the world tells, and this one had
     * been telling it since the class was written.
     *
     * <p><b>Now it goes where everything else in this mod already goes.</b>
     * {@link Mystery#toward} gives every position one of eight bearings to its
     * cell's node, and the obelisks and the arches have been standing on that
     * same lattice for months. So the path aims at the node, and two ruins in one
     * cell put out paths that <em>agree</em> - which is the whole trick
     * {@code Mystery} is built on, arriving in dirt where a player walking with
     * their eyes down will meet it.
     *
     * <p><b>The bearing is {@code Mystery}'s and not
     * {@code Sightlines.legAt(..).heading()}, and the difference is load
     * bearing.</b> A leg's heading is the step from one cell to the next; it says
     * nothing about where inside its cell the node wandered, and with
     * {@link Sightlines#JITTER} at 96 a straight cardinal walk from an arbitrary
     * ruin can miss the node by the better part of two hundred blocks. The leg
     * heading answers "which way does the thread run", which is the obelisk's
     * question. This is asking "which way is the waypoint from here", which is a
     * different one.
     *
     * <p><b>Diagonals are walked, and that is deliberate.</b> {@code Mystery}
     * quantises to eight and the ring it was quantised for has eight positions,
     * so refusing the four diagonals would round half of all bearings onto a
     * cardinal and put the paths back to being approximately true. A diagonal
     * step moves one on each axis, so the envelope per axis is what it always
     * was.
     *
     * <p><b>Save-safety.</b> A path is a direction and never a road. It is a
     * handful of blocks inside the site's own chunk that says which way to walk;
     * nothing here builds anything a player can cross ground quickly on, and
     * nothing here reaches into another chunk to meet a neighbour's path.
     */
    private static void path(WorldGenLevel level, RandomSource random, BlockPos anchor,
                             RuinAge age, BlockPos pathTarget) {
        if (age == RuinAge.ANCIENT || !rolls(random, age)) {
            return;
        }
        Mystery.Mark heading = aim(level.getSeed(), anchor, pathTarget);
        if (heading == null) {
            crossroads(level, random, anchor, age);
            return;
        }
        walk(level, random, anchor, age, heading.dx(), heading.dz(),
                PATH_STEPS_MIN + random.nextInt(PATH_STEPS_MAX - PATH_STEPS_MIN + 1));
    }

    /**
     * What a site standing on a node puts out instead of a path.
     *
     * <p>Everywhere else, one path leaves aimed at the waypoint. Here there is no
     * waypoint to aim at, because this is it - so the site puts out stubs on
     * three or four cardinals and lets the player work out what that means. It is
     * the same information the missing panel in a wreck's floor carries, said in
     * dirt: <i>you have arrived, and this is where the ways part</i>.
     *
     * <p><b>Nothing explains this and nothing ever should.</b> A player who has
     * walked five aimed paths has learned that dirt points somewhere. The first
     * crossroads breaks that rule, and the only place the exception can be
     * resolved is by looking around at the place they are standing in - which is
     * a node, which is where the obelisk and the arch are. The rule teaches the
     * exception and the exception teaches the lattice.
     *
     * <p>Cardinals only, never the diagonals an aimed path can take. Four arms
     * square to the world read as a junction; eight read as a sunburst, which is
     * decoration.
     */
    private static void crossroads(WorldGenLevel level, RandomSource random,
                                   BlockPos anchor, RuinAge age) {
        Direction[] arms = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        // Shuffled rather than drawn with rejection, so which three of the four
        // are missed is uniform and the draw count does not depend on the roll.
        for (int i = arms.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Direction held = arms[i];
            arms[i] = arms[j];
            arms[j] = held;
        }

        int count = ARMS_MIN + random.nextInt(ARMS_MAX - ARMS_MIN + 1);
        for (int i = 0; i < count; i++) {
            walk(level, random, anchor, age, arms[i].getStepX(), arms[i].getStepZ(),
                    ARM_STEPS_MIN + random.nextInt(ARM_STEPS_MAX - ARM_STEPS_MIN + 1));
        }
    }

    /**
     * The walk itself, once the heading is settled. The block writes and the
     * wear palette by age are exactly what {@link #path} always did.
     *
     * <p><b>The first stride is always one.</b> Everything after it is one or
     * two, as before. A path whose first block is two out starts with a gap
     * between the ruin and its own path, which reads as two unrelated things -
     * and it is the reason a gametest could not say for certain that a path
     * exists at all. Worth noting that this makes the reach shorter than the roll
     * it replaced, never longer: eleven blocks at the far end where it was
     * twelve, so the write stays inside the envelope that keeps a site in its own
     * chunk.
     *
     * @param dx    east-positive unit step, -1, 0 or 1
     * @param dz    south-positive unit step, -1, 0 or 1
     * @param steps how many strides to take
     */
    private static void walk(WorldGenLevel level, RandomSource random, BlockPos anchor,
                             RuinAge age, int dx, int dz, int steps) {
        BlockPos walking = anchor;
        for (int i = 0; i < steps; i++) {
            int stride = i == 0 ? 1 : 1 + random.nextInt(2);
            walking = walking.offset(dx * stride, 0, dz * stride);
            BlockPos surface = RuinGround.surfaceNear(level, walking);
            if (surface == null) {
                continue;
            }
            BlockPos under = surface.below();
            if (!isGround(level.getBlockState(under)) || nearCore(level, under)) {
                continue;
            }
            RuinGround.put(level, under, age == RuinAge.RECENT
                    ? Blocks.DIRT_PATH.defaultBlockState()
                    : Blocks.COARSE_DIRT.defaultBlockState());
        }
    }

    /**
     * Whether this block is ground a path could be worn into.
     *
     * <p>A whitelist, and it has to be. {@link #path} is the one thing here
     * that deliberately writes over a solid block rather than into air, because
     * a path is the ground being different rather than something laid on top of
     * it - and "the block under a free surface" is the ruin's own floor when the
     * surface is inside the ruin. The obelisk found this immediately: its plinth
     * sits one below the surface around it, so the first path laid across it
     * paved a hole through the thing it was decorating.
     */
    private static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND);
    }

    /**
     * A free, settled surface position around the anchor, or null.
     *
     * <p>Every prop that has to stand routes through here, so the footing
     * question is asked once rather than per prop. That deliberately includes
     * {@link #light}: a torch pops off a tuft on its first update anyway, and
     * giving the age switch its own ground knowledge would be two placement
     * rules where one does. Glow lichen losing tuft-top sites is the accepted
     * cost.
     */
    private static BlockPos spot(WorldGenLevel level, RandomSource random, BlockPos anchor) {
        BlockPos found = RuinGround.scatter(level, random, anchor, SPREAD_MIN, SPREAD_MAX);
        return found != null && free(level, found) && settled(level, found) ? found : null;
    }

    /**
     * A free surface position that is allowed to hang, or null.
     *
     * <p>{@link #spot}'s old body, kept for {@link #wear} alone. A cobweb
     * hangs in vanilla mineshafts, and vanilla carpet itself asks only for
     * non-air below, so the wear strands keep the looser question - a
     * decision, not an omission.
     */
    private static BlockPos looseSpot(WorldGenLevel level, RandomSource random, BlockPos anchor) {
        BlockPos found = RuinGround.scatter(level, random, anchor, SPREAD_MIN, SPREAD_MAX);
        return found != null && free(level, found) ? found : null;
    }

    /**
     * Whether a prop may be written here.
     *
     * <p>Air only, and never in a hull ring. The second half is the one that
     * matters: a position whose horizontal neighbour is a ship core <em>is</em>
     * one of the eight slots {@code hullIntact} counts, and putting anything
     * there unmoors the ship.
     */
    private static boolean free(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && !nearCore(level, pos);
    }

    /**
     * Whether the block under this position holds a block up.
     *
     * <p>{@link RuinGround#surfaceNear} answers "the block below is not air",
     * and a grass tuft is not air - the playtest found a decorated pot standing
     * on one (23.43.26, 264 193 22). Air-only was never the whole question; the
     * ground has to be ground. This asks the vanilla full-face support question,
     * one block read, and it is the exact question that was missing: a tuft, a
     * fence, the gap over a slab and a pot already placed all answer no.
     *
     * <p>It lives here and not in {@link RuinGround} because only the dressing
     * asks it today. The day a second dresser wants it, it moves there -
     * RuinGround's own extraction rule.
     */
    private static boolean settled(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** True if this position is one of some core's eight ring slots. */
    private static boolean nearCore(WorldGenLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getBlockState(pos.offset(dx, 0, dz)).getBlock() instanceof ShipCoreBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (free(level, pos)) {
            RuinGround.put(level, pos, state);
        }
    }
}
