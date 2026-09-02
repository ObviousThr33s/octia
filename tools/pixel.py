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

# ---- the sky ramp, computed rather than kept -------------------------------
# docs/PALETTE.md rules that a sky colour is named in three words and that the
# sky is not a second palette: it is RAMP above, warmth removed, hue turned to
# blue, LIGHTNESS HELD. Holding lightness is the load-bearing part - it is what
# makes PALE_BLUE_GREY exactly as bright as the `l` andesite highlight, so a lit
# hull against the sky reads as one material lit two ways.
#
# This is a FUNCTION and not a table on purpose. A second table would be a second
# source of truth, and the note above RAMP already asks for one copy of that
# problem, not two - move a ramp colour and the sky follows it in the same commit.
SKY_HUE = 210.0 / 360.0
SKY_SAT = 0.15

# The void is not sky and takes no blue. It is listed in the doc for completeness
# and is exempt from the transform, which is why it is named here rather than
# silently skipped.
SKY_EXEMPT = {"v"}

SKY_NAMES = {
    "b": "COLD_BONE_WHITE",
    "l": "PALE_BLUE_GREY",
    "m": "DEEP_BLUE_GREY",
    "k": "DARK_BLUE_GREY",
    "v": "FAR_VOID_BLACK",
}


def sky_colour(key):
    """The sky colour derived from one ramp key, as an (r, g, b) triple."""
    import colorsys

    r, g, b, _ = RAMP[key]
    if key in SKY_EXEMPT:
        return (r, g, b)
    h, lightness, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    out = colorsys.hls_to_rgb(SKY_HUE, lightness, SKY_SAT)
    return tuple(round(c * 255) for c in out)


def sky_ramp():
    """Every sky colour, in the order docs/PALETTE.md lists them: light to dark."""
    return [(SKY_NAMES[k], k, RAMP[k][:3], sky_colour(k)) for k in ("b", "l", "m", "k", "v")]


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


EGADS = "# EGADS "


def read_egads(path):
    """The reason a sprite declares for departing from the discretionary rules.

    A line `# EGADS <reason>` at the top of an art file. Returns the reason, or
    None when the file makes no such claim.

    WHY THIS EXISTS. Every rule below is a good rule and one of them will
    eventually be wrong for one sprite. A checker with no way to say "this one is
    deliberate" does not get obeyed - it gets bypassed, and a person who runs
    pixel.py once, loses, and hand-writes the PNG afterwards has taken the sprite
    out of every check at once rather than out of one. So the hatch is here, it
    costs a sentence, and it is never quiet: main() prints what was waived and
    why, every run, pass or fail.

    It waives only what is discretionary - the colour cap and the closed
    outline. It cannot waive 16x16 or an unknown key, because those are not
    opinions about taste: a grid of the wrong size is not a sprite, and a key
    with no entry in RAMP has no colour to write.
    """
    with open(path, "r", encoding="ascii") as handle:
        for line in handle:
            stripped = line.strip()
            if stripped.startswith(EGADS.strip() + " "):
                reason = stripped[len(EGADS.strip()):].strip()
                return reason or "(no reason given)"
            if stripped and not stripped.startswith("#"):
                # The picture has started. A declaration below the art is a
                # comment about it, not a claim on it.
                return None
    return None


