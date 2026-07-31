```
ACT ONE
MILESTONE 0
OCTIOID_[0.1.0.A.C.T.1]_spec
SEEK KEG |ALL|
```

# The KEG notation

Authored by KEG, in-world and in chat. This document codifies it. The JVM
transposition lives in `com.serenity.octioid.codex` so future devs get the
rules as types rather than as prose they have to remember.

Three forms, one system. Each does a different job; using one where another
belongs is the deviation that matters.

---

## 1. Structure — `ACT` / `MILESTONE` / directive / `SEEK`

Four lines. Caps headers. This is the sign form, taken literally from the
marker at `(-112, 67, -149)`:

```
ACT ONE
MILESTONE 0
START HERE
SEEK KEG
```

| Line | Meaning |
|---|---|
| `ACT <WORD>` | Which act. Spelled, not numeric: ONE, TWO, THREE, FOUR, FIVE. |
| `MILESTONE <N>` | Numeric, zero-based. MILESTONE 0 is the entry, not the first achievement. |
| directive | Free text, imperative. `START HERE`. |
| `SEEK <TARGET> [\|SCOPE\|]` | The call. Always the last line. |

Four lines because a Minecraft sign has four. The notation was designed on a
sign, and the constraint is the point — anything that does not fit in four
lines is more than one milestone.

**`SEEK` is the call verb throughout the project.** Not "find", not "get",
not "resolve". `SEEK KEG`. `SEEK ACT ONE`. `SEEK GEMINI`. It denotes a
directed request to a named target that may or may not answer.

`ACT` maps onto the design brief's five era layers, earliest to present:
ACT ONE is the Infinite City, ACT FIVE the Modern World. The acts and the eras
are the same ordering, which is why acts are spelled and capped at five.

---

## 2. Artifact naming — `NAME_[version.FLAGS]_type`

```
SERENITY_[0.0.0.S.A.F.E].project
OCTIOID_[0.1.0.A.C.T.1]_build
```

- `NAME` — caps.
- `[...]` — a version triple, then dot-separated status flags, all in one
  bracket. `[0.0.0.S.A.F.E]` is version 0.0.0 with flags S, A, F, E.
- `_type` — what kind of artifact: `project`, `build`, `spec`, `world`.

**On the two separators.** The dev world's level name is
`SERENITY_[0.0.0.S.A.F.E].project` but its folder on disk is
`SERENITY_[0_0_0_S_A_F_E]_project`. That is not a second notation — Minecraft
sanitises dots out of save-directory names. The dotted form is canonical and
authored; the underscore form is derived by the filesystem.
`ArtifactId.toFilesystemName()` performs exactly that derivation, so nobody
has to rediscover it.

Flags are single characters and carry no fixed meaning yet. They are read as a
word when they spell one: `S.A.F.E` is deliberate.

---

## 3. Scope — `|A|B|C|`

```
|SEEK|SAGE|HELIOS|ALL|USERS|
```

Pipe-delimited, leading and trailing pipes included. Names an audience.

It also appears as a divisor in the Mobius endpoint note:

```
aberration = d(theta)/d(arc) / |SCOPE|
```

**One thing is still undecided, and the code reflects that rather than hiding
it.** Written as an alternation, `|A|B|C|` reads as a *selector* — pick one.
Divided by, it implies a *cardinality* — how many. Those are different
functions and give different aberration curves. `Scope` exposes both
`size()` and `members()` and commits to neither until KEG says which.

---

## Composite

Headers on a walkthrough, a milestone, or a status report:

```
ACT ONE
MILESTONE 0
OCTIOID_[0.1.0.A.C.T.1]_spec
SEEK KEG |ALL|
```

`Notation.block(...)` renders precisely this, and its test asserts the sign at
`(-112, 67, -149)` round-trips through the parser unchanged. If the in-world
sign and the code ever disagree, the test fails — the world is the source of
truth, not this document.
