# PALETTE.md - the one ramp everything is drawn from

Set down 2026-08-24, from the owner's art direction during the `[0_6_8]` playtest:
*"we need more pixel art... crappy opensource pixel art"*, *"thats how this vibes,
generative pixel art"*, *"keep it simple"* - and then the correction that decided the
method, when offered noise-driven and per-seed generators: *"that all sounds like it
would gen incohesive nonsense."*

So the art is not generated from noise. It is **coherent by constraint**: one small
fixed ramp, one shape discipline, and every sprite in the mod drawn inside both. Cheap
on purpose, consistent by arithmetic. A pack of crude sprites that all obey the same
eleven colours reads as one pack; eleven crude sprites that each invent their own
colours read as nonsense, which is the thing the owner named.

---

## The ramp

Eleven colours. Nothing new in this mod is drawn with a colour outside this table.

| key | role | hex | where it comes from |
|-----|------|-----|---------------------|
| `k` | andesite dark - outlines, shadow | `#3B3B3F` | the existing hull and panel textures |
| `m` | andesite mid - the body of most things | `#6E6E73` | same |
| `l` | andesite light - the lit face, the highlight | `#A9A9AE` | same |
| `g` | grime - cordage, leather, worn wood, dirt | `#4A4438` | the existing weathered dressing |
| `b` | bone - cloth, plinth stone, the beacon's cream | `#D9D2C0` | the existing plinth and beacon blocks |
| `i` | ink - squid, glyphs, writing, and nothing else | `#161C2B` | new, and reserved |
| `p` | road purple | `#6B3FA0` | the road cube |
| `y` | own gold | `#C8A02C` | the yours cube |
| `r` | sealed red | `#9E2B25` | the sealed cube |
| `v` | void black | `#0B0B10` | the void, the starfield ground |
| `.` | transparent | - | the sprite's own outside |

`i` is reserved. Ink means writing, and writing has a source in this world - the squid.
Nothing else is drawn in it, so a glyph is always legible as a glyph.

## The sky ramp

Set down 2026-08-28, from the owner's direction — *"creep across the BASSALT with me
ICE CRABS in the GRASS AND PALE_BLUE_GREY sky"* — and the rule that came with it:
**a sky colour is named in three words.** Always three. `PALE_BLUE_GREY`, never
`PALE_BLUE` and never `SKY`.

The eleven above are for sprites: 16x16, in the hand, seen close. The sky is the other
half of what a player looks at, and on a floored sky world over an ocean (see
[ISLANDS.md](ISLANDS.md) XI) it is most of the screen. It needed its own ramp. It did
not need a second method.

**These are not new colours, and they were not chosen.** They are the ramp put through
one operation: convert to HLS, **hold lightness exactly**, force hue to 210 degrees and
saturation to 0.15. Nothing else. `tools/pixel.py --sky` computes and prints them, so
the table below is output rather than art direction, and there is no way for the doc
and the code to drift into two different skies.

That is why the sky belongs to the world instead of sitting behind it: holding
lightness means a `PALE_BLUE_GREY` sky and an `l` andesite highlight are the *same
brightness*, so a lit hull against the sky reads as one material lit two ways rather
than two palettes meeting at an edge.

| name | derived from | hex | where it goes |
|------|--------------|-----|---------------|
| `COLD_BONE_WHITE` | `b` `#D9D2C0` | `#C5CCD4` | the horizon haze - bone with the warmth taken out |
| `PALE_BLUE_GREY` | `l` `#A9A9AE` | `#9FACB8` | the sky itself |
| `DEEP_BLUE_GREY` | `m` `#6E6E73` | `#607081` | the water surface, and cloud shadow |
| `DARK_BLUE_GREY` | `k` `#3B3B3F` | `#343D46` | water fog, and the underside of everything |
| `FAR_VOID_BLACK` | `v` `#0B0B10` | `#0B0B10` | **exempt, and deliberately.** The void is not sky and takes no blue. It was already this colour and already three words |

**Where they are set.** Not in a sprite - in biome JSON: `sky_color`, `fog_color`,
`water_color`, `water_fog_color`. `octia:sky` draws vanilla Overworld biomes through
`minecraft:multi_noise` today, so all four are still Minecraft's. Applying this ramp
means overriding those keys per biome, which is data, and therefore pack-overridable
the same way the terrain is.

