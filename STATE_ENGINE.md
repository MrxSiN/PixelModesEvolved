# State engine

## Compiled rule

`TriggerEngine.Compiled` (private), built in `onRulesChanged`:

| Field | Type | Meaning |
|---|---|---|
| `triggers` | `Array<Trigger>` | in the order the person added them |
| `groupEnds` | `IntArray` | exclusive end of each `or` group; `A and B or C` → `[2, 3]` |
| `answers` | `BooleanArray` | each trigger's previous answer; read by `AreaTrigger` for exit hysteresis |
| `signals` | `Int` | `1 shl Signal.ordinal` for every trigger's signal |
| `decision` | `Int` | `NONE` (not decided since the rule appeared or changed), `OFF`, `ON` |
| `decidedAt` | `Long` | sequence number of the first decision; orders switch-offs of removed rules |
| `waitMet`, `timer` | `Boolean`, `AutoCloseable?` | the edge being waited out for `stabilityMillis` |

A rule is met when every trigger of any one group is met. Every trigger is evaluated every time
(no short-circuit), because each one's answer is remembered.

## Transition semantics (unchanged from the interpreted engine)

Per evaluated rule, with `met` the current answer:

1. `decision == met` → cancel any wait (the edge came back); done.
2. First decision (`NONE`) and not met and not an edit → record `OFF` silently (load never turns a Mode off).
3. Already waiting for this same `met` → done (a wait covers a whole flapping burst).
4. Otherwise cancel any wait; apply at once when it is an edit (`reset = true`), the first
   decision, or `stabilityMillis == 0`; else wait `stabilityMillis` and then apply.

On rule change: rules removed while `ON` switch their Mode off (in first-decision order); changed
or removed rules lose their decision and wait; a changed rule inherits the previous answers of
triggers it still has (one memory per Mode, matched by trigger equality); then every rule is
evaluated, and rules that differ from before count as edits when the engine had loaded before.

`stop()` cancels every wait and marks the engine dirty: the next state change evaluates every
rule, as the interpreted engine did, since a cancelled wait leaves a held answer that differs
from the decision.

## Why skipping unaffected rules is exact

Invariant after every evaluation and every timer: for each rule, the held answer equals either
its decision or the answer its pending wait is for. Triggers are pure functions of their own
signal's slice and their previous answer, and idempotent in that answer (for `AreaTrigger`: met
with `wasMet = false` implies met with the wider exit boundary; unmet with the wider boundary
implies unmet with the radius). So re-evaluating a rule whose slices did not change gives the same
answers and the same `met`, and steps 1 or 3 above make it a no-op. The only operation that
breaks the invariant, `stop()`, sets the dirty flag. `TriggerEngineParityTest` checks this
against the old engine on 2,000 random traces of 200 steps (rule edits with duplicate triggers
and duplicate Mode ids, all six signals including unknown, waits, timer advances, `stop()`), and
fails when either the dirty flag or the "evaluate everything on load" rule is removed.

## Dependency index

The index is the per-rule mask itself. Lookup is a scan with one `and` per rule; with 1–10 rules
that beats a `signal → rules` list (no indirection, no extra arrays to rebuild). Revisit when rule
counts reach the hundreds.

## Complexity

| Operation | Cost |
|---|---|
| Configuration load | O(JSON size) decode + O(Σ triggers) compile, + O(t²) per changed rule for answer inheritance (t ≤ a handful) |
| State event, no rule reads the slice | O(signals) = 6 reference/equality checks, no rule touched |
| State event | O(rules) mask tests + O(triggers of affected rules) evaluations |
| Rule evaluation | O(triggers), allocation-free |
| Provider activation | O(signals) on configuration change only |

## Setting claims (`action/SettingClaims.kt`)

System settings are not actions that run; they are claims that last while a Mode is on.
`ActionAutomation` tells the coordinator about genuine Mode transitions it already detects
(`activate`, `deactivate`), about claims saved, edited or removed while a Mode is on (`update`),
and, on its first look after load, about Modes already on (`adopt`, then `finishLoad`).

| Per setting (dense arrays, indexed by `SystemSetting.ordinal`) | Meaning |
|---|---|
| `owned`, `baseline` | the value read once, at the first claim |
| `written` | what this module last wrote (or found already there) |
| `target` | what it applies to: the default data subscription for mobile data, else 0 |
| `restoreOnEnd` | the restore flag of the claim that last won |
| `claims[ordinal]` | one slot per setting: parallel arrays of Mode id, value, restore flag, activation order |

Transitions, all on the automation thread:

1. **Mode on**: its claims enter the slots with a new activation order; every baseline is read
   before anything is written; then each setting the Mode holds is settled in `SystemSetting`
   order.
2. **Settle**: the winner is the highest activation order. If the setting no longer holds what was
   last written here, someone changed it: nothing is written, unless the winner is the Mode that
   just turned on or saved the claim. Otherwise the winner's value is written only when it
   differs from what is there.
3. **Mode off / claim removed**: settings are released in reverse order. With claims left, the next
   winner is settled. With none, the baseline is written back only when restore was asked, the
   target is the same, and the setting still holds what was last written here.
4. **Load**: ownership comes back from `Settings.Secure mode_evolved_setting_claims`. A Mode on at
   load keeps its claims without a write; settings no Mode on still claims are released as in 3.

Order matters because Android couples sound settings: a ring volume above zero sets the ringer to
Ring. Ring volume is held as `ringer shl 8 or last audible index`, read before any write and
restored before the ringer, so the ringer restored last is what stays.

Complexity: a transition touches only the Mode's own settings, O(settings × claims on each), with
1–4 claims per setting in practice. No event, timer or observer exists for claims.
