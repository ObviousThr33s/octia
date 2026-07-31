```
ACT TWO
MILESTONE 1
DESOP_[0.1.0.R.M.B.H]_door
SEEK KEG |ALL|
```

# The front door

A desktop window that opens onto the repo. It is the thing you double-click
first: the codex block, the two scripts, and a doorway with the sentinel
standing at the end of it.

It is not part of the mod. `tools/frontdoor/` compiles with whatever JDK is on
`PATH`, has no Fabric and no Loom on its classpath, and cannot reach the game.
It lives outside `src/` for the same reason the mod's own sources live inside
it — the mod builds on a provisioned JDK 21 against a pinned toolchain, and a
desktop shell has no business in that source set.

```powershell
.\tools\frontdoor.ps1            # build if stale, then open
.\tools\frontdoor.ps1 -Install   # write the icon and a desktop shortcut
```

---

## 1. 37 percent to median

The one number the drawing turns on, and the reason the door is legible.

Each side of the opening recedes toward the **median** — the centre of the
opening — by 37% of its own distance to that median. The left edge is half a
width from the median, so it moves in by 0.37 of a half width; the right edge
mirrors it; top and bottom do the same against the horizontal median. The far
opening is therefore the near opening scaled by `1 - 0.37 = 0.63` about its own
centre, and the vanishing point is the median itself: dead ahead.

```
near opening  ────────────────────────────
                \                        /
                 \      0.63 x near     /      each side gives up 0.37
                  \  ────────────────  /       of its distance to the median
```

**Why a fraction and not a depth.** At 0.63 both jambs stay visible and neither
is foreshortened to a line, so which side is which never has to be inferred.
That is the whole job of the number — you do not get lost in the passage.
Shallower and the door flattens into a painted rectangle. Deeper and one jamb
swallows the view.

Three things carry the same reading, so no single cue has to survive alone:

- **The faces are lit from beyond, not from the room.** Each of the four is a
  gradient along its own direction of recession, dark at the near end and
  accent-tinted at the far one. The lintel is darkest, the sill lightest, and
  the two jambs differ by a few percent — never a mirror of each other.
- **Two cross-sections are drawn across all four faces**, at a third and two
  thirds. With them the depth is countable rather than felt.
- **The light spills forward onto the floor**, widening as it comes. Out is
  where the light gets wider.

`Threshold` owns all of it. The four faces read off one pair of rectangles, so
nothing in the geometry can drift out of agreement with anything else in it.
`Threshold.atDepth(t)` runs the same line past both ends: `t` above 1 goes
further in, below 0 comes toward the viewer, which is how the floor spill is
shaped.

---

## 2. The design system is a stylesheet; Swing cannot read one

`Nocturne` is the one place the tokens cross over — every colour, ramp step,
space, radius and shadow, each named beside the CSS variable it came from. The
rule that makes it worth having is the design system's own: *never hard-code a
hex, a font name or a px value the tokens already carry.* Nothing else in the
program names a colour. A retune upstream is a retune of one file.

Two Nocturne signatures that are easy to lose in a port and are implemented
here rather than approximated:

- **Rules fade to transparent at both ends** over 48px, rather than stopping
  clean. Box outlines and short accent marks stay solid.
- **Elevation on a dark ground is an edge plus ambient darkness**, not a
  stacked shadow. `--shadow-sm` is literally a hairline.

Buttons are `JComponent`, not `JButton`, so the paint is ours end to end and no
look-and-feel can reach it. That costs the accessible context a bare
`JComponent` does not have, so `PortalButton` supplies one with the push-button
role — drawing your own button should not cost a screen reader the button.

---

## 3. The glyphs are the design's own path data

`Glyphs` holds the `d` strings copied verbatim out of the `<symbol>` defs in
`Obelisk Icon.dc.html`, and a small parser for the subset of SVG path syntax
they use. They are not transcribed into Java geometry calls, because then a
change upstream would be a re-transcription that somebody has to prove correct
by eye. This way it is a copy-paste. The parser throws on a command it does not
know rather than guessing — if the doc grows an arc, it should stop.

