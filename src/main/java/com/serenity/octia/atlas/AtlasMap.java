package com.serenity.octia.atlas;

import java.util.ArrayList;
import java.util.List;

import com.serenity.octia.Octia;
import com.serenity.octia.world.OctiaWorldOption;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

/**
 * The whole texture atlas, as a map in every player's inventory, permanently.
 *
 * <p>A Minecraft map is a 128x128 grid of bytes where each byte names a colour.
 * The atlas tensor is an N-by-16-by-16 grid of bytes where each byte names a
 * colour. They are the same kind of object, and 128 is exactly eight tiles of
 * sixteen on a side - so the game's art fits onto one map with room to spare,
 * and the conversion is an index remap rather than a rendering. That
 * coincidence is why this is a map and not a GUI screen.
 *
 * <p>Forty-four tiles land in an 8x8 grid of sixty-four. The twenty spare
 * squares are left as map colour zero, which draws as nothing, so the sheet
 * grows downward as the mod does without anything having to move.
 *
 * <p><b>Locked, so the world cannot paint over it.</b> Vanilla updates a map's
 * colours from the terrain whenever a player holds one in its own dimension.
 * {@link MapItemSavedData#locked()} is the vanilla mechanism for freezing that
 * - it is what a cartography table does - so the atlas survives being carried
 * around without a mixin on the update path. {@code trackPosition} is false for
 * the same reason: nothing about this map is about where anybody is standing.
 *
 * <p><b>Held, not merely given</b> - the pattern {@code KeepInventory} uses,
 * and for the same reason. Handing the map out once at first join would be
 * undone by the first Q press. So the inventory is checked every tick and the
 * map put back when it is missing, which costs one scan of a player's slots
 * against a value already in cache. What was asked for was a map nobody can get
 * rid of, and "cannot be removed" is a property that has to be maintained
 * rather than one that can be set.
 *
 * <p><b>One map, not one per player.</b> Every copy carries the same
 * {@link MapId}, so the server stores a single {@code map_N.dat} and every
 * player is looking at the same picture. The id is remembered in this class's
 * own {@link SavedData} so a restart reuses it rather than leaking a fresh map
 * every load - which is what the obvious version of this does, invisibly, until
 * a save has four hundred of them.
 *
 * <p><b>No mixin,</b> per the standing rule. Two Fabric events and vanilla's
 * own map API do the whole job.
 */
public final class AtlasMap {

    /** Vanilla map edge. MapItem.IMAGE_WIDTH, restated so the arithmetic reads. */
    private static final int MAP = 128;

    /** 128 / 16. How many tiles fit across the sheet. */
    private static final int ACROSS = MAP / AtlasTensor.TILE;

    /** How far around a player to sweep for a map they just threw away. */
    private static final double SWEEP = 64.0;

    /** Loaded once per launch. The jar cannot change under a running server. */
    private static AtlasTensor tensor;

    /** Painted once per launch from {@link #tensor}. 128*128 map colour bytes. */
    private static byte[] painted;

    /** So the "handed out" line is logged once per launch, not once per tick. */
    private static boolean announced;

    private AtlasMap() {
    }

