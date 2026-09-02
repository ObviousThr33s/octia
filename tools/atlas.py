"""Stack every texture in the game into one tensor.

The art of this mod is already a categorical array and nothing had written it
down as one. A sprite here is not a photograph that happens to be small - it is
a grid of symbols from a closed alphabet, which is a tensor of palette indices
wearing a PNG costume. This tool takes the costume off.

    art/**/*.txt  --build-->  atlas.safetensors  --paint-->  assets/**/*.png

WHAT IS IN THE TENSOR, AND WHY IT IS NOT JUST THE RAMP.

The first cut of this stored indices into the eleven-key ramp of
docs/PALETTE.md and nothing else. Measurement killed it. Only four of the
fourteen textures in this mod are on the ramp - the four that `pixel.py` wrote.
The other ten predate that tool: they are anti-aliased, they spend seventeen to
twenty-five colours in a 16x16, and several of their colours have no ramp
representative at any distance (the cyan beacon glow, the warm gold, the HEV
suit's green). A ramp-only tensor would have covered 4/14 of the game and
called it "the entire game", which is the one thing it must not do.

So the tensor indexes a palette that starts with the ramp and continues with
whatever else is actually on disk:

    palette[0:11]   the docs/PALETTE.md ramp, in the doc's own table order
    palette[11:]    every other colour the game actually contains, sorted

That keeps three properties at once. The whole game fits, losslessly, with no
pixel changed. Ramp indices are fixed constants - 'k' is 0 today and 0 forever,
because a ramp colour holds its slot even if no texture uses it. And the split
at 11 is itself the measurement: `on_ramp` says which tiles are clean, so
bringing a texture onto the ramp later is a number that goes up rather than an
argument.

THE SOURCE OF RECORD IS STILL TEXT, WHERE TEXT EXISTS.
docs/PALETTE.md rules that `art/` is the source and the PNG is derived. That is
true of four textures and false of ten. This tool does not paper over the gap -
it records it per texture in the manifest as `source: art` or `source: png`,
and `--check` gates each kind differently. The count of `art` sources is the
progress meter on that claim.

No third-party imports, for the reason `pixel.py` gives: a texture tool that
needs a pip install stops working the first time somebody clones the repo. The
.npy and .safetensors containers are a header and a raw buffer, so they are
hand-written here the same way `pixel.py` hand-writes PNG. Nothing here needs
numpy; anything that reads the output can use it.

Usage:
    python tools/atlas.py --report          measure, write nothing
    python tools/atlas.py --build           write the tensor
    python tools/atlas.py --paint           write the PNGs back from the tensor
    python tools/atlas.py --paint --dry-run prove the repaint is pixel-identical
    python tools/atlas.py --check           the gate
"""

import argparse
import json
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import pixel  # noqa: E402  - the ramp and the PNG codec live there, not here

TILE = 16

# Where things are, relative to the repo root (the parent of tools/).
ART = "art"
ASSETS = os.path.join("src", "main", "resources", "assets", "octia")

# The tensor SHIPS. It lives under assets/ rather than beside the grids in art/
# because the mod reads it at runtime to paint the atlas map, so it has to be
# inside the jar. art/ keeps what a person edits (the grids) and what a person
# reads (the report); assets/ keeps what the game loads.
TENSOR = os.path.join(ASSETS, "atlas")
OUT_SAFE = os.path.join(TENSOR, "atlas.safetensors")
OUT_NPY = os.path.join(TENSOR, "atlas.npy")
OUT_PALETTE = os.path.join(TENSOR, "palette.npy")
OUT_JSON = os.path.join(TENSOR, "atlas.json")
OUT_UNRAMPED = os.path.join(ART, "UNRAMPED.md")
OUT_SHEET = os.path.join(ART, "atlas-sheet.png")

# The map edge, and the cap in docs/TRAJECTORY.traj. The same number twice, and
# that is the coincidence the atlas map is built on: 128 is eight tiles of 16.
SHEET = 128

# The ramp, as an ordered list. dict order is insertion order and pixel.py's
# RAMP is written in docs/PALETTE.md table order, so this IS the doc's order -
# but pin it explicitly rather than leaning on that, because a reordering of
# the dict would silently renumber every tensor ever written.
RAMP_KEYS = ["k", "m", "l", "g", "b", "i", "p", "y", "r", "v", "."]


