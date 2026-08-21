package com.serenity.octia.world;

import com.serenity.octia.Octia;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import com.serenity.octia.ship.ShipCoreBlock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;


/**
 * What Octia adds to the terrain, and the one honest place the world switch can
 * still be applied once it does.
 *
 * <p><b>The problem this class exists to solve.</b> {@code OctiaBeacon} raises a
 * mast at spawn instead of generating anything, and its note says why: Fabric's
 * biome modifications are registered once per game launch and apply to every
 * world that launch opens, so they cannot be gated on a per-save flag. That is
 * still true. What is also true, and is the way through, is that a biome
 * modification only ever schedules a <em>feature</em> - and the feature runs with
 * a level in hand and can decline. So the modification is global and the
 * placement is conditional, which produces exactly the per-save behaviour the
 * switch promises without pretending the registration is per-save.
 *
 * <p><b>Why the flag is cached rather than read.</b> The obvious implementation
 * is for the feature to ask {@code OctiaWorldOption.get(server)} directly. That
 * is a {@code SavedData} lookup against {@code DimensionDataStorage}, which is a
 * plain map owned by the server thread - and features run on chunk-generation
 * worker threads, several at once. Reading it there is a data race that would
 * surface as an intermittent crash under exactly the conditions nobody tests: a
 * fresh world generating many chunks at speed. So the server thread publishes
 * the answer once, at world load, through a volatile, and the workers only read.
 *
 * <p>The flag is cleared on server stop so that a second world opened in the
 * same launch cannot inherit the first one's answer. That is the bug this shape
 * would otherwise introduce, and it is the reason the reset is not optional.
 */
public final class OctiaWorldgen {

    /**
     * Registry path for both the feature type and the two worldgen JSON files
     * that configure and place it. They must agree; this constant is why they do.
     */
    /**
     * Registry paths, and also the kinds a ruin is recorded under.
     *
     * <p>Public because {@link RuinRegistry} keys on exactly these strings and a
     * second spelling of "obelisk" somewhere else would be a landmark nobody can
     * look up. One name, one place.
     */
    public static final String DERELICT = "derelict";
    public static final String OBELISK = "obelisk";

    /**
     * The arch. A landmark, but not yet a recorded one.
     *
     * <p>Private where the two above are public, and the asymmetry is a decision
     * rather than something a merge left behind. {@link ArchFeature} never calls
     * {@link RuinRegistry#report}, so no kind string "arch" ever reaches the
     * store and nothing outside this class has anything to look one up with -
     * making it public would advertise a key that answers with an empty list.
     * The day the arch reports itself, which it has every reason to (it is a
     * sightline anchor and the F6 map wants it), this line joins the two above
     * and that is the whole change.
     */
    private static final String ARCH = "arch";

    /**
     * The feature type that places authored .nbt ruins. Its registry path is
     * the TYPE, not a ruin - many placed features share it, one per template,
     * each naming its own file in the configured feature JSON.
     */
    private static final String TEMPLATE_RUIN = "template_ruin";

    /** Placed features backed by a template. One entry per authored ruin. */
    private static final String[] TEMPLATE_RUINS = {"waystation"};

    /**
     * How far out to look for somewhere to seat the spawn derelict, nearest
     * first. The near end is a short walk rather than a stroll - close enough to
     * find without looking for it, far enough that it is not sharing the
     * beacon's plinth.
     */
    private static final int[] SPAWN_RADII = {48, 64, 80, 96, 112};

    /** Compass directions tried at each radius. */
    private static final int SPAWN_SPOKES = 8;

    /** Keeps this draw off the world seed's other consumers. */
    private static final long SPAWN_SALT = 0x0C71A_DE2E1_1CL;

    /**
     * Written by the server thread at world load, read by generation workers.
     * Volatile rather than synchronised: it is one boolean, written rarely and
     * read constantly, and the only guarantee needed is visibility.
     *
     * <p><b>Why one static is enough.</b> A process holds at most one save: a
     * dedicated server owns exactly one for its whole life, and an integrated
     * server is stopped before the next world opens. That premise is what the
     * SERVER_STOPPED reset preserves, and it is the thing to re-check before
     * anything here goes per-level - the day two saves can be open at once,
     * this field is wrong rather than merely stale.
     */
    private static volatile boolean active;

