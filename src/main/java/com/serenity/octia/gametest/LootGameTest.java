package com.serenity.octia.gametest;

import java.util.List;

import com.serenity.octia.OctiaBlocks;
import com.serenity.octia.OctiaItems;
import com.serenity.octia.item.Bindle;
import com.serenity.octia.item.BindleItem;
import com.serenity.octia.world.OctiaLoot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * MILESTONE 2 - what a ruin gives up.
 *
 * <p>A wrong loot table path does not crash anything. It resolves to
 * {@code LootTable.EMPTY}, the dig yields nothing, the barrel is bare, and the
 * only trace is one line in a log nobody reads until a player asks why the
 * ruins are pointless. So these ask the server directly whether each table
 * loaded, and then whether it actually produces items.
 *
 * <p><b>A quieter failure than a broken table is a working table nobody is in.</b>
 * The sail-rig had a model, a name, a recipe, a creative tab entry and no loot
 * entry anywhere, so every part of it worked and no player could meet it. A
 * build cannot catch that. The three tests at the bottom of this file are what
 * catches it: the rig is findable, the rarest site always carries one, and the
 * ink that this world's writing is made of is in the old loot.
 */
public class LootGameTest implements FabricGameTest {

    private static final BlockPos SPOT = new BlockPos(2, 2, 2);

    /** The tables, and how many items each should be capable of producing. */
    private static LootTable resolve(GameTestHelper helper, ResourceKey<LootTable> key) {
        return helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
    }

