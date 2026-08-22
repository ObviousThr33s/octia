package com.serenity.octia.gametest;

import java.util.List;

import com.serenity.octia.OctiaItems;
import com.serenity.octia.item.Bindle;
import com.serenity.octia.item.BindleItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * What a bindle will and will not carry.
 *
 * <p>The sums are pinned by {@code BindleTest} without a game running. What
 * needs the game is everything that touches an {@code ItemStack}: the container
 * component, stack limits that come from the item rather than from a constant,
 * and the refusal to nest. None of those can be reached from plain JUnit,
 * because every one of them wants registries that only exist once a server is
 * up - which is the split {@code docs/DEVOPS.md} describes.
 *
 * <p>No inventory screen is driven here. {@code overrideStackedOnOther} needs a
 * menu, a cursor and a slot, and a test that builds those tests Minecraft's
 * click handling rather than the bindle. What is ours is what goes in the bag.
 */
public class BindleGameTest implements FabricGameTest {

    /** The item is in the registry under the path this mod names, and it is ours. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theBindleIsRegistered(GameTestHelper helper) {
        ItemStack bindle = new ItemStack(OctiaItems.BINDLE);
        if (!(bindle.getItem() instanceof BindleItem)) {
            throw new AssertionError("octia:bindle did not resolve to a BindleItem");
        }
        if (!BuiltInRegistries.ITEM.getKey(OctiaItems.BINDLE).getPath().equals("bindle")) {
            throw new AssertionError("the bindle is registered under an unexpected path");
        }
        if (bindle.getMaxStackSize() != 1) {
            throw new AssertionError("a bindle stacked past one, which would give two bags one set of contents");
        }
        helper.succeed();
    }

    /**
     * Four stacks, then no more - and the fifth offer is refused rather than
     * silently swallowed.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fourStacksAndNoMore(GameTestHelper helper) {
        ItemStack bindle = new ItemStack(OctiaItems.BINDLE);
        List<ItemStack> offers = List.of(
                new ItemStack(Items.BREAD, 4),
                new ItemStack(Items.TORCH, 4),
                new ItemStack(Items.COAL, 4),
                new ItemStack(Items.STRING, 4));

        for (ItemStack offer : offers) {
            if (BindleItem.add(bindle, offer) != 4) {
                throw new AssertionError(offer.getItem() + " did not go in whole");
            }
        }
        if (BindleItem.slotsUsed(bindle) != Bindle.SLOTS) {
            throw new AssertionError("four different stacks did not fill four slots");
        }
        if (BindleItem.add(bindle, new ItemStack(Items.APPLE, 1)) != 0) {
            throw new AssertionError("a full bindle took a fifth kind of thing");
        }
        helper.succeed();
    }

    /**
     * A matching stack is topped up before a slot is spent, and the top-up
     * still works when every slot is taken.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void matchingStacksTopUp(GameTestHelper helper) {
        ItemStack bindle = new ItemStack(OctiaItems.BINDLE);
        BindleItem.add(bindle, new ItemStack(Items.BREAD, 1));
        BindleItem.add(bindle, new ItemStack(Items.TORCH, 1));
        BindleItem.add(bindle, new ItemStack(Items.COAL, 1));
        BindleItem.add(bindle, new ItemStack(Items.STRING, 1));

        if (BindleItem.add(bindle, new ItemStack(Items.BREAD, 8)) != 8) {
            throw new AssertionError("a full bindle refused more of something already inside");
        }
        if (BindleItem.slotsUsed(bindle) != Bindle.SLOTS) {
            throw new AssertionError("topping up opened a slot that does not exist");
        }
        ItemStack bread = BindleItem.contents(bindle).get(0);
        if (!bread.is(Items.BREAD) || bread.getCount() != 9) {
            throw new AssertionError("the bread stack was not topped up to nine, it holds " + bread.getCount());
        }
        helper.succeed();
    }

    /**
     * What {@code capacity} promises is what {@code add} does.
     *
     * <p>They are two statements of one rule - the prediction in integers that
     * {@code BindleTest} pins, and the loop that moves real stacks - and the
     * click path trusts the first before touching the second. If they ever
     * disagree, an item is either doubled or deleted at the moment somebody
     * right-clicks a stack onto a bindle.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void capacityIsWhatGoesIn(GameTestHelper helper) {
        List<ItemStack> offers = List.of(
                new ItemStack(Items.BREAD, 40),
                new ItemStack(Items.BREAD, 40),
                new ItemStack(Items.ENDER_PEARL, 40),
                new ItemStack(Items.WATER_BUCKET, 3),
                new ItemStack(Items.COAL, 64),
                new ItemStack(Items.APPLE, 1));

        ItemStack bindle = new ItemStack(OctiaItems.BINDLE);
        for (ItemStack offer : offers) {
            int promised = BindleItem.capacity(bindle, offer);
            int taken = BindleItem.add(bindle, offer.copy());
            if (promised != taken) {
                throw new AssertionError("capacity promised " + promised + " of " + offer.getItem()
                        + " and add took " + taken);
            }
        }
        helper.succeed();
    }

    /** Last in, first out, and an emptied bindle is empty rather than nearly so. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void lastInFirstOut(GameTestHelper helper) {
        ItemStack bindle = new ItemStack(OctiaItems.BINDLE);
        BindleItem.add(bindle, new ItemStack(Items.BREAD, 2));
        BindleItem.add(bindle, new ItemStack(Items.COAL, 3));

        ItemStack first = BindleItem.takeLast(bindle);
        if (!first.is(Items.COAL) || first.getCount() != 3) {
            throw new AssertionError("the last stack in was not the first one out");
        }
        ItemStack second = BindleItem.takeLast(bindle);
        if (!second.is(Items.BREAD) || second.getCount() != 2) {
            throw new AssertionError("the bread did not come back out whole");
        }
        if (!BindleItem.takeLast(bindle).isEmpty() || BindleItem.slotsUsed(bindle) != 0) {
            throw new AssertionError("an emptied bindle still had something in it");
        }
        helper.succeed();
    }

    /**
     * No nesting, in either direction.
     *
     * <p>A bindle inside a bindle is how one item ends up holding a save file's
     * worth of items, and a shulker box inside one is the same trick with an
     * extra step - which is why the refusal is on contents, not on the item.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void nothingNests(GameTestHelper helper) {
        ItemStack bindle = new ItemStack(OctiaItems.BINDLE);

        if (BindleItem.add(bindle, new ItemStack(OctiaItems.BINDLE)) != 0) {
            throw new AssertionError("a bindle went into a bindle");
        }
        ItemStack packed = OctiaItems.forTheRoad(RandomSource.create(7));
        if (BindleItem.add(bindle, packed) != 0) {
            throw new AssertionError("a packed bindle went into a bindle");
        }
        ItemStack box = new ItemStack(Blocks.SHULKER_BOX);
        BindleItem.add(box, new ItemStack(Items.BREAD, 1));
        if (BindleItem.add(bindle, box) != 0) {
            throw new AssertionError("something carrying its own contents went in");
        }
        if (BindleItem.slotsUsed(bindle) != 0) {
            throw new AssertionError("a refused offer still took a slot");
        }
        helper.succeed();
    }

    /**
     * What a wayfarer leaves behind is packed, but never full.
     *
     * <p>A full bindle is one nobody was using. Rolled across several seeds
     * because the packing is random and one draw proves nothing.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRoadPacksTwoOrThree(GameTestHelper helper) {
        for (long seed = 0; seed < 12; seed++) {
            ItemStack bindle = OctiaItems.forTheRoad(RandomSource.create(seed));
            int used = BindleItem.slotsUsed(bindle);
            if (used < 2 || used > Bindle.SLOTS - 1) {
                throw new AssertionError("a road bindle held " + used + " stacks on seed " + seed);
            }
            if (!bindle.is(OctiaItems.BINDLE)) {
                throw new AssertionError("forTheRoad returned something that is not a bindle");
            }
        }
        helper.succeed();
    }
}
