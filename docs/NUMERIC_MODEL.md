# Numeric model — what is measurable, and at what derivative order

A pass over every quantity in the design, sorted by **derivative order with
respect to the dive**: how many times you differentiate before the number
appears. This is the frame the Möbius note asks for — endpoint deviation is
only meaningful once you know which quantities are states and which are rates.

Nothing here is decided. It is a survey, laid out so the parts that are
genuinely derivative can be marked as such.

Sources: `HEXAHEDRON_DESIGN.md`, `HEXAHEDRON_ARCHITECTURE.md`,
`HEXAHEDRON_IDEAS.md`, `GLOSSARY.md`, and `LavaLampBlock.java` /
`vibemetrics.go` in the working Hexehedron/ARCADIA trees.

---

## Order 0 — states. Values you can read off at an instant.

| Quantity | Value | Source | Note |
|---|---|---|---|
| Era count | 5 | design | Named eras. Decoupled from live cost. |
| Live eras per player | ≈1 (2 briefly, in transit) | design | The tether guarantees it. The "2" is the thermocline. |
| Structure tiers | 10 / 25 / 50 / 75 / 100 % | design | One asset, five rarities. |
| Full base room count | 20 | design | Also the DireWolf20 homage. Buried in IDs/thresholds. |
| Chunk budget | capped, counted | design | The cap is a state; its *consumption* is order 1. |
| Dunbar ceiling | ~150 | design | Synchronous visible actors. |
| Engine ceiling | ~300 (Paper) … ~1000 (Folia) | design | Per server process. |
| World border | 60M × 60M | design | ~3.6×10¹⁵ blocks². |
| Lamp geometry | 9 × 7, 4 blobs | `LavaLampBlock` | Width, height, blob count. |
| Shard width | 12 hex of sha256 | `vibemetrics.go` | Advisory only — "metrics, not gospel." |

**These are not derivatives.** They are dials. Changing one changes a
threshold, never a slope.

---

## Order 1 — rates. Values that only exist as *change per unit of something*.

This is the interesting tier, and I think the one the question is pointing at.

| Quantity | Differentiated w.r.t. | Source | Form |
|---|---|---|---|
| **RF charge drain** | time, and depth | ideas (dive model) | `dE/dt`, scaled by era index |
| **Hull stress accrual** | depth and actions | ideas (dive model) | `dσ/dt` — explicitly "a no-clock pressure" |
| **Erosion gradient** | era index | design | The whole era stack *is* one derivative: `d(intactness)/d(era)` |
| **Tier distribution shift** | era index | design | `d(tier mix)/d(era)` — intact early, ruined late |
| **Propagation likelihood** | tier | design | Stated as *inverse* to tier: `dP/d(tier) < 0` |
| **Wax drift** | world time | `LavaLampBlock` | Reading window 40 ticks; 24 develop steps per read |
| **Chunk budget consumption** | bubble radius | design | `d(chunks)/d(r)` — grows as `r²`, which is why the cap bites fast |

The design's own language already says derivative in three places without
using the word: *"a single erosion gradient"*, *"propagation inverse to tier"*,
*"accumulates with depth"*.

---

## Order 2 — curvature. Rates whose rates matter.

Only one candidate is load-bearing, and it is the open question already pinned
in the idea log.

| Quantity | Why it's second order |
|---|---|
| **Hull stress vs. depth** | If accrual per tick is itself a function of depth, then `d²σ/dt·d(depth)` is what makes "how deep do I dare" a real gamble rather than a linear tax. A constant accrual makes every dive feel the same. |
| **Chunk budget as pressure hull** | `HEXAHEDRON_IDEAS.md` pins exactly this: should the perf cap and the risk dial be the *same number*? That is a question about whether two curves share a second derivative. |

---

## The Möbius endpoint deviation

> *"when traveling along a mobius loop … the chromatic aberration of end points
> in terms of spread over space time such that it is deviation of
> (theta)/|SEEK|SAGE|DEVOPS|ALL|USERS|"*

Read as a numeric spec, this is well-formed and lands in order 1:

- **Möbius, not a circle.** Traverse the era stack far enough and you return to
  the front — but *flipped*. Earliest→present is not a segment with two loose
  ends; it is a loop with one surface. Era 5 → era 1 is a legal edge.
- **Chromatic aberration at the endpoints.** The seam does not register
  exactly. Two eras that should coincide are offset, and the offset is not
  uniform across the seam — it *spreads*, the way a lens splits wavelengths.
  This is the error term of the era stack, and it is the thing worth rendering.
- **Deviation of θ.** The offset is angular, measured along the loop, not a
  linear distance. `θ` accumulates over traversal, so the observable is
  `dθ/d(arc)` — order 1.
- **Divided by a scope selector.** `|SEEK|SAGE|DEVOPS|ALL|USERS|` normalises
  the deviation by *audience*. The same seam reads differently depending on who
  is asking. Narrower scope → larger visible deviation; `ALL` → the aberration
  averages toward zero.

Taken together: **aberration = dθ/d(arc) ÷ |scope|.** A rate over a
normalisation, which is why it belongs beside hull stress and not beside the
tier table.

The one part I would flag rather than assume: `SEEK | SAGE | DEVOPS | ALL |
USERS` is written as an alternation, so it reads as a *selector* (pick one) —
but dividing by it implies a *cardinality* (how many). Those give different
functions. Which one is intended changes the formula.

---

## Not numeric, and worth keeping that way

- **Vibemetrics.** `vibemetrics.go` states the law first and load-bearing:
  *metrics, not gospel; no code path may branch on a vibe.* A shard is a
  12-hex identity, not a magnitude — differences between shards carry no
  distance. Treating "vibemetric block generation" as a *measure* rather than a
  *label* would break the law the file is written to enforce.
- **Glyphs.** Authored vocabulary, procedural arrangement. The arrangement is
  countable; the vocabulary is not a scale.
- **The bat.** Never explained, by design.

---

## Open, for you to mark

1. Which of the order-1 rates are **linear** in their variable and which are
   not? The design says "inverse to tier" (non-linear) for propagation, but
   leaves the erosion gradient's shape unstated.
2. Is hull stress genuinely second order, or is the second-order framing more
   than the mechanic needs?
3. `|SCOPE|` — selector or cardinality?
4. Does the Möbius flip apply to the *era* index only, or also to the
   position-keyed store (i.e. does a flipped traversal address the same
   `BlockPos`)? The store is dimension-agnostic, so a flip that changes
   addressing would be a genuine exception to the spine.