**The stroke is a ratio, and a ratio does not survive scale.** The doc's 1.5 on
a 24-unit box is right at icon size and a club at 300px. An icon keeps the
ratio; the sentinel standing in the doorway does not, and passes a finer
stroke. That is the only knob deciding which you get.

### Antennae and dishes

Variant 1c of the doc is the sentinel: the shared obelisk with four accent side
marks. The dishes are the one addition, made in the doc's own language — same
accent, apex landing exactly on the end of its mark so the pair reads as one
part. `Obelisk.DISHES` turns them off and leaves the doc's icon untouched.

They are **filled crescents, not stroked curves**, and that took three tries to
get right. A stroked cup cannot survive the icon's own line weight: at 128px
the stroke is thicker than the cup is deep, and the two rim tips and the apex
close up into a fork on the end of a stick. Filled, the dish is a silhouette,
and a silhouette holds at any weight because it has none. At icon sizes they
still compress toward marks — there are not enough pixels for a bowl at 32px,
and pretending otherwise produces mush. They read as dishes where they are
drawn large, which is in the doorway.

`Obelisk.bounds()` measures what the glyph actually occupies from the paths
themselves. The obelisk does not fill its box and the dishes push past where
the marks end, so anything sizing or placing it has to ask rather than assume.

---

## 4. The icon is verified, not eyeballed

`IconFile` assembles the `.ico` by hand, because Java has no writer for one.

Every entry is a 32-bit DIB, including 256. PNG-compressed entries are legal,
smaller, and read fine by Explorer — but **GDI+ cannot read them**, so a PNG
entry cannot be checked without shipping it and looking at a desktop. DIB
entries round-trip through `System.Drawing.Icon` at every size, which makes the
icon testable before it reaches anybody. A third of a megabyte is a fair price.

```powershell
Add-Type -AssemblyName System.Drawing
$i = New-Object System.Drawing.Icon('tools\frontdoor\build\obelisk.ico', 48, 48)
$i.ToBitmap().Save('proof.png', [System.Drawing.Imaging.ImageFormat]::Png)
```

Two renderings, not one scaled: above 48px the icon is the full sentinel; below
it the marks are finer than a pixel, so the small sizes fall back to the plain
obelisk at a stroke rounded to a whole pixel. An icon that is unreadable at
16px is not a smaller icon, it is a smudge.

---

## 5. Verify by rendering it

The window is undecorated, so the panel is the whole of it:

```powershell
java -jar tools\frontdoor\build\frontdoor.jar --shot shot.png 1180 760
```

writes exactly what appears on screen, without opening anything. Same argument
as `verify.ps1` — a check that needs a person watching a window is a demo.

---

## 6. Windows specifics that already bit

**Finding a JDK is not "look on `PATH`."** Oracle's installer puts a shim
directory on `PATH` — `C:\Program Files\Common Files\Oracle\Java\javapath` —
carrying `java`, `javaw` and `javac` but **not** `jar`. Trusting `PATH`
compiles clean and then dies on the packaging step. `frontdoor.ps1` collects
every plausible bin directory (JAVA_HOME, `PATH`, the registry, the usual
vendor roots) and accepts only one holding `javac` and `jar` together; failing
that it runs from loose classes rather than giving up.

**A running door holds its own jar open.** Rebuilding while the window is open
fails with a `FileSystemException` from `jar`. Close it first.

---

## 7. Still open

Answer these before building on them; do not decide them silently.

1. **Pointing the obelisk.** The next milestone. It lands in `DoorwayView` and
   `Threshold` and nowhere else — the scene is deliberately still until then.
2. **Whether the door should open the game at all.** Clicking the doorway runs
   `tools/play.ps1`. A front door that cannot be opened is odd; a front door
   that starts a Gradle build on a stray click is also odd. It is recoverable
   — the script gets its own window — but it is a decision, not an accident.
3. **The artifact flags.** `DESOP_[0.1.0.R.M.B.H]_door` reads `DESOP-RMBH` into
   the notation's `NAME_[version.FLAGS]_type` form. The flags are carried, not
   interpreted, exactly as `S.A.F.E` was before anyone said what it meant.
