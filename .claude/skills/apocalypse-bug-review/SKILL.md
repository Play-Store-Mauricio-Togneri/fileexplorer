---
name: apocalypse-bug-review
description: Run an extremely strict, exhaustive whole-codebase defect hunt for bugs, correctness errors, inconsistencies, security flaws, and edge-case failures.
disable-model-invocation: true
---

# Apocalypse Bug Review

Use this skill for an unusually strict, exhaustive hunt for **defects**: bugs, correctness errors,
inconsistencies, edge-case failures, and security flaws that will misbehave at runtime.

Above all, this skill should push the reviewer to be **adversarial**. You are not here to admire the
code or to tidy it — you are here to break it. Assume the code is guilty until proven innocent. Read
it looking for the input, the sequence, or the state that makes it fail, and do not stop at the
first plausible-looking path.

This skill has a single concern: whether the code is *correct* — whether it actually works, not
whether it is built well.

It assumes nothing works correctly until verified and asks "where are the bugs?" **Behavior is the
target, not structure.** Do not spend effort here on abstractions, naming, file size, or style —
that is out of scope. Hunt defects.

## Scope

This hunt covers the **entire codebase**, not a diff or a single change. Audit all existing code —
every module, file, and layer — regardless of when it was last touched.

This skill is **generic**: it is language- and framework-agnostic and applies to any project.
Nothing in it assumes a particular language, runtime, or stack.

Audit **first-party source only**. Skip generated, vendored, build, and dependency output (for
example `node_modules`, `vendor/`, `build/`, `dist/`, `target/`, `.git`, and minified or generated
files). Bugs there are not yours to fix.

## Core Prompt

Start from this baseline:

> Perform an exhaustive defect audit of the entire codebase.
> Hunt for bugs, correctness errors, inconsistencies, edge-case failures, security flaws, and latent
> defects that will misbehave at runtime.
> Assume the code is guilty until proven innocent: read it adversarially and actively try to make it
> fail.
> Cast a wide net first, then verify every candidate by tracing the real code path and identifying a
> concrete trigger.
> Report only what survives verification, each tagged with confidence and severity.
> Be extremely thorough and rigorous. Measure twice, cut once.

## Defect Taxonomy

Three cumulative tiers. Hunt all of them.

### Tier A — Runtime correctness

- **Logic errors:** off-by-one, inverted conditions, wrong operator or comparison, incorrect boolean
  logic, swapped arguments, wrong variable used, wrong unit.
- **Null / undefined / optional mishandling:** unchecked dereferences, force-unwraps, missing
  absence checks, assuming a value is present when it may not be.
- **Edge cases:** empty / single-element / boundary inputs, integer overflow and underflow, division
  by zero, limits and off-by-one at the extremes, very large inputs, unusual encodings.
- **Error handling:** swallowed exceptions, over-broad catches, ignored return or error codes, error
  paths that leave invalid or partial state, retries without bound or backoff, failures that surface
  as success.
- **Concurrency:** race conditions, data races on shared mutable state, deadlocks and livelocks,
  missing synchronization, work running on the wrong thread or executor, unhandled cancellation or
  timeouts, async/await misuse, non-atomic check-then-act sequences.
- **Resource management:** leaked handles / sockets / streams / memory, resources not released on
  error paths, missing cleanup or dispose, listeners and observers never removed, unbounded growth.
- **State & lifecycle:** stale or inconsistent state, use-after-dispose or use-after-free,
  initialization-order bugs, unstated ordering assumptions, reentrancy, cache-invalidation errors.

### Tier B — Inconsistencies, data integrity & security

- **Contract mismatches:** caller and callee disagree on units, range, nullability, or ownership;
  two code paths that must stay in sync but do not; an invariant asserted in one place and violated
  in another.
- **Data validation & coercion:** missing or insufficient validation of external input, unsafe type
  coercion, lossy conversions, parsing that does not handle malformed input, trusting
  caller-supplied data.
- **Resource / config parity:** configuration keys, enum or switch cases, lookup tables, or parallel
  resource sets that are incomplete or out of sync — a missing case, a non-exhaustive dispatch, a
  key defined in one place but not its counterpart.
- **Security defects:** injection (SQL / command / template / path traversal), missing
  authentication or authorization checks, exposed secrets or credentials, insecure storage or
  transport, unsafe deserialization, weak randomness used for security, time-of-check/time-of-use
  gaps, missing bounds checks.

### Tier C — Broader anomalies

