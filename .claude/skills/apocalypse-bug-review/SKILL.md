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
  incorrect result survive verification. Validate each finding twice: first establish its
  reachability and incorrect result, then perform a separate refutation attempt that tries to
  disprove the suspected defect.
- Treat the worktree as read-only by default. Do not fix product code or configuration.
- Perform verification only against local, isolated, or explicitly approved test environments.
- Never deploy, mutate production data, run destructive migrations, exploit live systems, expose
  secrets, or contact external services without explicit user approval.
- Preserve all pre-existing tracked and untracked work. Except for an explicitly approved
  `BUG_FINDINGS.md` replacement under the Output contract, never delete, revert, overwrite, or clean
  up pre-existing state or user-authored changes.
- Make the first audit workflow action a worktree snapshot, before substantive inspection of the
  audit target or any verification. Store snapshot metadata outside the repository. Record the
  branch and commit; tracked and untracked status; hashes of the initial staged and unstaged tracked
  diffs; and a manifest of every initially non-clean tracked path and initially untracked path.
  Include ignored paths that verification might touch, or explicitly exclude them from the
  preservation guarantee. For every manifested path, record its existence, type, and mode, plus its
  content hash or symlink target when applicable.
- Run commands expected or reasonably likely to create, delete, or modify files only in a disposable
  verification workspace outside the repository. Construct it from a copy of the audited source
  state after capturing the snapshot; exclude `.git` internals and unnecessary secret-bearing files;
  and record material differences from the audited worktree. If representative verification cannot
  be performed there safely, skip it and record the limitation.
- Treat `BUG_FINDINGS.md` as the sole permitted repository change, subject to the existing-report
  approval rule in the Output contract. Do not claim preservation of ignored or otherwise excluded
  paths unless they were captured and compared.

## Scope

Audit the entire first-party codebase, not only a diff or recently changed files.

For this audit:

- **First-party file:** a repository-owned file that defines, validates, builds, releases, deploys,
  migrates, configures, documents, or tests shipped behavior, including documentation that records
  an authoritative or asserted behavioral contract.
- **Inspected file:** a file whose relevant contents and role have been reviewed at least once.
- **Skipped file:** an included first-party file that was not inspected.
- **Meaningful flow:** an externally invocable operation or independently triggered user-visible or
  operational behavior traced from an entry point through its material contracts, state changes,
  side effects, result, and error handling. Split flows when authorization, persistence, side
  effects, or failure handling materially differ. Do not count internal helper calls as separate
  flows.
- **Traced or skipped flow:** a meaningful flow is traced when its material contracts, state
  changes, side effects, result, and error handling were reviewed; otherwise it is skipped.

Include:

- Application, library, service, and command-line source.
- First-party scripts, migrations, build logic, CI workflows, infrastructure definitions, and
  configuration that can affect shipped behavior, releases, deployments, operations, or data.
- Tests, documentation, schemas, and interface definitions as evidence of intended contracts.
  Report a defect in one only when it directly causes incorrect shipped behavior, asserts an
  incorrect authoritative contract, or demonstrably conceals an established shipped defect.
  Missing coverage or unclear documentation alone is not a finding.
- Checked-in generated artifacts that are shipped, deployed, consumed directly, or used to validate
  behavior. Attribute the finding to the first-party generator or source configuration when that is
  the root cause.

Exclude:

- Vendored, minified, dependency, cache, and disposable build output such as `node_modules`,
  `vendor/`, `build/`, `dist/`, `target/`, and `.git`, unless a generated artifact is checked in and
  directly shipped, deployed, consumed, or used to validate behavior. The inclusion rule for such
  generated artifacts takes precedence over this exclusion.
- Generated artifacts that are neither checked in nor consumed as part of shipped behavior.
- Pure maintainability concerns with no demonstrated behavioral consequence.

Record the audited snapshot: repository path, branch, commit, initial tracked modifications, and
initial untracked files. Record captured and excluded ignored-path categories, explicit scope
exclusions, and any ambiguous ownership decision. Inventory meaningful flows with stable
audit-local IDs and record exact total, traced, and skipped flow counts.

Count each included file path and each meaningful flow exactly once. Maintain these coverage
invariants:

- `included first-party files = inspected files + skipped files`
- `total meaningful flows = traced flows + skipped flows`

## Candidate Versus Finding

