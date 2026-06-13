---
name: apocalypse-bug-review
description: Run an extremely strict, exhaustive whole-codebase defect hunt for bugs, correctness errors, inconsistencies, security flaws, and edge-case failures.
disable-model-invocation: true
---

# Apocalypse Bug Review

Use this skill for an unusually strict, exhaustive hunt for defects: bugs, correctness errors,
inconsistencies, edge-case failures, and security flaws that can cause incorrect application, build,
release, deployment, migration, operational, security, or data behavior.

Be adversarial about behavior. Read the code looking for the input, sequence, state, environment, or
interleaving that makes it fail. Do not spend effort on abstractions, naming, file size, or style
unless they directly cause incorrect behavior.

## Operating Rules

- Assume behavior is unverified until its contracts and failure paths have been inspected.
- Cast a wide net during discovery, but report only defects whose reachable failure path and
  incorrect result survive verification. Verify twice, report once.
- Treat the worktree as read-only by default. Do not fix product code or configuration.
- Perform verification only against local, isolated, or explicitly approved test environments.
- Never deploy, mutate production data, run destructive migrations, exploit live systems, expose
  secrets, or contact external services without explicit user approval.
- Preserve all pre-existing tracked and untracked work. Never delete, revert, overwrite, or clean up
  pre-existing state or user-authored changes.

## Scope

Audit the entire first-party codebase, not only a diff or recently changed files.

For this audit:

- **First-party file:** a repository-owned file that defines, validates, builds, releases, deploys,
  migrates, configures, documents, or tests shipped behavior.
- **Inspected file:** a file whose relevant contents and role have been reviewed at least once.
- **Meaningful flow:** a user-visible or operational behavior traced from an entry point through its
  material contracts, state changes, side effects, result, and error handling.

Include:

- Application, library, service, and command-line source.
- First-party scripts, migrations, build logic, CI workflows, infrastructure definitions, and
  configuration that can affect shipped behavior, releases, deployments, operations, or data.
- Tests, documentation, schemas, and interface definitions as evidence of intended contracts.
  Report defects in them only when they can hide, permit, or cause incorrect shipped behavior.
- Checked-in generated artifacts that are shipped, deployed, consumed directly, or used to validate
  behavior. Attribute the finding to the first-party generator or source configuration when that is
  the root cause.

Exclude:

- Vendored, minified, dependency, cache, and disposable build output such as `node_modules`,
  `vendor/`, `build/`, `dist/`, `target/`, and `.git`.
- Generated artifacts that are neither checked in nor consumed as part of shipped behavior.
- Pure maintainability concerns with no demonstrated behavioral consequence.

Record the audited snapshot: repository path, branch, commit, initial tracked modifications, and
initial untracked files. Record explicit exclusions and explain any ambiguous ownership decision.

## Candidate Versus Finding

- A **candidate** is a suspicious pattern that requires investigation.
- A **finding** is a candidate whose code-path reachability and incorrect result are established
  under a concrete or plausible stated trigger. Every finding must appear in `BUG_FINDINGS.md`,
  either independently or merged with findings that share the same root cause.
- A candidate is **refuted** when a guard, invariant, caller contract, unreachable state, or other
  evidence prevents the suspected incorrect behavior.
- A candidate is **unverified** when verification is blocked or cannot establish the defect path.
  Do not report it as a finding; record the location, suspected risk, blocker, and unresolved
  question under **Exclusions and limitations**.

Keep a transient candidate ledger in memory or outside the repository. For each candidate, record
its location, taxonomy category, suspected trigger and failure path, verification evidence, and
final disposition: finding, merged, refuted, or unverified. Remove any on-disk ledger before
completion.

## Defect Taxonomy

Hunt all three cumulative tiers. Assign each finding the tier and category that most directly
describe its root cause.

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

Use parallel agents when they are available and permitted. Discovery agents may over-report
candidates, but only the synthesis step writes `BUG_FINDINGS.md`.

### Phase 0 - Orient and Inventory

1. Capture the audited snapshot and initial worktree state before running verification commands.
2. Read project instructions, documentation, architecture material, schemas, and key contracts.
3. Build an inventory of included first-party files, with an exact included-file count and explicit
   excluded paths or categories, grouped into meaningful modules and flows.
4. Identify high-risk surfaces: external input, authentication and authorization, persistence,
   migrations, concurrency, error handling, resource ownership, security sinks, release logic, and
   deployment configuration.
5. Record exclusions, ambiguous ownership, and files or flows that cannot be inspected.

### Phase 1 - Discover

1. Inspect every inventoried file and meaningful flow at least once.
2. Record each candidate in the transient ledger.
3. Trace cross-module contracts and parallel resources that must remain consistent.
4. Run a dedicated high-risk pass covering external input, dangerous sinks, authorization, error
   paths, concurrency, resource cleanup, boundaries, migrations, and partial updates.

For every meaningful module or flow, ask:

- What empty, null, boundary, huge, concurrent, malformed, or out-of-order input makes this fail?
- Which assumption about input, state, ownership, ordering, or environment can be violated?
- Can a failure surface as success or leave partial state?
- Which concurrency interleaving breaks this?
- Is every acquired resource released on every path?
- Do caller and callee agree on units, ranges, nullability, indexing, and ownership?
- Is every dispatch case handled?
- Can untrusted input reach a dangerous sink?
- Does unreachable behavior reveal missing or ineffective shipped behavior?

