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

Audit the entire first-party codebase, not only a diff or recently changed files.

Include:

- Application, library, service, and command-line source.
- First-party scripts, migrations, build logic, CI workflows, infrastructure definitions, and
  configuration that can affect runtime behavior, releases, deployments, or data.
- Tests, documentation, schemas, and interface definitions as evidence of intended contracts.
  Report defects in them only when they can hide, permit, or cause an incorrect shipped behavior.

Exclude:

- Generated, vendored, minified, dependency, cache, and build output such as `node_modules`,
  `vendor/`, `build/`, `dist/`, `target/`, and `.git`.
- Pure maintainability concerns with no demonstrated behavioral consequence.

## Core Prompt

> Perform an exhaustive defect audit of the entire first-party codebase.
> Hunt for bugs, correctness errors, inconsistencies, edge-case failures, security flaws, and latent
> defects that can misbehave at runtime.
> Assume the code is guilty until proven innocent: read it adversarially and actively try to make it
> fail.
> Cast a wide net, then verify each candidate by tracing a reachable failure path and identifying a
> concrete or plausible trigger.
> Report only candidates that survive verification, each with evidence, confidence, and severity.
> Be extremely thorough and rigorous. Measure twice, cut once.

## Candidate Versus Finding

- A **candidate** is any suspicious pattern discovered during the hunt. Over-report candidates
  internally so they can be investigated.
- A **finding** is a candidate that survives verification and may appear in `BUG_FINDINGS.md`.
- Every finding must have a reachable failure path and a concrete or plausible trigger.
- Drop candidates whose dangerous path is prevented by an upstream guard, invariant, caller
  contract, or unreachable state.
- A Low-confidence finding may rely on an unverified assumption, but it must still explain the
  plausible trigger and what remains unverified. Never report a mere smell with no failure path.

## Defect Taxonomy

Hunt all three cumulative tiers.

### Tier A - Runtime Correctness

- **Logic errors:** off-by-one errors, inverted conditions, wrong operators or variables, swapped
  arguments, incorrect units, precedence mistakes, stale copy-paste logic, wrong loop bounds,
  integer division, negative modulo behavior, and skipped side effects.
- **Null and numeric hazards:** unchecked absence, force-unwrapping, absent values confused with
  empty or zero values, null collection elements, NaN propagation, overflow, underflow, division by
  zero, narrowing conversions, and precision loss.
- **Boundary and encoding cases:** empty, singleton, duplicate, sorted, zero, negative, maximum,
  malformed, or very large inputs; whitespace; Unicode and combining characters; time zones, DST,
  leap years, and date boundaries.
- **Error handling:** swallowed or over-broad errors, ignored status codes, failure reported as
  success, partial state without rollback, cleanup masking the original error, and unbounded
  retries.
- **Concurrency:** races, deadlocks, livelocks, missing synchronization, wrong executors, unhandled
  cancellation, non-atomic check-then-act sequences, unsafe lazy initialization, and failures hidden
  by fire-and-forget work.
- **Resource management:** leaked handles, streams, sockets, listeners, subscriptions, timers,
  temporary files, unbounded collections, and missing cleanup on failure paths.
- **State and lifecycle:** stale state, invalid transitions, use-after-dispose, initialization-order
  errors, reentrancy, cache invalidation, double initialization, and mutation during iteration.

### Tier B - Contracts, Data Integrity, and Security

- **Contract mismatches:** callers and callees disagree on units, ranges, indexing, nullability,
  ownership, serialization, return shape, or version.
- **Validation and coercion:** malformed external input, unsafe coercion, lossy conversion,
  locale-dependent parsing, missing range checks, unbounded allocations, and pathological regexes.
- **Resource and configuration parity:** missing enum or switch cases, incomplete lookup tables,
  drifted defaults, missing localization keys, and inconsistent feature-flag behavior.
- **Security defects:** injection, path traversal, missing authentication or authorization, exposed
  secrets, insecure storage or transport, unsafe deserialization, weak security randomness,
  time-of-check/time-of-use gaps, request forgery, open redirects, insecure direct object
  references, missing rate limits, predictable tokens, and sensitive logs.

### Tier C - Broader Behavioral Anomalies

- **Dead or unreachable behavior:** report only when it demonstrates a behavioral defect, such as a
  missing feature path, impossible intended state transition, ineffective guard, or silently skipped
  operation. Do not report harmless dead code by itself.
- **API or library misuse:** violated preconditions, skipped cleanup, wrong lifecycle or call order,
  ignored status values, thread-safety violations, or reliance on changed semantics.
- **Debt markers:** investigate `TODO`, `FIXME`, and `HACK` only when they identify a reachable
  latent defect. Do not turn this into a debt inventory.

## Four-Phase Workflow

Use parallel agents when available. Discovery agents may over-report candidates, but only the
synthesis step writes `BUG_FINDINGS.md`.

### Phase 0 - Orient and Inventory

1. Read project documentation, architecture material, schemas, and key contracts.
2. Build an inventory of all included first-party files and group them into meaningful modules and
   flows.
3. Identify high-risk surfaces: external input, authentication and authorization, persistence,
   migrations, concurrency, error handling, resource ownership, and deployment configuration.