**Untuned, and say so.** These five hexes are arithmetic, not observation. Nobody has
stood in a world and looked at them. The rule at the top of AGENTS.md applies at full
strength here, because a sky is the one thing in this project that **cannot be
verified by a gametest** - there is no assertion for "that reads as cold". Generate,
go and look, move the numbers.

## EGADS - the hatch, because a rule with no exception gets bypassed instead

Added 2026-08-28, on the owner's instruction: *"always allow for egads."*

Every rule below is a good rule, and one of them will eventually be wrong for one
sprite. A checker with no way to say **"this one is deliberate"** does not get obeyed
- it gets gone around. Someone runs `pixel.py`, loses an argument with it, and
hand-writes the PNG instead, which takes that sprite out of *every* check at once
rather than out of the one it disagreed with. The hatch costs a sentence and keeps
the sprite inside the system.

Put this line above the grid in an art file:

```
# EGADS the sealed cube needs its own red, and the cap says five
```

**What it waives:** the five-colour cap and the closed outline. The discretionary
rules - the ones that are opinions about taste.

**What it cannot touch:** 16x16, and a key that is not in the ramp. Those are not
opinions. A grid of the wrong size is not a sprite, and a key with no entry in `RAMP`
has no colour to write, so there is nothing to waive.

**It is never quiet.** `pixel.py` prints the reason and every rule it let through, to
stderr, on a pass as well as a failure, and `--check` marks the sprite `[EGADS]`. A
waiver nobody reads is the same as having no rule. A declaration that turns out to
waive nothing is reported too, because it is either a rule that got fixed and a note
left behind or a misunderstanding of what the hatch reaches - and both want deleting.

The reason goes in the file, next to the picture it excuses, rather than in a list
somewhere else. It has to survive being read by whoever opens that sprite in a year.

## The shape rules

These are the whole spec. They are short because the point is that they are followed.

1. **16x16.** Every item sprite, no exceptions.
2. **No anti-aliasing.** A pixel is one of the eleven keys. There are no in-between
   colours, so there is no way to drift off the ramp by blending.
3. **One-pixel outline in `k`,** closed all the way round. A closed outline is what
   survives being extruded into a hand-held model, and under this register the extruded
   sprite *is* the model - see `GREENFIELD-BLOCKING.md` defect F.
4. **At most five keys per sprite,** counting the outline and not counting transparent.
   The cap is what stops a sprite reaching for detail it cannot carry at this size.
5. **No gradients, no dithering.** Shade by placing `l` where the light lands and `k`
   where it does not, in flat regions.
6. **Readable silhouette first.** Squint at the transparent mask alone: if you cannot
   tell what it is, the colours will not save it.

## How a sprite is authored

Sprites are written as ASCII grids and compiled by `tools/pixel.py`, so the source of
every texture is a plain-text picture a person can read and edit in any editor. The
grid *is* the art; the PNG is derived, the same way `noise_settings/sky.json` is derived
from its generator command.

```
python tools\pixel.py art\item\red_cube.txt src\main\resources\assets\octia\textures\item\red_cube.png
```

An art file is 16 lines of 16 characters, each character a key from the table above.
`tools\pixel.py --check <file>` validates the grid without writing anything: it fails
on a wrong size, an unknown key, a sprite over the five-colour cap, or an outline that
is not closed. Run it before the PNG, and the rules above stop being advice.

The `art/` tree is the source of record. A texture is regenerated from its grid, never
hand-edited as a PNG - a PNG edited directly is a correction that the next regeneration
silently throws away.

## The whole atlas as one tensor

Set down 2026-09-01, on the owner's instruction: *"make the texture atlas one AI tensor
for the entire game."*

A sprite on this ramp is not a picture that happens to be small. It is a grid of symbols
from a closed alphabet, which is a **tensor of palette indices** wearing a PNG costume.
`tools/atlas.py` takes the costume off. Every texture the mod ships is cut into 16x16
tiles and stacked into one array:

