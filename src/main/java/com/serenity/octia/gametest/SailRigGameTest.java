package com.serenity.octia.gametest;

import com.serenity.octia.OctiaItems;
import com.serenity.octia.traverse.SailRig;
import com.serenity.octia.traverse.SailRigItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The sail-rig's wiring, driven the way the game drives it.
 *
 * <p>The cap, the band, and the deploy truth table are pinned by
 * {@code SailRigTest} without a game running - water and elytra retraction are
 * rows of that table and are not re-tested here. What needs the game is the
 * wiring: that the registered item is ours, that {@code inventoryTick} reaches
 * the clamps from a hand and only from a hand, and that the clamped vector and
 * the capped fallDistance actually land on a real player.
 *
 * <p>A headless suite cannot press keys, so each test drives
 * {@code inventoryTick} directly - the exact entry {@code Inventory.tick}
 * calls once per tick - and asserts the decision, not the particles. No floor
 * is built and no block is placed: the plot stays at its minimum, because a
 * generous floor writes into the next test. The falling posture is
 * {@code fallDistance = 10} with the ground flag off, and no heading is
 * hard-coded anywhere - every look derives from the level seed, the
 * {@code ObeliskGameTest} discipline, so a test that only passed facing north
 * cannot exist here.
 */
public class SailRigGameTest implements FabricGameTest {

    /** Somewhere in the plot's own airspace. */
    private static final Vec3 AIRSPACE = new Vec3(2, 6, 2);

