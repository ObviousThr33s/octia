package com.serenity.octia.gametest;

import com.serenity.octia.atlas.AtlasMap;
import com.serenity.octia.atlas.AtlasTensor;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The atlas reaches a real world as a real map.
 *
 * <p>Three things can break here and none of them fail the build. The tensor
 * can be missing from the jar, because it is written by a Python tool outside
 * Gradle and nothing in {@code processResources} knows it exists. The map can be
 * created unlocked, in which case it works perfectly until the first player
 * walks around holding it and the terrain quietly paints over the art. And the
 * quantisation can collapse - a nearest-colour search with a sign error still
 * returns a valid byte for every pixel, so a completely black map is a passing
 * build and a broken feature.
 *
 * <p>All three are the {@code AGENTS.md} III case exactly: {@code gradlew build}
 * proves it compiles and proves none of this.
 */
public class AtlasMapGameTest implements FabricGameTest {

    /** 128x128. Restated rather than imported so a change to either is visible here. */
    private static final int MAP_TEXELS = 128 * 128;

    /**
     * How much of the sheet must be drawn on for the picture to be real.
     *
     * <p>Forty-four tiles is 11,264 texels, and a good many of those are the
     * transparent margin every item sprite has. This floor is well under the
     * true figure and exists to catch a total collapse - an all-transparent or
     * all-one-colour map - rather than to pin the art down. Pinning it would
     * make every new sprite a test failure.
     */
    private static final int MINIMUM_DRAWN = 3000;

    /**
     * The tensor in the jar is loadable, whole, and indexes its own palette.
     *
     * <p>No count is asserted. The mod gains sprites, and a test that says
     * "forty-four" is a test that fails the next time somebody draws something.
     * What must hold regardless of how much art there is: every index resolves,
     * and the ramp is present.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theTensorLoadsAndEveryIndexResolves(GameTestHelper helper) {
        AtlasTensor tensor = AtlasTensor.load();

        if (tensor.count() <= 0) {
            throw new AssertionError("the atlas tensor holds no tiles");
        }
        // The eleven-key ramp of docs/PALETTE.md always occupies palette[0..10],
        // whether or not any texture uses all of it.
        if (tensor.colours() < 11) {
            throw new AssertionError(
                    "the palette has " + tensor.colours() + " colours; the ramp alone is 11");
        }

        // Reading every texel is the assertion: texel() indexes the palette, so
        // an out-of-range index throws here rather than drawing a wrong colour
        // somewhere nobody is looking.
        boolean anyOpaque = false;
        for (int tile = 0; tile < tensor.count(); tile++) {
            for (int y = 0; y < AtlasTensor.TILE; y++) {
                for (int x = 0; x < AtlasTensor.TILE; x++) {
                    if ((tensor.texel(tile, x, y) >>> 24) == 0xFF) {
                        anyOpaque = true;
                    }
                }
            }
        }
        if (!anyOpaque) {
            throw new AssertionError("every texel in the game is transparent, which cannot be right");
        }
        helper.succeed();
    }

    /**
     * The map exists, is locked, and has a picture on it.
     *
     * <p>Nothing here creates the map. It is painted by the tick hold the first
     * time an Octia world runs a tick, so by the time any gametest is called it
     * has either happened or the feature is broken - which is the assertion.
     *
     * <p><b>Locked is the load-bearing one.</b> An unlocked map is repainted
     * from the terrain whenever a player holds it in its own dimension, so
     * without the lock this feature works in a screenshot and is gone an hour
     * later. That failure cannot be seen from the build, from the item, or from
     * the first minute of play.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theAtlasIsPaintedLockedAndNotBlank(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        MapId id = AtlasMap.painted(server);
        if (id == null) {
            throw new AssertionError("no atlas map was painted; the tick hold never ran or bailed");
        }

        MapItemSavedData data = server.overworld().getMapData(id);
        if (data == null) {
            throw new AssertionError("map #" + id.id() + " was recorded but is not in the level");
        }
        if (!data.locked) {
            throw new AssertionError(
                    "the atlas map is not locked; the terrain will paint over the art");
        }
        if (data.colors.length != MAP_TEXELS) {
            throw new AssertionError(
                    "the map holds " + data.colors.length + " texels, expected " + MAP_TEXELS);
        }

        int drawn = 0;
        for (byte texel : data.colors) {
            if (texel != 0) {
                drawn++;
            }
        }
        if (drawn < MINIMUM_DRAWN) {
            throw new AssertionError("the atlas map is nearly blank: only " + drawn
                    + " of " + MAP_TEXELS + " texels are drawn on");
        }

        // A single flat colour passes the count above and is still a collapsed
        // quantiser, so the picture has to have more than one colour in it.
        byte first = 0;
        boolean varied = false;
        for (byte texel : data.colors) {
            if (texel == 0) {
                continue;
            }
            if (first == 0) {
                first = texel;
            } else if (texel != first) {
                varied = true;
                break;
            }
        }
        if (!varied) {
            throw new AssertionError("the whole atlas quantised to one colour");
        }
        helper.succeed();
    }
}
