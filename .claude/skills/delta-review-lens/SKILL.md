---
name: delta-review-lens
description:
  Project lens for the File Explorer Android app — Kotlin, Jetpack Compose, Material 3, DataStore, shipping to Play with all-files access. Supplies the project-specific briefs — architecture, domain, security, resource parity and style — that the delta-review skill turns into reviewer agents. Not a standalone review skill: it defines no change-set collection, orchestration, severities, or fixes of its own.
---

This file is a **lens**, not a review. The `delta-review` skill reads it, takes each `##` section
below as one reviewer brief, and owns everything else — collecting the change set, spawning agents,
refuting findings, assigning severity, reporting, and fixing. Nothing here overrides that.

**`CLAUDE.md` already states the rules** — theming, localization, testing, architecture, resource
management, error handling, performance — and the parent skill hands it to every reviewer. This lens
does not restate them. It says **what breaks, where, and what to check** in this specific codebase.

**The project.** A native Android file explorer. Kotlin with Jetpack Compose and Material 3, one
Activity per screen with Compose inside, manual dependency injection (no Hilt or Koin), DataStore
Preferences for persistence, Coil for images, compose-markdown for the legal documents. `minSdk 23`,
`targetSdk 37` — a decade of Android storage behaviour in one binary. Shipped on Play (`versionName`
in `app/build.gradle.kts` is real), holding `MANAGE_EXTERNAL_STORAGE` and
`REQUEST_INSTALL_PACKAGES`.

**Layout.** `activities/` (11 activities, `MainActivity` the only exported one), `data/model`,
`data/repository`, `data/source`, `data/util`, `ui/components`, `ui/navigation`,
`ui/screens/<feature>`, `ui/theme`, `ui/util`, `util/`. Tests: 121 files under `app/src/test` (JUnit
4 style on the JUnit Platform via the vintage engine, with MockK and Turbine) and
`app/src/androidTest` (Compose UI testing, Espresso).

## Architecture

Layer boundaries, the persistence seam, and the failure modes of a Compose app that walks the
filesystem.

- **The source/repository seam is what makes this codebase testable, and it is easy to collapse.**
  Every persistence concern is an interface plus one implementation — `FavoriteFilesSource`/
  `DataStoreFavoriteFilesSource`, `PreferencesSource`/`DataStorePreferencesSource`,
  `RecentFilesSource`/`DataStoreRecentFilesSource`, `LocationsCacheSource`/
  `DataStoreLocationsCacheSource`, `StorageSource`/`AndroidStorageSource` — with a repository on
  top. A repository that reaches DataStore or `java.io.File` directly, or a new concern that ships
  only a concrete class, removes the substitution point the 121 tests depend on.
- **All DataStore access goes through `DataStoreSafeAccess`.** Its policy is deliberate and
  documented in the file: an `IOException` degrades to a no-op write or a default read and is
  reported unless the device is out of space; anything else is rethrown so real bugs are not
  swallowed. A raw `edit {}` or `.data.first()` reintroduces the crash those helpers exist to
  remove, and a widened `catch` silently buries genuine failures.
- **Composables do not touch the filesystem, MediaStore, or DataStore.** The path is composable →
  ViewModel → repository → source. A `File` call, a `ContentResolver` query, or a
  `context.dataStore` read inside a composable re-runs on every recomposition and blocks the frame.
- **One Activity per screen**, all `exported="false"` except `MainActivity`. A new screen adds an
  activity, its manifest entry, and its `ui/screens/<feature>` package; a new manifest component
  that does not explicitly set `exported` is a finding.
- **The `minSdk 23` → `targetSdk 37` spread is a correctness axis, not a formality.** Storage access
  differs across API 23–28 (legacy read/write permissions), 29 (scoped storage), and 30+ (
  `MANAGE_EXTERNAL_STORAGE`, SAF). Any filesystem or MediaStore API introduced must be checked at *
  *both** ends of that range, along with the permission model it assumes and its behaviour when the
  permission is revoked mid-session.
- **Runtime failure modes to hunt:** directory listing, sorting, filtering, size calculation, zip
  and unzip on the main thread (a folder with thousands of entries, or a directory tree several
  levels deep, is the realistic case — not the happy path); thumbnails re-decoded on every
  recomposition instead of cached; allocations inside composition; a `LazyColumn` without stable
  keys re-creating rows on every list change; and coroutines launched outside `viewModelScope` that
  outlive the screen.

