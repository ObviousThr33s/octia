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
 */
public final class OctiaLoot {

    /** Brushable ground at any Octia ruin. One item, mostly mundane. */
    public static final ResourceKey<LootTable> RUIN_DIG = table("archaeology/ruin");

    /** A barrel someone was still using when they left. */
    public static final ResourceKey<LootTable> RUIN_STORE = table("chests/ruin_store");

    /** The same barrel, some seasons later. Thinner, and nothing fresh. */
    public static final ResourceKey<LootTable> RUIN_STORE_OLD = table("chests/ruin_store_old");

    private OctiaLoot() {
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Octia.id(path));
    }
}
