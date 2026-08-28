### [c/dead-or-unreachable/test-structure-guards/backtick-identifier-blinds-scanner] A backticked test name containing an apostrophe silently disables two structural guards

- **Location:** `scripts/check_tests.py:104` (inside `blank()`); consumed by
  `check_no_test_composables` (`:159`), `check_no_discarded_assertions` (`:419` via
  `_opaque_literals:370`)
- **Severity:** Medium
- **Confidence:** High
- **Defect:** `blank()` erases comments and string literals before the guards scan a file. Its
  literal branch is `elif text[i] in "\"'":` — it handles `"` and `'` but has **no branch for a
  backtick**. Kotlin's backtick-quoted declaration names are therefore scanned as ordinary code, so
  an apostrophe inside one (`` fun `it doesn't crash`() ``) is read as opening a char literal. The
  scanner runs to the next `'` in the file, or to EOF, and for the `strings=True` callers erases
  everything in between. Every violation after that point becomes invisible and `check-tests.sh`
  still exits 0 — the guard reports OK rather than reporting that it could not scan.
- **Trigger:** any file under `app/src/androidTest/java` that declares a backtick-quoted name
  containing an apostrophe, with a violation later in the file.
- **Evidence / verification:** Reproduced with a control/treatment pair in an isolated workspace
  outside the repository (a copy of the script with `ROOT` repointed, run with `python3 -B`). Two
  files identical except for one added backticked test name:
    - control (no backticked name): `No test-local @Composable declarations` **FAIL**,
      `No discarded assertion results` **FAIL**
    - treatment (` fun `it doesn't crash`() ` added above the violations): both **OK**, violations
      still present.

  The guards are otherwise not vacuous — against a synthetic violating file they correctly fail, and
  against the real tree all four print OK. Refutation attempt: I checked whether the 8
  apostrophe-bearing backticked names already present in `app/src/test` trigger this today. They do
  not — `kotlin_files()` (`:42-43`) globs `ANDROID_TEST` only (`:28`), so the unit-test tree is
  never scanned. I also confirmed the ordering constraint: violations *before* the backticked name
  still fail, so only content after it is blinded.
- **Reachability:** latent, not currently active. `app/src/androidTest` has **0** backticked
  declaration names today, so nothing is being concealed right now, and `check-tests.sh` passes
  legitimately. But `app/src/test` has **813** such names (8 with apostrophes) and `CLAUDE.md`'s
  testing section endorses that style, so the first instrumentation test written that way turns two
  guards off for its file with no signal. The guard that catches discarded assertions is exactly the
  one protecting against tests that assert nothing.
- **Baseline attribution:** INTRODUCED. `git cat-file -e d0b63a1c:scripts/check_tests.py` fails —
  the file does not exist at baseline (`A` in the diff).
- **Suggested fix:** add a backtick case to `blank()` that skips from one `` ` `` to its closing
  `` ` `` without interpreting anything inside, placed alongside the existing `"` / `'` branch. A
  backticked name cannot span a newline, so bounding the skip at the next newline also guards
  against an unterminated backtick. Consider making an unterminated literal a hard error rather than
  an erase-to-EOF, so a scan that cannot be completed fails loudly instead of passing.