### Phase 2 - Verify and Refute

For every candidate:

1. Trace its real callers, data flow, guards, contracts, and state transitions.
2. Establish a concrete or plausible trigger and the resulting incorrect behavior.
3. Try to refute it by finding a preventing invariant, guard, contract, or unreachable condition.
4. Where safe and useful, confirm it with an existing test, isolated scratch test, REPL, or focused
   command.
5. Record evidence and assign a disposition in the candidate ledger.

Verification commands must not be expected to rewrite tracked files or modify external state.
Before and after verification, compare the worktree with the captured initial state. Remove only
scratch artifacts created by the audit. If an unexpected change cannot be restored exactly without
disturbing pre-existing work, stop that verification path and record the limitation.

When independent agents are available and permitted, assign the refutation pass to a different
agent. Otherwise, perform and document a separate self-refutation pass.

### Phase 3 - Complete and Synthesize

Repeat the final discovery pass until it produces no new candidates, or mark the audit Partial if
constraints prevent another pass. Resolve every candidate as finding, merged, refuted, or
unverified. Then deduplicate findings, merge shared root causes, assign final confidence and
severity, and write the report.

Assign one audit status:

- **Complete:** every included file was inspected; every meaningful flow was traced; every candidate
  became a finding, was merged, or was refuted; no unverified candidates remain; the dedicated
  high-risk pass was completed; and a final discovery pass produced no new candidates.
- **Partial:** any Complete condition was not met. State each unmet condition and do not claim the
  audit was exhaustive or complete.

## Verification and Ranking

### Confidence - How Strong Is the Evidence?

Reachability and incorrect behavior must be established for every finding. Confidence describes
remaining uncertainty about the stated conditions, environment, frequency, or impact, not whether
the defect path exists.

- **High:** the trigger and failure path were reproduced or fully traced, with no material
  uncertainty remaining.
- **Medium:** the defect path is established, but one material uncertainty remains about its stated
  conditions, environment, trigger frequency, or impact.
- **Low:** the defect path is established, but multiple material uncertainties remain about its
  stated conditions, environment, trigger frequency, or impact. State each assumption explicitly.

Do not use Low confidence for a candidate whose reachability or incorrect result remains
unverified.

### Severity - What Is the Worst Credible Impact?

Assign severity from the worst impact supported by a realistic trigger and stated preconditions.
Keep occurrence frequency and confidence separate from severity.

- **Critical:** credible broad security compromise, irreversible or widespread data loss or
  corruption, safety impact, or prolonged total system-wide outage.
- **High:** major loss of core functionality, materially incorrect core results, serious contained
  security or data-integrity failure, or a recoverable system-wide outage.
- **Medium:** recoverable incorrect behavior, degraded non-core functionality, or failure limited to
  an edge case with meaningful impact.
- **Low:** minor behavioral defect with limited impact.

## Output

- **Report only.** Do not fix product code or configuration.
- `BUG_FINDINGS.md` is the sole permitted persistent change and is a transient, skill-owned work
  product. Replace its prior contents when it already exists. Write it even when no findings
  survive.
- Do not intentionally modify any other pre-existing file. Remove only temporary artifacts created
  by the audit, and preserve the captured initial worktree state.
- Order findings by Severity (`Critical`, `High`, `Medium`, `Low`), then by Confidence (`High`,
  `Medium`, `Low`) within each severity section.

Use stable IDs in the form `[<tier>-<category>-<component>-<defect-slug>]`:

- Use lowercase ASCII kebab-case.
- Use the primary root-cause tier and category.
- Use a stable logical component or subsystem, not a file path.
- Describe the root cause, not the report position or observed symptom.
- Keep an existing ID when the same root cause moves files.

Each finding must contain:

- A heading with its stable ID and short title.
- **Location:** one primary `path/to/file:line` and any related locations needed to trace the
  defect.
- **Severity:** Critical | High | Medium | Low.
- **Confidence:** High | Medium | Low.
- **Defect:** what is wrong and the incorrect resulting behavior.
- **Trigger:** the input, sequence, state, environment, or interleaving that activates it.
- **Evidence / verification:** the traced path, reproduction, command, refutation attempt, and any
  remaining assumptions.
- **Suggested fix:** the change described in prose; apply no diff.

End `BUG_FINDINGS.md` with:

- **Audit status:** Complete | Partial, including unmet completion conditions for a Partial audit.
- **Audited snapshot:** repository path, branch, commit, and initial worktree state.
- **Audit coverage:** exact included, inspected, and skipped first-party file counts; excluded paths
  or categories and their counts when practical; inspected modules; and traced meaningful flows.
- **Verification performed:** tests, commands, reproductions, and refutation work.
- **Exclusions and limitations:** skipped areas, unverified candidates, unavailable tooling, blocked
  verification, and unresolved uncertainty.
- **Summary:** finding counts by severity, by confidence, and as a severity-confidence matrix, plus
  the unique affected files.

End the chat response with a brief inline summary containing the audit status, the same counts,
affected files, top findings, and a link to `BUG_FINDINGS.md`. If no findings survive, say so
plainly
and summarize coverage and limitations without claiming the codebase is universally bug-free.
