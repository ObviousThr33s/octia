```
ACT ONE
MILESTONE 0
OCTIA_[0.2.0.A.L.P.H.A]_spec
SEEK KEG |ALL|
```

# Prior art - what the neighbours do, and what Octia took

**Written 2026-08-28.** Set down the day Better than Adventure! was read properly
rather than glanced at, so the next hand does not have to read it again to find out
whether any of it applies here.

**This file is not a claim about Octia's era.** Nothing in this repo targets, imitates
or wants Minecraft Beta 1.7.3. It is named `PRIOR-ART` and not `BETA` deliberately: a
file with "beta" on it, in a repo with no beta-era orientation, invites the exact
misreading it caused once already on the day this was written - a careful reader
concluding from the filename that Octia is a b1.7.3 mod. It is a Fabric mod for
Minecraft 1.21.1 and this page changes nothing about that.

---

## I. What Better than Adventure! is

An unofficial fork of Minecraft Beta 1.7.3 by Mak and jonkadelic, first released
**2021-07-20**, still shipping in 2026. Its premise is a single sentence:
*"Minecraft without the Adventure Update"* - the game as it might have continued if
the 2011 update that added hunger, sprinting and the End had never landed.

The GitHub orgs are `Better-than-Adventure` and `Turnip-Labs`.

## II. What is open, and what is not

Worth being exact about, because "it's all open source" is half true.

| Public | What it is |
|---|---|
| `Turnip-Labs/bta-halplibe` | the modding helper library. **CC0-1.0** |
| `Turnip-Labs/bta-example-mod` | the mod template - "Template for making Babric mods for BTA!" |
| `Better-than-Adventure/legacy-lwjgl3` | runs pre-1.13 Minecraft on LWJGL 3 |
| `Better-than-Adventure/bta-brigadier` | Brigadier, backported |
| `Better-than-Adventure/bta-release-api` | a REST API for querying releases, in Rust |

The **game fork itself is in no public repository.** It is a decompiled Minecraft
fork and it ships as a binary through their own launcher. So the libraries and the
whole build path are open; the game is not, and cannot be.

## III. The stack is the one this repo already runs

BTA mods are **Babric** mods: Fabric Loader backported to b1.7.3, driven by **Loom**,
with **mixins**, a `fabric.mod.json`, and `processResources` expanding template
variables into it. That is the same four pieces as `build.gradle.kts` here. Their
template asks for **JDK 21** and compiles to a `JAVA_8` mixin compatibility level;
BTA 8.0 itself raised its runtime floor from Java 8 to Java 17.

**This is why there is no port.** Octia's toolchain knowledge transfers to BTA almost
one-for-one, which means a port would be possible and would teach nothing, at the cost
of the entire game underneath. Decided 2026-08-28: **Octia stays on 1.21.1.**

## IV. The versioning is the thesis

BTA did not pick a version number. It continued Mojang's.

| Version | Released |
|---|---|
| `Minecraft Beta 1.7.4_01` | 2021-07-20 |
| `1.7.5`, `1.7.6` | 2021-2023 |
| `1.7.7.0` | 2023-08-01 - the last to carry the `1.7.` prefix |
| `7.1`, `7.1_01` | 2024-04-11, 2024-04-25 |
| `7.2`, `7.2_01` | 2024-07-26, 2024-08-04 |
| `7.3`, `7.3_01`, `7.3_02`, `7.3_03` | 2025-01-26 through 2025-05-06 |
| `8.0`, `8.0.1` | 2026-07-20 (the fifth anniversary), 2026-07-26 |

The first release named itself as the beta that never shipped. `_NN` is Mojang's own
beta-era hotfix notation, kept on purpose. The premise is legible in the version
string before you read a word of description - and when the fiction had run its
course, at `7.1`, they dropped the prefix rather than pretending.

## V. The cadence is slow, and honest about it

