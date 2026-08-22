"""
Every Roman emperor as two glyphs, and the sheet that proves it fits.

Why this exists. docs/TRAJECTORY.traj claims a story can be packed into a line
of glyphs, one glyph a beat, and read off a cave wall without a wiki. That is
easy to claim on twelve beats of a mod's own fiction, where the author picks
both the beats and the alphabet. Eighty-five Roman emperors are the honest test:
somebody else chose the events, there are far more of them than the wall wants,
and every one of them ends the same two ways - in bed or not in bed.

So the alphabet gets thirteen glyphs and every emperor gets exactly two: how he
came in, how he went out. If the whole Principate and Dominate fit on one sheet
under the 128 px ceiling and still read left to right, the claim survives. If
they do not, the claim was decoration.

What it draws: one column per emperor, rise glyph over nothing and fall glyph
beside it, eight emperors a row, 8 px a glyph. Eighty-five emperors is eleven
rows, which is 128 x 110 - inside the ceiling on both sides with room spare.

No dependencies, no network, and the PNG is written by hand out of zlib for the
same reason tools/sightline-map.py embeds its own data: a tool that needs
installing before it answers a question does not get run.

Usage:
    python tools/emperor-strip.py                 the strip, as text
    python tools/emperor-strip.py --legend        what each glyph means
    python tools/emperor-strip.py -o strip.png    the sheet, as a PNG
"""

import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "emperors.tsv")

BEATS_PER_ROW = 8
CELL = 8
ROW_GAP = 2
CEILING = 128  # docs/TRAJECTORY.traj VII. Nothing here goes over it.

INK_RISE = (0x2A, 0x21, 0x18)
INK_FALL = (0x7A, 0x2E, 0x22)
PARCHMENT = (0xE8, 0xDE, 0xC6)

RISES = {
    ">": "acclaimed by soldiers",
    "^": "dynastic or adopted heir",
    "$": "bought the throne",
    "x": "seized it in civil war",
    "+": "raised by the senate",
    "=": "made colleague by an emperor",
}

FALLS = {
    "~": "died of illness or age",
    "!": "murdered",
    "#": "killed in battle",
    "*": "executed, or died a captive",
    "%": "took his own life",
    "v": "put down, and outlived it",
    "?": "the sources disagree",
}

# Eight by eight, drawn rather than described, because a glyph argued about in
# prose is a glyph nobody can check. Read them as pictures: a spearhead, a roof,
# a coin, crossed blades, a dagger, a wave.
ART = {
    ">": ("..#.....",
          "..##....",
          "..###...",
          "..####..",
          "..####..",
          "..###...",
          "..##....",
          "..#....."),
    "^": ("........",
          "...##...",
          "..####..",
          ".##..##.",
          "##....##",
          "........",
          "........",
          "........"),
    "$": ("..####..",
          ".##..##.",
          "##.##.##",
          "##.##.##",
          "##.##.##",
          "##.##.##",
          ".##..##.",
          "..####.."),
    "x": ("##....##",
          ".##..##.",
          "..####..",
          "...##...",
          "...##...",
          "..####..",
          ".##..##.",
          "##....##"),
    "+": ("........",
          "...##...",
          "...##...",
          ".######.",
          ".######.",
          "...##...",
          "...##...",
          "........"),
    "=": ("........",
          "........",
          ".######.",
          ".######.",
          "........",
          ".######.",
          ".######.",
          "........"),
    "~": ("........",
          "........",
          "........",
          ".##...#.",
          "#..#.#..",
          "....##..",
          "........",
          "........"),
    "!": ("...##...",
          "...##...",
          "...##...",
          "..####..",
          "...##...",
          "...##...",
          "........",
          "...##..."),
    "#": ("..#..#..",
          "..#..#..",
          "########",
          "..#..#..",
          "..#..#..",
          "########",
          "..#..#..",
          "..#..#.."),
    "*": ("...#....",
          ".#.#.#..",
          "..###...",
          "#######.",
          "..###...",
          ".#.#.#..",
          "...#....",
          "........"),
    "%": ("##....#.",
          "##...#..",
          "....#...",
          "...#....",
          "..#.....",
          ".#...###",
          "#....###",
          "........"),
    "v": ("........",
          "##....##",
          ".##..##.",
          "..####..",
          "...##...",
          "........",
          "........",
          "........"),
    "?": ("..####..",
          ".##..##.",
          ".....##.",
          "...###..",
          "...##...",
          "........",
          "...##...",
          "........"),
}