**Report shape:** the screen, repository or source affected, the boundary or contract broken, and
the runtime or maintenance consequence.

## Domain

This app's domain is **irreversible operations on the user's own files**, and there is no undo.
Copy, move, delete, rename, compress, extract, share and install. A defect here does not produce a
wrong number on screen — it destroys data the user cannot get back. Weight accordingly.

- **Move is not rename.** Across volumes it must be copy, then verify, then delete the source; a
  source deleted before the copy is confirmed is unrecoverable loss, and `File.renameTo` fails
  silently across mount points on many devices.
- **Name collisions must be resolved explicitly and per file** — overwrite, rename, or skip — not
  decided once for a whole batch. An overwrite that was never confirmed is data loss.
- **Partial failure must leave a describable state.** A batch interrupted by a revoked permission, a
  full disk (`isNoSpaceLeft` is a modelled case), a vanished file, or an unmounted SD card must stop
  cleanly and report what actually happened, not report success for the whole batch.
- **Path handling is the hazard surface:** names containing separators, leading dots, spaces,
  Unicode and emoji, names at the filesystem length limit, case-insensitive collisions on FAT
  volumes, symlinks, and any path that escapes the directory the user is operating in. Extraction is
  the sharpest case — an archive entry with `../` in its name writes outside the destination unless
  the code prevents it.
- **Persisted user data is a hand-rolled format.** Favorites, recent files and the locations cache
  are serialised to JSON **by hand** into a DataStore string preference — no schema, no version, no
  library. A changed field name, a changed serialiser, or a parse that throws instead of degrading
  silently empties the user's favorites with no migration and no backup.
- **Stale references** are normal, not exceptional: a favorite or recent entry pointing at a file
  since deleted, moved, or living on storage that is currently unmounted. Every read path must
  survive it.
- **MediaStore must stay consistent** with what the app did — a file created, deleted, or moved
  without the corresponding MediaStore update leaves ghosts in the gallery and other apps.
- **Sorting, filtering and formatting are user-visible truth:** sorting must be stable and
  locale-aware, and a displayed size, date or item count must match what the operation actually did.
- **Tests are the recorded contract here.** With 121 test files and a CLAUDE.md rule that coverage
  must not decrease, an existing test that changed shape in the same diff as the behaviour it guards
  deserves the report even when the edit looks intentional.

**Report shape:** a reachable user scenario, the rule or invariant it violates, and what the user
observes — naming the data at risk when there is any.

## Security

This app holds two of the most sensitive capabilities Android grants: **`MANAGE_EXTERNAL_STORAGE`**,
which is read and write access to everything on the device, and **`REQUEST_INSTALL_PACKAGES`**,
which is the ability to install an APK. Both are Play-policy gated. Review any change that widens
what is read, shared, launched or logged as though it were a server handling other people's data —
because in effect it is.

- **APK installation must originate from an explicit user action on a file the user selected.** The
  install intent must not be reachable from an external caller, must not be constructed from an
  unvalidated path, and must not be triggered as a side effect of navigation or a preview.
- **Exported components are the front door.** `MainActivity` is the only `exported="true"`
  component. Any new activity, service, receiver or provider must be explicitly non-exported unless
  it genuinely handles an external intent — and if it does, everything in that intent is
  attacker-controlled: paths, URIs, extras and flags alike.
- **Incoming paths and URIs must be validated and confined.** A path arriving from an intent, a
  share, or an archive entry must resolve inside the directory the user is acting on, with `..`
  segments and symlinks resolved before use, and must not be able to reach app-private storage or
  another app's data.
- **Outbound sharing must grant the minimum.** A shared URI is a capability handed to another app;
  it must cover exactly the file the user picked, with a scoped, temporary grant, and must not
  expose a directory or a broader path.
- **A file explorer's paths are personal data.** File names, paths, directory listings and content
  must never reach logs, `ErrorReporter` payloads, or crash reports — the path alone tells you what
  the user has.
