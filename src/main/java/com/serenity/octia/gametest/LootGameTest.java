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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
 */
public class LootGameTest implements FabricGameTest {

    private static final BlockPos SPOT = new BlockPos(2, 2, 2);

    /** The tables, and how many items each should be capable of producing. */
    private static LootTable resolve(GameTestHelper helper, ResourceKey<LootTable> key) {
        return helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
    }

    /** Every table Octia names is a table the server actually has. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyTableLoads(GameTestHelper helper) {
        for (ResourceKey<LootTable> key : List.of(
                OctiaLoot.RUIN_DIG, OctiaLoot.RUIN_STORE, OctiaLoot.RUIN_STORE_OLD)) {
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
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(SPOT)))
                .create(LootContextParamSets.CHEST);

        for (ResourceKey<LootTable> key : List.of(OctiaLoot.RUIN_STORE, OctiaLoot.RUIN_STORE_OLD)) {
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
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(SPOT)))
                .create(LootContextParamSets.CHEST);

        int found = 0;
        for (ResourceKey<LootTable> key : List.of(OctiaLoot.RUIN_STORE, OctiaLoot.RUIN_STORE_OLD)) {
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

        // Two hundred rolls a table, three to six items a roll, and a bindle is
        // weight 3 of 62. Seeing none is not bad luck, it is the entry gone.
        if (found == 0) {
            throw new AssertionError("400 rolls of the two stores yielded no bindle at all");
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
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(SPOT)))
                .create(LootContextParamSets.CHEST);

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
}