def emperors(path=DATA):
    """The corpus, in order. Comment lines and blanks are not emperors."""
    out = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 5:
                continue
            name, start, end, rise, fall = parts[:5]
            out.append({
                "name": name,
                "from": int(start),
                "to": int(end),
                "rise": rise,
                "fall": fall,
            })
    return out


def check(reigns):
    """Every glyph used is a glyph the legend defines. A silent typo is a lie."""
    for reign in reigns:
        if reign["rise"] not in RISES:
            raise SystemExit("unknown rise glyph %r on %s" % (reign["rise"], reign["name"]))
        if reign["fall"] not in FALLS:
            raise SystemExit("unknown fall glyph %r on %s" % (reign["fall"], reign["name"]))


def year(value):
    return "%d BC" % -value if value < 0 else "%d" % value


def as_text(reigns):
    """The strip, and then the same thing with the names put back."""
    lines = []
    beats = ["%s%s" % (r["rise"], r["fall"]) for r in reigns]
    for start in range(0, len(beats), BEATS_PER_ROW):
        lines.append(" ".join(beats[start:start + BEATS_PER_ROW]))
    lines.append("")
    for reign in reigns:
        lines.append("%s%s  %-20s %s-%s  %s, %s" % (
            reign["rise"], reign["fall"], reign["name"],
            year(reign["from"]), year(reign["to"]),
            RISES[reign["rise"]], FALLS[reign["fall"]]))
    return "\n".join(lines)


def as_png(reigns, path):
    """The sheet. Two glyphs a column, eight columns a row, ink over parchment."""
    rows = (len(reigns) + BEATS_PER_ROW - 1) // BEATS_PER_ROW
    width = BEATS_PER_ROW * CELL * 2
    height = rows * (CELL + ROW_GAP) - ROW_GAP
    if width > CEILING or height > CEILING:
        raise SystemExit("%dx%d is over the %d px ceiling" % (width, height, CEILING))

    pixels = [[PARCHMENT for _ in range(width)] for _ in range(height)]

    def stamp(glyph, ink, left, top):
        for dy, line in enumerate(ART[glyph]):
            for dx, mark in enumerate(line):
                if mark == "#":
                    pixels[top + dy][left + dx] = ink

    for index, reign in enumerate(reigns):
        row, column = divmod(index, BEATS_PER_ROW)
        left = column * CELL * 2
        top = row * (CELL + ROW_GAP)
        stamp(reign["rise"], INK_RISE, left, top)
        stamp(reign["fall"], INK_FALL, left + CELL, top)

    raw = b"".join(b"\x00" + b"".join(struct.pack("BBB", *p) for p in line) for line in pixels)

    def chunk(tag, body):
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(png)
    return width, height


def legend():
    lines = ["rise"]
    for glyph, meaning in RISES.items():
        lines.append("  %s  %s" % (glyph, meaning))
    lines.append("fall")
    for glyph, meaning in FALLS.items():
        lines.append("  %s  %s" % (glyph, meaning))
    return "\n".join(lines)


def main(argv):
    reigns = emperors()
    check(reigns)

    if "--legend" in argv:
        print(legend())
        return 0

    if "-o" in argv:
        out = argv[argv.index("-o") + 1]
        width, height = as_png(reigns, out)
        print("%d emperors, %d rows, %dx%d px -> %s" % (
            len(reigns), (len(reigns) + BEATS_PER_ROW - 1) // BEATS_PER_ROW, width, height, out))
        return 0

    print(as_text(reigns))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