    /** A survival mock in the plot's air, holding a rig in the main hand. */
    private static Player rigged(GameTestHelper helper, ItemStack rig) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(AIRSPACE));
        player.setItemInHand(InteractionHand.MAIN_HAND, rig);
        return player;
    }

    /** The falling posture: airborne, well past the deploy threshold. */
    private static void falling(Player player) {
        player.setOnGround(false);
        player.fallDistance = 10.0F;
    }

    /** A look read from the seed, never hard-coded. Horizontal, since pitch stays 0. */
    private static void lookFromSeed(GameTestHelper helper, Player player, int tick) {
        player.setYRot((float) Math.floorMod(helper.getLevel().getSeed() + tick * 37L, 360L));
    }

    /** One main-hand tick, the entry Inventory.tick calls. */
    private static void tickMainHand(GameTestHelper helper, Player player, ItemStack rig) {
        OctiaItems.SAIL_RIG.inventoryTick(rig, helper.getLevel(), player,
                player.getInventory().selected, true);
    }

    /** The item is in the registry under the path this mod names, and it is ours. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSailRigIsRegistered(GameTestHelper helper) {
        ItemStack rig = new ItemStack(OctiaItems.SAIL_RIG);
        if (!(rig.getItem() instanceof SailRigItem)) {
            throw new AssertionError("octia:sail_rig did not resolve to a SailRigItem");
        }
        if (!BuiltInRegistries.ITEM.getKey(OctiaItems.SAIL_RIG).getPath().equals("sail_rig")) {
            throw new AssertionError("the sail-rig is registered under an unexpected path");
        }
        if (rig.getMaxStackSize() != 1) {
            throw new AssertionError("a sail-rig stacked past one; it is equipment, not a material");
        }
        helper.succeed();
    }

    /**
     * One deployed tick does all three jobs: caps the horizontal, refuses the
     * upward component, and caps the memory of the fall.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aLongFallOpensTheSail(GameTestHelper helper) {
        ItemStack rig = new ItemStack(OctiaItems.SAIL_RIG);
        Player player = rigged(helper, rig);
        falling(player);
        lookFromSeed(helper, player, 0);
        player.setDeltaMovement(3.0, 1.0, -2.0);

        tickMainHand(helper, player, rig);

        Vec3 out = player.getDeltaMovement();
        double h = SailRig.horizontal(out.x, out.z);
        if (h > SailRig.HARD_CAP + 1e-9) {
            throw new AssertionError("the cap did not hold: " + h);
        }
        if (Math.abs(out.y + SailRig.SINK_MIN) > 1e-9) {
            throw new AssertionError("the upward component was not refused: dy=" + out.y);
        }
        if (player.fallDistance != SailRig.SOFT_FALL) {
            throw new AssertionError("the fall was remembered as " + player.fallDistance
                    + ", not the soft fall");
        }
        helper.succeed();
    }

    /**
     * The charter's first invariant, at the decision level: no pattern of
     * injected bursts ever leaves a tick above the cap. Burst headings walk
     * from the seed, so the pattern is different every run and hard-codes
     * nothing.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void diveChainingNeverBeatsTheCap(GameTestHelper helper) {
        ItemStack rig = new ItemStack(OctiaItems.SAIL_RIG);
        Player player = rigged(helper, rig);
        falling(player);
        long seed = helper.getLevel().getSeed();

        for (int tick = 0; tick < 120; tick++) {
            if (tick % 8 == 0) {
                double a = Math.toRadians(Math.floorMod(seed + tick * 53L, 360L));
                player.setDeltaMovement(4 * Math.cos(a), -2.0, 4 * Math.sin(a));
            }
            lookFromSeed(helper, player, tick);
            tickMainHand(helper, player, rig);

            Vec3 out = player.getDeltaMovement();
            double h = SailRig.horizontal(out.x, out.z);
            if (h > SailRig.HARD_CAP + 1e-6) {
                throw new AssertionError("tick " + tick + " left the cap behind: " + h);
            }
        }
        helper.succeed();
    }

    /**
     * Net altitude never increases over N ticks of any input pattern - stated
     * on the vector the game is about to move with: every executed tick is in
     * the band, and the sum of eighty of them is at least eighty gentle sinks.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void noInputPatternNetsAClimb(GameTestHelper helper) {
        ItemStack rig = new ItemStack(OctiaItems.SAIL_RIG);
        Player player = rigged(helper, rig);
        falling(player);

        double sum = 0;
        for (int tick = 0; tick < 80; tick++) {
            if (tick % 2 == 0) {
                Vec3 before = player.getDeltaMovement();
                player.setDeltaMovement(before.x, 1.0, before.z);
            }
            lookFromSeed(helper, player, tick);
            tickMainHand(helper, player, rig);

            double dy = player.getDeltaMovement().y;
            if (dy < -SailRig.SINK_MAX - 1e-9 || dy > -SailRig.SINK_MIN + 1e-9) {
                throw new AssertionError("tick " + tick + " left the band: dy=" + dy);
            }
            sum += dy;
        }
        if (sum > -80 * SailRig.SINK_MIN + 1e-6) {
            throw new AssertionError("eighty ticks netted " + sum + ", which is a climb");
        }
        helper.succeed();
    }

    /** On the ground the rig touches nothing - not the motion, not the memory. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theGroundStowsTheSail(GameTestHelper helper) {
        ItemStack rig = new ItemStack(OctiaItems.SAIL_RIG);
        Player player = rigged(helper, rig);
        player.setOnGround(true);
        player.fallDistance = 0.0F;
        lookFromSeed(helper, player, 0);
        player.setDeltaMovement(3.0, 1.0, 0.0);

        tickMainHand(helper, player, rig);

        Vec3 out = player.getDeltaMovement();
        if (out.x != 3.0 || out.y != 1.0 || out.z != 0.0) {
            throw new AssertionError("a stowed sail touched the motion: " + out);
        }
        if (player.fallDistance != 0.0F) {
            throw new AssertionError("a stowed sail touched the memory: " + player.fallDistance);
        }
        helper.succeed();
    }

    /** Only a hand deploys. From a backpack slot the rig is inert. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRigDoesNothingFromABackpack(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(AIRSPACE));
        ItemStack rig = new ItemStack(OctiaItems.SAIL_RIG);
        player.getInventory().setItem(9, rig);
        falling(player);
        lookFromSeed(helper, player, 0);
        player.setDeltaMovement(3.0, 1.0, 0.0);

        OctiaItems.SAIL_RIG.inventoryTick(rig, helper.getLevel(), player, 9, false);

        Vec3 out = player.getDeltaMovement();
        if (out.x != 3.0 || out.y != 1.0 || out.z != 0.0) {
            throw new AssertionError("a backpacked rig touched the motion: " + out);
        }
        if (player.fallDistance != 10.0F) {
            throw new AssertionError("a backpacked rig touched the memory: " + player.fallDistance);
        }
        helper.succeed();
    }

    /**
     * A rig in each hand pushes once, not twice. Ticked in the order
     * {@code Inventory.tick} uses - main compartment first, offhand after - so
     * this is the order the running game asks the same question in.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void twoRigsSailOnce(GameTestHelper helper) {
        ItemStack main = new ItemStack(OctiaItems.SAIL_RIG);
        ItemStack off = new ItemStack(OctiaItems.SAIL_RIG);
        Player player = rigged(helper, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        falling(player);
        lookFromSeed(helper, player, 0);
        player.setDeltaMovement(0.0, -1.0, 0.0);

        tickMainHand(helper, player, main);
        OctiaItems.SAIL_RIG.inventoryTick(off, helper.getLevel(), player,
                Inventory.SLOT_OFFHAND, false);

        double h = SailRig.horizontal(player.getDeltaMovement().x, player.getDeltaMovement().z);
        if (h <= 0) {
            throw new AssertionError("no push happened at all");
        }
        if (h > SailRig.GLIDE_PUSH + 1e-9) {
            throw new AssertionError("two rigs pushed twice: " + h);
        }
        helper.succeed();
    }
}
