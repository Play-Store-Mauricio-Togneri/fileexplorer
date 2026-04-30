# Critical Instructions

## Planning

- **Plan & Pause**: Propose a clear, step-by-step plan before making any code changes. Stop after planning and proceed only after I verify.
- **Ambiguity Handling**: If any requirement is ambiguous, risky, or unclear, state your assumptions explicitly and ask for confirmation before proceeding.

## Rules

- **Strict Scope**: Do not add features, refactor, or reorganize beyond what was explicitly requested.
- **Code Review**: After completing code changes, run `/sway-android:delta-review` before responding.
- **Maintenance**: Keep `CLAUDE.md` up to date after changes.

---

## Migration Progress

### Completed Phases

#### Phase 1: Project Setup (COMPLETED)
- [x] Gradle configured with Kotlin DSL and version catalog
- [x] Dependencies added: Navigation, ViewModel, Coroutines, DataStore, kotlinx.serialization, Firebase, Coil
- [x] Firebase configured (google-services.json)
- [x] Material 3 theme with dark mode support (ThemeMode enum)
- [x] FileProvider configured (provider_paths.xml)
- [x] String resources copied (7 locales: en, de, es, fr, pt, el, tr)
- [x] AndroidManifest updated with permissions (MANAGE_EXTERNAL_STORAGE, legacy storage permissions)
- [x] ProGuard rules configured for serialization and Firebase

### Next Phase

**Phase 2: Core Data Layer**
- [ ] Create FileItem data class
- [ ] Create StorageDevice data class
- [ ] Create Clipboard data class and ClipboardManager
- [ ] Implement FileRepository
- [ ] Implement StorageRepository
- [ ] Implement MimeTypeUtil
- [ ] Implement FileSizeFormatter
