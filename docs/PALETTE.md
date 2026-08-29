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

`[2026-08-24]`