def faults(rows, egads=None):
    """Every rule in docs/PALETTE.md that a machine can decide, in one pass.

    Returns `(complaints, waived)`. Both are lists of plain sentences: the first
    stops the compile, the second is what `# EGADS` allowed through and is
    printed rather than swallowed. An empty `complaints` means the grid obeys the
    shape rules; it does not mean the sprite is good.
    """
    found = []
    waived = []

    # ---- structural. EGADS cannot reach these ----------------------------
    if len(rows) != SIZE:
        found.append("the grid is %d rows; a sprite is %d" % (len(rows), SIZE))
        return found, waived

    for n, row in enumerate(rows):
        if len(row) != SIZE:
            found.append("row %d is %d characters; a sprite is %d wide"
                         % (n + 1, len(row), SIZE))

    if found:
        return found, waived

    unknown = sorted({c for row in rows for c in row} - set(RAMP))
    if unknown:
        found.append("off the ramp: %s - see docs/PALETTE.md"
                     % ", ".join("'%s'" % c for c in unknown))
        return found, waived

    # ---- discretionary. EGADS moves these from fatal to noted ------------
    discretionary = []

    spent = sorted({c for row in rows for c in row} - {CLEAR})
    if len(spent) > MAX_COLOURS:
        discretionary.append("%d colours spent (%s); the cap is %d"
                             % (len(spent), "".join(spent), MAX_COLOURS))

    discretionary.extend(outline_faults(rows))

    if egads:
        waived.extend(discretionary)
    else:
        found.extend(discretionary)
    return found, waived


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
    on every scanline: at these sizes there is nothing to gain from filtering
    and a reader can follow this by eye.

    The size comes from the grid rather than from SIZE, because an entity UV
    sheet is not 16x16 and a writer that can only make one size cannot repaint
    one. `faults` is what decides whether a given size is allowed; this only
    writes what it is handed.
    """
    height = len(rows)
    width = len(rows[0]) if rows else 0

    raw = bytearray()
    for row in rows:
        raw.append(0)
        for key in row:
            raw.extend(bytes(RAMP[key][:4]))

    def chunk(tag, body):
        out = struct.pack(">I", len(body)) + tag + body
        return out + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def unfilter(kind, line, prior, bpp):
    """Undo one PNG scanline filter, in place.

    `png` above only ever writes filter 0, but the textures that predate this
    tool were written by encoders that had opinions, so a reader that only
    handles 0 would refuse most of the mod's own art. All five are here; the
    arithmetic is straight out of the PNG spec, section 7.
    """
    if kind == 0:
        return
    if kind == 1:  # Sub - the pixel to the left
        for i in range(bpp, len(line)):
            line[i] = (line[i] + line[i - bpp]) & 0xFF
    elif kind == 2:  # Up - the pixel above
        for i in range(len(line)):
            line[i] = (line[i] + prior[i]) & 0xFF
    elif kind == 3:  # Average - the mean of left and above, rounded down
        for i in range(len(line)):
            left = line[i - bpp] if i >= bpp else 0
            line[i] = (line[i] + ((left + prior[i]) >> 1)) & 0xFF
    elif kind == 4:  # Paeth - whichever of left/above/above-left is nearest
        for i in range(len(line)):
            a = line[i - bpp] if i >= bpp else 0
            b = prior[i]
            c = prior[i - bpp] if i >= bpp else 0
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            if pa <= pb and pa <= pc:
                pred = a
            elif pb <= pc:
                pred = b
            else:
                pred = c
            line[i] = (line[i] + pred) & 0xFF
    else:
        raise ValueError("scanline filter %d is not one of the five" % kind)


def unpng(data):
    """Read a 32-bit RGBA PNG back into pixels - the inverse of `png` above.

    Returns `(width, height, rows)`, where a row is a list of `(r, g, b, a)`
    tuples. It lives here rather than in the atlas tool because the inverse of
    a function belongs beside it: move one and the other is in the diff.

    Deliberately narrow. 8-bit RGBA, not interlaced, is what this mod's art is
    and what `png` emits, so anything else is refused **by name** instead of
    guessed at. A texture tool that quietly mangles an unexpected colour type
    is worse than one that stops.
    """
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG - the eight-byte signature is wrong")

    width = height = None
    idat = bytearray()
    pos = 8
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length  # length, tag, body, CRC

        if tag == b"IHDR":
            width, height, depth, colour, _comp, _filt, interlace = \
                struct.unpack(">IIBBBBB", body)
            if depth != 8:
                raise ValueError("bit depth %d; this reader does 8" % depth)
            if colour != 6:
                raise ValueError(
                    "colour type %d; this reader does 6 (RGBA) - see the ramp, "
                    "every key of which carries an alpha" % colour)
            if interlace:
                raise ValueError("interlaced; this reader does not de-interlace")
        elif tag == b"IDAT":
            # Split across chunks is legal and common. Concatenate, then inflate
            # once - the compressed stream is continuous across the split.
            idat.extend(body)
        elif tag == b"IEND":
            break

    if width is None:
        raise ValueError("no IHDR chunk - the file has no header")

    raw = zlib.decompress(bytes(idat))
    stride = width * 4
    prior = bytearray(stride)
    rows = []
    at = 0
    for y in range(height):
        kind = raw[at]
        at += 1
        line = bytearray(raw[at:at + stride])
        at += stride
        if len(line) != stride:
            raise ValueError("scanline %d is short: %d bytes, wanted %d"
                             % (y, len(line), stride))
        unfilter(kind, line, prior, 4)
        rows.append([tuple(line[x * 4:x * 4 + 4]) for x in range(width)])
        prior = line

    return width, height, rows


def main(argv):
    parser = argparse.ArgumentParser(
        description="Compile an ASCII sprite grid into a PNG on the estate ramp.")
    # nargs="?" on source too, because --sky compiles nothing and needs no art
    # file. Without it, printing the sky ramp would require naming a sprite that
    # has nothing to do with it.
    parser.add_argument("source", nargs="?", help="the art file - 16 lines of 16 keys")
    parser.add_argument("target", nargs="?",
                        help="where the PNG goes; omitted with --check")
    parser.add_argument("--check", action="store_true",
                        help="validate the grid and write nothing")
    parser.add_argument("--sky", action="store_true",
                        help="print the sky ramp derived from the sprite ramp, and exit")
    args = parser.parse_args(argv)

    if args.sky:
        # The table in docs/PALETTE.md is this output pasted in. Regenerate it
        # here rather than editing the doc by hand, so the two cannot disagree.
        print("sky ramp - RAMP with hue %d, saturation %.2f, lightness held"
              % (round(SKY_HUE * 360), SKY_SAT))
        for name, key, base, out in sky_ramp():
            exempt = "  (exempt - the void takes no blue)" if key in SKY_EXEMPT else ""
            print("  %-16s %s  #%02X%02X%02X  ->  #%02X%02X%02X%s"
                  % (name, key, base[0], base[1], base[2], out[0], out[1], out[2], exempt))
        return 0

    if not args.source:
        parser.error("an art file is required unless --sky is given")
    if not args.check and not args.target:
        parser.error("a target path is required unless --check is given")

    rows = read_grid(args.source)
    egads = read_egads(args.source)
    complaints, waived = faults(rows, egads)

    # Printed before the verdict and to stderr, so it is seen on a pass as well
    # as a failure, and so a pipe that only keeps stdout still cannot lose it. A
    # waiver nobody reads is the same as no rule at all.
    if waived:
        sys.stderr.write("%s: EGADS - %s\n" % (args.source, egads))
        for allowed in waived:
            sys.stderr.write("  allowed: %s\n" % allowed)
    elif egads and not complaints:
        # Worth saying, but only on a pass. A declaration that waives nothing is
        # either a rule that got fixed and a note left behind, or a
        # misunderstanding of what EGADS reaches, and both want deleting.
        #
        # Suppressed when the sprite is failing anyway: a structural fault
        # returns before the discretionary rules are even reached, so "nothing
        # needed waiving" would be true, useless, and read as reassurance
        # printed directly above a refusal.
        sys.stderr.write("%s: EGADS declared but nothing needed waiving - %s\n"
                         % (args.source, egads))

    if complaints:
        sys.stderr.write("%s does not obey docs/PALETTE.md:\n" % args.source)
        for complaint in complaints:
            sys.stderr.write("  - %s\n" % complaint)
        return 1

    if args.check:
        spent = sorted({c for row in rows for c in row} - {CLEAR})
        print("%s: ok, %d colours (%s)%s"
              % (args.source, len(spent), "".join(spent), " [EGADS]" if waived else ""))
        return 0

    with open(args.target, "wb") as handle:
        handle.write(png(rows))
    print("%s -> %s" % (args.source, args.target))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