```
assets/octia/atlas/atlas.safetensors   sprites (44,16,16) uint8, palette (103,4) uint8, + manifest
assets/octia/atlas/atlas.npy           the sprites alone, for numpy.load
assets/octia/atlas/palette.npy         the palette alone
assets/octia/atlas/atlas.json          the manifest again, in text a reviewer can read
art/atlas-sheet.png                    the whole game as one 128x128 picture, from --sheet
art/UNRAMPED.md                        the ramp measurement, from --report
```

The tensor lives under `assets/` rather than beside the grids because **it ships**: the
mod reads it at runtime to paint the atlas map, so it has to be inside the jar. `art/`
keeps what a person edits and what a person reads; `assets/` keeps what the game loads.

Forty-four tiles: twelve 16x16 textures at one tile each, plus `hev_suit.png` and
`icon.png`, which are 64x64 and therefore sixteen tiles apiece. **The uniform tile is the
whole trick.** Without it the game is a bag of differently shaped arrays; with it, it is
one stack, and `palette[sprites[n]]` is any texture in the mod as RGBA.

Nothing here generates art. This is the ruling at the top of this file held to: the
tensor is a *record* of what is drawn, not a machine for drawing it.

### Why the palette is 103 colours and not eleven

The first design stored ramp indices and nothing else, which would have been the prettier
answer. **Measurement killed it.** `tools/atlas.py --report` was pointed at the fourteen
shipped textures and found that **four are on the ramp - the four this tool wrote.**

The other ten predate `pixel.py`. They are anti-aliased, they spend seventeen to
twenty-five colours inside a 16x16, and the sentences above about where each ramp colour
"comes from" turn out to describe an *eyeballed* derivation rather than a sampled one:
`andesite_frame_panel.png` contains no pixel of `#3B3B3F` at all. Its nearest is
`#3A3C3E`, two units away. Worse, several colours in the shipped art have no ramp
representative at any distance - the cyan beacon glow at `#6BC5D3` is 77 from its nearest
key, the warm gold at `#E0BB73` is 80, and the HEV suit's green at `#7CFF4A` is 125.

So a ramp-only tensor would have covered four textures out of fourteen and called itself
"the entire game", which is the one thing it must not do. The palette instead starts with
the ramp and continues with whatever is actually on disk:

```
palette[0:11]    the eleven keys above, in this table's order - k m l g b i p y r v .
palette[11:]     every other colour the shipped art contains, sorted
```

That keeps three things at once. The whole game fits, losslessly, with **no pixel
changed**. Ramp indices are constants: `k` is 0 today and 0 forever, because a ramp key
holds its slot whether or not any texture uses it. And **the split at eleven is itself
the measurement** - `on_ramp` in the manifest says which textures are clean, so bringing
one onto the ramp later is a number that goes up rather than an argument that gets had.
`art/UNRAMPED.md` is that measurement written out, colour by colour, and it is
regenerated rather than maintained.

### The source of record, stated honestly

The section above says `art/` is the source of record. That is true of four textures and
false of ten, and the tensor does not paper over it. Each entry in the manifest carries
`source: art` or `source: png`, and the two are gated differently: a texture with a grid
must match what the grid compiles to, and a texture without one is read from its PNG
because there is nothing else to read. **The count of `art` sources is the progress meter
on this file's central claim.** It is 4/14. It was 4/14 before the tensor existed; the
only new thing is that it now says so on every run.

### The atlas map

Set down 2026-09-01, on the owner's instruction: *"show me the changed texture atlas as
a map texture on the world load. every character gets one texture atlas map and they cant
get rid of it."*

`com.serenity.octia.atlas.AtlasMap` paints the tensor onto a filled map and holds one in
every player's inventory. This is not a whimsical delivery mechanism, it is the shape of
the data: **a Minecraft map is a 128x128 grid of bytes where each byte names a colour,
and the tensor is an N-by-16-by-16 grid of bytes where each byte names a colour.** They
are the same object. 128 is eight tiles of sixteen, so forty-four tiles land in an 8x8
grid of sixty-four with twenty squares spare, and the sheet grows downward as the mod
does without anything having to move. `atlas.py --sheet` prints the layout and writes the
same picture as a PNG.