    /** Held from registration so nothing has to cast them back out of the registry. */
    private static DerelictFeature derelict;
    private static ObeliskFeature obelisk;
    private static ArchFeature arch;
    private static TemplateRuinFeature templateRuin;

    private OctiaWorldgen() {
    }

    /** Registers the feature type and schedules it into Overworld biomes. */
    public static void bootstrap() {
        derelict = Registry.register(BuiltInRegistries.FEATURE, Octia.id(DERELICT),
                new DerelictFeature(NoneFeatureConfiguration.CODEC));
        obelisk = Registry.register(BuiltInRegistries.FEATURE, Octia.id(OBELISK),
                new ObeliskFeature(NoneFeatureConfiguration.CODEC));
        arch = Registry.register(BuiltInRegistries.FEATURE, Octia.id(ARCH),
                new ArchFeature(NoneFeatureConfiguration.CODEC));
        templateRuin = Registry.register(BuiltInRegistries.FEATURE, Octia.id(TEMPLATE_RUIN),
                new TemplateRuinFeature(TemplateRuinFeature.Config.CODEC));

        // SURFACE_STRUCTURES rather than a later step: these sit on the ground
        // and want to be there before grass, flowers and trees decorate over
        // them, so a ruin looks weathered into the landscape rather than
        // dropped on top of it.
        // The code-driven features first, then everything backed by a template.
        // Same decoration step either way, so this is also the order they run in
        // a chunk. Nothing depends on that today; the two lists are kept apart
        // because only the second one is meant to grow without touching Java.
        for (String path : new String[] {DERELICT, OBELISK, ARCH}) {
            scheduleInOverworld(path);
        }
        for (String path : TEMPLATE_RUINS) {
            scheduleInOverworld(path);
        }
    }

