```
ACT TWO
MILESTONE 2
OCTIA_[0.2.0.B.E.N.C.H]_spec
SEEK BENCH |DEV|
```

# The crew bench, and what a gate is allowed to need

`CrewBenchGameTest` is the only test in this repo that wants something the
machine might not have: a local model server answering on `127.0.0.1`. This is
the record of how that was settled, because it is the kind of decision that gets
re-litigated every time somebody sees a red gate.

---

## The two contracts

There is **no skip**. The network side is *declared* and then checked against
reality, and any disagreement is a failure. That is the whole design and it is
the right one — a test that quietly does nothing when the thing it tests is
absent is the hole this file was written to close.

| declared | what must be true |
|---|---|
| `octia.crew.network=required` | a bench answers, it lists at least one chat cleric, and a summoned cleric comes back with words |
| `octia.crew.network=absent` | the bench reports itself unreachable, its error names **every** candidate it tried, and the crew still works through the tender |

A bench that answers under `absent` is *also* red. An offline run that is
quietly online proves nothing about the tender, which is the only thing `absent`
exists to check.

Any other value is refused outright. A typo must not read as "off".

---

## The tension, stated plainly

The class defaults to `required`, and its javadoc accepts the consequence: *"A
GitHub runner has no bench, so under the default this file goes red there…
That is the intended behaviour and not an oversight."*

That is a defensible default **for the class**. It is the wrong default **for a
gate**, and the reason is already written down two files away — `OCTIA.md`
standing orders:

1. *The local gate and the CI gate are one gate.* If they diverge, the local one
   is wrong, because CI is the one that cannot be skipped.
2. *No display, no Mojang account, no secrets.* The gates run without any of the
   three and must keep doing so.

A model server on a loopback port is the same class of thing as a display or an
account: something the gate cannot guarantee and must not require. Both
standing orders cannot hold while the gate's effective default is `required`.

---

## How it was resolved

**The gates declare `absent`. The class keeps its default.**

- `build.gradle.kts` passes `-Doctia.crew.network=` from `octiaBench`, defaulting
  to `absent`, on the `gametest` run configuration.
- `tools/verify.ps1 -Bench` passes `-PoctiaBench=required` instead.

So a clean checkout is green, and it is green having *proved the offline
contract* rather than having skipped anything. The class's own default is
untouched, which matters: run `runGametest` by hand at a desk with LM Studio
open and you still get the online contract, which is the case the default was
written for.

**Why not flip the class default.** Because the default is a statement about
what the test is *for*, and the gate is a statement about what this machine can
be assumed to have. They are different questions and they now have different
answers, each stated where it belongs.

**Why not `@GameTest(required = false)`.** It only neutralises the *server's*
exit code. `tools/verify.ps1` counts every `<failure>` node in the report and
exits 1 regardless, while `verify.yml` checks the exit code — so that route
makes the two gates disagree, which breaks standing order 1 to fix standing
order 2.

**Why not leave the class unregistered.** It was, for a while: the
`fabric.mod.json` entrypoint line sat in a stash, so `runGametest` never ran
these two tests at all and the gate was green by omission. A test that runs
nowhere is not a test, and a green that comes from a stashed line is the exact
shape of the problem this file exists to end.

---

## Running it both ways

```powershell
.\tools\verify.ps1           # asserts the offline tender contract
.\tools\verify.ps1 -Bench    # asserts the online contract; start an endpoint first
```

The default endpoint list is three, in `CrewConfig`: `:1234` (LM Studio),
`:11434` (Ollama), `:8080` (llama.cpp's `llama-server`). It is configurable per
run directory through `config/octia-crew.json` — for gametests that is
`build/gametest/config/`, which `gradlew clean` deletes and which is regenerated
with the defaults. Note that `"endpoints": []` does **not** switch the bench
off; an empty or all-blank list restores the three defaults.

---

## One repair, unrelated to the policy

Both tests read the same declaration, but only one of them checked the result.
`theBenchIsExactlyAsReachableAsDeclared` guarded `reach == null` and produced a
considered sentence; `aClericAnswersInWordsOrTheTenderCoversForIt` handed
`bench.reach()` straight to `chatCleric`, which dereferenced it. On one offline
machine, from one cause, seconds apart, you got a diagnostic from one test and
`Cannot invoke "ClericBench$Reach.models()" because "reach" is null` from the
other.

The fault was not in `ClericBench`. `reach()` answering null is its documented
contract — that class never throws on purpose, because its callers are tick
loops — so **every** caller owes it a null check. The sentence now lives once,
in `noBenchAnswered`, and both tests throw it.