- **Dead / unreachable code and impossible branches** — often a symptom of a real logic bug, not
  just clutter.
- **API / library misuse:** violating a documented precondition, skipping required cleanup, using a
  deprecated call whose semantics changed, misusing a framework lifecycle.
- **Debt markers (de-emphasized):** `TODO` / `FIXME` / `HACK` matter only when they flag a real
  latent defect. Do not turn this into a debt inventory.

## Methodology — Three-Phase Hunt

This is guidance, not rigid orchestration. If parallel execution is available, fan out; if running
single-threaded, perform the same phases in sequence. Scale the number of parallel passes to the
size of the codebase.

If subagent or workflow orchestration is available, prefer it — this hunt is a:
discover → verify → synthesize pipeline and maps directly onto it:

- **Discover** fans out into one agent per module or area (and optionally per defect tier); each
  agent over-reports candidates for its slice.
- **Verify** spawns a *fresh* agent per surviving candidate whose job is to **refute** it (Phase 2).
  This is how the independent-verification rule below is actually achieved: the refuter must not be
  the agent that found the candidate.
- **Synthesize** is a single final step — only it deduplicates and writes the report, so parallel
  discovery agents never write `BUG_FINDINGS.md` concurrently.

A single pass is not "exhaustive." Repeat Discover until it runs dry: keep launching discovery
rounds — each excluding the candidates already found — until one or two consecutive rounds surface
nothing new.

### Phase 0 — Orient

Before hunting, read the project's own documentation (`CLAUDE.md`, `README`, architecture docs, key
interface and contract definitions) to learn the domain invariants that define "correct." Many bugs
are violations of a project-specific contract you can only recognize once you know the rules.

### Phase 1 — Discover (cast a wide net)

- Fan out across the codebase, splitting the work by module or area (and optionally by defect tier).
- Read adversarially and list **every** candidate defect. Do not self-censor at this stage —
  over-report; verification will filter.
- For each candidate, record: location, the suspected defect, its tier/category, and why it looks
  wrong.

### Phase 2 — Verify (try to refute each candidate)

- Treat every candidate as guilty — then try to prove it innocent.
- Trace the real call paths and data flow that reach it. Identify a **concrete trigger**: the input,
  sequence, or state that actually makes it fail.
- Where it is cheap and safe, **confirm the trigger dynamically** instead of only by reading: run
  the relevant existing test, write a throwaway scratch test or snippet that exercises the path, or
  evaluate the expression in a REPL. A reproduction outranks static tracing — it is the strongest
  evidence for High confidence. This does not violate "report only" (see Output): you may execute
  code to confirm a defect, you just do not fix it.
- Check it is not already handled or prevented elsewhere — a guard upstream, an invariant that makes
  the bad input impossible, a caller that never passes the dangerous value.
- Drop candidates you cannot substantiate. Assign **Confidence** by how firmly you established a
  real trigger.
- Prefer **independent** verification: where possible, the pass that verifies a candidate should not
  be the one that found it.

### Phase 3 — Synthesize

- Deduplicate overlapping findings; merge a single root cause reported from several angles.
- Rank, assign final Confidence and Severity, and write the report.

## Verification Rules

- A finding must name a **concrete trigger or path to failure**, not just "this looks risky."
- Read the surrounding code and the callers before flagging. Minimize false positives.
- Distinguish what you **proved** from what you **suspect**. Never present a guess as confirmed.
- If you cannot find a trigger but the smell is strong, you may still report it at **Low**
  confidence
  — but state explicitly what you could not establish.

## Confidence & Severity

Two **independent** axes; report both for every finding.

**Confidence — is the defect real?**

- **High:** concrete trigger identified and traced; confirmed not handled elsewhere.
- **Medium:** plausible trigger, but some assumption about inputs or state is unverified.
- **Low:** suspicious pattern, no confirmed trigger; flagged for human judgment.

**Severity — how bad if it fires?**

- **Critical:** crash, data loss or corruption, security breach, or incorrect results in a core
  path.
- **High:** significant malfunction or wrong behavior in a common path.
- **Medium:** malfunction in an edge case or a recoverable path.
- **Low:** minor or cosmetic incorrectness.

These axes are independent: a High-confidence bug can be Low-severity, and a Critical-severity bug
can be Low-confidence.

## Primary Hunt Questions

For every meaningful module, file, or flow, ask:

- What input or sequence makes this fail? Empty, null, boundary, huge, concurrent, out-of-order,
  malformed?
