package com.serenity.octia.world;

import com.serenity.octia.Octia;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * What a ruin gives up, and where those tables live.
 *
 * <p>The ruins borrowed vanilla's tables until now - trail ruins for the digs,
 * village houses for the barrels - which worked and said nothing. What you pull
 * out of the ground is the only voice a ruin has: nobody reads a wiki about who
 * lived somewhere, they look at what is in the box. Octia's ruins should
 * therefore answer with a person's belongings and a piece of a hull, not with
 * dyed candles from a different civilisation.
 *
 * <p>The keys live here rather than at each call site because a loot table is
 * addressed by a namespaced path, and a namespaced string written by hand is
 * exactly what {@code AGENTS.md} rule II forbids - it is the one thing a rename
 * cannot find. Every path below goes through {@link Octia#id}.
 *
 * <p>A wrong path here does not crash. It resolves to an empty table, the dig
 * yields nothing, and the only sign is a line in the log - which is why
 * {@code LootGameTest} asks the server whether each of these actually loaded.
 *
 * <p><b>A store table is staples and then a signature, and the signature is
 * what the site is.</b> The first pool is staples - bread, coal, a torch,
 * cordage - and it is deliberately much the same everywhere, because a barrel
 * that held nothing ordinary reads as a prize box rather than as somebody's.
 * The pools after it roll once each and hold the one characterful thing: what a
 * recent barrel was for, what an old one did not lose, what a station carried.
 * Before this every table was staples and nothing else, so every ruin in the
 * world gave up the same handful and there was nothing to tell one from
 * another.
 *
 * <p><b>The odds live in this file because a loot table cannot carry a
 * comment.</b> They are provisional - owner tunes by walking the world.
 * <ul>
 * <li>A sail-rig is weight 1 of 12 in {@link #RUIN_STORE}'s signature pool, so
 *     about one recent barrel in twelve. Rare, and no rarer: the item appeared
 *     in no table at all before this, and a full session was played without one
 *     ever being held.</li>
 * <li>Ink is weight 4 of 10 in {@link #RUIN_STORE_OLD}'s signature pool. Ink is
 *     what writing in this world is made of, so a faded page is the thing an
 *     old barrel keeps when the food and the tools have gone.</li>
 * <li>{@link #STATION_STORE} rolls for neither of its two signatures. See its
 *     note.</li>
 * </ul>
 *
 * <p><b>The bindle's odds were not touched.</b> A pool is its own roll, so a
 * signature pool beside the staples dilutes nothing above it - the bindle is
 * still weight 3 of 62 and 2 of 50 where it always was, and 4 of 45 in the new
 * station table. Anyone adding an entry to a <em>staples</em> pool is changing
 * those numbers, and {@code LootGameTest.aFoundBindleIsPacked} is the
 * arithmetic that will notice.
 *
 * <p><b>What a bindle in a barrel holds is one definition kept in four
 * places.</b> The road is: something to eat, something to see by, and then half
 * the time one thing off the road - cordage, a hide, or a piece of hull. Two
 * stacks or three, never four, because a full bindle is one nobody was using.
 * That is written once in {@code OctiaItems.forTheRoad} for the bindle a
 * wayfarer drops, and once as a {@code set_contents} block in each of the three
 * store tables, and until now those copies had drifted - the two JSON ones
 * disagreed with each other and both disagreed with the Java. They are the same
 * shape now. A change to one is a change owed to the other three, and the only
 * guard on it is this paragraph.
 */
public final class OctiaLoot {

    /** Brushable ground at any Octia ruin. One item, mostly mundane. */
    public static final ResourceKey<LootTable> RUIN_DIG = table("archaeology/ruin");

    /** A barrel someone was still using when they left. */
    public static final ResourceKey<LootTable> RUIN_STORE = table("chests/ruin_store");

    /** The same barrel, some seasons later. Thinner, and nothing fresh. */
    public static final ResourceKey<LootTable> RUIN_STORE_OLD = table("chests/ruin_store_old");

    /**
     * The cache at a beamline station: the wreck that reached a node.
     *
     * <p><b>Why this table exists at all.</b> A station lands in about one chunk
     * in 1,575, and it is the only site in the mod a player can be <em>led</em>
     * to - sight down an obelisk's slot, walk the tangent, and the wreck is
     * where the line said it would be. Until now that wreck rolled the same
     * barrel as one tripped over in a field, which spends the rarity on nothing
     * and teaches a player that following a sightline is not worth the walk.
     *
     * <p><b>Nothing here is rolled for.</b> The rig and a piece of hull are
     * separate single-entry pools, so both come out every time. A site somebody
     * may meet once in a save cannot afford a dice roll stacked on top of the
     * dice roll that put them in front of it - and this is the one place in the
     * mod where the traversal item is a certainty rather than a hope.
     *
     * <p>It is laid by {@link BeamlineDerelictFeature} rather than by the ruin
     * dressing, and that is deliberate: the dressing's barrel is gated on its
     * own draws and skipped entirely for an ancient or a submerged wreck, so a
     * guarantee routed through it would not be one.
     */
    public static final ResourceKey<LootTable> STATION_STORE = table("chests/station_store");

    private OctiaLoot() {
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Octia.id(path));
    }
}
