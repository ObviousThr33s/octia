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

    /** Whether a bindle will take this at all. See the class note on nesting. */
    public static boolean mayHold(ItemStack offered) {
        return !offered.isEmpty()
                && !(offered.getItem() instanceof BindleItem)
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
        if (!mayHold(offered)) {
            return 0;
        }
        List<ItemStack> held = contents(bindle);
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
        int fits = capacity(bindle, offered);
        if (fits <= 0) {
            return 0;
        }
        List<ItemStack> held = contents(bindle);
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

        if (taken > 0) {
            store(bindle, held);
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
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = held.remove(held.size() - 1);
        store(bindle, held);
        return out;
    }

    /** How many slots are in use. */
    public static int slotsUsed(ItemStack bindle) {
        return contents(bindle).size();
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
            ItemStack out = takeLast(bindle);
            if (out.isEmpty()) {
                return false;
            }
            ItemStack over = slot.safeInsert(out);
            // safeInsert can refuse part of it - a furnace fuel slot, a hotbar
            // rule, an armour slot. Whatever it would not take goes back rather
            // than being deleted, which is the entire reason this is not a
            // straight setItem.
            if (!over.isEmpty()) {
                add(bindle, over);
            }
            play(player, false);
            return true;
        }
        // Taken out of the slot first, then put in, which is the order vanilla's
        // bundle uses and the only one that cannot double an item: safeTake is
        // the authority on how many actually left the slot, and only that many
        // are ever offered to the bag.
        int fits = capacity(bindle, target);
        if (fits <= 0) {
            return false;
        }
        ItemStack removed = slot.safeTake(target.getCount(), fits, player);
        int taken = add(bindle, removed);
        if (taken < removed.getCount()) {
            // Unreachable while capacity and add agree, and here anyway because
            // the alternative to being wrong about that is deleting somebody's
            // items. Whatever the bag would not hold goes back where it was.
            ItemStack spare = slot.safeInsert(removed.copyWithCount(removed.getCount() - taken));
            if (!spare.isEmpty()) {
                add(bindle, spare);
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
            ItemStack out = takeLast(bindle);
            if (out.isEmpty()) {
                return false;
            }
            access.set(out);
            play(player, false);
            return true;
        }
        int taken = add(bindle, other);
        if (taken == 0) {
            return false;
        }
        other.shrink(taken);
        play(player, true);
        return true;
    }

    /** Vanilla's bundle sounds. A bindle is the same gesture, so it is the same noise. */
    private static void play(Player player, boolean in) {
        player.playSound(in ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE, 0.8F, 0.8F);
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