    /**
     * Params with an origin and nothing else, which is the whole point.
     *
     * <p>{@code CHEST} rather than each table's own set, including for the
     * archaeology table, and it works only because none of these tables uses a
     * condition or a function that wants a parameter - no tool check, no
     * location check, no looting, no copy_state. That is a constraint on the
     * JSON, not a convenience here: the first param-dependent entry added to one
     * of these tables throws out of this method rather than failing an
     * assertion, and the message will be about a missing parameter rather than
     * about the entry that wanted it.
     */
    private static LootParams params(GameTestHelper helper) {
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(SPOT)))
                .create(LootContextParamSets.CHEST);
    }

    /** Every table Octia names is a table the server actually has. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyTableLoads(GameTestHelper helper) {
        for (ResourceKey<LootTable> key : List.of(
                OctiaLoot.RUIN_DIG, OctiaLoot.RUIN_STORE, OctiaLoot.RUIN_STORE_OLD,
                OctiaLoot.STATION_STORE)) {
            if (resolve(helper, key) == LootTable.EMPTY) {
                throw new AssertionError("loot table " + key.location() + " did not load. "
                        + "A malformed or misplaced table resolves to EMPTY and yields nothing, "
                        + "silently - check data/octia/loot_table/ and the server log.");
            }
        }
        helper.succeed();
    }

    /**
     * The tables produce items, not merely exist.
     *
     * <p>A table that loads but rolls nothing is the same experience as one that
     * was never there. Rolled several times because pools are random and one
     * empty draw proves nothing either way.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theTablesProduceThings(GameTestHelper helper) {
        LootParams params = params(helper);

        for (ResourceKey<LootTable> key : List.of(
                OctiaLoot.RUIN_STORE, OctiaLoot.RUIN_STORE_OLD, OctiaLoot.STATION_STORE)) {
            int items = 0;
            for (int attempt = 0; attempt < 8; attempt++) {
                items += resolve(helper, key).getRandomItems(params).size();
            }
            if (items == 0) {
                throw new AssertionError(key.location() + " loaded but produced nothing in 8 rolls");
            }
        }
        helper.succeed();
    }

    /**
     * A dig set to Octia's table actually gives something up.
     *
     * <p>{@code BrushableBlockEntity} has no getter for the table it was handed,
     * so this cannot check the wiring by reading it back - which turns out to be
     * the better test anyway. Setting the table and unpacking it exercises the
     * whole path a player walks: the block, the table, and an item in the hand.
     * An unresolvable table gets through {@code setLootTable} without complaint
     * and only shows up here, as nothing.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDigGivesUpSomething(GameTestHelper helper) {
        helper.setBlock(SPOT.below(), Blocks.GRAVEL);
        helper.setBlock(SPOT, Blocks.SUSPICIOUS_GRAVEL);

        BlockPos absolute = helper.absolutePos(SPOT);
        if (!(helper.getLevel().getBlockEntity(absolute) instanceof BrushableBlockEntity brush)) {
            throw new AssertionError("suspicious gravel has no brushable block entity");
        }

        brush.setLootTable(OctiaLoot.RUIN_DIG, 1L);
        brush.unpackLootTable(helper.makeMockPlayer(GameType.SURVIVAL));

        if (brush.getItem().isEmpty()) {
            throw new AssertionError("a dig carrying " + OctiaLoot.RUIN_DIG.location()
                    + " unpacked to nothing");
        }
        helper.succeed();
    }

    /**
     * A bindle out of a ruin's store is somebody's, not an empty bag.
     *
     * <p>The whole reason the item exists. An empty bindle in a barrel is
     * packaging; one with bread and a torch and a piece of hull in it is a
     * person who left. That difference is a {@code set_contents} function on one
     * loot entry, and a malformed one does not crash - the table resolves to
     * {@code LootTable.EMPTY}, every ruin in the world goes quiet, and the only
     * sign is a line in a log. Which is what {@code everyTableLoads} above
     * catches, and this catches the narrower version: the table loads, the
     * bindle drops, and the bindle is empty.
     *
     * <p>Also checks the capacity, because nothing in a loot table knows about
     * {@link Bindle#SLOTS}. The entry list is written to stay inside four; if it
     * ever grows past that, a found bindle would hold more than a crafted one
     * can, and the overflow would only appear when somebody tried to take
     * something out.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aFoundBindleIsPacked(GameTestHelper helper) {
        LootParams params = params(helper);

        int found = 0;
        for (ResourceKey<LootTable> key : List.of(
                OctiaLoot.RUIN_STORE, OctiaLoot.RUIN_STORE_OLD, OctiaLoot.STATION_STORE)) {
            LootTable store = resolve(helper, key);
            for (int attempt = 0; attempt < 200; attempt++) {
                for (ItemStack stack : store.getRandomItems(params)) {
                    if (!stack.is(OctiaItems.BINDLE)) {
                        continue;
                    }
                    found++;
                    int slots = BindleItem.slotsUsed(stack);
                    if (slots == 0) {
                        throw new AssertionError("a bindle out of " + key.location()
                                + " came up empty - set_contents is not reaching the item");
                    }
                    if (slots > Bindle.SLOTS) {
                        throw new AssertionError("a bindle out of " + key.location() + " held "
                                + slots + " stacks, which is more than a bindle can hold");
                    }
                }
            }
        }

        // Two hundred rolls a table over three tables, two to six items a roll,
        // and a bindle is weight 3 of 62 in the ruin store, 2 of 50 in the old
        // one, 4 of 45 at a station. Seeing none is not bad luck, it is the
        // entry gone.
        //
        // Those three fractions are the whole reason this number is written
        // down. The second pool each store gained is a roll of its own and
        // therefore dilutes nothing here - but an entry added to a STAPLES pool
        // moves the denominator, and the fix for that is to raise the bindle's
        // weight to match, not to lower this floor.
        if (found == 0) {
            throw new AssertionError("600 rolls of the three stores yielded no bindle at all");
        }
        helper.succeed();
    }

    /**
     * A hull panel is in the ground where a ship failed.
     *
     * <p>The one item that says Octia was here rather than somebody was. If the
     * signature ever falls out of the table, a dig becomes indistinguishable
     * from any other archaeology in the game.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aDigCanYieldAPieceOfHull(GameTestHelper helper) {
        LootParams params = params(helper);

        LootTable dig = resolve(helper, OctiaLoot.RUIN_DIG);
        for (int attempt = 0; attempt < 400; attempt++) {
            for (ItemStack stack : dig.getRandomItems(params)) {
                if (stack.is(OctiaBlocks.ANDESITE_FRAME_PANEL.asItem())) {
                    helper.succeed();
                    return;
                }
            }
        }
        throw new AssertionError("400 rolls of " + OctiaLoot.RUIN_DIG.location()
                + " never yielded a frame panel - the signature is gone from the table");
    }

    /**
     * The rig can be found by playing, which it could not be before.
     *
     * <p><b>This is the assertion the whole table pass exists for.</b> The
     * sail-rig was registered, modelled, named, given a recipe and a creative
     * tab entry, and put in no loot table at all - so the only way to meet the
     * mod's traversal item was to already know it existed and to already have
     * the panels to craft it. A full session was played without one ever being
     * held. An item nobody can find is an item that is not in the game, and
     * nothing in a build catches that, because every part of it works.
     *
     * <p>Four hundred rolls against a one-in-twelve entry: seeing none is odds
     * of about eight in a thousand million million, so a failure here is the
     * entry gone rather than a run of bad luck. The number is chosen to be
     * survivable if the owner makes the rig rarer by a factor of ten.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aRuinStoreCanYieldTheRig(GameTestHelper helper) {
        LootParams params = params(helper);

        LootTable store = resolve(helper, OctiaLoot.RUIN_STORE);
        for (int attempt = 0; attempt < 400; attempt++) {
            for (ItemStack stack : store.getRandomItems(params)) {
                if (stack.is(OctiaItems.SAIL_RIG)) {
                    helper.succeed();
                    return;
                }
            }
        }
        throw new AssertionError("400 rolls of " + OctiaLoot.RUIN_STORE.location()
                + " never yielded a sail-rig - the mod's traversal item cannot be found by playing, "
                + "which is the state this table pass was written to end");
    }

    /**
     * A station wreck always carries the rig and always carries a piece of hull.
     *
     * <p>Every roll, not one roll in some - and that is the decision being
     * asserted rather than the odds. A station is about one chunk in 1,575 and
     * it is the only ruin a player is <em>led</em> to: sight down an obelisk,
     * walk the tangent, and the wreck is where the line said. Rolling for the
     * reward at the far end of that walk stacks a second dice throw on the one
     * that already made the site rare, and a player who arrives and finds
     * bread learns that following a sightline is not worth doing twice.
     *
     * <p>Thirty-two rolls rather than four hundred because a guarantee fails on
     * the first roll or not at all. What would break it is somebody folding the
     * two single-entry pools into the staples pool, where a weight makes them
     * merely likely - and that reads as a tidy-up, which is exactly why it
     * wants a test standing over it.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aStationStoreAlwaysCarriesTheRigAndHull(GameTestHelper helper) {
        LootParams params = params(helper);

        LootTable store = resolve(helper, OctiaLoot.STATION_STORE);
        for (int attempt = 0; attempt < 32; attempt++) {
            boolean rig = false;
            boolean hull = false;
            for (ItemStack stack : store.getRandomItems(params)) {
                rig |= stack.is(OctiaItems.SAIL_RIG);
                hull |= stack.is(OctiaBlocks.ANDESITE_FRAME_PANEL.asItem());
            }
            if (!rig) {
                throw new AssertionError("roll " + attempt + " of "
                        + OctiaLoot.STATION_STORE.location() + " carried no sail-rig. The rarest "
                        + "site in the mod promises one every time, and a pool that rolls for it "
                        + "is not that promise");
            }
            if (!hull) {
                throw new AssertionError("roll " + attempt + " of "
                        + OctiaLoot.STATION_STORE.location() + " carried no frame panel - the "
                        + "wreck that reached a node is meant to hand over a piece of itself");
            }
        }
        helper.succeed();
    }

    /**
     * Ink is in the old store, because writing is what an old ruin keeps.
     *
     * <p>Ink is the material this world's writing is made of and it has a source
     * in the world - the squid - so a sac in a barrel that lost its food and its
     * tools is the page that faded rather than a squid that wandered indoors.
     * Vanilla {@code ink_sac} and no new item, so the two lanes that care about
     * ink cannot disagree about what ink is.
     *
     * <p>Four hundred rolls against a two-in-five entry. This is a floor on the
     * entry existing, not on the odds: the owner may take ink down a long way
     * before this notices, and that is the intent.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theOldStoreHoldsInk(GameTestHelper helper) {
        LootParams params = params(helper);

        LootTable store = resolve(helper, OctiaLoot.RUIN_STORE_OLD);
        for (int attempt = 0; attempt < 400; attempt++) {
            for (ItemStack stack : store.getRandomItems(params)) {
                if (stack.is(Items.INK_SAC)) {
                    helper.succeed();
                    return;
                }
            }
        }
        throw new AssertionError("400 rolls of " + OctiaLoot.RUIN_STORE_OLD.location()
                + " never yielded ink - the writing that faded is gone from the old loot");
    }
}
