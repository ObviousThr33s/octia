```
ACT TWO
MILESTONE 4
OCTIA_[0.1.0.B.I.N.D]_brief
SEEK KEG |ALL|
```

# Bindles

**Set down 2026-08-22.** A cloth tied to a stick: four stacks, no screen, and
whatever is inside it was chosen by whoever tied it.

---

## I. Why an item at all, and why this one

The ruins are empty on purpose — `Habitation` places the things people left and
never the people — and the strangers hold a six-block band they will not cross.
Between those two rules the mod has very little way of saying anything directly,
so nearly everything it says it says through objects.

A bindle is the smallest object that says something. An empty chest is furniture
and a full one is a reward, but a bag with three things in it is a decision
somebody made: bread because the road is long, a torch because it gets dark, one
piece of andesite because they had been at a dig. Read what is inside and you
know something about who tied it, which is more than any ruin currently manages.

It is also the first item in this mod that is not a block item, which is why
`OctiaItems` exists beside `OctiaBlocks` rather than inside it. The funnel there
makes exactly one plain `BlockItem` per block, and its own note says the first
item that does not fit is the one that needs a second path. This is that item.

## II. What it is, in the code

| | |
|---|---|
| the item | `item/BindleItem.java` — the stacks, the tooltip, the two click paths |
| the sums | `item/Bindle.java` — slots, intake, the fullness bar; no Minecraft types |
| registration | `OctiaItems.BINDLE`, and `OctiaItems.forTheRoad` for a packed one |
| what pins it | `BindleTest` (JUnit, the sums) and `BindleGameTest` (in-world, the stacks) |

**It stores nothing of its own.** The contents live in vanilla's
`DataComponents.CONTAINER` — the same component a picked-up shulker box uses —
so a bindle survives a hopper, a chest, a death and the save file without a line
of NBT written here, and `/give` with a container component simply works.

**No screen, deliberately.** A menu would need a container, a menu type, a screen
on the client and a sync path between them, and at the end of it a bindle would
be a small shulker box. Instead it is worked the way vanilla's bundle is:
right-click a stack onto it to put that stack in, right-click it onto an empty
slot to take the last one back out. Last in, first out, because a bag with a
stick through it does not have an order.

**Four slots.** Nine would be a backpack and one would be a pocket. Four is
enough for the road and few enough that choosing what goes in is a decision.

**The bar counts slots, not items.** Counting items would draw a bindle holding
one stack of cobble as nearly full and the same bindle holding four eggs as
nearly empty, when it is the second one that cannot take anything else.

**Nothing nests.** A bindle refuses bindles, and refuses anything already
carrying contents of its own — the check is on the component, not on the item,
so a shulker box is caught by the same line. Nesting containers is how one item
ends up holding a save file's worth of items.

## III. Where they come from

- **Crafted.** A stick, a length of string, three leather. `data/octia/recipe/bindle.json`.
- **Found.** Weight 3 in the ruin store, 2 in the older one. A spare bag in
  somebody's barrel, empty — see the note below on why it is empty.
- **Left.** One departure in three, a wayfarer puts theirs down where they stood
  and it stays there. `Wayfarer.leaveBindle`.

The dropped one keeps an unlimited lifetime, and that is not a detail. A
wayfarer leaves once nobody has had eyes on them for twenty seconds, so a bindle
on the normal five-minute despawn would rot on an empty road every single time:
the one player who might come back for it is by definition not there when it
lands. It is also the only thing a wayfarer ever gives you, and it is given by
being put down rather than handed over — a stranger who will not close past six
blocks cannot pass you anything, and one that traded would be a wandering trader
with a better prompt.

**The found ones are empty, and that is the one thing here left half-done.**
A bindle in a wreck holding a dead person's belongings is the better version of
this feature, and the loot function that would do it — `minecraft:set_contents`
against the container component — was not written, because a malformed loot
function does not crash: the table resolves to `LootTable.EMPTY`, every ruin in
the world goes quiet, and the only trace is one line in a log. That is precisely
the failure `LootGameTest` exists to catch, and it cannot be checked from a
machine that cannot reach `maven.fabricmc.net` to build the game. Write it, then
run `tools/verify.ps1`, and only then commit it.

## IV. Still open

1. **Packed loot.** The above.
2. **A wayfarer's bindle should say who tied it.** `WayfarerLedger` already
   remembers names and places; a bindle that arrived with a custom name — *Fen's
   bindle* — would make the second meeting land harder than the first.
3. **Nothing on the client draws the contents.** The tooltip lists them as text.
   Vanilla's bundle draws its contents as a grid of sprites, and doing the same
   is a client-side screen overlay, not a menu — the same category of work as
   the F6 map, and the same file to put it in.
4. **A bindle is not a ruin's voice yet.** Entry 1 makes it one.
