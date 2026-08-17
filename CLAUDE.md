# Critical Instructions

Keep `CLAUDE.md` up to date if rules need to be added or updated.

## Commands

Run these after every change, with these flags:

```bash
./gradlew -w --console=plain -I gradle/agent-quiet.init.gradle testDebugUnitTest  # unit tests
./gradlew -w --console=plain lintDebug                                            # Android lint
./scripts/check-tests.sh                                                          # structural guards
```

The flags are not decoration — strip them and every run re-prints ~35 lines of
`> Task :app:... UP-TO-DATE` inventory that an agent session then carries for the rest of its
context. Measured on a passing run: unit tests **1,842 chars → 130**, `lintDebug` **2,064 → 232**.

- `-w` hides Gradle's per-task lines but keeps compiler warnings; `-q` would silence the `w:` lines
  too, so prefer `-w`.
- `-I gradle/agent-quiet.init.gradle` is required whenever the command runs unit tests. Gradle logs
  test failures at LIFECYCLE, so `-w` on its own reports `1 test failed` without naming which one.
  The init script promotes those events to WARN and adds the assertion message the default output
  leaves out: a failing test prints its name, `expected:<...> but was:<...>`, and the
  `SomeTest.kt:12` frame in **1,026 chars, against 2,088 at default verbosity**. It is applied only
  via `-I`, so humans and CI are unaffected.
- Compile errors (`e: file://...:26:29 Return type mismatch`) and lint errors (file, line, rule ID
  and source snippet) survive `-w` unchanged — both were verified against deliberate failures.

Reserved for the owner — never part of the per-change loop:

- `./scripts/test.sh` — `clean` + `--rerun-tasks` + the full instrumentation suite. Needs a running
  emulator, discards all incremental build state, and prints **43,524 chars**, 86% of it repeated
  `androidTest` deprecation warnings.
- `./scripts/build.sh` — release AAB packaging (2,985 chars).
- `./gradlew connectedDebugAndroidTest` — only when an emulator is already running.
- `python3 scripts/audit_tests.py` — on-demand audit, 13,069 chars; pass section names
  (`coverage duplicates`, 4,629 chars) to narrow it.

Hand any instrumentation run to the `instrumentation-runner` subagent, which reports only the
failures. Its output is unbounded — ~700 tests, plus per-test device output on failure — so no flag
can bound it the way `-w` bounds the unit suite. Run the unit suite inline instead; at 130 chars a
subagent would cost a round-trip and save nothing.

## Development Standards

### Theming (Dark/Light)

- Use Material3 theme attributes (`MaterialTheme.colorScheme.*`) — never hardcode colors
- All UI must render correctly in LIGHT, DARK, and SYSTEM theme modes
- Use tintable vector drawables; if a drawable needs theme variants, provide both
- Test new UI in both themes before completing work

### Feature Discovery Badges

A `BadgeDot` marks something the user has not seen yet. Dismissing one stores the version it was at,
so `PreferencesRepository.BADGE_VERSIONS` decides which badges a release shows again.

- When a release adds something worth pointing at, raise **every badge on the trail** to it, not
  just the destination: the hamburger dot opens the drawer, the drawer's row opens the screen. A dot
  on a settings row is unreachable for users who dismissed the steps before it
- Leave every badge the release did not change alone — dots leading to nothing new teach users to
  ignore dots, and the next release then has nothing to point with
- A badge added by this release needs no entry: it has never been dismissed, so it already shows

### Testing

- **Unit tests** (`app/src/test/`): Required for all business logic (ViewModels, repositories, use
  cases, utilities)
- **Instrumentation tests** (`app/src/androidTest/`): Required for critical user flows and Compose
  UI
- Do not run instrumentation tests if an emulator is not up and running
- New code must not decrease overall test coverage
- Write tests with JUnit 4 annotations and assertions, plus Compose UI Testing for Compose screens.
  Unit tests run on the JUnit 5 platform (`useJUnitPlatform()`), where the vintage engine executes
  them — so JUnit 5 dependencies in the build are not an invitation to write Jupiter tests
- Mockk, Turbine and `kotlinx-coroutines-test` are already available for mocking, Flow assertions
  and coroutine control — no need to add them

