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
