package com.serenity.octia.tower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A tower, as somebody typed it, turned into blocks to put in the world.
 *
 * <p><b>Why text.</b> The brief is a terminal in which a person describes a
 * factory - an office tower seen from the side, one floor above another. What a
 * terminal produces is characters, so that is what this reads. It costs nothing
 * to store, nothing to diff, a person can write one in Notepad, and when this
 * eventually has to cross a wire it is already a string. A binary grid format
 * would have bought nothing and would have needed a tool to author.
 *
 * <p><b>No Minecraft on the imports, and none wanted.</b> Same discipline as
 * {@code Sightlines}, for a different reason. There the constraint was worker
 * threads; here it is that this is the half of the feature that can be
 * <em>proven</em>. The gates run headless - no display, no account - so nothing
 * about how a tower looks can ever be defended by CI. What can be defended is
 * that a given description yields exactly these placements, and that is a pure
 * function testable in milliseconds without a world. Everything uncertain is
 * pushed to the other side of this line on purpose.
 *
 * <p><b>The projection.</b> A side-on drawing has two axes and the world has
 * three. Rows become height and columns become width, which is the reading a
 * person already has when they type it: the top row is the top floor. The third
 * axis is depth, extruded, because a drawing does not carry one - so a tower is
 * a slab, and how thick a slab is the caller's business rather than the
 * author's.
 *
 * <p><b>The caps are a trust boundary, not tidiness.</b> This is the one object
 * in the mod that is authored by a person rather than derived from the seed, and
 * the intent is that it eventually arrives from a client. So the limits below
 * are enforced at the point of parsing, before anything is allocated in
 * proportion to the input, and the vocabulary is a closed set of characters
 * rather than anything that could name an arbitrary block. A description that
 * cannot be parsed is refused whole; there is no partial tower.
 */
public final class TowerPlan {

    /**
     * The widest and tallest a description may be.
     *
     * <p>Chosen against the derelict, which is three blocks on a side, and the
     * obelisk, which is at most thirteen tall. A tower an order of magnitude
     * past the largest thing the mod builds is already a landmark; past that it
     * is a wall. These also bound the work this class does before it has any
     * reason to trust its input.
     */
    public static final int MAX_WIDTH = 64;
    public static final int MAX_HEIGHT = 64;

    /** Belt and braces on the two above: no description may exceed this many cells. */
    public static final int MAX_CELLS = 2048;

    /**
     * How thick a slab may be extruded to.
     *
     * <p>A separate ceiling because depth is the caller's, not the author's, and
     * it multiplies: a description already at {@link #MAX_CELLS} extruded to
     * sixteen is 32,768 blocks, which is the point where this stops being a
     * structure and starts being a chunk rewrite.
     */
    public static final int MAX_DEPTH = 16;

    /**
     * The rhythm: five columns to a bay.
     *
     * <p>Five is already this mod's module. {@code ArchFeature} is keystone plus
     * four - five stones across, chosen because it is the smallest odd span that
     * reads as an arch rather than a doorway and the only kind that can carry a
     * centre stone. A tower drawn in bays of five is in the same measure as the
     * arches on the threads, which is what makes an authored building look like
     * it belongs to the same hands.
     *
     * <p>It is not enforced. {@link #bays()} reports it and {@link #inRhythm()}
     * answers whether a drawing keeps it, so a tool can tell an author they have
     * fallen out of measure without the parser refusing a tower over taste.
     */
    public static final int BAY = 5;

    /** What a cell of the drawing is. The whole vocabulary, deliberately small. */
    public enum Cell {

        /** Nothing. Air, and not written to the world at all. */
        EMPTY(' '),

        /** Structure. An unlit frame panel - the walls, the floors, the shafts. */
        FRAME('#'),

        /** A working light. Frame panel at the middling tier. */
        LAMP('+'),

        /** A dressed light. The brightest thing the mod owns. */
        BEACON('*'),

        /**
         * The anchorage. Exactly one per tower, and the reason a tower is a place
         * rather than a sculpture - it is where the hull is, so it is where
         * arrival happens.
         */
        CORE('O');

        private final char glyph;

        Cell(char glyph) {
            this.glyph = glyph;
        }

        public char glyph() {
            return glyph;
        }

        /** The cell a character names, or null if the vocabulary does not have it. */
        public static Cell of(char c) {
            // A dot reads as empty too: leading spaces are easy to lose in transit
            // through a terminal, a chat message, or a JSON string, and a drawing
            // whose left margin has been trimmed is a different tower.
            if (c == ' ' || c == '.') {
                return EMPTY;
            }
            for (Cell cell : values()) {
                if (cell.glyph == c) {
                    return cell;
                }
            }
            return null;
        }
    }

    /**
     * One block to place, in the tower's own frame.
     *
     * @param x across the face, zero at the drawing's left column
     * @param y up, zero at the drawing's <em>bottom</em> row
     * @param z into the slab, zero at the front face
     */
    public record Placement(int x, int y, int z, Cell cell) {
    }

