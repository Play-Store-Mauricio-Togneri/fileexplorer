# Critical Instructions

## Planning

- **Plan & Pause**: Propose a clear, step-by-step plan before making any code changes. Stop after
  planning and proceed only after I verify.
- **Ambiguity Handling**: If any requirement is ambiguous, risky, or unclear, state your assumptions
  explicitly and ask for confirmation before proceeding.

## Rules

- **Strict Scope**: Do not add features, refactor, or reorganize beyond what was explicitly
  requested.
- **Code Review**: After completing code changes, run `/delta-review` before responding.
- **Maintenance**: Keep `CLAUDE.md` up to date after changes.

## Development Standards

### Theming (Dark/Light)

- Use Material3 theme attributes (`MaterialTheme.colorScheme.*`) — never hardcode colors
- All UI must render correctly in LIGHT, DARK, and SYSTEM theme modes
- Use tintable vector drawables; if a drawable needs theme variants, provide both
- Test new UI in both themes before completing work

### Testing

- **Unit tests** (`app/src/test/`): Required for all business logic (ViewModels, repositories, use
  cases, utilities)
- **Instrumentation tests** (`app/src/androidTest/`): Required for critical user flows and Compose
  UI
- New code must not decrease overall test coverage
- Use JUnit 4 + Compose UI Testing; add Mockk if mocking is needed

### Localization

- Never hardcode user-facing strings — use `strings.xml` resources
- When adding a new string, add translations to ALL supported languages:
    - `values/` (English - default)
    - `values-de/` (German)
    - `values-el/` (Greek)
    - `values-es/` (Spanish)
    - `values-fr/` (French)
    - `values-pt/` (Portuguese)
    - `values-tr/` (Turkish)
- Use `<plurals>` for quantity-dependent text
- Consider text expansion (~30-40%) when designing layouts

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