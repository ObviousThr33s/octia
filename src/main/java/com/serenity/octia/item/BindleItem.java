package com.serenity.octia.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A cloth tied to a stick, and what somebody was carrying in it.
 *
 * <p>The ruins are empty and the strangers keep their distance, so almost
 * everything the world says it says through objects. A bindle is the smallest
 * object that says something: four stacks, no screen, and whatever is inside it
 * was chosen by whoever tied it. Found in a wreck it is a person's belongings;
 * left on the road by a wayfarer it is the only thing they ever hand over.
 *
 * <p><b>It stores nothing of its own.</b> The contents live in
 * {@link DataComponents#CONTAINER}, vanilla's own component - the same one a
 * picked-up shulker box uses. That is what makes a bindle survive a hopper, a
 * death, a chest, and the save file without a line of NBT written here, and it
 * is why {@code /give} with a container component simply works.
 *
 * <p><b>No screen, on purpose.</b> Opening a menu would need a container, a
 * menu type, a screen on the client and a sync path between them - and would
 * make a bindle a small shulker box. Instead it is worked the way vanilla's
 * bundle is: right-click a stack onto it to put that stack in, right-click it
 * onto an empty slot to take the last one back out. Last in, first out, because
 * a bag with a stick through it does not have an order.
 *
 * <p><b>What it refuses.</b> Bindles, and anything already carrying contents of
 * its own. Nesting containers is how a single item ends up holding a save file's
 * worth of items, and vanilla holds the same line for the same reason.
 *
 * <p><b>Three siblings, one choreography.</b> {@code [2026-08-24]} the cubes
 * arrived, and two of the three keep their contents somewhere other than the
 * stack they are drawn on. Nothing about a bindle changed to make room for
 * them. What was added is a seam: the click path below asks {@link #roomIn},
 * {@link #stow} and {@link #drawLast} instead of reaching for the static sums
 * directly, and a bindle's answers to those three <em>are</em> the static sums.
 * {@link CubeItem} overrides them and inherits every line of the gesture, which
 * is why there is one right-click behaviour in this mod and not four.
 */
public class BindleItem extends Item {

    public BindleItem(Properties properties) {
        super(properties);
    }

    // ---- The contents --------------------------------------------------------

    /** What is inside, as a mutable copy. Never the component's own list. */
    public static List<ItemStack> contents(ItemStack bindle) {
        List<ItemStack> held = new ArrayList<>();
        for (ItemStack stack : bindle.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyItems()) {
            held.add(stack.copy());
        }
        return held;
    }

    /** Writes a list back, dropping any empties so the slot count stays honest. */
    private static void store(ItemStack bindle, List<ItemStack> held) {
        held.removeIf(ItemStack::isEmpty);
        bindle.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(held));
    }

    /**
     * Whether a bindle will take this at all. See the class note on nesting.
     *
     * <p>The cube clause is redundant today, because every cube is a
     * {@link BindleItem}. It is written out anyway, because the refusal must
     * not rest on that: a gold or a purple cube carries no
     * {@link DataComponents#CONTAINER} of its own - its inside lives in
     * {@link CubePockets} - so the contents clause cannot see it, and a later
     * cube that stopped extending this class would start nesting in silence.
     */
    public static boolean mayHold(ItemStack offered) {
        return !offered.isEmpty()
                && !(offered.getItem() instanceof BindleItem)
                && !(offered.getItem() instanceof CubeItem)
                && !offered.has(DataComponents.CONTAINER);
    }

    /**
     * How much of an offered stack would fit, without putting any of it in.
     *
     * <p>This is the prediction {@link Bindle#intake} states in integers, given
     * real stacks to measure: the room left in every matching stack inside, and
     * a full stack per slot still free. {@link #add} is bounded by it, so the
     * sums a JUnit test pins are the same sums the game runs.
     */
    public static int capacity(ItemStack bindle, ItemStack offered) {
        return capacityIn(contents(bindle), offered);
    }

    /**
     * The same prediction, against a list somebody else is keeping.
     *
     * <p>Split out {@code [2026-08-24]} when the cubes arrived. Two of the
     * three keep their contents in {@link CubePockets}, so the sums had to stop
     * assuming that the bag and the store are the same object. No arithmetic
     * moved and none changed - only what it is handed.
     */
    public static int capacityIn(List<ItemStack> held, ItemStack offered) {
        if (!mayHold(offered)) {
            return 0;
        }
        int[] matching = held.stream()
                .filter(inside -> ItemStack.isSameItemSameComponents(inside, offered))
                .mapToInt(inside -> inside.getMaxStackSize() - inside.getCount())
                .toArray();
        return Bindle.intake(offered.getCount(), offered.getMaxStackSize(), matching,
                Bindle.SLOTS - held.size());
    }

    /**
     * Puts as much of an offered stack in as fits, and says how much went.
     *
     * <p>The caller is the one that removes what was taken, because only the
     * caller knows where the offered stack came from - a cursor, a slot, or a
     * list being packed before anything is in the world at all.
     *
     * @return how many items were taken, zero if none were
     */
    public static int add(ItemStack bindle, ItemStack offered) {
        List<ItemStack> held = contents(bindle);
        int taken = addTo(held, offered);
        if (taken > 0) {
            store(bindle, held);
        }
        return taken;
    }

    /**
     * The same packing, into a list somebody else is keeping.
     *
     * <p>The list is written through, so the caller is the one that saves it -
     * a stack-backed bag writes the container component back, a pocket-backed
     * one calls {@link CubePockets#write}. See {@link #capacityIn} for why the
     * split exists.
     *
     * @return how many items were taken, zero if none were
     */
    public static int addTo(List<ItemStack> held, ItemStack offered) {
        int fits = capacityIn(held, offered);
        if (fits <= 0) {
            return 0;
        }
        int taken = 0;

        // Top up what matches first, then open slots. A bindle that opened a
        // new slot for every handful would be four slots of ones.
        for (ItemStack inside : held) {
            if (taken >= fits) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(inside, offered)) {
                int move = Math.min(inside.getMaxStackSize() - inside.getCount(), fits - taken);
                if (move > 0) {
                    inside.grow(move);
                    taken += move;
                }
            }
        }
        while (taken < fits && Bindle.hasRoom(held.size())) {
            int move = Math.min(offered.getMaxStackSize(), fits - taken);
            held.add(offered.copyWithCount(move));
            taken += move;
        }
        return taken;
    }

    /**
     * Takes the last stack back out, or {@link ItemStack#EMPTY} if there is none.
     *
     * <p>Last in, first out. A bindle has no slots you can see, so there is no
     * such thing as reaching for the one at the bottom.
     */
    public static ItemStack takeLast(ItemStack bindle) {
        List<ItemStack> held = contents(bindle);
        ItemStack out = takeLastFrom(held);
        if (!out.isEmpty()) {
            store(bindle, held);
        }
        return out;
    }

    /**
     * The same draw, out of a list somebody else is keeping.
     *
     * <p>Mutates the list and hands the stack back without saving anything,
     * for the reason {@link #addTo} does not save either.
     */
    public static ItemStack takeLastFrom(List<ItemStack> held) {
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return held.remove(held.size() - 1);
    }

    /** How many slots are in use. */
    public static int slotsUsed(ItemStack bindle) {
        return contents(bindle).size();
    }

    // ---- Where the contents are kept ----------------------------------------

    /**
     * How much of an offered stack this bag would take, from this player's hand.
     *
     * <p>A bindle's contents are on its own stack, so the player is not
     * consulted and this is {@link #capacity}. A cube that opens a pocket needs
     * the player to know <em>which</em> pocket, and needs a server to reach it -
     * see {@link CubeItem}.
     */
    public int roomIn(ItemStack bag, ItemStack offered, Player player) {
        return capacity(bag, offered);
    }

    /** Puts what fits in, and says how much went. The pair of {@link #roomIn}. */
    public int stow(ItemStack bag, ItemStack offered, Player player) {
        return add(bag, offered);
    }

    /** Takes the last stack back out, or {@link ItemStack#EMPTY} if there is none. */
    public ItemStack drawLast(ItemStack bag, Player player) {
        return takeLast(bag);
    }

    // ---- Working it in an inventory -----------------------------------------

    /**
     * The bindle is on the cursor and has been right-clicked onto a slot.
     *
     * <p>Onto something: that something goes in. Onto nothing: the last stack
     * comes back out into the empty slot.
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack bindle, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            ItemStack out = drawLast(bindle, player);
            if (out.isEmpty()) {
                return false;
            }
            ItemStack over = slot.safeInsert(out);
            // safeInsert can refuse part of it - a furnace fuel slot, a hotbar
            // rule, an armour slot. Whatever it would not take goes back rather
            // than being deleted, which is the entire reason this is not a
            // straight setItem.
            if (!over.isEmpty()) {
                stow(bindle, over, player);
            }
            play(player, false);
            return true;
        }
        // Taken out of the slot first, then put in, which is the order vanilla's
        // bundle uses and the only one that cannot double an item: safeTake is
        // the authority on how many actually left the slot, and only that many
        // are ever offered to the bag.
        int fits = roomIn(bindle, target, player);
        if (fits <= 0) {
            return false;
        }
        ItemStack removed = slot.safeTake(target.getCount(), fits, player);
        int taken = stow(bindle, removed, player);
        if (taken < removed.getCount()) {
            // Unreachable while capacity and add agree, and here anyway because
            // the alternative to being wrong about that is deleting somebody's
            // items. Whatever the bag would not hold goes back where it was.
            ItemStack spare = slot.safeInsert(removed.copyWithCount(removed.getCount() - taken));
            if (!spare.isEmpty()) {
                stow(bindle, spare, player);
            }
        }
        play(player, true);
        return true;
    }

    /**
     * The bindle is in a slot and something has been right-clicked onto it.
     *
     * <p>The mirror of the above, with the cursor rather than the slot as the
     * other side: a held stack goes in, an empty hand draws the last one out.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bindle, ItemStack other, Slot slot, ClickAction action,
            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        if (other.isEmpty()) {
            ItemStack out = drawLast(bindle, player);
            if (out.isEmpty()) {
                return false;
            }
            access.set(out);
            play(player, false);
            return true;
        }
        int taken = stow(bindle, other, player);
        if (taken == 0) {
            return false;
        }
        other.shrink(taken);
        play(player, true);
        return true;
    }

    /**
     * Vanilla's bundle sounds. A bindle is the same gesture, so it is the same
     * noise.
     *
     * <p>{@code BUNDLE_REMOVE_ONE}, not {@code BUNDLE_REMOVE}. The sound is
     * {@code item.bundle.remove_one} and the constant is named after the id, so
     * the shorter spelling compiles nowhere - which the first CI run on this
     * branch established in forty-two seconds, and no amount of reading it back
     * had.
     */
    private static void play(Player player, boolean in) {
        player.playSound(in ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F);
    }

    // ---- What it looks like --------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack bindle) {
        return slotsUsed(bindle) > 0;
    }

    @Override
    public int getBarWidth(ItemStack bindle) {
        return Bindle.barWidth(slotsUsed(bindle));
    }

    @Override
    public int getBarColor(ItemStack bindle) {
        // Andesite-lit, the mod's own colour rather than the bundle's green.
        return 0x9A8F7B;
    }

    /**
     * Says what is inside, because nothing else can.
     *
     * <p>A bindle has no screen, so the tooltip is the only way to know what you
     * are holding without emptying it onto the floor. Everything is listed -
     * four lines at most, which is what four slots buys.
     */
    @Override
    public void appendHoverText(ItemStack bindle, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        List<ItemStack> held = contents(bindle);
        if (held.isEmpty()) {
            lines.add(Component.translatable("item.octia.bindle.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (ItemStack stack : held) {
            lines.add(Component.translatable("item.octia.bindle.line", stack.getCount(), stack.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