- A **candidate** is a suspicious pattern that requires investigation.
- A **finding** is a candidate whose code-path reachability and incorrect result are established
  under a concrete or plausible stated trigger. Every verified root cause must be represented in
  `BUG_FINDINGS.md`; merge candidates that share the same root cause into one finding.
- A candidate is **refuted** when a guard, invariant, caller contract, unreachable state, or other
  evidence prevents the suspected incorrect behavior.
- A candidate is **unverified** when verification is blocked or cannot establish the defect path.
  Do not report it as a finding; record the location, suspected risk, blocker, and unresolved
  question under **Exclusions and limitations**.

Assign each candidate a stable audit-local ID such as `C-001`. Keep a transient candidate ledger in
memory or outside the repository. For each candidate, record its ID, location, taxonomy category,
suspected trigger and failure path, verification evidence, and final disposition:

- **Finding:** became one unique reported finding.
- **Merged:** shares a root cause with another candidate and maps to that candidate's finding.
- **Refuted:** evidence disproved the suspected defect path.
- **Unverified:** verification remained blocked or inconclusive.

Maintain this accounting invariant:
`total candidates = finding + merged + refuted + unverified`. Preserve a compact candidate
disposition table in `BUG_FINDINGS.md`; remove only the more detailed working ledger.

## Defect Taxonomy

Inspect every category in all three taxonomy tiers. Assign each finding the tier and category that
most directly describe its root cause; the tiers classify defects and do not imply severity or
inspection order.

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

Use parallel agents when they are available and permitted. Agents may inspect files, trace flows,
and propose or refute candidates, but they must not write repository files or run commands expected
to modify repository or external state. Discovery agents may over-report candidates, but only the
synthesis step writes `BUG_FINDINGS.md`.

### Phase 0 - Orient and Inventory

1. As the first audit workflow action, capture the audited snapshot and initial worktree state
   specified in Operating Rules before substantive inspection or verification.
2. Read project instructions, documentation, architecture material, schemas, and key contracts.
3. Build a path-level inventory of included first-party files, with an inspected or skipped status
   for every included path, an exact included-file count, and explicit excluded paths or categories,
   grouped into meaningful modules and flows. Record deterministic selection rules and commands,
   exact counts per module, and a manifest digest so the included inventory can be reproduced and
   verified against the audited snapshot.
4. Assign stable audit-local IDs to meaningful flows and record exact total, traced, and skipped
   flow counts. For each flow, record its entry point, material result or side effect, and status.
5. Identify high-risk surfaces: external input, authentication and authorization, persistence,
   migrations, concurrency, error handling, resource ownership, security sinks, release logic, and
   deployment configuration.
6. Record exclusions, ambiguous ownership, and files or flows that cannot be inspected.

### Phase 1 - Discover

1. Inspect every inventoried file and trace every inventoried meaningful flow at least once.
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

Verification commands run in the audited worktree must be read-only and must not be expected to
rewrite files or modify external state. Run any command expected or reasonably likely to write in
the disposable verification workspace. Immediately before and after each verification command or
logically inseparable command batch run in the audited worktree, compare the worktree status,
staged- and unstaged-diff hashes, and captured file manifest with the initial snapshot, excluding the
permitted skill-owned `BUG_FINDINGS.md`. Remove only scratch artifacts created by the audit. If an
unexpected change cannot be restored exactly without disturbing pre-existing work, stop that
verification path and record the limitation.

When independent agents are available and permitted, assign the refutation pass to a different
agent. Otherwise, perform and document a separate self-refutation pass after the initial
reachability analysis. This separate refutation attempt is the second validation required by
Operating Rules; a second reproduction is not required.

### Phase 3 - Complete and Synthesize

Repeat additional discovery passes until one produces no new candidates; designate that last pass
as the final discovery pass. If constraints prevent another pass, mark the audit Partial. Resolve
every candidate as finding, merged, refuted, or unverified. Verify the candidate accounting
invariant and record exact disposition counts. Then deduplicate findings, merge shared root causes,
assign final confidence and severity, and write the report. Before completion, perform a final
comparison against the initial worktree snapshot, record its result and the snapshot-manifest
digest in the report, preserve the compact candidate disposition table, and remove external
snapshot metadata, the disposable verification workspace, and any detailed on-disk candidate
ledger.

Assign one audit status:

