# Critical Rules for IntelliJ Plugin Development

Rules that must never be violated. Consult this file before generating code involving threading,
PSI, VFS, services, or Kotlin extension points.

## Threading

1. **Never block EDT** — Use `scope.launch(Dispatchers.Default)` for heavy work,
   `withContext(Dispatchers.EDT)` for UI updates
2. **Never use `Application/Project.getCoroutineScope()`** — Inject `CoroutineScope` via service
   constructor parameter (auto-injected, auto-cancelled)
3. **Always use read/write actions for PSI** — Wrap reads in `readAction {}`, modifications in
   `writeAction {}` on background threads
4. **Never refresh VFS while holding read lock** — Use `virtualFile.refresh(async = true)` outside
   read actions
5. **Keep VFS listeners fast** — They run on EDT in write action; dispatch heavy work to background

**Dispatchers:**

- `Dispatchers.EDT` — UI updates only (prefer over `Dispatchers.Main`)
- `Dispatchers.Default` — CPU-bound work
- `Dispatchers.IO` — File/network I/O (use sparingly, right before actual I/O)

**Coroutine scope pattern:**

```kotlin
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,
    private val scope: CoroutineScope  // Auto-injected, auto-cancelled
) {
    fun doWork() = scope.launch(Dispatchers.Default) {
        val result = computeExpensiveThing()
        withContext(Dispatchers.EDT) {
            updateUI(result)
        }
    }
}
```

## PSI Safety

1. **PSI reads require read action** — `readAction { psiFile.text }`
2. **PSI modifications require write action on background thread** —
   `scope.launch(Dispatchers.Default) { writeAction { ... } }`
3. **Never hold PSI references across modifications** — Use smart pointers:
   `SmartPointerManager.createPointer(element)`, then `pointer.element` (may be null)
4. **Document changes need WriteCommandAction** —
   `WriteCommandAction.runWriteCommandAction(project) { document.setText(...) }`

## Kotlin Extension Rules

1. **Never use `object` for extensions** — Always use `class` (exception: `FileType` subclasses)
2. **Minimize companion objects** — Heavy initialization in companions slows IDE startup
3. **Only constants/loggers in companions** — Instance fields for everything else
4. **Use `lazy` for expensive initialization** — Constructors block IDE startup

## Services

1. **Never store `Project` references in app-level services** — Memory leak; pass as parameter
2. **Match service scope to state** — App-level state → `Service.Level.APP`,
   project-specific → `Service.Level.PROJECT`
3. **Make services `Disposable` if they register listeners/resources**
4. **Inject scope, don't fetch it** — Constructor parameter, not `project.getCoroutineScope()`

## Resources & Disposables

1. **Always register disposables with a parent** — `Disposer.register(parent, child)`
2. **MessageBus connections need a parent** — `messageBus.connect(parentDisposable)`
3. **Service scope is auto-cancelled** — Don't manually cancel injected `CoroutineScope`

## Common Pitfalls

| Pitfall                      | Symptom                             | Fix                                |
| ---------------------------- | ----------------------------------- | ---------------------------------- |
| Blocking EDT                 | UI freezes, "EDT frozen" errors     | Move work to `Dispatchers.Default` |
| App service holds `Project`  | Memory leak after project close     | Pass `Project` as method parameter |
| Heavy service constructor    | Slow IDE startup                    | Use `lazy` delegation              |
| Missing `@TestApplication`   | `NullPointerException` in tests     | Add annotation to test class       |
| PSI ref after modification   | `InvalidVirtualFileAccessException` | Use `SmartPointerManager`          |
| VFS refresh in read action   | Deadlock                            | Refresh outside read action        |
| `object` for extension class | Plugin fails to unload dynamically  | Use `class` instead                |

## API Stability

- **Never use `Impl` classes** — Only use public API interfaces
- **Avoid `@ApiStatus.Internal`** — Can change without notice between minor versions
- **Check `@ApiStatus.Experimental`** — May change in future releases
- **Check API notable changes** before targeting a new platform version
