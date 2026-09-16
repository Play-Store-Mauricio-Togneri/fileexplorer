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