- **DataStore is app-private plaintext.** Correct for favorites and preferences; wrong for anything
  that would matter if it leaked or were edited on a rooted device. Values read back must be
  validated, not trusted.
- **R8 runs in release with essentially no keep rules** — `isMinifyEnabled` and `isShrinkResources`
  are both on and `proguard-rules.pro` keeps only line-number attributes. Anything reflective,
  dynamically named, or looked up by resource name works in debug and breaks only in the shipped
  build. Introducing one requires the matching keep rule in the same change.
- **Signing material stays out of the repository.** `keystore.properties` and `local.properties` are
  read by the build; neither may be committed, and neither may have its values echoed into the build
  output.

**Report shape:** the attacker capability (a hostile app sending an intent, a crafted archive, a
malicious file name), the path from that input to the effect, the asset or capability affected, and
the smallest fix.

## Resource parity

**There are two localized resource sets in this app, and `CLAUDE.md` documents only one.**

- **`values/` plus 19 `values-*` directories** — ar, bn, ca, de, el, es, fr, hi, in, it, ja, nl, pt,
  ro, ru, tr, ur, vi, zh. A new string, plural, or array must land in all twenty. A missing key
  falls back to English silently for that language, which no test catches.
- **`raw/` plus 19 `raw-*` directories** — `privacy.md` and `terms.md`, rendered through
  compose-markdown. Changing the English legal text without the other nineteen leaves users reading
  a stale privacy policy in their own language, which is a compliance problem rather than a cosmetic
  one.
- **Plurals need the quantities each language actually requires** — Russian few/many, Arabic
  zero/one/two/few/many/other, Romanian few. A `<plurals>` with only `one`/`other` is wrong in those
  locales even though it compiles.
- **Removing or renaming a string, plural, dimension, colour or drawable that is still referenced**
  breaks the build in the best case and a single locale in the worst.
- **Layout must survive 30–40% text expansion and RTL** (Arabic, Urdu). A fixed width, a single-line
  constraint, or a hardcoded start/end padding added around localized text is the usual cause.
- **`shrinkResources = true`** strips resources nothing references statically. A resource reached
  only by name at runtime disappears in release.
- **Theming parity:** Material 3 `colorScheme` only, correct in light, dark and system; tintable
  vector drawables, or both variants supplied. `material-icons-extended` is on the classpath, so
  `Icons.Default`, `Filled`, `Sharp` and `TwoTone` all compile — only `Icons.Outlined.*` and
  `Icons.AutoMirrored.Outlined.*` are allowed, and nothing but review enforces it.
- **Test parity:** new business logic in a repository, source or util arrives with a unit test, and
  a critical user flow with an instrumentation test; `CLAUDE.md` forbids letting coverage fall.
- **`versionCode` and `versionName` move together** when the change is release-affecting.

**Report shape:** the two sides that drifted, and the user-visible consequence of the drift.

## Style

Everything here is a **Nit**; if it has a behavioural consequence it belongs to Architecture or
Domain. There is no ktlint or detekt configuration in this repository — the Kotlin compiler and
Android Lint are the only automated enforcement, so the conventions below are review-enforced.

- **Match the neighbouring file.** Package placement is the convention with the most consequence:
  model, repository, source and file utilities under `data/`, reusable UI under `ui/components`,
  screen-scoped code under `ui/screens/<feature>`, cross-cutting helpers under `util/`. A
  well-written class in the wrong package is still a finding.
- **Composable conventions:** PascalCase named for what it emits, `modifier: Modifier = Modifier` as
  the first optional parameter and passed through, state hoisted rather than owned, and no side
  effects performed during composition.
- **`stringResource()` in Compose**, not `context.getString()` where it can be avoided; no
  user-facing literal in Kotlin or in a composable.
- **No hardcoded colours, dimensions or strings** — colours come from the Material 3 theme rather
  than `colors.xml`, reusable dimensions from `dimens.xml`.
- **Error messages are localized, actionable, and never a raw exception message** — that rule is in
  `CLAUDE.md` and it is also a style tell: a `catch` that surfaces `e.message` to the user is both.
- **Dead code introduced by the change**, including an unused string in twenty locales, an unused
  drawable, or a source interface with a single implementation and no test using it.

**Report shape:** `file:line`, the rule as written above, and the conforming form.