**Locked, and that is the load-bearing part.** Vanilla repaints a map from the terrain
whenever a player holds one in its own dimension. `MapItemSavedData.locked()` is the
vanilla mechanism for freezing that - it is what a cartography table does - so the art
survives being carried around with no mixin on the update path. The failure this avoids
is the quiet kind: an unlocked atlas works in a screenshot and is gone an hour later.
`MapItem.inventoryTick` was read out of the 1.21.1 jar to confirm the other half - that
`tickCarriedBy` runs *before* the `locked` branch, so a locked map still sends its image
to the client and only skips the repaint. Standing order: when a version-dependent detail
matters, open the artifact and look.

**Held, not given.** Handing the map out once at join would be undone by the first Q
press, so the inventory is checked every tick and the map put back when missing - the
same pattern and the same reasoning as [keep-inventory](../src/main/java/com/serenity/octia/life/KeepInventory.java).
"Cannot be removed" is a property that has to be maintained, not one that can be set. A
copy thrown on the floor is swept, but only in the tick where one actually went missing
and only near the player who dropped it; a standing scan of every item entity would cost
more than the mechanic is worth.

**One map, not one per player.** Every copy carries the same `MapId`, so the save stores
a single `map_N.dat`. That id is remembered in its own `SavedData`, because
`getFreeMapId` hands out a new one on every call and the obvious version of this mints a
fresh map on every world load and leaves the old ones behind forever.

**The one place this pipeline snaps a colour.** Minecraft offers 62 base colours at four
brightnesses and nothing else, so the 103-entry palette is quantised to the nearest map
colour. That is allowed here and refused everywhere else in this file, and the difference
is worth stating: the ramp is what the art *is*, and must not be approximated; this map
is a *picture of* the art. The snap happens once over 103 palette entries rather than
once over 11,264 texels - which is the first practical dividend of storing indices
instead of colours, and a preview of the argument in the next section.

### Shaders, and what an index tensor is actually for

Held in mind 2026-09-01, on the owner's note: *"keep in mind super secret shaders."*
There are none in this mod today - no `assets/octia/shaders/`, no `PostChain`, no core
shader override - so this is a constraint being kept, not a thing being integrated with.

**The scope of the claim above, stated before a shader falsifies it.** This tensor holds
every texture *asset*. It does not hold what is on screen. Minecraft's post-processing
chain - the machinery behind the old Super Secret Settings button, still present in
1.21.1 and still what spectator vision runs through - changes the frame without touching
a file. The moment a `.fsh` lands here, "the whole game is in this tensor" is true of the
art and false of the image, and it should be said that way rather than discovered later.
This file has already been wrong once in exactly this shape: `art/` was the source of
record for four textures while the sentence claimed fourteen.

**And the reason to store indices rather than colours.** An index tensor plus a small
palette is not merely a tidy way to write the art down - it is the exact layout a palette
shader wants. An index texture and a 103x1 lookup table, and the whole game recolours on
a uniform update rather than on a reimport. The sky derivation at the top of this file -
hue 210, saturation 0.15, **lightness held** - is already written as a function over the
ramp rather than a second table of hexes, which means it is one step from being that
function *in a fragment shader*, applied to the palette instead of baked into it. Ship
state (adrift, called, moored), void corruption, damage flash, and time of day are all
palette animations from there, not new textures.

Two things follow, and both are free to hold now. Keep the palette small and ordered -
`palette[0:11]` being the ramp is what makes a swap meaningful, and every texture brought
onto the ramp shrinks the part a shader cannot reason about. And note that vanilla's
stitcher throws indices away: it stitches RGBA into `blocks.png`, so a palette shader
would need this mod to own its own atlas upload. That is runtime reach this tensor
deliberately does not have yet. The data being ready for it costs nothing; the Java to
use it is a separate decision, and not this one.

### The gate

`tools/verify.ps1` and `.github/workflows/verify.yml` run `atlas.py --check` first, ahead
of the build, because it costs milliseconds. It fails when the tensor is stale against
`art/`, when any shipped PNG disagrees with the tensor, when a texture ships without
being in the tensor at all, or when a grid a human wrote does not survive the codec.

The PNG comparison is on **decoded pixels, not bytes** - zlib output varies between
Python versions and pixel equality is the property that actually matters. A missing
`python` fails the gate rather than skipping it, on the same reasoning
[BENCH.md](BENCH.md) gives for the crew bench: a gate that quietly does nothing is worse
than no gate, because a green run then reads as proof.

`[2026-08-24]` `[2026-09-01]`
