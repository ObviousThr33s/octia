package com.serenity.octia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.serenity.octia.Octia;
import com.serenity.octia.OctiaItems;
import com.serenity.octia.item.Bindle;
import com.serenity.octia.item.BindleItem;
import com.serenity.octia.item.CubeItem;
import com.serenity.octia.item.CubePockets;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Whose inside each cube opens.
 *
 * <p>The four slots and the last-in-first-out order are the bindle's and are
 * already pinned by {@code BindleTest} and {@code BindleGameTest}; nothing here
 * re-tests the arithmetic for its own sake. What is new is a question the
 * bindle never had to answer - <b>where does the inside live</b> - and every
 * method below asserts that decision rather than the machinery under it: a
 * sealed cube's contents travel with the stack, two gold cubes in one hand are
 * one pocket, and two purple cubes anywhere in the save are one pocket.
 *
 * <p>No inventory screen is driven, for {@code BindleGameTest}'s reason: a test
 * that builds a menu, a cursor and a slot tests Minecraft's click handling. The
 * seams the click path calls - {@code roomIn}, {@code stow}, {@code drawLast} -
 * are what this drives, because those are the three methods the three cubes
 * disagree about.
 *
 * <p><b>The road pocket is the one piece of shared state in this file.</b> It
 * is keyed by a constant, so it outlives a test method and is visible to every
 * other one; {@link #theRoadIsOneBagForTheWholeSave} is therefore the only
 * method that asserts anything about its contents, and it empties it first.
 * Every other pocket here belongs to a mock player whose UUID was minted a line
 * earlier - {@code makeMockPlayer} mints a fresh one per call - and so cannot
 * have been touched by anything.
 */
public class CubeGameTest implements FabricGameTest {

    /** Three items, three insides, and each one stacks to a single door. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theThreeCubesAreRegistered(GameTestHelper helper) {
        registered(OctiaItems.RED_CUBE, "red_cube", CubeItem.Kind.SEALED);
        registered(OctiaItems.GOLD_CUBE, "gold_cube", CubeItem.Kind.OWN);
        registered(OctiaItems.PURPLE_CUBE, "purple_cube", CubeItem.Kind.ROAD);
        helper.succeed();
    }

    /**
     * A red cube's inside travels with the stack, and a copy is a second cube
     * rather than a second door.
     *
     * <p>Both halves matter. The first is what makes a sealed cube a message
     * somebody left: whatever a hopper, a death or a chest moves, the contents
     * go with it. The second is what makes it <em>sealed</em> - emptying one
     * must not empty a copy, which is exactly what would happen if the red cube
     * had been given a pocket like the other two.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aSealedCubeCarriesItsInside(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CubeItem red = OctiaItems.RED_CUBE;

        if (red.pocketKey(player) != null) {
            throw new AssertionError("the sealed cube named a pocket, so its inside is not on its stack");
        }

        ItemStack cube = new ItemStack(OctiaItems.RED_CUBE);
        if (red.stow(cube, new ItemStack(Items.BREAD, 3), player) != 3) {
            throw new AssertionError("a red cube would not take three bread");
        }

        ItemStack copy = cube.copy();
        List<ItemStack> held = BindleItem.contents(copy);
        if (held.size() != 1 || !held.get(0).is(Items.BREAD) || held.get(0).getCount() != 3) {
            throw new AssertionError("a copied red cube did not carry what the original held");
        }

        red.drawLast(cube, player);
        if (BindleItem.slotsUsed(copy) != 1) {
            throw new AssertionError("emptying a red cube emptied a copy of it, so the two share one inside");
        }
        helper.succeed();
    }

    /**
     * A gold cube is whoever is holding it.
     *
     * <p>Two cubes and one hand is the feature: not two bags, one bag with two
     * doors. The second player is the half that makes it mean anything - if a
     * gold cube opened one bag for everybody it would be the purple one.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aGoldCubeIsWhoeverHoldsIt(GameTestHelper helper) {
        CubeItem gold = OctiaItems.GOLD_CUBE;
        Player one = helper.makeMockPlayer(GameType.SURVIVAL);
        Player two = helper.makeMockPlayer(GameType.SURVIVAL);

        if (!gold.pocketKey(one).equals(one.getUUID().toString())) {
            throw new AssertionError("a gold cube keyed its pocket by something other than the holder");
        }
        if (gold.pocketKey(one).equals(gold.pocketKey(two))) {
            throw new AssertionError("two different players resolved to one gold pocket");
        }

        ItemStack left = new ItemStack(OctiaItems.GOLD_CUBE);
        ItemStack right = new ItemStack(OctiaItems.GOLD_CUBE);

        if (gold.stow(left, new ItemStack(Items.COAL, 7), one) != 7) {
            throw new AssertionError("a gold cube would not take seven coal");
        }
        ItemStack out = gold.drawLast(right, one);
        if (!out.is(Items.COAL) || out.getCount() != 7) {
            throw new AssertionError("a second gold cube in the same hand opened a different pocket");
        }

        gold.stow(right, out, one);
        if (!gold.drawLast(left, two).isEmpty()) {
            throw new AssertionError("another player's gold cube reached into this player's pocket");
        }
        if (gold.drawLast(left, one).isEmpty()) {
            throw new AssertionError("the second player's empty draw emptied the first player's pocket");
        }
        helper.succeed();
    }

    /**
     * Two purple cubes anywhere in the save are one inside.
     *
     * <p>Two players are used rather than one because the road pocket is not
     * keyed by anybody: if this passed with one player and failed with two, the
     * cube would be a gold one wearing purple. The cap and the order are
     * checked here as well, because this is the only method allowed to touch
     * the road pocket's contents - see the class note.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRoadIsOneBagForTheWholeSave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CubeItem purple = OctiaItems.PURPLE_CUBE;
        Player one = helper.makeMockPlayer(GameType.SURVIVAL);
        Player two = helper.makeMockPlayer(GameType.SURVIVAL);

        if (!purple.pocketKey(one).equals(purple.pocketKey(two))) {
            throw new AssertionError("two players resolved two road pockets");
        }
        CubePockets.get(server).write(CubePockets.ROAD, new ArrayList<>());

        ItemStack here = new ItemStack(OctiaItems.PURPLE_CUBE);
        ItemStack far = new ItemStack(OctiaItems.PURPLE_CUBE);

        if (purple.stow(here, new ItemStack(Items.BREAD, 5), one) != 5) {
            throw new AssertionError("a purple cube would not take five bread");
        }
        ItemStack out = purple.drawLast(far, two);
        if (!out.is(Items.BREAD) || out.getCount() != 5) {
            throw new AssertionError("what one purple cube swallowed did not come out of another one");
        }

        capAndOrder(purple, far, two);
        helper.succeed();
    }

    /**
     * Four slots and last-in-first-out, whichever store is underneath.
     *
     * <p>The sums are the bindle's and are pinned without a world. What is
     * asserted here is that the pocket path did not quietly get its own rules -
     * a store that forgot to write back would pass every capacity check and
     * lose the items on the way out.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fourSlotsAndLastInFirstOutForEveryInside(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        capAndOrder(OctiaItems.RED_CUBE, new ItemStack(OctiaItems.RED_CUBE), player);
        capAndOrder(OctiaItems.GOLD_CUBE, new ItemStack(OctiaItems.GOLD_CUBE), player);
        helper.succeed();
    }

    /**
     * No bag goes inside any bag, in any of the sixteen pairings.
     *
     * <p>Every pair is walked because the refusals do not all come from the
     * same clause: a bindle and a packed red cube are caught by their contents,
     * an empty red cube by its class, and a gold or purple cube <em>only</em>
     * by its class, because their stacks hold nothing to notice. One of those
     * three going missing costs a save file's worth of items in one bag.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void noBagGoesInsideAnyBag(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<BindleItem> bags = List.of(
                OctiaItems.BINDLE, OctiaItems.RED_CUBE, OctiaItems.GOLD_CUBE, OctiaItems.PURPLE_CUBE);

        ItemStack packed = new ItemStack(OctiaItems.RED_CUBE);
        BindleItem.add(packed, new ItemStack(Items.BREAD, 1));

        for (BindleItem holder : bags) {
            ItemStack into = new ItemStack(holder);
            for (BindleItem inner : bags) {
                if (holder.stow(into, new ItemStack(inner), player) != 0) {
                    throw new AssertionError(name(inner) + " went inside " + name(holder));
                }
            }
            if (holder.stow(into, packed.copy(), player) != 0) {
                throw new AssertionError("a packed red cube went inside " + name(holder));
            }
        }
        helper.succeed();
    }

    /**
     * A cube whose inside is elsewhere draws no fullness bar.
     *
     * <p>This is the honest-degradation decision, asserted rather than argued.
     * The bar is drawn client-side from the stack alone, and a pocket cube's
     * stack holds nothing - so a bar drawn from it would read as empty to a
     * player whose pocket is full. Nothing drawn says nothing. The sealed cube
     * is the contrast in the same method: its stack <em>is</em> the store, so it
     * draws exactly what a bindle would.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aPocketCubeDrawsNoBar(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        CubeItem gold = OctiaItems.GOLD_CUBE;
        ItemStack cube = new ItemStack(OctiaItems.GOLD_CUBE);
        gold.stow(cube, new ItemStack(Items.BREAD, 1), player);

        if (gold.isBarVisible(cube) || gold.getBarWidth(cube) != 0) {
            throw new AssertionError("a gold cube drew a fullness bar out of a stack that holds nothing");
        }
        if (!BindleItem.contents(cube).isEmpty()) {
            throw new AssertionError("a pocket-backed cube wrote its inside onto its own stack");
        }

        CubeItem red = OctiaItems.RED_CUBE;
        ItemStack sealed = new ItemStack(OctiaItems.RED_CUBE);
        red.stow(sealed, new ItemStack(Items.BREAD, 1), player);
        if (!red.isBarVisible(sealed) || red.getBarWidth(sealed) != Bindle.barWidth(1)) {
            throw new AssertionError("a packed red cube drew no bar, though its stack is its store");
        }
        helper.succeed();
    }

    // ---- Shared checks --------------------------------------------------------

    private static void registered(CubeItem cube, String path, CubeItem.Kind kind) {
        // Looked up by id rather than asserted about the field, because the id
        // is what a recipe, a /give and a model file all name. A field of the
        // right type registered under the wrong path passes every other check
        // here and ships an item nothing can reach.
        Item found = BuiltInRegistries.ITEM.get(Octia.id(path));
        if (found != cube) {
            throw new AssertionError("octia:" + path + " resolved to " + found + " rather than the cube");
        }
        if (!(found instanceof CubeItem)) {
            throw new AssertionError("octia:" + path + " did not resolve to a CubeItem");
        }
        ItemStack stack = new ItemStack(cube);
        if (stack.getMaxStackSize() != 1) {
            throw new AssertionError(path + " stacked past one; a sealed cube would then be"
                    + " two sets of contents sharing one component");
        }
        if (cube.kind() != kind) {
            throw new AssertionError(path + " opens a " + cube.kind() + " inside");
        }
    }

    /**
     * Fills a bag to its cap, refuses the fifth kind, and empties it in reverse.
     *
     * <p>{@code roomIn} is compared with {@code stow} on every offer for
     * {@code BindleGameTest.capacityIsWhatGoesIn}'s reason: the click path
     * trusts the promise before touching the store, so a disagreement doubles
     * or deletes an item at the moment somebody right-clicks. The bag is left
     * empty, which is what lets a pocket be reused by the next line.
     */
    private static void capAndOrder(CubeItem cube, ItemStack bag, Player player) {
        if (Bindle.SLOTS != 4) {
            throw new AssertionError("this check fills four slots and a bag now holds " + Bindle.SLOTS);
        }
        List<ItemStack> offers = List.of(
                new ItemStack(Items.BREAD, 4),
                new ItemStack(Items.TORCH, 4),
                new ItemStack(Items.COAL, 4),
                new ItemStack(Items.STRING, 4));

        for (ItemStack offer : offers) {
            int promised = cube.roomIn(bag, offer, player);
            int taken = cube.stow(bag, offer.copy(), player);
            if (promised != taken) {
                throw new AssertionError(name(cube) + " promised " + promised + " of "
                        + offer.getItem() + " and took " + taken);
            }
            if (taken != 4) {
                throw new AssertionError(offer.getItem() + " did not go whole into " + name(cube));
            }
        }
        if (cube.stow(bag, new ItemStack(Items.APPLE, 1), player) != 0) {
            throw new AssertionError("a full " + name(cube) + " took a fifth kind of thing");
        }

        for (int i = offers.size() - 1; i >= 0; i--) {
            ItemStack out = cube.drawLast(bag, player);
            ItemStack expected = offers.get(i);
            if (!out.is(expected.getItem()) || out.getCount() != expected.getCount()) {
                throw new AssertionError(name(cube) + " gave back " + out + " where " + expected
                        + " went in " + (offers.size() - i) + " from last");
            }
        }
        if (!cube.drawLast(bag, player).isEmpty()) {
            throw new AssertionError("an emptied " + name(cube) + " still had something in it");
        }
    }

    private static String name(BindleItem bag) {
        return BuiltInRegistries.ITEM.getKey(bag).toString();
    }
}
