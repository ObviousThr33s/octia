package com.serenity.octia.world;

import com.serenity.octia.ship.ShipCoreBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
 */
public final class Habitation {

    /** How far from the anchor props may be scattered. */
    private static final int SPREAD_MIN = 2;
    private static final int SPREAD_MAX = 5;

    private Habitation() {
    }

    /**
     * Dresses one ruin.
     *
     * @param anchor the middle of the ruin; props ring it
     * @param age    how long ago the people left
     */
    public static void dress(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        hearth(level, random, anchor, age);
        light(level, random, anchor, age);
        rest(level, random, anchor, age);
        store(level, random, anchor, age);
        work(level, random, anchor, age);
        wear(level, random, anchor, age);
        path(level, random, anchor, age);
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
            // The barrel is gone; what it held is not worth having.
            put(level, spot, Blocks.DECORATED_POT.defaultBlockState());
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
     */
    private static void path(WorldGenLevel level, RandomSource random, BlockPos anchor, RuinAge age) {
        if (age == RuinAge.ANCIENT || !rolls(random, age)) {
            return;
        }
        int steps = 3 + random.nextInt(4);
        Direction heading = Direction.Plane.HORIZONTAL.getRandomDirection(random);

        BlockPos walking = anchor;
        for (int i = 0; i < steps; i++) {
            walking = walking.relative(heading, 1 + random.nextInt(2));
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