def root():
    """The repo root - the parent of the directory this file sits in."""
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def ramp_table():
    """The eleven ramp colours as RGBA tuples, in table order."""
    missing = sorted(set(pixel.RAMP) - set(RAMP_KEYS))
    if missing:
        raise ValueError(
            "pixel.py has ramp keys this tool does not know: %s. Add them to "
            "RAMP_KEYS at the END, so existing tensor indices do not move."
            % ", ".join(missing))
    return [pixel.RAMP[k] for k in RAMP_KEYS]


# ---- finding the art -------------------------------------------------------

def resource_name(png_path):
    """The name a texture answers to, from its path under assets/octia/.

    Textures under `textures/` get the form model JSON uses - `octia:item/x` -
    because that is the string a person will search for. Anything else keeps
    its path, so `icon.png` is `octia:icon` and cannot collide with a texture.
    """
    rel = os.path.relpath(png_path, os.path.join(root(), ASSETS))
    rel = rel.replace(os.sep, "/")
    if rel.endswith(".png"):
        rel = rel[:-4]
    if rel.startswith("textures/"):
        rel = rel[len("textures/"):]
    return "octia:" + rel


def art_for(name):
    """The art grid backing a texture, or None when it has no text source.

    `octia:item/red_cube` -> `art/item/red_cube.txt`
    `octia:icon`          -> `art/icon.txt`
    """
    path = os.path.join(root(), ART, *name[len("octia:"):].split("/"))
    path += ".txt"
    return path if os.path.exists(path) else None


def textures():
    """Every PNG the mod ships, by resource name, in a stable order.

    Only `src/main/resources/assets/octia/`. `bin/main/` and `build/resources/`
    hold stale generated copies of the same files - `bin/` is already missing
    four of them - and `run/` is a dev client. Writing to any of those would be
    writing to build output.
    """
    base = os.path.join(root(), ASSETS)
    found = []
    for here, _dirs, files in os.walk(base):
        for name in sorted(files):
            if name.endswith(".png"):
                found.append(os.path.join(here, name))
    return sorted(found, key=resource_name)


# ---- reading pixels --------------------------------------------------------

def pixels_from_png(path):
    with open(path, "rb") as handle:
        return pixel.unpng(handle.read())


def pixels_from_grid(path):
    """Render an art grid to pixels, refusing a grid that breaks the rules."""
    rows = pixel.read_grid(path)
    egads = pixel.read_egads(path)
    complaints, _waived = pixel.faults(rows, egads)
    if complaints:
        raise ValueError("%s does not obey docs/PALETTE.md: %s"
                         % (path, "; ".join(complaints)))
    out = [[pixel.RAMP[key] for key in row] for row in rows]
    return len(out[0]), len(out), out


# ---- the palette -----------------------------------------------------------

def build_palette(sheets):
    """The ramp, then every other colour the game actually contains.

    Sorted after index 11 so the palette is a pure function of the art: the
    same textures always produce the same indices, on any machine, in any
    Python. An unsorted set would renumber the tensor between runs.
    """
    ramp = ramp_table()
    known = set(ramp)
    extra = set()
    for _name, _w, _h, rows in sheets:
        for row in rows:
            for texel in row:
                if texel not in known:
                    extra.add(texel)
    return ramp + sorted(extra)


# ---- containers, hand-rolled -----------------------------------------------

def npy(shape, data):
    """A .npy file holding a uint8 array - header plus raw buffer, nothing else.

    Format 1.0: magic, version, a little-endian uint16 header length, then a
    Python dict literal padded with spaces so the data begins on a 64-byte
    boundary. numpy.load reads this; numpy is not needed to write it.
    """
    header = "{'descr': '|u1', 'fortran_order': False, 'shape': (%s), }" % (
        ", ".join(str(n) for n in shape) + ("," if len(shape) == 1 else ""))
    prefix = 10  # magic(6) + version(2) + length(2)
    pad = -(prefix + len(header) + 1) % 64
    header = header + " " * pad + "\n"
    return (b"\x93NUMPY\x01\x00"
            + struct.pack("<H", len(header))
            + header.encode("ascii")
            + bytes(data))


