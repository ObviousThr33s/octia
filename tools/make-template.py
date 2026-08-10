"""
Write a starter structure template, so the template pipeline has something to
place before anyone has authored one.

This is scaffolding, and it is meant to be thrown away. The point of
TemplateRuinFeature is that ruins are built in the world by hand and saved with
a Structure Block; a shape emitted by a script is a placeholder for that, not a
substitute. It exists so the pipeline can be tested today, and so there is a
file to load into a Structure Block and start editing rather than a blank slate.

To replace it: build the ruin in world 0, save it with a Structure Block under
the name octia:waystation, and copy the .nbt out of
  run/saves/<world>/generated/octia/structures/waystation.nbt
into
  src/main/resources/data/octia/structure/waystation.nbt

The DATA marker matters. One structure block in DATA mode with metadata "core"
tells the feature where to stamp the hexahedron in code, after placement and
after erosion. Keep exactly one, or the ruin stops being a ship. See
TemplateRuinFeature for why that has to happen outside the template.

Usage:
    python tools/make-template.py
"""

import gzip
import os
import struct

# ---- NBT writing -----------------------------------------------------------
#
# Only the tags a structure template uses. A general writer would be more code
# and no more useful: the format is fixed and small.

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE = 0, 1, 2, 3, 4, 5, 6
BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = 7, 8, 9, 10, 11, 12


class Tag:
    """A value plus the tag id it must be written as."""

    def __init__(self, kind, value):
        self.kind = kind
        self.value = value


def i(value):
    return Tag(INT, value)


def s(value):
    return Tag(STRING, value)


def compound(mapping):
    return Tag(COMPOUND, mapping)


def lst(kind, values):
    return Tag(LIST, (kind, values))


def write_payload(out, tag):
    if tag.kind == INT:
        out += struct.pack(">i", tag.value)
    elif tag.kind == STRING:
        raw = tag.value.encode("utf-8")
        out += struct.pack(">H", len(raw)) + raw
    elif tag.kind == LIST:
        inner, values = tag.value
        out += struct.pack(">bi", inner if values else END, len(values))
        for v in values:
            write_payload(out, v)
    elif tag.kind == COMPOUND:
        for name, child in tag.value.items():
            raw = name.encode("utf-8")
            out += struct.pack(">b", child.kind)
            out += struct.pack(">H", len(raw)) + raw
            write_payload(out, child)
        out += struct.pack(">b", END)
    else:
        raise ValueError("unsupported tag %d" % tag.kind)


def write_nbt(path, root):
    out = bytearray()
    out += struct.pack(">b", COMPOUND)
    out += struct.pack(">H", 0)  # root name, always empty
    write_payload(out, root)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(gzip.compress(bytes(out)))


# ---- The template ----------------------------------------------------------

# Read out of this repo's own saves rather than looked up: octia_world.dat
# carries DataVersion 3955 for Minecraft 1.21.1. A wrong number here does not
# fail loudly; it sends the save through datafixers that were written for a
# different shape.
DATA_VERSION = 3955

PANEL = "octia:andesite_frame_panel"
SIZE = (7, 5, 7)


def build():
    """
    A waystation: a square andesite shell, open on one side, roof mostly gone.

    Deliberately plain. It is a frame for the lived-in pass to dress and for a
    person to rebuild by hand, not an attempt to be a good building.
    """
    blocks = {}

    w, h, d = SIZE
    for x in range(w):
        for z in range(d):
            edge = x in (0, w - 1) or z in (0, d - 1)

            # Floor across the whole footprint.
            blocks[(x, 0, z)] = (PANEL, {"light": "none"})

            if not edge:
                continue

            # Walls two courses high, with the south face left open as a way in.
            for y in (1, 2):
                if z == d - 1 and 2 <= x <= 4:
                    continue
                blocks[(x, y, z)] = (PANEL, {"light": "none"})

    # Two lit panels in the standing corners, which is the whole light budget.
    blocks[(0, 2, 0)] = (PANEL, {"light": "generic"})
    blocks[(w - 1, 2, 0)] = (PANEL, {"light": "generic"})

    # A partial roof. Left ragged on purpose - a complete one reads as intact,
    # and erosion works better on something already broken.
    for x in range(1, w - 1):
        for z in range(1, 3):
            blocks[(x, 3, z)] = (PANEL, {"light": "none"})

    # The marker. One, in the middle of the floor, one course up so the core
    # sits inside the room rather than in its foundation.
    blocks[(3, 1, 3)] = ("minecraft:structure_block", {"mode": "data"})

    return blocks


def encode(blocks):
    palette = []
    index = {}

    def state_id(name, props):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": s(name)}
            if props:
                entry["Properties"] = compound({k: s(v) for k, v in props.items()})
            palette.append(compound(entry))
        return index[key]

    entries = []
    for (x, y, z), (name, props) in sorted(blocks.items()):
        block = {
            "state": i(state_id(name, props)),
            "pos": lst(INT, [i(x), i(y), i(z)]),
        }
        if name == "minecraft:structure_block":
            # What TemplateRuinFeature reads back. mode must be DATA or the
            # feature will not treat it as a marker; metadata is the instruction.
            block["nbt"] = compound({
                "id": s("minecraft:structure_block"),
                "mode": s("DATA"),
                "metadata": s("core"),
                "name": s(""),
                "author": s(""),
            })
        entries.append(compound(block))

    return compound({
        "DataVersion": i(DATA_VERSION),
        "size": lst(INT, [i(v) for v in SIZE]),
        "palette": lst(COMPOUND, palette),
        "blocks": lst(COMPOUND, entries),
        "entities": lst(COMPOUND, []),
    })


def main():
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    # Singular "structure", verified against the 1.21.1 jar alongside
    # loot_table and recipe. See AGENTS.md rule V.
    path = os.path.join(repo, "src", "main", "resources", "data", "octia",
                        "structure", "waystation.nbt")

    blocks = build()
    write_nbt(path, encode(blocks))
    print("wrote %s  (%d blocks, %dx%dx%d)" % ((path, len(blocks)) + SIZE))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