    /**
     * Schedules one placed feature into every Overworld biome.
     *
     * <p>Adding a template ruin is meant to be this small: author the .nbt,
     * write its configured and placed feature JSON, and add its name to
     * {@link #TEMPLATE_RUINS}. No Java beyond the one string.
     */
    private static void scheduleInOverworld(String path) {
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.SURFACE_STRUCTURES,
                ResourceKey.create(Registries.PLACED_FEATURE, Octia.id(path)));
    }

    /**
     * Publishes whether this save wants Octia's terrain. Server thread only,
     * called once per world load before any chunk can generate.
     */
    public static void setActive(boolean value) {
        active = value;
    }

    /** Whether the currently open save wants Octia's terrain. Safe off-thread. */
    public static boolean active() {
        return active;
    }

    /** The registered features themselves, for tests that place one directly. */
    public static DerelictFeature derelict() {
        return derelict;
    }

    public static ObeliskFeature obelisk() {
        return obelisk;
    }

    public static ArchFeature arch() {
        return arch;
    }

    public static TemplateRuinFeature templateRuin() {
        return templateRuin;
    }

    /**
     * Puts one derelict within walking distance of spawn, once per save.
     *
     * <p><b>Why this exists at all.</b> The rarity filter is a coin flip per
     * chunk, and a coin flip guarantees nothing - a player can walk a thousand
     * blocks from spawn and meet no derelict, which means the mod's one piece of
     * generated content is invisible for as long as the world feels new. Vanilla
     * has the same problem and answers it the same way: a ruined portal is
     * reachable from where you wake up. The rest of them are luck; the first one
     * is not.
     *
     * <p><b>Why it is not another placement modifier.</b> Nothing in the placed
     * feature vocabulary can say "near this save's spawn" - modifiers see a
     * chunk, not a world. Structures can, through spacing and separation, but
     * that means abandoning {@code Feature} for the jigsaw machinery to buy one
     * guarantee. First load already exists as a moment, the beacon already uses
     * it, and this is the same claim.
     *
     * <p><b>What is different about being placed here.</b> This runs on the
     * server thread against a live {@code ServerLevel}, so {@code onPlace} fires
     * and the core moors itself immediately - the spawn derelict is on the F6
     * map from the moment the world opens. That is the intent and not a leak in
     * "discovery is registration": the near one is the signpost, and the wild
     * ones still have to be found.
     *
     * @return where it landed, or null if nowhere within reach would take it
     */
    public static BlockPos placeNearSpawn(ServerLevel level) {
        // "Spawn" here is whatever the level data says at world-load time, which
        // on a brand-new save is not yet where the player will wake up - see the
        // note in OctiaBeacon.raise. The rings below are measured from that
        // point, so on a save whose spawn is chosen elsewhere the guaranteed
        // derelict sits 48-112 blocks from the origin column, not from the
        // player, and the promise in this method's own javadoc is not kept.
        BlockPos spawn = level.getSharedSpawnPos();

        // Seeded off the world seed so a given save always puts it in the same
        // place. Two players on the same seed comparing notes is the whole
        // reason the beacon is seed-independent too.
        RandomSource random = RandomSource.create(level.getSeed() ^ SPAWN_SALT);

        // Outward in rings. The near radii are tried first and every ring is
        // walked in a rotated order, so a world whose spawn sits against a lake
        // does not always answer with the same compass direction.
        for (int radius : SPAWN_RADII) {
            int turn = random.nextInt(SPAWN_SPOKES);
            for (int i = 0; i < SPAWN_SPOKES; i++) {
                double angle = 2 * Math.PI * ((i + turn) % SPAWN_SPOKES) / SPAWN_SPOKES;
                int x = spawn.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * radius);

                BlockPos core = tryAt(level, random, x, z);
                if (core != null) {
                    Octia.LOGGER.info("Octia: derelict seated at {} ({} blocks from spawn).",
                            core, radius);
                    return core;
                }
            }
        }

        // Said out loud rather than swallowed. A world with no reachable
        // derelict is a fact worth being able to find in a log afterwards.
        Octia.LOGGER.info("Octia: no ground within {} blocks of spawn would take a derelict.",
                SPAWN_RADII[SPAWN_RADII.length - 1]);
        return null;
    }

    /** One candidate column: load it, find its surface, offer it to the feature. */
    private static BlockPos tryAt(ServerLevel level, RandomSource random, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);

        // The chunk is almost certainly not resident this early, and reading a
        // heightmap out of an unloaded chunk answers with nonsense - the same
        // trap OctiaBeacon documents at the top of raise().
        level.getChunk(column);

        // Not a _WG twin: this is a finished chunk on a live level, where the
        // worldgen heightmaps are not maintained and answer bottom-of-world.
        // That mistake cost five gametests once already.
        //
        // MOTION_BLOCKING_NO_LEAVES, and the choice is a correction. This asked
        // OCEAN_FLOOR, which answers with the seabed - so over water the feature
        // was handed a floor with solid rock beneath it and accepted, and the
        // guaranteed derelict generated underwater in seagrass, thirteen blocks
        // below the spawn it is supposed to be a short walk from. The wild ones
        // are dropped at WORLD_SURFACE_WG and their footing check lands in fluid,
        // so they refuse water outright. Two paths through one feature,
        // disagreeing about what counts as ground.
        //
        // Fluid counts as surface here, so the ring search rejects the sea and
        // keeps looking. Leaves do not, or a forest spawn seats the wreck in a
        // canopy. A wreck on a seabed is arguably the most in-fiction place for
        // one, but that is ROADMAP VI and wants the beacon's walk-down, not an
        // accident of which heightmap this line happened to name.
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

        // Straight to seat() rather than through the feature's place(), so the
        // guaranteed wreck is exempt from the structure check. See the javadoc
        // on DerelictFeature.seat for why the guarantee outranks it, and for
        // what the check would cost on the server thread if it ran here.
        //
        // What is handed over is the surface, as an ORIGIN, and not a core
        // position worked out here - because there is no longer a core position
        // that can be worked out here. The wreck walks down through air and
        // water to real ground and then sinks by an age it rolls for itself,
        // both of which happen inside seat(). The old "surface minus a constant"
        // would now aim one block under the top of a lake, since the heightmap
        // chosen above counts fluid as surface.
        if (!DerelictFeature.seat(level, random, surface)) {
            return null;
        }

        // Found rather than calculated. The wreck sinks by its age and walks
        // down to real ground before it settles, so "surface minus a constant"
        // stopped being where the core is the moment either of those landed -
        // and this position is what gets logged and reported as the answer.
        for (int dy = 2; dy >= -DerelictFeature.searchDepth(); dy--) {
            BlockPos candidate = surface.offset(0, dy, 0);
            if (level.getBlockState(candidate).getBlock() instanceof ShipCoreBlock) {
                return candidate;
            }
        }
        return surface;
    }
}