    /** Raised when a description is not a tower. Refused whole, never in part. */
    public static final class Malformed extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        Malformed(String message) {
            super(message);
        }
    }

    private final List<String> rows;
    private final int width;
    private final int height;

    private TowerPlan(List<String> rows, int width) {
        this.rows = rows;
        this.width = width;
        this.height = rows.size();
    }

    /** Columns in the drawing. */
    public int width() {
        return width;
    }

    /** Rows in the drawing, which is the tower's height in blocks. */
    public int height() {
        return height;
    }

    /**
     * Reads a description.
     *
     * <p>Rows are given top first, the way they were drawn and the way a person
     * reads them. Short rows are padded with empty rather than refused, because
     * trailing spaces do not survive most of the ways text moves between two
     * machines and a tower should not depend on them.
     *
     * @throws Malformed if the description is empty, too large, carries a
     *                   character outside the vocabulary, or does not name
     *                   exactly one core
     */
    public static TowerPlan parse(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new Malformed("a tower with no rows is not a tower");
        }
        if (lines.size() > MAX_HEIGHT) {
            throw new Malformed("tower is " + lines.size() + " rows, the ceiling is " + MAX_HEIGHT);
        }

        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, line == null ? 0 : line.length());
        }
        if (widest == 0) {
            throw new Malformed("a tower with no columns is not a tower");
        }
        if (widest > MAX_WIDTH) {
            throw new Malformed("tower is " + widest + " columns, the ceiling is " + MAX_WIDTH);
        }
        if (widest * lines.size() > MAX_CELLS) {
            throw new Malformed("tower is " + (widest * lines.size())
                    + " cells, the ceiling is " + MAX_CELLS);
        }

        int cores = 0;
        List<String> kept = new ArrayList<>(lines.size());
        for (int r = 0; r < lines.size(); r++) {
            String line = lines.get(r) == null ? "" : lines.get(r);
            StringBuilder row = new StringBuilder(widest);
            for (int c = 0; c < widest; c++) {
                char raw = c < line.length() ? line.charAt(c) : ' ';
                Cell cell = Cell.of(raw);
                if (cell == null) {
                    throw new Malformed("row " + r + " column " + c
                            + " is '" + raw + "', which is not in the vocabulary");
                }
                if (cell == Cell.CORE) {
                    cores++;
                }
                row.append(cell.glyph());
            }
            kept.add(row.toString());
        }

        if (cores != 1) {
            // Not a style rule. The core is the anchorage, and a tower with two
            // of them or none has no single place where arrival happens.
            throw new Malformed("a tower needs exactly one core, this one has " + cores);
        }

        return new TowerPlan(Collections.unmodifiableList(kept), widest);
    }

    /** Reads a description given as one string, rows separated by newlines. */
    public static TowerPlan parse(String text) {
        if (text == null) {
            throw new Malformed("a tower with no rows is not a tower");
        }
        return parse(List.of(text.split("\r?\n", -1)));
    }

    /** The cell at a column and a row, rows counted from the top as drawn. */
    public Cell at(int column, int rowFromTop) {
        if (column < 0 || column >= width || rowFromTop < 0 || rowFromTop >= height) {
            return Cell.EMPTY;
        }
        return Cell.of(rows.get(rowFromTop).charAt(column));
    }

    /** Where the core stands, as a placement at depth zero. */
    public Placement core() {
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (at(c, r) == Cell.CORE) {
                    return new Placement(c, height - 1 - r, 0, Cell.CORE);
                }
            }
        }
        // parse() refuses any description without exactly one, so this is
        // unreachable rather than a case worth handling.
        throw new IllegalStateException("a parsed tower lost its core");
    }

    /**
     * Every block the tower puts in the world, extruded {@code depth} deep.
     *
     * <p>Empty cells are absent rather than present-and-air: the caller writes
     * what it is given, so a tower carves nothing and a drawing's gaps leave
     * whatever was already there. That is the same rule every ruin in this mod
     * follows, and it is what lets a tower be dropped on uneven ground without
     * terraforming it.
     *
     * <p>The core is placed once at the front face however deep the slab is.
     * There is exactly one anchorage, and extruding it would make a column of
     * them - each of which {@code ShipCoreBlock} would survey separately.
     *
     * <p>Ordering is bottom row first, then left to right, then front to back,
     * and is stable for a given plan. Callers that write blocks in this order
     * build a tower from the ground up, which is the order that looks right if
     * anything is ever watching it happen.
     */
    public List<Placement> placements(int depth) {
        if (depth < 1 || depth > MAX_DEPTH) {
            throw new Malformed("a tower cannot be " + depth
                    + " deep; the range is 1 to " + MAX_DEPTH);
        }

        List<Placement> out = new ArrayList<>();
        for (int r = height - 1; r >= 0; r--) {
            int y = height - 1 - r;
            for (int c = 0; c < width; c++) {
                Cell cell = at(c, r);
                if (cell == Cell.EMPTY) {
                    continue;
                }
                if (cell == Cell.CORE) {
                    out.add(new Placement(c, y, 0, cell));
                    continue;
                }
                for (int d = 0; d < depth; d++) {
                    out.add(new Placement(c, y, d, cell));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The drawing, normalised: padded to width, dots and spaces both as spaces. */
    public List<String> rows() {
        return rows;
    }

    /**
     * How many whole bays of {@value #BAY} the drawing is wide, rounded up. A
     * tower eleven columns across is three bays with four columns to spare.
     */
    public int bays() {
        return (width + BAY - 1) / BAY;
    }

    /** Whether the drawing sits on the five-column measure with nothing left over. */
    public boolean inRhythm() {
        return width % BAY == 0;
    }

    @Override
    public String toString() {
        return "TowerPlan[" + width + "x" + height + "]";
    }
}
