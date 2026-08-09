```
ACT TWO
MILESTONE 2
OCTIA_[0.1.0.R.O.A.D]_roadmap
SEEK KEG |ALL|
```

# Roadmap

Standing notes on what is wrong, what is missing, and what is wanted next.
Written down because a note kept in the head is a note that gets re-derived.

Each entry says what was observed, what it actually is where that is known, and
what would close it. Entries move out of here when they land, not when they are
started.

---

## I. The map reads as one repeated dot

**Observed.** The player icon is the teal dot, and it is the same size as the
centre point of the obelisk reticle. Everything on the box looks like everything
else.

**What it actually is.** Confirmed in the code, and worse than it looks.
`OctiaDebugOverlay.plot` computes `half = Math.max(1, size / 2)` and fills
`x-half` to `x+half+1`. For the mooring's `size = 2` that is 3px across; for the
beacon's `size = 3` it is *also* 3px across, because integer division of 3 by 2
is 1. The player marker is a third hand-written 3x3 fill at the centre. So all
three marks are the same 3x3 square, the `size` argument has never had any
effect, and the player sits exactly on the crosshair intersection where it is
least distinguishable.

**Closing it.** Distinct silhouettes, not distinct sizes - colour and scale both
fail at 3px against a dark panel. A filled square for a mooring, a hollow ring
for the beacon, an open cross for the player so the reticle reads through it.
*(First pass landed; see the entry below for what it does not solve.)*

---

## II. The map needs a vibe, and an image cache

**Wanted.** A visual language, and a sprite cache behind it that makes sense,
rather than marks composed out of `graphics.fill` calls.

**Why it is a real item and not polish.** Every mark on the box today is
rectangles drawn one at a time in immediate mode. That caps the vocabulary at
what can be spelled in axis-aligned boxes - no diagonals, no outlines thinner
than a pixel, no rotation, no anti-aliasing - which is why distinguishing three
marks is already hard at the sizes involved. It also means every new marker type
is new drawing code rather than a new entry in an atlas.

**Closing it.** One texture atlas under `assets/octia/textures/gui/`, blitted
through `GuiGraphics.blit`, with the marker set defined as data. The cache is the
point: the icons stop being code.

---

## III. Starfield launch-day map

**Wanted.** The map rendered the way Starfield drew planets at launch: an array
of height-mapped points describing topology, rather than a flat panel with dots
on it.

**What it would take.** A grid of samples over the visible range, each carrying
a height, drawn as points whose brightness or offset encodes elevation. The
client cannot read terrain outside its own render distance, so the samples have
to come from the server in the same snapshot the moorings already ride in - and
that changes `OctiaDebug.Snapshot` from a handful of longs into something with a
real payload size. Worth measuring before designing: a 64x64 sample grid is 4096
values per refresh, at two refreshes a second, and the whole point of the pull
model was that a debug overlay nobody has open costs nothing.

**Open question.** Is this the debug map growing up, or a second, separate
instrument? They want different things. The debug box wants to be legible and
free; a topology map wants to be beautiful and can afford to cost something.

---

## IV. The derelict does not read as a wreck

**Observed.** The generated structure makes no sense to look at.

**What it actually is.** Partly identified. The mast used
`if (random.nextInt(height + 1) < y) continue;` per course, which skips
individual blocks anywhere up the column - so a "snapped mast" could generate
with holes in the middle and panels floating above the gap. Debris panels were
scattered onto any free surface with no relation to the wreck. Neither reads as
a structure; both read as litter.

**Closing it.** *(First pass landed.)* The mast is now contiguous from the base
to a break height, which is what a snapped mast looks like. Debris is placed
low and near.

**Still open.** Whether the silhouette is right at all is a judgement that needs
eyes in the world, not a rule. Look at one and decide whether a nine-block
footprint with a four-block stub is the shape, or whether the derelict should be
longer, lower, and more obviously a *vessel* than a mast.

---

## V. Density

**Observed.** The amount per chunk seems high.

**What it is.** One number: `chance` in
`data/octia/worldgen/placed_feature/derelict.json`. It is a rarity filter, so
`chance: N` means a one-in-N roll per chunk. Started at 400.

**Note the sampling problem.** Density judged while flying through fresh chunks
is not density as experienced on foot, and the explored areas in
[WORLDS.md](WORLDS.md) give a real yardstick: vanilla trial chambers land about
one per 1400 chunks in these saves, ocean ruins about one per 700. A landmark
should sit nearer those than to mineshafts at one per 185.

**Closing it.** Raised to 900. Revisit against a walked world, not a flown one.

---

## VI. Travelers

**Observed.** There are a lot of travellers in this game mod.

**Unresolved, deliberately.** This note has two readings and they lead opposite
directions: either wandering traders are appearing too often and something in
the pack or the spawn rules wants looking at, or travellers are a thing the mod
should lean into - a Serenity-class ship called to a dig is a story about people
who arrive from somewhere. Not acting on it until which one is meant is settled.

---

## Kept for whoever hits it next

**Commit titles must be exactly 29 characters.** A `commit-msg` hook enforces it
and rejects anything else. It lives in `.git/hooks`, which is not tracked, so it
does not survive a clone and is not discoverable from the repo - you find out by
being rejected. The recent history is all 29 on purpose.

**`OctiaBeacon` has two unused imports**, `ShipCoreBlock` and `ShipStatus`.
Harmless, still noise.

**No world has ever rendered the gold beacon mark.** `recordBeaconAt` arrived in
`644b83e` and every existing save raised its beacon before that;
`claimBeacon()` fires once per save, so none of them can ever record it. The
next world created will be the first.