def safetensors(named, metadata):
    """One file holding every tensor plus the manifest, in the AI-native format.

    A little-endian uint64 header length, a JSON header of name -> dtype/shape/
    byte range, then the buffers back to back. `__metadata__` is a string map,
    so the manifest goes in as JSON text.
    """
    header = {}
    blob = bytearray()
    for name, (shape, data) in named.items():
        start = len(blob)
        blob.extend(bytes(data))
        header[name] = {"dtype": "U8", "shape": list(shape),
                        "data_offsets": [start, len(blob)]}
    header["__metadata__"] = metadata

    text = json.dumps(header, separators=(",", ":"), sort_keys=True)
    pad = -(len(text)) % 8  # the buffer must start 8-byte aligned
    text = text + " " * pad
    return struct.pack("<Q", len(text)) + text.encode("utf-8") + bytes(blob)


# ---- the build -------------------------------------------------------------

def gather():
    """Every texture, as `(name, width, height, rows, source, art_path)`.

    The grid wins where a grid exists - that is docs/PALETTE.md's ruling - and
    the PNG is the source only where no grid has been written yet.
    """
    out = []
    for path in textures():
        name = resource_name(path)
        grid = art_for(name)
        if grid:
            w, h, rows = pixels_from_grid(grid)
            out.append((name, w, h, rows, "art", grid, path))
        else:
            w, h, rows = pixels_from_png(path)
            out.append((name, w, h, rows, "png", None, path))
    return out