Majors land 3 to 18 months apart. Hotfixes follow days later. **8.0 took over
eighteen months**, and the Nether overhaul in it was originally scheduled for
`v1.7.7` at **Halloween 2021** - five years late, announced without apology:

> Just like we've done with the Overworld, this update lays a foundation we intend to
> build on for many years to come. [...] please don't assume we're now "done with the
> Nether".

Every release gets a written announcement post on betterthanadventure.net. Prose, by
a person, saying what changed and why it matters - not a generated commit list.

## VI. The thesis is a refusal

"Without the Adventure Update" says what the game will never become. Everything
downstream - seasons, labyrinths, steel as the tier past diamond, the reworked armour,
the decorative blocks - reads as an answer to *"what would 2011 have done instead?"*
That single rejection is what makes a hundred new blocks feel like one game rather
than a pile of mods.

**Octia already has that shape of rule**, and it is worth naming here because it is
scattered:

- *"very mesolithic, not neolithic"* - [TRAJECTORY.traj](TRAJECTORY.traj) XII. No
  temples, no monoliths, no organised-labour construction, no written grammar, no gold.
- **128 px, nothing wider** - TRAJECTORY.traj VII, "a nerve number, not a performance
  one".
- **Ruins are never inhabited** - [MINUTES.md](MINUTES.md) 4, the chair's instruction,
  "not negotiable". Strangers are met on the road.
- **No terraforming** - every ruin refuses a bad site rather than levelling it.

Nothing was added to that list from BTA. It did not need adding to; it needed
noticing.

---

## VII. What was taken

Four things, all landed 2026-08-28.

1. **The Maven outage guard.** `bta-example-mod`'s `settings.gradle.kts` probes each
   candidate host with a two-second HEAD and takes the first that answers, walking
   `maven.fabricmc.net` to `maven2` to `maven3`. Ported into
   [`settings.gradle.kts`](../settings.gradle.kts) for plugin resolution, with the
   mirrors simply named in [`build.gradle.kts`](../build.gradle.kts) for dependency
   resolution, where Gradle already walks them in order. TRAJECTORY.traj XV is the
   incident that made this worth doing - work went unverified because that one host
   could not be reached and there was no second address to try. Checked the same day:
   `fabric-loader-0.19.3.jar` is 1976502 bytes and `fabric-loom-1.11.8.jar` is 1159795
   bytes on all three, so they are mirrors and not three names for one machine.

2. **A changelog written by a person.** [`CHANGELOG.md`](../CHANGELOG.md), one section
   per released version, keyed by the exact `mod_version`. `tools/release-card.ps1`
   looks its own version up in it and **refuses to build a card when it finds
   nothing**, on the argument the script already made about a missing version number.
   `release.yml` still appends the commit list underneath.

3. **A release that says which act it belongs to.** `mod_act`, `mod_milestone` and
   `mod_flags` joined `gradle.properties`, so the card heads with the four-line KEG
   block and the GitHub release is titled `Octia <version> - ACT ONE, MILESTONE 0`
   instead of a bare tag. `ReleaseNotationTest` asserts those three parse through
   `com.serenity.octia.codex`, because the two renderers - one in PowerShell, one in
   sh - cannot import the codex and so cannot be stopped from writing something it
   would reject.

4. **This page.**

## VIII. What was left

- **The port.** Section III.
- **The version scheme.** BTA's `7.3_02` is not SemVer, and Fabric's loader orders
  Octia's versions - `release.yml` refuses a tag that disagrees with `mod_version`,
  and the hyphen in `0.2.0-alpha.1` is what makes the release a prerelease. The jar's
  number stays SemVer. The **act** is the part that carries the story, which is the
  same split BTA lived with under different names: a number for the loader, a name for
  the identity.
- **A launcher, a downloads page, and a release API.** BTA has all three. Octia has
  GitHub releases and one playtester at a time. Revisit when that stops being true.
- **`legacy-lwjgl3`, `bta-brigadier`, HalpLibe.** All solve problems 1.21.1 does not
  have.

`[2026-08-28]`
