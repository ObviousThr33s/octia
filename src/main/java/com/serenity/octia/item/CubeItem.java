package com.serenity.octia.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A bindle folded into a box, and the three different answers to "whose inside
 * is this".
 *
 * <p>The bindle was one bag with its contents written on it. A cube is the same
 * four slots and the same last-in-first-out gesture, asked a question the bindle
 * never had to answer: <b>where does the inside live?</b> Three cubes give three
 * answers, and the answer is the whole item.
 *
 * <ul>
 * <li><b>Red is sealed.</b> Its contents are on its own stack, in
 * {@link net.minecraft.core.component.DataComponents#CONTAINER}, which is
 * exactly what a bindle does. Whatever its packer left in it is what a finder
 * takes out, wherever and whenever that happens. A red cube is a message from
 * somebody.
 * <li><b>Gold is yours.</b> Every gold cube a player holds opens that player's
 * one pocket, kept in {@link CubePockets} under their UUID. Two gold cubes in
 * one hand are not two bags, they are one bag with two doors. A gold cube found
 * in a ruin is empty for the finder and always was - it was never anybody's
 * until somebody picked it up.
 * <li><b>Purple is the road.</b> One bag for the whole save, under
 * {@link CubePockets#ROAD}. Every purple cube anywhere opens it. Put something
 * in one on an island, take it out of another a thousand blocks away.
 * </ul>
 *
 * <p><b>Purple is the only traversal in this mod that is not walking, and it is
 * legal.</b> The law from the 8/23 session is that no mechanic may enable
 * sustained fast horizontal travel - loaded chunks climbing to 2,690, 70ms MSPT
 * against a 50ms budget, a stalled autosave. A purple cube moves items and
 * never moves a player, loads not one chunk, and ticks nothing. It is the
 * opposite of the storm: a reason to leave the far thing where it is.
 *
 * <p><b>No screen, no menu, no packet - inherited, not restated.</b> Everything
 * about the right-click gesture lives in {@link BindleItem} and is used here
 * unchanged. All three cubes override the same three seams -
 * {@link BindleItem#roomIn}, {@link BindleItem#stow},
 * {@link BindleItem#drawLast} - and the sealed one does not even do that. That
 * is why there is one click behaviour in this mod: a fix to the bindle's
 * safe-take ordering is a fix to all four bags at once.
 *
 * <p><b>What the client is told, and why saying less is not a lie.</b> A pocket
 * lives in save-root {@code SavedData} and only the server can reach it. The
 * tooltip and the fullness bar are drawn client-side, on the render thread,
 * from the stack alone - and a gold or purple cube's stack holds nothing, by
 * design. So this class draws no bar for those two and lists no contents:
 * <em>silence, not a zero.</em> A bar of width zero and an "Empty" line are
 * both claims about what is inside, and the client is not in a position to make
 * either one. The tooltip instead says where the inside is kept, which is a
 * fact the client does know, and which is the useful half anyway - the question
 * a player has in front of a strange cube is whose bag it opens, not what is in
 * it. Reading the contents means opening it, which is the same deal the bindle
 * always offered.
 *
 * <p><b>The same reasoning covers the click, one layer down.</b> On the client
 * {@link BindleItem#roomIn} and {@link BindleItem#drawLast} answer nothing and
 * the click is declined, so the client predicts no change; the server runs the
 * identical click for real and the menu's own diff against the remote state
 * sends the corrections. No new channel, and no client guessing at a store it
 * cannot see.
 */
public class CubeItem extends BindleItem {

    /**
     * Which inside this cube opens.
     *
     * <p>The colours are the three keys {@code docs/PALETTE.md} reserves for
     * exactly these items, repeated here because the fullness bar is drawn from
     * a number and not from the sprite. If the ramp moves, this moves with it.
     *
     * <p>{@code note} is the tooltip line naming where the inside is kept, and
     * it is <b>null for the sealed cube on purpose</b> - a sealed cube's inside
     * is right there on the stack and can simply be listed, so there is nothing
     * to say about where it went. Null is the answer, in the sense
     * {@code Mystery.toward} means it.
     */
    public enum Kind {

        /** Holds what its packer left, on its own stack. */
        SEALED(0x9E2B25, null),

        /** Opens the pocket of whichever player is holding it. */
        OWN(0xC8A02C, "item.octia.cube.own"),

        /** Opens the one bag the whole save shares. */
        ROAD(0x6B3FA0, "item.octia.cube.road");

        private final int colour;
        private final String note;

        Kind(int colour, String note) {
            this.colour = colour;
            this.note = note;
        }

        /** The bar colour, from the ramp. */
        public int colour() {
            return colour;
        }

        /** Where the inside is kept, or null when it is on the stack. */
        public String note() {
            return note;
        }
    }

    private final Kind kind;

    public CubeItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * The pocket this cube opens, or null when the inside is on the stack.
     *
     * <p>This one method is the entire difference between the gold cube and the
     * purple one, which is why it is public: it is the decision worth asserting,
     * and a test that drives stacks around can only ever observe it second-hand.
     */
    public String pocketKey(Player player) {
        return switch (kind) {
            case SEALED -> null;
            case OWN -> player.getUUID().toString();
            case ROAD -> CubePockets.ROAD;
        };
    }

    // ---- Where the contents are kept -----------------------------------------

    @Override
    public int roomIn(ItemStack cube, ItemStack offered, Player player) {
        if (sealed()) {
            return super.roomIn(cube, offered, player);
        }
        CubePockets pockets = pocketsOf(player);
        return pockets == null ? 0 : capacityIn(pockets.read(pocketKey(player)), offered);
    }

    @Override
    public int stow(ItemStack cube, ItemStack offered, Player player) {
        if (sealed()) {
            return super.stow(cube, offered, player);
        }
        CubePockets pockets = pocketsOf(player);
        if (pockets == null) {
            return 0;
        }
        String key = pocketKey(player);
        List<ItemStack> held = pockets.read(key);
        int taken = addTo(held, offered);
        if (taken > 0) {
            pockets.write(key, held);
        }
        return taken;
    }

    @Override
    public ItemStack drawLast(ItemStack cube, Player player) {
        if (sealed()) {
            return super.drawLast(cube, player);
        }
        CubePockets pockets = pocketsOf(player);
        if (pockets == null) {
            return ItemStack.EMPTY;
        }
        String key = pocketKey(player);
        List<ItemStack> held = pockets.read(key);
        ItemStack out = takeLastFrom(held);
        if (!out.isEmpty()) {
            pockets.write(key, held);
        }
        return out;
    }

    /** Whether this cube's inside is on its own stack. */
    private boolean sealed() {
        return kind == Kind.SEALED;
    }

    /**
     * The store, or null when there is no server to ask.
     *
     * <p>Null on the client, where a pocket cannot be reached and must not be
     * guessed at. The side test is the level's, not a flag on the item, because
     * that is the one thing that is true on a dedicated server, in single
     * player, and inside a gametest alike.
     */
    private static CubePockets pocketsOf(Player player) {
        return player.level() instanceof ServerLevel level ? CubePockets.get(level.getServer()) : null;
    }

    // ---- What it looks like --------------------------------------------------

    /**
     * A bar only when the stack is the store.
     *
     * <p>See the class note: a pocket cube's stack holds nothing, so any bar
     * drawn from it would read as "empty" to a player whose pocket is full.
     * Drawing nothing says nothing, which is the only true thing available.
     */
    @Override
    public boolean isBarVisible(ItemStack cube) {
        return sealed() && super.isBarVisible(cube);
    }

    @Override
    public int getBarWidth(ItemStack cube) {
        return sealed() ? super.getBarWidth(cube) : 0;
    }

    @Override
    public int getBarColor(ItemStack cube) {
        return kind.colour();
    }

    /**
     * Says what is inside if it can, and where the inside is if it cannot.
     *
     * <p>The sealed cube lists its contents exactly as a bindle does, because
     * it holds them exactly as a bindle does. The other two say whose bag this
     * is and then stop, which is the class note's whole argument in two lines.
     */
    @Override
    public void appendHoverText(ItemStack cube, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (kind.note() == null) {
            List<ItemStack> held = contents(cube);
            if (held.isEmpty()) {
                lines.add(Component.translatable("item.octia.cube.empty").withStyle(ChatFormatting.GRAY));
                return;
            }
            for (ItemStack stack : held) {
                lines.add(Component.translatable("item.octia.cube.line", stack.getCount(), stack.getHoverName())
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        lines.add(Component.translatable(kind.note()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.octia.cube.elsewhere").withStyle(ChatFormatting.DARK_GRAY));
    }
}
