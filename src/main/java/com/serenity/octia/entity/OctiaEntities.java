package com.serenity.octia.entity;

import com.serenity.octia.Octia;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Every entity this mod registers, and the one place it happens.
 *
 * <p>The third sibling of {@code OctiaBlocks} and {@code OctiaItems}, and it
 * carries their contract word for word: registration runs in the static
 * initialiser, which fires when {@link #bootstrap()} is first called from
 * {@code Octia.onInitialize}, and touching this class any earlier moves
 * registration to whatever phase touched it. That invariant matters more here
 * than it does for a block, because the class that has every reason to read
 * {@link #VOID_SQUID} early is a client renderer registration, and the client
 * entrypoint runs after the main one only as long as nobody hangs a static field
 * off this class from somewhere else.
 *
 * <p><b>This is the first entity type the mod has ever registered</b>, so three
 * things that did not exist anywhere in the tree are established here at once:
 * the type itself, its default attributes, and where it is allowed to be born.
 * All three go through the same plain {@code Registry.register} funnel the
 * blocks and items use, and no mixin, no access widener and no new registry are
 * involved.
 *
 * <p><b>Why AMBIENT and not one of the water categories.</b> A category is a
 * spawn budget: vanilla caps each one globally and scales it by loaded chunks,
 * which is exactly the cap the charter asks for and it is free. The obvious
 * choice for a squid would be {@code WATER_AMBIENT}, and it is the wrong one on
 * this world. {@code docs/ISLANDS.md} X measured the shipped terrain and found
 * a median of 18,059 fluid blocks per chunk with <b>no dry chunk at all</b>, so
 * the water categories are permanently full of cod and salmon and a void squid
 * behind them would spawn approximately never. {@code AMBIENT} is the bat's
 * category - a small non-hostile flyer that never persists and despawns at
 * range - and on a world with almost no dark cave roof it is nearly unspent. So
 * the squid gets a budget instead of starving behind a category the terrain
 * already filled.
 *
 * <p>The cost, said out loud: the fifteen-per-category cap is now shared with
 * bats, so a chunk carrying void squid carries fewer bats. On this terrain that
 * is close to nothing, and it is a cap doing its job rather than a collision.
 */
public final class OctiaEntities {

    /**
     * Registry path, and the id the type is built under.
     *
     * <p>The two must agree: {@code EntityType.Builder.build} takes the id the
     * datafixer keys on, and a type registered at one path and built under
     * another is a save that cannot be read back by name. One constant is why
     * they cannot disagree.
     */
    public static final String VOID_SQUID_PATH = "void_squid";

    /**
     * How often one is offered against everything else in {@link
     * MobCategory#AMBIENT}. A bat is 10 in the biomes that carry one.
     */
    private static final int WEIGHT = 6;      // provisional - owner tunes by walking the world

    /**
     * How many arrive together. Never a shoal: a squid you find with seven
     * others is a spawn event, and a squid you find alone is a thing you found.
     */
    private static final int MIN_GROUP = 1;   // provisional - owner tunes by walking the world
    private static final int MAX_GROUP = 2;   // provisional - owner tunes by walking the world

    /**
     * How far away a client is still told about one, in chunks.
     *
     * <p>Eight, which is 128 blocks, and it is the number that makes the whole
     * encounter possible: the squid lives under the continent and the player
     * stands on top of it, so a range that only covered a few chunks would mean
     * nothing was ever visible from a ledge and nobody would learn there was
     * anything to look for. Tracking costs no chunk loading - it only decides
     * whether an entity already being ticked is described to a client.
     */
    private static final int TRACKING_CHUNKS = 8;  // provisional - owner tunes by walking the world

    /**
     * Ticks between position updates on the wire. Three rather than vanilla's
     * usual one, because nothing that moves 0.03 blocks a tick needs to be
     * described twenty times a second.
     */
    private static final int UPDATE_INTERVAL = 3;

    /** Width and height of the hitbox. Small; a squid is not an obstacle. */
    private static final float WIDTH = 0.7F;   // provisional - owner tunes by walking the world
    private static final float HEIGHT = 0.7F;  // provisional - owner tunes by walking the world

    /** Held from registration so nothing has to cast it back out of the registry. */
    public static final EntityType<VoidSquid> VOID_SQUID = register(VOID_SQUID_PATH,
            EntityType.Builder.of(VoidSquid::new, MobCategory.AMBIENT)
                    .sized(WIDTH, HEIGHT)
                    .clientTrackingRange(TRACKING_CHUNKS)
                    .updateInterval(UPDATE_INTERVAL));

    private OctiaEntities() {
    }

    private static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, Octia.id(path), builder.build(path));
    }

    /**
     * Forces class initialisation, then files the attributes, the placement and
     * the spawn schedule.
     *
     * <p><b>{@code NO_RESTRICTIONS} is not "anywhere".</b> It is the placement
     * type that declines to have an opinion, which hands the whole decision to
     * {@code VoidSquid.spawnRules} - and that is what is wanted, because none of
     * the four vanilla opinions fits: {@code ON_GROUND} wants a floor, the two
     * fluid ones want a fluid, and this animal wants open air with neither. The
     * heightmap argument is only consulted by {@code ON_GROUND}'s own position
     * search and is named here because the signature requires one.
     *
     * <p>The biome modification is registered unconditionally, for the reason
     * {@code OctiaWorldgen} sets out at length: Fabric applies these once per
     * game launch to every world that launch opens, so they cannot be gated per
     * save. What is gated per save is the spawn rule, which reads
     * {@code OctiaWorldgen.active()} - global registration, conditional answer,
     * exactly as the terrain does it.
     */
    public static void bootstrap() {
        FabricDefaultAttributeRegistry.register(VOID_SQUID, VoidSquid.attributes());

        SpawnPlacements.register(VOID_SQUID, SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, VoidSquid::spawnRules);

        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.AMBIENT,
                VOID_SQUID, WEIGHT, MIN_GROUP, MAX_GROUP);
    }
}