### Localization

- Never hardcode user-facing strings — use `strings.xml` resources
- When adding a new string, add translations to ALL supported languages:
    - `values/` (English - default)
    - `values-ar/` (Arabic - RTL)
    - `values-bn/` (Bengali)
    - `values-ca/` (Catalan)
    - `values-de/` (German)
    - `values-el/` (Greek)
    - `values-es/` (Spanish)
    - `values-fr/` (French)
    - `values-hi/` (Hindi)
    - `values-in/` (Indonesian)
    - `values-it/` (Italian)
    - `values-ja/` (Japanese)
    - `values-nl/` (Dutch)
    - `values-pt/` (Portuguese)
    - `values-ro/` (Romanian)
    - `values-ru/` (Russian)
    - `values-tr/` (Turkish)
    - `values-ur/` (Urdu - RTL)
    - `values-vi/` (Vietnamese)
    - `values-zh/` (Chinese - Simplified)
- Use `<plurals>` for quantity-dependent text; some languages require additional quantities (e.g.,
  Russian: few/many, Arabic: zero/one/two/few/many/other, Romanian: few)
- Consider text expansion (~30-40%) when designing layouts
- RTL languages (Arabic, Urdu) are supported via `android:supportsRtl="true"` in manifest
- Localized documents (`privacy.md`, `terms.md`) live in `raw/` and the matching `raw-*/`
  directories for the same 20 languages — when the English version changes, update all 19
  translations

### Architecture

- Business logic belongs in ViewModels, not Composables or Activities
- Use `StateFlow`/`Flow` for reactive state; avoid `LiveData` in new code
- Keep Composables stateless where possible; hoist state up

### Resource Management

- Prefer vector drawables (`res/drawable/`) over rasterized images
- Define reusable dimensions in `dimens.xml`, colors in theme (not `colors.xml`)
- Use `stringResource()` in Compose, not `context.getString()` where avoidable
- Use **Outlined** Material icons (`Icons.Outlined.*`, `Icons.AutoMirrored.Outlined.*`) — never use
  `Icons.Default`, `Icons.Filled`, `Icons.Sharp`, or `Icons.TwoTone`

### Error Handling

- All user-facing errors must use localized strings from `strings.xml`
- Provide actionable guidance in error messages when possible
- Never expose raw exception messages to users

### Analytics & Crash Reporting

- Track user-visible screens through `AnalyticsTracker.trackScreen*`; report caught failures as
  non-fatals through `ErrorReporter`
- Event params describe a file, never identify it — extension, MIME type, source, counts. Never log
  file names, paths, or contents
- Telemetry is fire-and-forget: an unavailable or failing reporter must never surface as a failure
  of the operation being diagnosed, so never let it throw into the caller
- Firebase collection (Crashlytics and Analytics) must stay off on debug builds and emulators.
  Debug and release ship the same `applicationId`, so anything else files dev and test noise —
  including the failure paths instrumentation tests drive on purpose — into the production project
  alongside real user crashes, indistinguishable from them
- Two layers enforce that: the `crashlyticsCollectionEnabled`/`analyticsCollectionEnabled` manifest
  placeholders per build type, and the `init()` calls in `FileExplorerApplication`. A new build
  type inherits `defaultConfig`'s `true` and must set both placeholders to `false` unless it is a
  real release build
- `FirebaseCollectionTest` guards the debug build's merged manifest values; keep it passing

### Performance

- Use `LazyColumn`/`LazyRow` for all lists; never use `Column` with `forEach` for dynamic content
- Provide stable `key` parameters in lazy lists to prevent unnecessary recomposition
- Use `remember` and `derivedStateOf` to avoid redundant computations during recomposition
- Mark data classes as `@Immutable` or `@Stable` when safe to help the Compose compiler skip
  recomposition
- Run file I/O, sorting, and filtering on background dispatchers (`Dispatchers.IO`) — never block
  the main thread
- Cache thumbnails and file metadata; avoid re-reading from disk on every recomposition
- Avoid allocations in composition (no `listOf()`, `mapOf()`, or lambdas inside `remember {}` keys)
- Profile with Layout Inspector and Compose compiler reports before optimizing prematurely