4. Record exclusions and any files or flows that cannot be inspected.

### Phase 1 - Discover

1. Inspect every inventoried module or flow at least once.
2. Record each candidate's location, category, suspected defect, and suspected failure path.
3. Trace cross-module contracts and parallel resources that must remain consistent.
4. Run a dedicated high-risk pass covering security sinks, error paths, concurrency, resources,
   boundaries, and external input.

### Phase 2 - Verify and Refute

For every candidate:

1. Trace its real callers, data flow, guards, and state transitions.
2. Identify a concrete or plausible trigger and the resulting incorrect behavior.
3. Try to refute the candidate by finding a preventing invariant, guard, or unreachable condition.
4. Where safe and useful, confirm it with an existing test, a temporary scratch test, a REPL, or a
   focused command.
5. Delete all scratch artifacts after verification.

When independent agents are available, a different agent must perform the refutation pass. When
they are unavailable, perform and document a separate self-refutation pass.

### Phase 3 - Complete and Synthesize

The audit is complete only after all of these conditions are met:

1. Every included first-party file has been inspected, and every meaningful flow has been traced.
2. Every recorded candidate has either become a finding with explicit evidence and confidence or
   been consciously refuted.
3. The dedicated high-risk pass is complete.
4. One final discovery pass across the inventory produces no new candidates.
5. Coverage gaps and limitations are recorded in the report.

Then deduplicate findings, merge shared root causes, assign final confidence and severity, and write
the report.

## Verification and Ranking

### Confidence - How Strong Is the Evidence?

- **High:** the trigger and failure path were traced or reproduced, and no preventing guard or
  invariant was found.
- **Medium:** the failure path is reachable under a plausible trigger, but one material assumption
  about inputs, state, environment, or impact remains unverified.
- **Low:** the failure path and plausible trigger are identified, but multiple material assumptions
  remain unverified. State those assumptions explicitly.

### Severity - What Is the Worst Credible Impact?

Assign severity from impact, independent of confidence and occurrence frequency:

- **Critical:** credible security compromise, irreversible data loss or corruption, safety impact,
  or system-wide failure.
- **High:** major loss of core functionality, materially incorrect core results, or a contained but
  serious security or data-integrity failure.
- **Medium:** recoverable incorrect behavior, degraded non-core functionality, or failure limited to
  an edge case.
- **Low:** minor behavioral defect with limited impact.

Do not raise or lower severity merely because the trigger is common or rare. Describe trigger
frequency separately when it is known.

## Primary Hunt Questions

For every meaningful module or flow, ask:

- What empty, null, boundary, huge, concurrent, malformed, or out-of-order input makes this fail?
- Which assumption about input, state, ownership, ordering, or environment can be violated?
- Can any failure surface as success or leave partial state?
- Which concurrency interleaving breaks this?
- Is every acquired resource released on every path?
- Do caller and callee agree on units, ranges, nullability, indexing, and ownership?
- Is every dispatch case handled?
- Can untrusted input reach a dangerous sink?
- Does unreachable behavior reveal missing or ineffective runtime behavior?

## What to Investigate Aggressively

- Unchecked absence, boundary errors, inverted conditions, wrong variables, and wrong units.
- Swallowed errors, silent fallbacks, partial updates, and ignored return values.
- Non-atomic shared-state operations and resources without guaranteed cleanup.
- External input reaching dangerous sinks without validation.
- Missing dispatch cases and parallel resources or configuration that have drifted.

Aggressive investigation does not lower the verification bar. Report only candidates that survive
verification.

## Output

- **Report only.** Do not fix product code or configuration.
- `BUG_FINDINGS.md` is the sole permitted persistent change and is a transient, skill-owned work
  product. Replace its prior contents when it already exists. Do not delete, revert, or modify any
  other pre-existing file.
- Temporary verification artifacts are allowed but must be removed before completion.
- Order findings by Severity (`Critical`, `High`, `Medium`, `Low`), then by Confidence (`High`,
  `Medium`, `Low`) within each severity section. This prevents critical risks from being buried.
- Use stable IDs in the form `[<tier>-<category>-<path-slug>-<defect-slug>]`, derived from the
  finding's root cause rather than its report position.

Each finding must contain:

- A heading with its stable ID and short title.
- **Location:** `path/to/file:line`.
- **Severity:** Critical | High | Medium | Low.
- **Confidence:** High | Medium | Low.
- **Defect:** what is wrong and the incorrect resulting behavior.
- **Trigger:** the input, sequence, state, or environment that activates it.
- **Evidence / verification:** the traced path, reproduction, command, or unresolved assumptions.
- **Suggested fix:** the change described in prose; apply no diff.

End `BUG_FINDINGS.md` with:

- **Audit coverage:** inspected modules and meaningful flows.
- **Verification performed:** tests, commands, reproductions, and refutation work.
- **Exclusions and limitations:** skipped areas, unavailable tooling, and unresolved uncertainty.
- **Summary:** counts by severity and confidence plus the files affected.

End the chat response with a brief inline summary containing the same counts, affected files, top
findings, and a link to `BUG_FINDINGS.md`. If no findings survive, say so plainly and summarize
coverage and limitations without claiming the codebase is universally bug-free.