    public static void bootstrap() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!OctiaWorldOption.get(server).enabled()) {
                return;
            }
            hold(server);
        });

        // Cleared on the way out so a second world opened in the same launch
        // does not inherit the first one's log state. The tensor itself is not
        // cleared - it came out of the jar and cannot have changed.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> announced = false);
    }

    /**
     * Puts the atlas back in every hand that is missing it.
     *
     * <p>Returns quietly when everyone already has one, which is every tick
     * except the handful where somebody tried to drop it.
     */
    private static void hold(MinecraftServer server) {
        MapId id = ensure(server);
        if (id == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (carries(player, id)) {
                continue;
            }
            if (!player.getInventory().add(stack(id))) {
                // No room. Not an error and not worth a log line every tick -
                // the next tick with a free slot puts it back. A player who
                // fills every slot to be rid of it has bought a delay, not an
                // escape.
                continue;
            }
            // Only sweep when a map actually went missing, and only near the
            // player who lost it. A standing scan of every item entity on the
            // server would cost far more than this mechanic is worth, and there
            // is nothing to find on the ticks where nothing was dropped.
            discardDropped(player, id);
            if (!announced) {
                announced = true;
                Octia.LOGGER.info(
                        "Octia: the atlas map is held. {} tiles, {} colours, and no way to put it down.",
                        tensor.count(), tensor.colours());
            }
        }
    }

    /**
     * The atlas map's id on this save, or null before it has been painted.
     *
     * <p>Exists for the gametest, which cannot reach {@link Held} from its own
     * package and should not be given a second way to work out the answer.
     */
    public static MapId painted(MinecraftServer server) {
        Held held = Held.get(server);
        return held.id < 0 ? null : new MapId(held.id);
    }

    /** Whether this player already has the atlas anywhere in their inventory. */
    private static boolean carries(ServerPlayer player, MapId id) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isAtlas(player.getInventory().getItem(slot), id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAtlas(ItemStack held, MapId id) {
        return held.is(Items.FILLED_MAP) && id.equals(held.get(DataComponents.MAP_ID));
    }

    /**
     * Removes a copy the player just threw on the ground.
     *
     * <p>Without this, dropping the map duplicates it: the hold puts a fresh one
     * in the inventory while the thrown one lies there. Since every copy shares
     * one {@link MapId} the duplicate is harmless to the save, but a floor
     * slowly filling with atlases is not what was asked for.
     */
    private static void discardDropped(ServerPlayer player, MapId id) {
        ServerLevel level = player.serverLevel();
        AABB near = player.getBoundingBox().inflate(SWEEP);
        List<ItemEntity> found = new ArrayList<>(
                level.getEntitiesOfClass(ItemEntity.class, near, e -> isAtlas(e.getItem(), id)));
        for (ItemEntity dropped : found) {
            dropped.discard();
        }
    }

    /** A fresh copy of the atlas, named so it is obvious what it is. */
    private static ItemStack stack(MapId id) {
        ItemStack out = new ItemStack(Items.FILLED_MAP);
        out.set(DataComponents.MAP_ID, id);
        out.set(DataComponents.CUSTOM_NAME, Component.literal("The Atlas"));
        return out;
    }

    /**
     * The atlas map's id, creating and painting it the first time it is asked
     * for on this save.
     *
     * @return null if the tensor could not be loaded, which is a broken jar and
     *         is logged once rather than thrown into a tick loop
     */
    private static MapId ensure(MinecraftServer server) {
        Held held = Held.get(server);
        if (held.id >= 0) {
            return new MapId(held.id);
        }

        if (painted == null) {
            try {
                tensor = AtlasTensor.load();
                painted = paint(tensor);
            } catch (RuntimeException exc) {
                if (!announced) {
                    announced = true;
                    Octia.LOGGER.error("Octia: no atlas map - the tensor would not load.", exc);
                }
                return null;
            }
        }

        ServerLevel overworld = server.overworld();
        MapId id = overworld.getFreeMapId();

        // Fresh, then locked, then painted. locked() returns a NEW instance -
        // it is not a setter - so painting before locking would paint the copy
        // that gets thrown away.
        MapItemSavedData data = MapItemSavedData
                .createFresh(0.0, 0.0, (byte) 0, false, false, Level.OVERWORLD)
                .locked();
        System.arraycopy(painted, 0, data.colors, 0, painted.length);
        data.setDirty();
        overworld.setMapData(id, data);

        held.id = id.id();
        held.setDirty();
        Octia.LOGGER.info("Octia: painted the atlas onto map #{}. {} tiles across {} colours.",
                held.id, tensor.count(), tensor.colours());
        return id;
    }

    /**
     * The tensor, laid out on a map sheet and quantised to map colours.
     *
     * <p>Quantisation happens over the palette, not over the pixels: 103
     * nearest-colour searches instead of 11,264. That is the payoff of storing
     * art as indices rather than as RGB.
     *
     * <p>It is also the one place this pipeline snaps a colour, and that is
     * allowed here for the reason it is refused in {@code docs/PALETTE.md}: the
     * ramp is what the art <i>is</i> and must not be approximated, whereas this
     * map is a <i>picture of</i> the art. Minecraft offers 62 base colours at
     * four brightnesses and nothing else, so a map either snaps or does not
     * exist.
     */
    static byte[] paint(AtlasTensor from) {
        int[] palette = from.palette();
        byte[] quantised = new byte[palette.length];
        for (int i = 0; i < palette.length; i++) {
            quantised[i] = nearestMapColour(palette[i]);
        }

        byte[] out = new byte[MAP * MAP];
        for (int tile = 0; tile < from.count() && tile < ACROSS * ACROSS; tile++) {
            int originX = (tile % ACROSS) * AtlasTensor.TILE;
            int originY = (tile / ACROSS) * AtlasTensor.TILE;
            for (int y = 0; y < AtlasTensor.TILE; y++) {
                for (int x = 0; x < AtlasTensor.TILE; x++) {
                    out[(originY + y) * MAP + originX + x] = nearestMapColour(from.texel(tile, x, y));
                }
            }
        }
        return out;
    }

    /**
     * The closest map colour to a packed ARGB texel.
     *
     * <p>Built from {@link MapColor#col} and {@link MapColor.Brightness#modifier}
     * rather than from {@code calculateRGBColor}, because {@code col} is
     * unambiguously {@code 0xRRGGBB} - {@code MapColor.FIRE} is
     * {@code 0xFF0000} in the shipped 1.21.1 jar - while the packed return of
     * {@code calculateRGBColor} has changed channel order between versions.
     * OCTIA.md standing order: when a version-dependent detail matters, open the
     * artifact and look. It was opened.
     */
    private static byte nearestMapColour(int argb) {
        // Map colour 0 is "nothing" and draws as fully transparent, which is
        // exactly what the ramp's '.' key means. Anything part-transparent goes
        // the same way: a map has no alpha channel to put it in.
        if ((argb >>> 24) < 128) {
            return 0;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        byte best = 0;
        long closest = Long.MAX_VALUE;
        // Ids run 0..63 - the jar's own bounds message says so. Id 0 is NONE
        // and is skipped: it is the transparent slot handled above, and letting
        // it win here would punch holes in opaque art.
        for (int id = 1; id <= 63; id++) {
            MapColor colour = MapColor.byId(id);
            if (colour == null || colour == MapColor.NONE) {
                continue;
            }
            for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
                int shade = brightness.modifier;
                int cr = ((colour.col >> 16 & 0xFF) * shade) / 255;
                int cg = ((colour.col >> 8 & 0xFF) * shade) / 255;
                int cb = ((colour.col & 0xFF) * shade) / 255;
                long distance = (long) (r - cr) * (r - cr)
                        + (long) (g - cg) * (g - cg)
                        + (long) (b - cb) * (b - cb);
                if (distance < closest) {
                    closest = distance;
                    best = colour.getPackedId(brightness);
                }
            }
        }
        return best;
    }

    /**
     * Which map id this save painted the atlas onto.
     *
     * <p>One integer, and it has to be stored: {@code getFreeMapId} hands out a
     * new id every call, so a server that forgot would mint a fresh map on every
     * load and leave the old ones in {@code data/} forever.
     */
    public static final class Held extends SavedData {

        /** Becomes {@code <save>/data/octia_atlas_map.dat}. */
        private static final String FILE = "octia_atlas_map";

        /** For the DataFixTypes choice, see the note in ShipMoorings. */
        private static final SavedData.Factory<Held> FACTORY = new SavedData.Factory<>(
                Held::new, Held::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

        private static final String KEY = "map";

        /** -1 until the atlas has been painted for this save. */
        private int id = -1;

        private Held() {
        }

        static Held get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
        }

        private static Held load(CompoundTag tag, HolderLookup.Provider registries) {
            Held out = new Held();
            out.id = tag.contains(KEY) ? tag.getInt(KEY) : -1;
            return out;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt(KEY, id);
            return tag;
        }
    }
}
