---
name: intellij-plugin-best-practices
description: IntelliJ Platform plugin development best practices including threading, Kotlin patterns, testing, UI development, and common pitfalls
allowed-tools:
  - Read
  - Grep
  - Glob
  - WebFetch
  - WebSearch
  - Bash
---

# IntelliJ Plugin Development Best Practices

**Critical mandatory rules** for IntelliJ Platform plugin development. Violating these causes crashes, memory leaks, or IDE instability.

---

## CRITICAL: Kotlin Rules

### 1. Never Use `object` for Extensions

```kotlin
// ❌ WRONG - Breaks dependency injection
object MyAction : AnAction() { }

// ✅ CORRECT - Use class
class MyAction : AnAction() { }
```

**Why:** Extensions are managed via dependency injection. `object` singletons interfere with lifecycle management.

**Exception:** `FileType` extensions only.

### 2. Minimize Companion Objects

Companion objects slow IDE startup:

```kotlin
class MyService {
    companion object {
        // ✅ OK - Logger only
        private val LOG = Logger.getInstance(MyService::class.java)

        // ✅ OK - Constants only
        const val MAX_RETRIES = 3

        // ❌ AVOID - Heavy initialization
        val expensiveMap = loadFromDisk()  // Slows startup!
    }
}

// ✅ BETTER - Lazy initialization
class MyService {
    private val heavyMap by lazy { loadFromDisk() }
}
```

**Why:** Companion objects trigger class loading during IDE startup.

---

## CRITICAL: Threading Rules

### 1. Never Block EDT

```kotlin
// ❌ WRONG - Freezes IDE
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val result = heavyComputation()  // Blocks UI!
        showResult(result)
    }
}

// ✅ CORRECT - Background thread
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val service = e.project?.service<MyService>() ?: return
        service.scope.launch(Dispatchers.Default) {
            val result = heavyComputation()
            withContext(Dispatchers.EDT) {
                showResult(result)
            }
        }
    }
}
```

**Why:** EDT must remain responsive for UI updates. Blocking it freezes the entire IDE.

**Dispatchers:**
- `Dispatchers.EDT` - UI updates only
- `Dispatchers.Default` - CPU-bound work
- `Dispatchers.IO` - File/network I/O (use narrowly)

### 2. Never Use Application/Project.getCoroutineScope()

```kotlin
// ❌ WRONG - Deprecated, causes leaks
Application.getCoroutineScope()
Project.getCoroutineScope()

// ✅ CORRECT - Service-scoped injection
@Service(Service.Level.PROJECT)
class MyService(private val scope: CoroutineScope) {
    fun doWork() {
        scope.launch(Dispatchers.Default) {
            // Scope auto-cancelled when project closes
        }
    }
}
```

**Why:** Direct scopes cause resource leaks. Service injection ensures proper lifecycle management.

### 3. Always Use Read/Write Actions for PSI

```kotlin
// ✅ CORRECT - Reading PSI
readAction {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    val text = psiFile.text
}

// ✅ CORRECT - Modifying PSI (background thread)
scope.launch(Dispatchers.Default) {
    writeAction {
        val factory = KtPsiFactory(project)
        ktClass.addDeclaration(factory.createFunction("fun foo() {}"))
    }
}
```

**Why:** PSI requires locks. Missing actions cause exceptions or data corruption.

---

## CRITICAL: Resource Management

### Always Register Disposables

```kotlin
// ❌ WRONG - Listener never removed, causes leak
MessageBus.connect().subscribe(MyTopic.TOPIC, listener)

// ✅ CORRECT - Register with parent
val connection = MessageBus.connect(parentDisposable)
connection.subscribe(MyTopic.TOPIC, listener)
```

**Why:** Unregistered resources cause memory leaks. Parent disposables ensure cleanup.

**For services:**
```kotlin
@Service(Service.Level.PROJECT)
class MyService : Disposable {
    override fun dispose() {
        // Clean up resources
    }
}
```

---

## CRITICAL: Common Pitfalls

### 1. PSI Modifications on EDT

```kotlin
// ❌ WRONG - Deadlock/crash
ktClass.addDeclaration(factory.createFunction("fun foo() {}"))

// ✅ CORRECT
scope.launch(Dispatchers.Default) {
    writeAction {
        ktClass.addDeclaration(factory.createFunction("fun foo() {}"))
    }
}
```

### 2. Holding Project References in App Services

```kotlin
// ❌ WRONG - Memory leak
@Service(Service.Level.APP)
class MyAppService {
    private var project: Project? = null  // Leaks project!
}

// ✅ CORRECT - Use parameter
@Service(Service.Level.APP)
class MyAppService {
    fun doSomething(project: Project) {
        // Use parameter, don't store
    }
}
```

### 3. Heavy Companion Object Initialization

```kotlin
// ❌ WRONG - Slows startup
companion object {
    val heavyMap = loadFromDisk()
}

// ✅ CORRECT - Lazy
private val heavyMap by lazy { loadFromDisk() }
```

---

## Additional Resources

**For detailed examples and patterns, search:**
- Threading: https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html
- Coroutines: https://plugins.jetbrains.com/docs/intellij/coroutine-scopes.html
- Kotlin: https://plugins.jetbrains.com/docs/intellij/using-kotlin.html
- Disposers: https://plugins.jetbrains.com/docs/intellij/disposers.html
- Testing: https://plugins.jetbrains.com/docs/intellij/testing-plugins.html
- UI DSL: https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html
- Services: https://plugins.jetbrains.com/docs/intellij/plugin-services.html
- State: https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html

**Community:**
- Slack: https://plugins.jetbrains.com/slack/
- Stack Overflow: [intellij-plugin] tag