def tile(rows, w, h):
    """Cut a sheet into 16x16 tiles, row-major. This is what makes it a tensor.

    A 64x64 entity sheet is sixteen tiles; a 16x16 sprite is one. Uniform tile
    size is the whole trick - without it the game is a bag of differently
    shaped arrays rather than a single stack.
    """
    if w % TILE or h % TILE:
        raise ValueError("%dx%d is not a whole number of %d-pixel tiles"
                         % (w, h, TILE))
    tiles = []
    for ty in range(h // TILE):
        for tx in range(w // TILE):
            tiles.append([row[tx * TILE:(tx + 1) * TILE]
                          for row in rows[ty * TILE:(ty + 1) * TILE]])
    return tiles


def build():
    """Stack the game into `(sprites, palette, manifest)`, all in memory."""
    sheets = gather()
    palette = build_palette([(n, w, h, r) for n, w, h, r, _s, _a, _p in sheets])
    index = {colour: i for i, colour in enumerate(palette)}
    if len(palette) > 256:
        raise ValueError("%d colours; a uint8 index tops out at 256. The "
                         "tensor would need uint16 and every reader with it."
                         % len(palette))

    sprites = bytearray()
    manifest = {}
    slot = 0
    for name, w, h, rows, source, art_path, png_path in sheets:
        tiles = tile(rows, w, h)
        first = slot
        on_ramp = True
        for one in tiles:
            for row in one:
                for texel in row:
                    i = index[texel]
                    if i >= len(RAMP_KEYS):
                        on_ramp = False
                    sprites.append(i)
            slot += 1
        manifest[name] = {
            "source": source,
            "art": os.path.relpath(art_path, root()).replace(os.sep, "/") if art_path else None,
            "png": os.path.relpath(png_path, root()).replace(os.sep, "/"),
            "size": [w, h],
            "tiles": [w // TILE, h // TILE],
            "slots": [first, slot],
            "on_ramp": on_ramp,
        }

    meta = {
        "tile": str(TILE),
        "sprites": str(slot),
        "palette": str(len(palette)),
        "ramp_keys": "".join(RAMP_KEYS),
        "ramp_slots": "0..%d" % (len(RAMP_KEYS) - 1),
        "textures": json.dumps(manifest, sort_keys=True),
        "note": ("palette[0:11] is the docs/PALETTE.md ramp in table order; "
                 "palette[11:] is every other colour the shipped art contains. "
                 "Generated by tools/atlas.py --build; do not hand-edit."),
    }
    return slot, bytes(sprites), palette, manifest, meta


def artifacts(slot, sprites, palette, manifest, meta):
    """The four files, as `path -> bytes`."""
    flat = bytearray()
    for colour in palette:
        flat.extend(bytes(colour))

    doc = {
        "note": meta["note"],
        "tile": TILE,
        "sprites": slot,
        "shape": [slot, TILE, TILE],
        "palette": {
            "size": len(palette),
            "ramp": {k: i for i, k in enumerate(RAMP_KEYS)},
            "colours": ["#%02X%02X%02X%02X" % c for c in palette],
        },
        "textures": manifest,
    }
    return {
        OUT_SAFE: safetensors(
            {"sprites": ([slot, TILE, TILE], sprites),
             "palette": ([len(palette), 4], flat)}, meta),
        OUT_NPY: npy([slot, TILE, TILE], sprites),
        OUT_PALETTE: npy([len(palette), 4], flat),
        OUT_JSON: (json.dumps(doc, indent=2, sort_keys=True) + "\n").encode("ascii"),
    }


# ---- the report ------------------------------------------------------------

def nearest_ramp(texel):
    """The closest opaque ramp key and its distance - a suggestion, never a rule.

    Nothing in this tool snaps a colour. The number exists so a person can see
    whether a texture is two units off the ramp (a rounding) or eighty (a
    colour the ramp has no word for), because those are different problems and
    only one of them is fixable by quantising.
    """
    r, g, b, a = texel
    if a != 255:
        return None, None
    best, far = None, None
    for key in RAMP_KEYS:
        cr, cg, cb, ca = pixel.RAMP[key]
        if ca != 255:
            continue
        d = (r - cr) ** 2 + (g - cg) ** 2 + (b - cb) ** 2
        if far is None or d < far:
            best, far = key, d
    return best, int(round(far ** 0.5))


def report():
    """Measure every texture against the ramp. Writes UNRAMPED.md, nothing else."""
    ramp = set(ramp_table())
    lines = [
        "# UNRAMPED.md - every texture measured against the one ramp",
        "",
        "Generated by `python tools/atlas.py --report`. Do not hand-edit; the",
        "next run overwrites it.",
        "",
        "docs/PALETTE.md rules that `art/` is the source of record and that no",
        "colour outside the eleven-key ramp is drawn. That is true of the",
        "textures `pixel.py` wrote and false of the ones that predate it. This",
        "file is the size of the gap, stated rather than implied, so that",
        "closing it is a number going down.",
        "",
        "A distance is Euclidean in RGB. Read it as the difference between a",
        "rounding and a colour the ramp has no word for: single digits are the",
        "same colour written twice; seventy is a hue that is simply absent.",
        "",
    ]
    clean = 0
    for path in textures():
        name = resource_name(path)
        w, h, rows = pixels_from_png(path)
        seen = {}
        for row in rows:
            for texel in row:
                seen[texel] = seen.get(texel, 0) + 1
        off = {t: n for t, n in seen.items() if t not in ramp}
        grid = art_for(name)
        head = "## `%s`  %dx%d, %d colours" % (name, w, h, len(seen))
        if not off:
            clean += 1
            lines += [head, "",
                      "On the ramp. Source: %s."
                      % ("`%s`" % os.path.relpath(grid, root()).replace(os.sep, "/")
                         if grid else "the PNG - **no art grid yet**"), ""]
            print("%-40s on-ramp" % name)
            continue
        worst = max((nearest_ramp(t)[1] or 0) for t in off)
        lines += [head, "",
                  "**Off the ramp.** %d of %d colours, %d of %d pixels. "
                  "Furthest: %d." % (len(off), len(seen), sum(off.values()),
                                     w * h, worst),
                  "",
                  "| colour | pixels | nearest | distance |",
                  "|---|---|---|---|"]
        for texel, count in sorted(off.items(), key=lambda kv: -kv[1]):
            key, far = nearest_ramp(texel)
            if key is None:
                lines.append("| `#%02X%02X%02X` alpha %d | %d | - | not opaque |"
                             % (texel[0], texel[1], texel[2], texel[3], count))
            else:
                lines.append("| `#%02X%02X%02X` | %d | `%s` | %d |"
                             % (texel[0], texel[1], texel[2], count, key, far))
        lines.append("")
        print("%-40s OFF-RAMP  %2d colours, %4d px, furthest %d"
              % (name, len(off), sum(off.values()), worst))

    total = len(textures())
    # Index 5 is the blank line after the "generated, do not edit" note, so the
    # headline lands in its own paragraph rather than glued to that note.
    lines[5:5] = ["**%d of %d textures are on the ramp today.**" % (clean, total), ""]
    with open(os.path.join(root(), OUT_UNRAMPED), "w", encoding="ascii",
              newline="\n") as handle:
        handle.write("\n".join(lines))
    print()
    print("%d/%d on the ramp -> %s" % (clean, total, OUT_UNRAMPED))
    return 0


# ---- the modes -------------------------------------------------------------

def loud(manifest):
    """Say what is not yet on the ramp, every run, pass or fail.

    Same reasoning as EGADS in pixel.py: a waiver nobody reads is the same as
    no rule at all. A tensor that is partly derived from PNGs because ten
    textures have no text source must say so out loud, or it will be read as a
    clean bill of health.
    """
    no_art = sorted(n for n, m in manifest.items() if m["source"] == "png")
    off = sorted(n for n, m in manifest.items() if not m["on_ramp"])
    if no_art:
        sys.stderr.write("no art grid (%d of %d) - the PNG is the source for:\n"
                         % (len(no_art), len(manifest)))
        for name in no_art:
            sys.stderr.write("  %s\n" % name)
    if off:
        sys.stderr.write("off the ramp (%d of %d) - see %s:\n"
                         % (len(off), len(manifest), OUT_UNRAMPED))
        for name in off:
            sys.stderr.write("  %s\n" % name)


def do_build():
    slot, sprites, palette, manifest, meta = build()
    loud(manifest)
    for path, data in artifacts(slot, sprites, palette, manifest, meta).items():
        full = os.path.join(root(), path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as handle:
            handle.write(data)
        print("%s  %d bytes" % (path, len(data)))
    print("sprites (%d, %d, %d) uint8, palette (%d, 4) - %d on the ramp"
          % (slot, TILE, TILE, len(palette),
             sum(1 for m in manifest.values() if m["on_ramp"])))
    return 0


def repaint(manifest, palette, sprites, slot):
    """Every PNG, rebuilt from the tensor alone, as `path -> bytes`."""
    out = {}
    for name, entry in sorted(manifest.items()):
        w, h = entry["size"]
        cols = entry["tiles"][0]
        first, _last = entry["slots"]
        rows = [[None] * w for _ in range(h)]
        for n, s in enumerate(range(first, _last)):
            tx, ty = (n % cols) * TILE, (n // cols) * TILE
            base = s * TILE * TILE
            for y in range(TILE):
                for x in range(TILE):
                    rows[ty + y][tx + x] = palette[sprites[base + y * TILE + x]]
        # png() takes ramp KEYS, not colours, so hand it a grid of keys where
        # the ramp covers the texture and fall back to a direct write where it
        # does not. Keeping one PNG writer is worth the small detour.
        out[entry["png"]] = png_from_pixels(rows, w, h)
    return out


def png_from_pixels(rows, w, h):
    """Write RGBA pixels straight out, reusing pixel.py's chunk arithmetic.

    pixel.py's `png` maps ramp keys to colours; here the colours are already
    known and ten of the fourteen textures are not on the ramp, so there are no
    keys to map. Same container, same filter-0 scanlines, one fewer lookup.
    """
    import zlib
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for texel in row:
            raw.extend(bytes(texel))

    def chunk(tag, body):
        out = struct.pack(">I", len(body)) + tag + body
        return out + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def do_paint(dry):
    slot, sprites, palette, manifest, _meta = build()
    loud(manifest)
    drift = 0
    for rel, data in sorted(repaint(manifest, palette, sprites, slot).items()):
        path = os.path.join(root(), rel)
        with open(path, "rb") as handle:
            before = handle.read()
        same_pixels = pixel.unpng(before) == pixel.unpng(data)
        if not same_pixels:
            sys.stderr.write("PIXELS DIFFER: %s\n" % rel)
            drift += 1
            continue
        note = "identical" if before == data else "%+d bytes" % (len(data) - len(before))
        if dry:
            print("%-58s would repaint, pixels identical, %s" % (rel, note))
        else:
            with open(path, "wb") as handle:
                handle.write(data)
            print("%-58s repainted, %s" % (rel, note))
    if drift:
        sys.stderr.write("%d texture(s) do not survive the tensor - "
                         "nothing was written for those\n" % drift)
    return 1 if drift else 0


def do_sheet():
    """The tensor laid out as one 128x128 picture - what the atlas map shows.

    The same 8x8 arrangement of tiles that AtlasMap paints, in true colour
    rather than quantised to Minecraft's map palette, so it is a faithful
    preview of the layout and an honest one about the colour: the map cannot
    show these exact hues and this can.

    128 is the cap in docs/TRAJECTORY.traj and it is also the map edge, which is
    why forty-four tiles fit with room to grow and nothing has to move when the
    forty-fifth arrives.
    """
    slot, sprites, palette, manifest, _meta = build()
    across = SHEET // TILE
    if slot > across * across:
        sys.stderr.write("%d tiles will not fit on a %dx%d sheet (%d squares); "
                         "the extras are not drawn\n"
                         % (slot, SHEET, SHEET, across * across))

    clear = (0, 0, 0, 0)
    rows = [[clear] * SHEET for _ in range(SHEET)]
    for n in range(min(slot, across * across)):
        ox, oy = (n % across) * TILE, (n // across) * TILE
        base = n * TILE * TILE
        for y in range(TILE):
            for x in range(TILE):
                rows[oy + y][ox + x] = palette[sprites[base + y * TILE + x]]

    data = png_from_pixels(rows, SHEET, SHEET)
    with open(os.path.join(root(), OUT_SHEET), "wb") as handle:
        handle.write(data)
    print("%s  %dx%d, %d of %d squares used, %d bytes"
          % (OUT_SHEET, SHEET, SHEET, slot, across * across, len(data)))
    for name, entry in sorted(manifest.items(), key=lambda kv: kv[1]["slots"][0]):
        first, last = entry["slots"]
        where = "square %d" % first if last - first == 1 else "squares %d-%d" % (first, last - 1)
        print("   %-14s %s" % (where, name))
    return 0


def do_check():
    """The gate. Nothing here writes; everything here compares."""
    slot, sprites, palette, manifest, meta = build()
    loud(manifest)
    faults = []

    # 1. art -> tensor. A grid edited without a rebuild.
    for path, want in artifacts(slot, sprites, palette, manifest, meta).items():
        full = os.path.join(root(), path)
        if not os.path.exists(full):
            faults.append("%s is missing - run --build" % path)
            continue
        with open(full, "rb") as handle:
            got = handle.read()
        if got != want:
            faults.append("%s is stale - run --build" % path)

    # 2. tensor -> PNG. Compare DECODED PIXELS, not bytes: zlib output varies
    #    between Python versions and pixel equality is the property that matters.
    for rel, data in sorted(repaint(manifest, palette, sprites, slot).items()):
        full = os.path.join(root(), rel)
        with open(full, "rb") as handle:
            before = handle.read()
        if pixel.unpng(before) != pixel.unpng(data):
            faults.append("%s does not match the tensor" % rel)

    # 3. coverage. Every shipped PNG is in the tensor. Nothing gets to be neither.
    listed = {m["png"] for m in manifest.values()}
    for path in textures():
        rel = os.path.relpath(path, root()).replace(os.sep, "/")
        if rel not in listed:
            faults.append("%s ships but is not in the tensor" % rel)

    # 4. round-trip. Import each texture that HAS a grid and assert the pixels
    #    a human wrote survive the codec. Proven against known-good art.
    for name, entry in sorted(manifest.items()):
        if entry["source"] != "art":
            continue
        grid = os.path.join(root(), entry["art"])
        w, h, rows = pixels_from_grid(grid)
        again = pixel.unpng(png_from_pixels(rows, w, h))
        if again != (w, h, rows):
            faults.append("%s does not survive png/unpng" % entry["art"])

    if faults:
        sys.stderr.write("the atlas gate says no:\n")
        for fault in faults:
            sys.stderr.write("  - %s\n" % fault)
        return 1
    print("atlas: %d sprites, %d colours, %d/%d textures on the ramp, "
          "%d/%d with an art grid"
          % (slot, len(palette),
             sum(1 for m in manifest.values() if m["on_ramp"]), len(manifest),
             sum(1 for m in manifest.values() if m["source"] == "art"), len(manifest)))
    return 0


def main(argv):
    parser = argparse.ArgumentParser(
        description="Stack every texture in the game into one tensor.")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--report", action="store_true",
                      help="measure every texture against the ramp; writes UNRAMPED.md")
    mode.add_argument("--build", action="store_true",
                      help="write the tensor and its manifest")
    mode.add_argument("--paint", action="store_true",
                      help="write every PNG back out from the tensor")
    mode.add_argument("--sheet", action="store_true",
                      help="write art/atlas-sheet.png, the whole game as one 128x128 picture")
    mode.add_argument("--check", action="store_true",
                      help="the gate - compare everything, write nothing")
    parser.add_argument("--dry-run", action="store_true",
                        help="with --paint: prove the repaint, write nothing")
    args = parser.parse_args(argv)

    if args.report:
        return report()
    if args.build:
        return do_build()
    if args.paint:
        return do_paint(args.dry_run)
    if args.sheet:
        return do_sheet()
    return do_check()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
