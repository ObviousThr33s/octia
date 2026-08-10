package com.serenity.octia.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where Octia's landmarks are, once the world has actually made them.
 *
 * <p>Until now the mod could not answer its own simplest question. A derelict is
 * a {@code Feature}, and a feature leaves no record of itself anywhere - not a
 * structure start, not a chunk tag, nothing but the blocks it wrote.
 * {@code world-report.py} finds them by scanning every section palette in every
 * region file, which is fine for a tool run from a shell and useless to anything
 * inside the game. So a wayfarer could not be told to arrive near an obelisk, the
 * debug map could not draw one, and a minimap integration had nothing to hand
 * over. This is that missing answer.
 *
 * <p><b>Recording crosses a thread boundary, and it has to be done on purpose.</b>
 * Features run on chunk-generation workers, several at once;
 * {@code SavedData} is a plain map owned by the server thread. That is exactly
 * why {@code ShipMoorings} is never written from inside a feature - see the note
 * on {@code DerelictFeature}. So {@link #report} does not touch the store at
 * all. It appends to a concurrent queue, and {@link #drain} moves the queue into
 * the store on the server tick, where writing is legal. A feature that wants to
 * be remembered says so and carries on generating.
 *
 * <p><b>Kinds are strings, deliberately.</b> They are the feature's own registry
 * path - {@code derelict}, {@code obelisk}, {@code waystation} - so adding a
 * template ruin adds a kind without touching an enum, which is the same promise
 * {@code OctiaWorldgen.TEMPLATE_RUINS} makes: one new name, no new Java.
 *
 * <p><b>It grows with exploration and is never pruned.</b> A world walked far
 * enough accumulates thousands of entries, which is the same shape of cost
 * {@code ShipMoorings} already carries and is acceptable for the same reason: a
 * packed long each, written once, read rarely. If it ever stops being
 * acceptable, the fix is a per-region index rather than a cap - forgetting a
 * landmark is worse than storing it.
 */
public final class RuinRegistry extends SavedData {

    /** Becomes {@code <save>/data/octia_ruins.dat}. */
    private static final String FILE = "octia_ruins";

    /** For the DataFixTypes choice, see the note in ShipMoorings. */
    private static final SavedData.Factory<RuinRegistry> FACTORY = new SavedData.Factory<>(
            RuinRegistry::new, RuinRegistry::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    /**
     * What has been generated but not yet written down, per server.
     *
     * <p>The whole reason this class is not simply a store. Producers are chunk
     * workers and the consumer is the server thread; nothing else here needs a
     * lock because nothing else crosses.
     */
    private static final Map<MinecraftServer, Queue<Pending>> PENDING = new ConcurrentHashMap<>();

    /** Kind to the positions recorded under it. Insertion-ordered so dumps are stable. */
    private final Map<String, Set<Long>> found = new LinkedHashMap<>();

    private record Pending(String kind, long packed) {
    }

    private RuinRegistry() {
    }

    // ---- Wiring --------------------------------------------------------------

    public static void bootstrap() {
        ServerTickEvents.END_SERVER_TICK.register(RuinRegistry::drain);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // One last drain, then let go. A ruin generated in the last tick
            // before a save closes is still a ruin.
            drain(server);
            PENDING.remove(server);
        });
    }

    /**
     * Notes a landmark. Safe from any thread, including a generation worker.
     *
     * <p>Deliberately takes the level rather than the server so a caller inside
     * a feature can hand over what it already has.
     */
    public static void report(ServerLevel level, String kind, BlockPos where) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        PENDING.computeIfAbsent(server, ignored -> new ConcurrentLinkedQueue<>())
                .add(new Pending(kind, where.asLong()));
    }

    /** Moves everything the workers reported into the store. Server thread only. */
    private static void drain(MinecraftServer server) {
        Queue<Pending> queue = PENDING.get(server);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        RuinRegistry registry = get(server);
        Pending next;
        while ((next = queue.poll()) != null) {
            registry.add(next.kind(), next.packed());
        }
    }

    // ---- The store -----------------------------------------------------------

    /** The one registry for the whole save. Never per-dimension. */
    public static RuinRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    private static RuinRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        RuinRegistry out = new RuinRegistry();
        for (String kind : tag.getAllKeys()) {
            Set<Long> positions = ConcurrentHashMap.newKeySet();
            for (long packed : tag.getLongArray(kind)) {
                positions.add(packed);
            }
            out.found.put(kind, positions);
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        found.forEach((kind, positions) ->
                tag.putLongArray(kind, positions.stream().mapToLong(Long::longValue).toArray()));
        return tag;
    }

    private void add(String kind, long packed) {
        if (found.computeIfAbsent(kind, ignored -> ConcurrentHashMap.newKeySet()).add(packed)) {
            setDirty();
        }
    }

    // ---- Reading it ----------------------------------------------------------

    /** Every position recorded under one kind. */
    public List<BlockPos> of(String kind) {
        List<BlockPos> out = new ArrayList<>();
        for (long packed : found.getOrDefault(kind, Set.of())) {
            out.add(BlockPos.of(packed));
        }
        return out;
    }

    /** Every kind that has ever been recorded in this save. */
    public Set<String> kinds() {
        return Set.copyOf(found.keySet());
    }

    public int count() {
        int total = 0;
        for (Set<Long> positions : found.values()) {
            total += positions.size();
        }
        return total;
    }

    /**
     * The nearest landmark of a kind, or null.
     *
     * <p>Horizontal distance only, for the reason the debug map gives: the store
     * spans dimensions, so a vertical component would be arithmetic on two
     * numbers that may not share a space.
     *
     * @param kind  the feature path, or null for any kind
     * @param range how far to look, in blocks
     */
    public BlockPos nearest(BlockPos from, String kind, double range) {
        BlockPos best = null;
        double nearest = range * range;

        for (Map.Entry<String, Set<Long>> entry : found.entrySet()) {
            if (kind != null && !kind.equals(entry.getKey())) {
                continue;
            }
            for (long packed : entry.getValue()) {
                BlockPos at = BlockPos.of(packed);
                double dx = at.getX() - from.getX();
                double dz = at.getZ() - from.getZ();
                double flat = dx * dx + dz * dz;
                if (flat < nearest) {
                    nearest = flat;
                    best = at;
                }
            }
        }
        return best;
    }
}