- **Complete:** the included-file inventory and meaningful-flow inventory are enumerated with exact
  counts; every included file was inspected; every meaningful flow was traced; every candidate
  became a finding, was merged, or was refuted; no unverified candidates remain; the dedicated
  high-risk pass and all taxonomy-category inspections were completed; a separate refutation
  attempt was performed for every finding; a final discovery pass produced no new candidates; and
  the final worktree comparison confirmed that no non-exempt captured pre-existing state changed.
  The report must contain enough inventory, flow, and candidate-disposition evidence to reproduce
  every exact count.
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

Do not promote an unverified candidate to a Low-confidence finding.

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
- `BUG_FINDINGS.md` is the sole permitted persistent report artifact. At snapshot time, detect and
  hash any existing report, then read it to retain stable IDs for recurring root causes.
- Do not replace a pre-existing `BUG_FINDINGS.md` without explicit user approval; approval may be
  supplied with the skill invocation. If approval is unavailable or denied, do not modify it, mark
  the audit Partial, and provide the blocked report summary in chat. Otherwise, replace its contents
  and write it even when no findings survive. Immediately before replacement, verify that its
  current hash still matches the snapshot; if it appeared or changed after the snapshot, treat it as
  user-authored work and obtain new approval before replacing it.
- Do not intentionally modify any other pre-existing file. Remove only temporary artifacts created
  by the audit, and preserve the captured initial worktree state.
- Order findings by Severity (`Critical`, `High`, `Medium`, `Low`), then by Confidence (`High`,
  `Medium`, `Low`) within each severity section.

Use stable IDs in the form `[<tier>-<category>-<component>-<defect-slug>]`:

- Encode `<tier>` as `a`, `b`, or `c`. Encode the taxonomy category heading as lowercase kebab-case,
  such as `error-handling` or `contract-mismatches`.
- Use lowercase ASCII kebab-case.
- Use the primary root-cause tier and category.
- Use a stable logical component or subsystem, not a file path.
- Describe the root cause, not the report position or observed symptom.
- Keep an existing ID when the same root cause moves files.
- Example: `[a-error-handling-file-operations-failure-reported-as-success]`.

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

Use this top-level structure for `BUG_FINDINGS.md`, omitting only severity sections that contain no
findings:

```markdown
# Bug Findings

## Critical
### [stable-id] Short title
...

## High
...

## Medium
...

## Low
...

## Audit Details
### Audit Status
### Audited Snapshot
### Audit Coverage
### Candidate Dispositions
### Verification Performed
### Exclusions and Limitations
### Summary
```

Populate the final report sections as follows:

- **Audit status:** Complete | Partial, including unmet completion conditions for a Partial audit.
- **Audited snapshot:** repository path, branch, commit, initial worktree state, a digest of the
  external snapshot manifest, captured and excluded ignored-path categories, approval status for
  replacing any pre-existing report, and the result of the final comparison against that snapshot.
- **Audit coverage:** exact included, inspected, and skipped first-party file counts; excluded paths
  or categories and their counts when practical; inspected modules; deterministic inventory
  selection rules and commands, exact counts per module, the inventory-manifest digest, and every
  skipped included path; and a table of every meaningful-flow ID, entry point, material result or
  side effect, and status, with exact total, traced, and skipped flow counts. Confirm both coverage
  accounting invariants and summarize coverage of every taxonomy category.
- **Candidate dispositions:** a compact table containing every candidate ID, primary location,
  category, disposition, and resulting finding ID or concise disposition reason. Include exact
  counts for finding, merged, refuted, and unverified, and confirm the accounting invariant.
- **Verification performed:** tests, commands, reproductions, and refutation work.
- **Exclusions and limitations:** skipped areas, unverified candidates, unavailable tooling, blocked
  verification, and unresolved uncertainty.
- **Summary:** finding counts by severity, by confidence, and as a severity-confidence matrix, plus
  the unique affected files. Use a matrix table with severity rows; `High`, `Medium`, and `Low`
  confidence columns; and row and column totals.

End the chat response with a brief inline summary containing the audit status; finding counts by
severity and confidence; candidate disposition counts; included, inspected, and skipped file and
flow counts; the affected-file count and most important affected files; top findings; and a link to
`BUG_FINDINGS.md` when it was written. If no findings survive, say so plainly and summarize coverage
and limitations without claiming the codebase is universally bug-free.
