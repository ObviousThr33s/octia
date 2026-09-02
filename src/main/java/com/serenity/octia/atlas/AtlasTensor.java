package com.serenity.octia.atlas;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The whole game's art, as one tensor, read out of the jar.
 *
 * <p>Every texture this mod ships is cut into 16x16 tiles and stacked into a
 * single {@code (N, 16, 16)} array of palette indices, alongside the palette
 * those indices point into. {@code tools/atlas.py --build} writes it and
 * {@code tools/atlas.py --check} gates it against the PNGs on every verify, so
 * what this class loads cannot have drifted from what the game renders. See
 * {@code docs/PALETTE.md}.
 *
 * <p><b>Why an index tensor and not just the PNGs.</b> The mod already ships
 * the PNGs and vanilla already stitches them; re-reading them here would buy
 * nothing. Indices buy something: a palette swap is one array write instead of
 * fourteen reimports, and the atlas map this class feeds is itself an indexed
 * image - Minecraft map data is a byte per pixel naming a colour, which is the
 * same shape of thing. Going index-to-index means the quantisation happens once
 * over 103 palette entries rather than once over every one of 11,264 texels.
 *
 * <p><b>Loaded from the classpath, not the ResourceManager.</b> This is server
 * data - it paints a map that a dedicated server hands out - and
 * {@code ResourceManager} is a client-and-datapack thing that would make the
 * mod need a resource reload before a map could be drawn. A jar entry is
 * available the moment the class is.
 *
 * <p>The container is safetensors: a little-endian {@code uint64} header
 * length, a JSON header naming each tensor's dtype, shape and byte range, then
 * the buffers back to back. It is parsed by hand below because that is roughly
 * twelve lines and the alternative is a dependency - the same trade
 * {@code tools/pixel.py} makes when it writes PNG without Pillow.
 */
public final class AtlasTensor {

    /** The tile every sheet is cut into. Matches TILE in tools/atlas.py. */
    public static final int TILE = 16;

    /** Written by tools/atlas.py --build. Inside the jar, so no reload is needed. */
    private static final String PATH = "/assets/octia/atlas/atlas.safetensors";

    /** count * TILE * TILE palette indices, tile-major then row-major. */
    private final byte[] sprites;

    /** Packed ARGB per palette entry. Index 0..10 is the docs/PALETTE.md ramp. */
    private final int[] palette;

    private final int count;

    private AtlasTensor(byte[] sprites, int[] palette, int count) {
        this.sprites = sprites;
        this.palette = palette;
        this.count = count;
    }

    /** How many 16x16 tiles the game amounts to. */
    public int count() {
        return count;
    }

    /** How many distinct colours the shipped art contains. */
    public int colours() {
        return palette.length;
    }

    /**
     * One texel, as packed ARGB.
     *
     * @param tile which 16x16 tile, {@code 0 <= tile < count()}
     */
    public int texel(int tile, int x, int y) {
        return palette[Byte.toUnsignedInt(sprites[tile * TILE * TILE + y * TILE + x])];
    }

    /** The palette itself, copied, so a caller cannot edit the loaded tensor. */
    public int[] palette() {
        return palette.clone();
    }

    /**
     * Reads the tensor out of the jar.
     *
     * @throws IllegalStateException if it is missing or malformed, which means
     *         the build shipped without running {@code atlas.py --build}. That
     *         is a broken jar rather than a runtime condition, so it is loud.
     */
    public static AtlasTensor load() {
        byte[] raw;
        try (InputStream in = AtlasTensor.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "no " + PATH + " in the jar. Run: python tools/atlas.py --build");
            }
            raw = in.readAllBytes();
        } catch (IOException exc) {
            throw new IllegalStateException("could not read " + PATH, exc);
        }

        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        long headerLength = buf.getLong(0);
        if (headerLength <= 0 || headerLength > raw.length - 8L) {
            throw new IllegalStateException(PATH + ": header length " + headerLength
                    + " does not fit in " + raw.length + " bytes");
        }
        int start = 8 + (int) headerLength;
        JsonObject header = JsonParser
                .parseString(new String(raw, 8, (int) headerLength, StandardCharsets.UTF_8))
                .getAsJsonObject();

        JsonObject spritesEntry = header.getAsJsonObject("sprites");
        JsonObject paletteEntry = header.getAsJsonObject("palette");
        if (spritesEntry == null || paletteEntry == null) {
            throw new IllegalStateException(PATH + ": expected 'sprites' and 'palette' tensors");
        }

        JsonArray shape = spritesEntry.getAsJsonArray("shape");
        int count = shape.get(0).getAsInt();
        if (shape.get(1).getAsInt() != TILE || shape.get(2).getAsInt() != TILE) {
            throw new IllegalStateException(PATH + ": tiles are "
                    + shape.get(1).getAsInt() + "x" + shape.get(2).getAsInt()
                    + ", this reader expects " + TILE + "x" + TILE);
        }

        byte[] sprites = slice(raw, start, spritesEntry);
        byte[] paletteBytes = slice(raw, start, paletteEntry);
        if (sprites.length != count * TILE * TILE) {
            throw new IllegalStateException(PATH + ": sprites buffer is "
                    + sprites.length + " bytes, shape says " + (count * TILE * TILE));
        }
        if (paletteBytes.length % 4 != 0) {
            throw new IllegalStateException(PATH + ": palette is not a whole number of RGBA");
        }

        int[] palette = new int[paletteBytes.length / 4];
        for (int i = 0; i < palette.length; i++) {
            int r = Byte.toUnsignedInt(paletteBytes[i * 4]);
            int g = Byte.toUnsignedInt(paletteBytes[i * 4 + 1]);
            int b = Byte.toUnsignedInt(paletteBytes[i * 4 + 2]);
            int a = Byte.toUnsignedInt(paletteBytes[i * 4 + 3]);
            palette[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        // An index that points past the palette would be a silent wrong colour
        // later; caught here, where the message can still name the file.
        for (byte index : sprites) {
            if (Byte.toUnsignedInt(index) >= palette.length) {
                throw new IllegalStateException(PATH + ": an index points outside the palette");
            }
        }

        return new AtlasTensor(sprites, palette, count);
    }

    private static byte[] slice(byte[] raw, int start, JsonObject entry) {
        JsonArray offsets = entry.getAsJsonArray("data_offsets");
        int from = start + offsets.get(0).getAsInt();
        int to = start + offsets.get(1).getAsInt();
        if (from < start || to > raw.length || to < from) {
            throw new IllegalStateException(PATH + ": a tensor's byte range is outside the file");
        }
        byte[] out = new byte[to - from];
        System.arraycopy(raw, from, out, 0, out.length);
        return out;
    }
}
