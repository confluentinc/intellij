---
name: intellij-plugin-sdk-reference
description: IntelliJ Platform SDK reference - critical rules, common pitfalls, and core concepts (threading, PSI, VFS, services)
allowed-tools:
  - Read
  - Grep
  - Glob
  - WebFetch
  - WebSearch
---

# IntelliJ Platform SDK Reference

Critical rules and core concepts for IntelliJ plugin development.

## Critical Rules (Never Violate These)

### Threading

1. **Never block EDT** - Use `scope.launch(Dispatchers.Default)` for heavy work, `withContext(Dispatchers.EDT)` for UI updates
2. **Never use `Application/Project.getCoroutineScope()`** - Inject `CoroutineScope` into service constructor
3. **Always use read/write actions for PSI** - Wrap reads in `readAction {}`, modifications in `writeAction {}` on background thread
4. **Never refresh VFS while holding read lock** - Use `virtualFile.refresh(async = true)` outside read actions
5. **Keep VFS listeners fast** - Run on EDT in write action; dispatch heavy work to background

**Dispatchers:**
- `Dispatchers.EDT` - UI updates only
- `Dispatchers.Default` - CPU-bound work
- `Dispatchers.IO` - File/network I/O (use sparingly)

### Kotlin

1. **Never use `object` for extensions** - Use `class` (except `FileType`)
2. **Minimize companion objects** - Heavy initialization slows startup; use `lazy`
3. **Only constants/loggers in companions** - Everything else goes in instance fields

### Resources

1. **Always register disposables** - `MessageBus.connect(parentDisposable)`, `Disposer.register(parent, child)`
2. **Use smart pointers for PSI** - `SmartPointerManager.createPointer(element)` survives modifications
3. **Make services Disposable if needed** - If registering listeners/resources

### Common Pitfalls

1. **App services storing Project refs** - Memory leak; use parameters instead
2. **Wrong service scope** - App-level state → APP service, project-specific → PROJECT service
3. **Heavy constructors** - Use `lazy`; constructors block IDE startup
4. **Missing `@TestApplication`** - Required for tests using Platform APIs
5. **Holding PSI references across modifications** - Use smart pointers

## Core Concepts

### Services

```kotlin
// Application-scoped (singleton)
@Service(Service.Level.APP)
class MyAppService {
    companion object {
        fun getInstance() = service<MyAppService>()
    }
}

// Project-scoped (one per project)
@Service(Service.Level.PROJECT)
class MyProjectService(
    private val project: Project,
    private val scope: CoroutineScope  // Auto-injected, auto-cancelled
) {
    companion object {
        fun getInstance(project: Project) = project.service<MyProjectService>()
    }
}
```

### PSI (Program Structure Interface)

Code parsing and manipulation - **foundation for all code analysis**.

```kotlin
// Reading PSI (always in read action)
readAction {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    val text = psiFile.text
}

// Modifying PSI (background thread + write action)
scope.launch(Dispatchers.Default) {
    writeAction {
        val factory = KtPsiFactory(project)
        ktClass.addDeclaration(factory.createFunction("fun foo() {}"))
    }
}

// Smart pointers (survive modifications)
val pointer = SmartPointerManager.createPointer(psiElement)
// Later...
pointer.element?.navigate()  // Safe, may be null
```

### Virtual File System (VFS)

Platform file system abstraction with change tracking.

```kotlin
// Finding files
val file = LocalFileSystem.getInstance().findFileByPath("/path/to/file")
val projectFile = project.baseDir.findFileByRelativePath("src/Main.kt")

// Listening to changes
connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        // ⚠️ Runs on EDT in write action - keep fast!
        scope.launch(Dispatchers.Default) {
            events.forEach { processEvent(it) }
        }
    }
})
```

### Project Model

- `Project` - Root organizational unit
- `Module` - Independent functionality unit
- `VirtualFile` - Platform file abstraction (not `java.io.File`)
- `PsiFile` - Parsed file representation (AST)

### plugin.xml

Required fields:

```xml
<idea-plugin>
    <id>com.example.myplugin</id>
    <name>My Plugin</name>
    <version>1.0.0</version>
    <vendor>Author</vendor>
    <description>Description</description>
    <idea-version since-build="253.28294" until-build="253.*"/>
    <depends>com.intellij.modules.platform</depends>
</idea-plugin>
```

## Exploring the Platform

### Finding APIs

1. **Extension Points** - Autocomplete in `plugin.xml` `<extensions>` blocks shows available EPs
2. **Platform Explorer** - Search for real-world examples of API usage
3. **Find Usages** - Navigate to Manager/Service classes, find usages to see how they're used
4. **Package Contents** - Explore `com.intellij.openapi.*` packages for related functionality

### Debugging Entry Points

Set strategic breakpoints to find code responsible for features:
- `DocumentImpl.changedUpdate()` - Document modifications
- `HighlightInfoHolder.add()` - Code highlighting
- `AnAction.actionPerformed()` - Action execution

### Development Tools

Enable **Internal Mode** (`Help > Edit Custom Properties` → add `idea.is.internal=true`):
- **UI Inspector** (`Tools > Internal Actions > UI > UI Inspector`) - Inspect any UI component
- **PSI Viewer** (`Tools > View PSI Structure`) - Visualize PSI tree
- **Internal Actions** - Access platform debugging tools

### Important Rules

- **Never use `Impl` classes** - Only use public APIs
- **Avoid `@ApiStatus.Internal`** - Internal APIs can change without notice
- **Check API Changes** - https://plugins.jetbrains.com/docs/intellij/api-notable.html

## Documentation

**Core docs:**
- SDK: https://plugins.jetbrains.com/docs/intellij/welcome.html
- Threading: https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html
- Coroutines: https://plugins.jetbrains.com/docs/intellij/coroutine-scopes.html
- PSI: https://plugins.jetbrains.com/docs/intellij/psi.html
- VFS: https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html

**Lookup:**
- Platform Explorer: https://plugins.jetbrains.com/intellij-platform-explorer/
- GitHub: https://github.com/JetBrains/intellij-community
- API Changes: https://plugins.jetbrains.com/docs/intellij/api-notable.html

**Packages:**
- `com.intellij.openapi.*` - Core platform
- `com.intellij.psi.*` - PSI APIs
- `com.intellij.ui.*` - UI components
- `com.intellij.execution.*` - Run configs