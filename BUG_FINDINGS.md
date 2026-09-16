### [c/api-or-library-misuse/mediastore-provider-test/a-hard-assert-behind-a-real-clock-latch] A MediaStore test turned a capability skip into a timing-dependent hard failure

**Location:**
`app/src/androidTest/java/com/mauriciotogneri/fileexplorer/util/MediaStoreUtilProviderTest.kt:170-197`

**Severity:** Low
**Confidence:** Medium

**Defect:** `createAndScan` changed `assumeTrue("Provider did not report a path for the row", …)`
into `assertTrue(…)`, evaluated after `scanned.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)` — a
wall-clock wait on `MediaScannerConnection` completing on the device, sampled once. The tightening
itself is deliberate and correct in intent: three classes' worth of silent skips were reporting
green. What is new is that this particular class's assertion now depends on a real-clock deadline
rather than on a device capability. The sibling conversions in `IntentUtilOpenFileTest.kt:126-136`,
`IntentUtilPlayStoreTest.kt:112-122`, `IntentUtilOpenFileWithTest.kt:164-170` and
`PermissionScreenActionsTest.kt:58-73` key on capability (no viewer, no browser, API < R), not on
timing.

**Trigger:** Run the instrumentation suite on a loaded emulator where the media scan does not
complete inside the timeout.

**Incorrect result:** The class fails rather than skipping — a timing-dependent red in the suite
that gates releases.

**Evidence / verification:** Read both versions (
`git show 9e87306d…:…/MediaStoreUtilProviderTest.kt` returns
`file.absolutePath.takeIf { rowExists(it) }`, nullable, with `assumeTrue` at each call site).
Confirmed it cannot redden the per-change loop: CLAUDE.md excludes instrumentation from it, so
`testDebugUnitTest` and `lintDebug` are unaffected. That bounds the blast radius to the
instrumentation suite, which is what keeps this at Low; the exposure is the tail, so a single green
run does not disprove it, which is why confidence is Medium.

**Suggested fix:** Keep the hard assert, but poll `rowExists` for a bounded window after the latch
instead of sampling once, so a slow scan costs seconds rather than a failure.

### [c/api-or-library-misuse/file-repository-test-fixture/a-write-denial-assertion-that-cannot-hold-as-root] A new unit test fails for an environment reason when the suite runs as root

**Location:**
`app/src/test/java/com/mauriciotogneri/fileexplorer/data/repository/FileRepositoryTest.kt:62-95`

**Severity:** Low
**Confidence:** Medium

**Defect:** The new test `the fixture filesystem enforces a write denial` asserts
`!directory.canWrite()` after `setWritable(false, false)`. Root ignores the permission bit, so the
assertion cannot hold for a process running as uid 0. It is the only test on this branch that can
turn the mandated per-change unit loop red for a reason that is not the code.

**Trigger:** Run `./gradlew testDebugUnitTest` as root — a Docker CI image, or any container without
a mapped user.

**Incorrect result:** The unit loop goes red with a message about the environment. This is
deliberate: the test stands in for thirteen `assumeTrue` guards elsewhere (
`FileRepositoryTest.kt:2069-2076`, `:2252-2255`, `PickerViewModelTest.kt:292-300`,
`FileAccessTest.kt:122-126`) that would otherwise skip invisibly, and it has no `assumeTrue` escape
by design. The cost is that the failure mode is environmental rather than diagnostic.

**Evidence / verification:** Read the test and the guards it substitutes for. Verified it passes in
this environment (`id -u` is 1000) by running the full unit suite in a disposable copy of the
branch: `testDebugUnitTest` exits 0. The residual exposure is the root case, which I could not
exercise here — hence Medium confidence. The file does not exist at the baseline (
`git show 9e87306d…:…/FileRepositoryTest.kt | grep "write denial"` returns nothing).

**Suggested fix:** None required if the suite is never run as root; the assertion message already
names the remedy. If a root CI runner is ever used, guard the class on `!canWrite()` being honoured
and fail loudly once per run rather than per test.
