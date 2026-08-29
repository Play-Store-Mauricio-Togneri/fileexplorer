### [a/resource-management/startup-routing/activity-captured-by-storages-lambda] A destroyed
`MainActivity` is retained by the abandoned startup-folder resolution

**Location:**`app/src/main/java/com/mauriciotogneri/fileexplorer/activities/MainActivity.kt:41-46` (
primary)
Related: `app/src/main/java/com/mauriciotogneri/fileexplorer/util/StartupFolderResolver.kt:56-62`

**Severity:** Low
**Confidence:** Medium

**Defect:** The `storages` lambda handed to `StartupFolderResolver` reads `applicationContext`:

```kotlin
private val startupFolderResolver by lazy {
    StartupFolderResolver(
        scope = lifecycleScope,
        storages = { StorageRepository(AndroidStorageSource(applicationContext)).getStorages() }
    )
}
```

`applicationContext` is `ContextWrapper.getApplicationContext()`, an *instance* method, so the
lambda's synthetic class captures `this$0 = MainActivity` in order to call it. The lambda therefore
holds the **Activity**, which is exactly what writing `applicationContext` was meant to avoid.
`StartupFolderResolver` stores it in a field for the life of the resolver.

This matters because the class is explicitly designed around the resolution coroutine outliving its
caller. Its own KDoc states that `resolution.cancel()` cannot stop the work — "a stat of a wedged
volume is an uninterruptible syscall... the abandoned coroutine finishes on its own thread and its
result is discarded". During that window the running IO-dispatcher thread's stack is a GC root
reaching the continuation → the resolver → the `storages` field → the lambda → the destroyed
`MainActivity`, its window, and its whole Compose tree.

**Trigger:** A startup folder is configured; its volume is spinning up or wedged so the stat blocks
past the 2 s `withTimeoutOrNull`; the user then rotates the device or leaves the app, destroying the
Activity. The Activity stays reachable for as long as the syscall blocks.

**Evidence / verification:**

- **Introduced.** `git cat-file -e d0b63a1:.../util/StartupFolderResolver.kt` → absent. Baseline
  `MainActivity.kt` is 48 lines with no resolver, no `lifecycleScope`, and no startup routing.
- The capture is unambiguous: `applicationContext` has no local, import, or Compose-local shadow
  anywhere in `MainActivity.kt`, and the `by lazy` initializer is itself a lambda over the same
  outer `this`, so nothing pre-resolves the context.
- Refutation attempt 1 — *"it is a self-referential cycle (Activity → resolver → lambda → Activity),
  which GC collects fine."* Fails. The cycle is collectable only with no external root, and during
  the stall the running IO thread's stack is exactly that root. The chain holds wherever the wedge
  occurs: if the stall is inside `StartupDestinationResolver.resolve(path, storages())` rather than
  inside `storages()`, the resolver is still the continuation's receiver.
- Refutation attempt 2 — *"something else already holds the Activity strongly, so the capture is
  incidental."* Fails, and this is what makes the capture load-bearing. `scope` is`lifecycleScope` →
  `LifecycleCoroutineScopeImpl` → `LifecycleRegistry`, which holds its owner via a `WeakReference`.
  The separate `lifecycleScope.launch { openStartupFolder(path) }` coroutine does capture the
  Activity strongly, but it completes at the 2 s timeout and is unlinked from its parent job. After
  that instant the lambda capture is the sole strong reference.
- Refutation attempt 3 — *"`lifecycleScope` cancellation frees it."* Fails by construction.
  `resolution.cancel()` is already called on every path (`StartupFolderResolver.kt:59`);
  cancellation is cooperative and the uninterruptible stall is the premise the timeout exists for.
- Refutation attempt 4 — *"the retention is transient and bounded, so it is not a defect."* Weak. It
  is bounded only by the syscall, which is the unbounded case the class was written for. One blocked
  IO thread is a cost the design knowingly accepts; dragging a destroyed Activity and its Compose
  tree along is not, and nothing in the design chose it.
- Remaining assumptions (why Confidence is Medium, not High): the mechanism is certain, but the
  retention window's length and how often a volume actually wedges past 2 s were not measured, and
  no heap dump or LeakCanary run was performed.

**Suggested fix:** Resolve the context once, outside the lambda, so only the `Application` is
captured — hoist a `val appContext = applicationContext` (or take the `Application` as a constructor
parameter) and reference that inside the lambda. `AndroidStorageSource` only needs a `Context`, so
nothing else moves and there is no behavioral change.