- What does this assume about its inputs, state, or environment — and what happens when that
  assumption is false?
- Are all error and failure paths handled, or can a failure surface as success or leave invalid
  state?
- Can two things run here concurrently? Which interleaving breaks it?
- Is every resource acquired here released on **every** path, including errors?
- Do caller and callee actually agree on units, ranges, nullability, and ownership?
- Is every case or branch handled, or is the dispatch non-exhaustive?
- Can untrusted input reach a dangerous sink without validation?
- Is this branch reachable at all — and if not, why is it here?

## What to Flag Aggressively

Escalate findings when you see:

- Unchecked nulls or optionals on a path that can carry absence.
- Off-by-one and boundary errors at loop, array, or range limits.
- Swallowed or over-broad error handling that hides failures.
- Check-then-act and other non-atomic sequences on shared state.
- Resources opened without a guaranteed release on every error path.
- External input reaching a sink without validation.
- Non-exhaustive dispatch, a missing case, or a default branch that silently hides new variants.
- Two code paths or parallel resource sets that must stay in sync but have drifted.
- Inverted conditions, swapped arguments, the wrong variable, or the wrong unit.
- Silent fallbacks that mask an invariant violation.

## What Not to Report

Keep the signal high. Do not report:

- **Structure, style, naming, file size, or abstraction quality** — that is the
  `thermo-nuclear-code-quality-review` skill's job, not this one.
- **Defects you have shown cannot trigger** — if a guard, invariant, or caller makes the dangerous
  path unreachable, the candidate is refuted; drop it. (Dead or unreachable code is itself a Tier C
  finding about the *unreachability* — a separate, narrower claim from a bug inside it.)
- **Debt inventories** — bare `TODO` / `FIXME` / `HACK` markers, unless one flags a real latent
  defect (see Tier C).
- **Speculation dressed up as a confirmed bug** — if you could not establish a trigger and the smell
  is weak, drop it. A *strong* smell that is plausibly reachable may still go in at Low confidence
  per Verification Rules (state what you could not establish); an implausible one does not.

## Output

- **Report only.** Do not *fix* anything — suggest fixes in text and apply none. Running existing
  tests or throwaway scratch code to confirm a trigger (Phase 2) is fine and encouraged; just leave
  the codebase as you found it and delete any scratch files.
- Write a Markdown report to `BUG_FINDINGS.md` at the repository root. This file is a transient work
  product — prefer to gitignore it rather than commit it.
- Group findings under `## High confidence`, `## Medium confidence`, and `## Low confidence`. Within
  each group, order by Severity (Critical → Low).
- Each finding contains:
    - A heading with a stable id and short title, e.g. `### [A-concurrency-1] Race on shared cache
      map`.
    - **Location:** `path/to/file:line`.
    - **Severity:** Critical | High | Medium | Low.
    - **Confidence:** High | Medium | Low.
    - **Defect:** what is wrong.
    - **Trigger:** the concrete input, sequence, or state that makes it fail (from verification).
    - **Suggested fix:** the change described in prose — no diff applied.
- End with a brief **inline summary** in the chat response (not only in the file): counts by
  confidence and severity, the files affected, and the top few findings. For example: "9 findings —
  4 High / 3 Medium / 2 Low; 2 Critical. Top: race in X, path traversal in Y. Full report in
  `BUG_FINDINGS.md`."
- If, after a genuinely exhaustive hunt, nothing survives verification, say so plainly and state
  what you covered — but hold to the Approval Bar below before declaring the code clean.

## Tone

Be direct, serious, and adversarial about correctness. You are trying to break the code, not admire
it. Do not soften a real defect into a mild "you might consider." Do not pad the report with hunches
dressed up as confirmed bugs, either — every finding earns its place through verification. If the
codebase is genuinely solid, say so, but only after you have actually tried to break it.

## Approval Bar

Do not declare the codebase bug-free merely because it looks reasonable or the tests pass.

The bar for a clean bill of health is:

- you cast a genuinely wide net across the **entire** codebase, not a sampling;
- you adversarially tried to trigger failure in every meaningful flow;
- every candidate was either substantiated with a concrete trigger or consciously refuted — none was
  silently skipped;
- the high-risk areas — concurrency, error paths, external-input handling, resource lifecycles,
  boundary conditions, and security sinks — were each specifically hunted.

If you have not met that bar, you are not done hunting — keep going. A short report is only credible
after a long search.