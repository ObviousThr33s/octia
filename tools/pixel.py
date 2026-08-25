"""Compile an ASCII sprite grid into a 16x16 PNG on the estate's one ramp.

The art of this mod is coherent by constraint, not generated from noise - see
`docs/PALETTE.md` for the ruling and the eleven colours. This script is the half
of that ruling a machine can enforce: it refuses a grid that leaves the ramp,
that is the wrong size, that spends more than five colours, or whose outline has
a hole in it. What it cannot check is whether the picture is any good, which is
the owner's half.

A sprite's source is a plain-text picture. The PNG is derived, the way
`noise_settings/sky.json` is derived from its generator command - so a texture is
regenerated from its grid, never hand-edited as a PNG. An edited PNG is a
correction the next regeneration throws away in silence.

Usage:
    python tools/pixel.py --check art/item/red_cube.txt
    python tools/pixel.py art/item/red_cube.txt src/main/resources/.../red_cube.png

No third-party imports on purpose. Pillow is not installed on the gardener's
machine and a texture tool that needs a pip install is a texture tool that stops
working the first time somebody clones the repo.
"""

import argparse
import struct
import sys
import zlib

SIZE = 16
MAX_COLOURS = 5

# docs/PALETTE.md is the record; this table is the executable copy of it.
# Keep the two in step - if a colour is added there it must be added here, and
# the doc is the one that carries the reason.
RAMP = {
    "k": (0x3B, 0x3B, 0x3F, 255),  # andesite dark - outlines, shadow
    "m": (0x6E, 0x6E, 0x73, 255),  # andesite mid - the body of most things
    "l": (0xA9, 0xA9, 0xAE, 255),  # andesite light - the lit face
    "g": (0x4A, 0x44, 0x38, 255),  # grime - cordage, leather, worn wood
    "b": (0xD9, 0xD2, 0xC0, 255),  # bone - cloth, plinth, beacon cream
    "i": (0x16, 0x1C, 0x2B, 255),  # ink - squid, glyphs, writing, nothing else
    "p": (0x6B, 0x3F, 0xA0, 255),  # road purple
    "y": (0xC8, 0xA0, 0x2C, 255),  # own gold
    "r": (0x9E, 0x2B, 0x25, 255),  # sealed red
    "v": (0x0B, 0x0B, 0x10, 255),  # void black
    ".": (0x00, 0x00, 0x00, 0),    # transparent
}

OUTLINE = "k"
CLEAR = "."


def read_grid(path):
    """Read an art file into a list of rows, dropping comments and blank lines.

    A line whose first non-space character is '#' is a note to a person, not
    part of the picture. Trailing whitespace is dropped because an editor that
    strips it and one that does not must produce the same sprite.
    """
    rows = []
    with open(path, "r", encoding="ascii") as handle:
        for line in handle:
            line = line.rstrip("\n").rstrip("\r").rstrip()
            if not line or line.lstrip().startswith("#"):
                continue
            rows.append(line)
    return rows


def faults(rows):
    """Every rule in docs/PALETTE.md that a machine can decide, in one pass.

    Returns a list of complaints in plain sentences. An empty list means the
    grid obeys the shape rules; it does not mean the sprite is good.
    """
    found = []

    if len(rows) != SIZE:
        found.append("the grid is %d rows; a sprite is %d" % (len(rows), SIZE))
        return found

    for n, row in enumerate(rows):
        if len(row) != SIZE:
            found.append("row %d is %d characters; a sprite is %d wide"
                         % (n + 1, len(row), SIZE))

    if found:
        return found

    unknown = sorted({c for row in rows for c in row} - set(RAMP))
    if unknown:
        found.append("off the ramp: %s - see docs/PALETTE.md"
                     % ", ".join("'%s'" % c for c in unknown))
        return found

    spent = sorted({c for row in rows for c in row} - {CLEAR})
    if len(spent) > MAX_COLOURS:
        found.append("%d colours spent (%s); the cap is %d"
                     % (len(spent), "".join(spent), MAX_COLOURS))

    found.extend(outline_faults(rows))
    return found


def outline_faults(rows):
    """Whether the drawn shape is fenced all the way round in the outline key.

    The rule exists because a closed outline is what survives extrusion into a
    held model - under this register the extruded sprite IS the model. So the
    test is exactly that: every drawn pixel that touches transparency, or the
    edge of the sprite, must itself be the outline colour.
    """
    holes = []
    for y in range(SIZE):
        for x in range(SIZE):
            here = rows[y][x]
            if here in (CLEAR, OUTLINE):
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                outside = not (0 <= nx < SIZE and 0 <= ny < SIZE)
                if outside or rows[ny][nx] == CLEAR:
                    holes.append((x, y))
                    break

    if not holes:
        return []

    shown = ", ".join("(%d,%d)" % spot for spot in holes[:6])
    more = "" if len(holes) <= 6 else " and %d more" % (len(holes) - 6)
    return ["the outline is open at %s%s - a drawn pixel touching nothing "
            "must be '%s'" % (shown, more, OUTLINE)]


def png(rows):
    """Serialise the grid as a 32-bit RGBA PNG.

    Hand-rolled because the alternative is a dependency. Filter byte 0 (none)
    on every scanline: at 16x16 there is nothing to gain from filtering and a
    reader can follow this by eye.
    """
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for key in row:
            raw.extend(bytes(RAMP[key][:4]))

    def chunk(tag, body):
        out = struct.pack(">I", len(body)) + tag + body
        return out + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def main(argv):
    parser = argparse.ArgumentParser(
        description="Compile an ASCII sprite grid into a PNG on the estate ramp.")
    parser.add_argument("source", help="the art file - 16 lines of 16 keys")
    parser.add_argument("target", nargs="?",
                        help="where the PNG goes; omitted with --check")
    parser.add_argument("--check", action="store_true",
                        help="validate the grid and write nothing")
    args = parser.parse_args(argv)

    if not args.check and not args.target:
        parser.error("a target path is required unless --check is given")

    rows = read_grid(args.source)
    complaints = faults(rows)
    if complaints:
        sys.stderr.write("%s does not obey docs/PALETTE.md:\n" % args.source)
        for complaint in complaints:
            sys.stderr.write("  - %s\n" % complaint)
        return 1

    if args.check:
        spent = sorted({c for row in rows for c in row} - {CLEAR})
        print("%s: ok, %d colours (%s)" % (args.source, len(spent), "".join(spent)))
        return 0

    with open(args.target, "wb") as handle:
        handle.write(png(rows))
    print("%s -> %s" % (args.source, args.target))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
