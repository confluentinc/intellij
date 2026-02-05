---
name: intellij-plugin-sdk-reference
description: IntelliJ Platform SDK API reference, documentation lookup, and core platform concepts (PSI, Project Model, Extensions)
allowed-tools:
  - Read
  - Grep
  - Glob
  - WebFetch
  - WebSearch
  - Bash
---

# IntelliJ SDK Reference

Critical SDK concepts and how to find detailed API documentation.

## Finding API Documentation

**When you need detailed API information, search:**
1. **Official Docs:** https://plugins.jetbrains.com/docs/intellij/
2. **Platform Explorer:** https://plugins.jetbrains.com/intellij-platform-explorer/
3. **GitHub source:** https://github.com/JetBrains/intellij-community
---

## plugin.xml Required Fields

**These fields are MANDATORY - plugin fails to load without them:**

```xml
<idea-plugin>
    <id>com.example.myplugin</id>                    <!-- Unique identifier -->
    <name>My Plugin Name</name>                      <!-- Display name -->
    <version>1.0.0</version>                         <!-- Semantic version -->
    <vendor>Company/Author</vendor>                  <!-- Vendor name -->
    <description>Plugin description</description>    <!-- Marketplace description -->

    <!-- IDE version compatibility - since-build is REQUIRED -->
    <idea-version since-build="253.28294" until-build="253.*"/>

    <!-- Platform dependency -->
    <depends>com.intellij.modules.platform</depends>
</idea-plugin>
```

**Why critical:** Missing required fields prevents plugin from loading in IDE.

**Search for details:** https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html

---

## Core Concepts

### Project Model
All SDK operations need a project context:
- **Project** - Root organizational unit
- **Module** - Independent unit of functionality
- **Library** - JAR dependencies (Module/Project/Global)
- **SDK** - Build environment (e.g., JDK)
- **Facet** - Framework-specific configurations

**Search for details:** https://plugins.jetbrains.com/docs/intellij/project-model.html

---

## PSI (Program Structure Interface)

PSI parses files and creates code models. **Foundation for all code analysis/manipulation.**

### CRITICAL RULES

**1. Never Modify PSI on EDT:**
```kotlin
// ✅ CORRECT - Background thread + write action
scope.launch(Dispatchers.Default) {
    writeAction {
        val factory = KtPsiFactory(project)
        ktClass.addDeclaration(factory.createFunction("fun foo() {}"))
    }
}

// ❌ WRONG - On EDT, will deadlock/crash
ktClass.addDeclaration(factory.createFunction("fun foo() {}"))
```

**2. Always Use Read Actions:**
```kotlin
// ✅ CORRECT
readAction {
    val text = psiFile.text
}

// ❌ WRONG - No read lock
val text = psiFile.text  // Can throw exceptions
```

**3. Use Smart Pointers:**
PSI elements become invalid after modifications. Always use smart pointers:
```kotlin
val pointer = SmartPointerManager.createPointer(psiElement)
// Later, after modifications...
val element = pointer.element  // May be null if invalidated
```

**Why critical:** Violating these rules causes IDE deadlocks, crashes, or data corruption.

**Search for PSI patterns:** https://plugins.jetbrains.com/docs/intellij/psi.html

---

## Virtual File System (VFS)

VFS provides file system abstraction and change tracking.

### CRITICAL RULES

**1. Never Refresh While Holding Read Lock:**
```kotlin
// ❌ WRONG - Deadlock!
readAction {
    virtualFile.refresh(async = false, recursive = true)  // Deadlock!
}

// ✅ CORRECT - Release read lock first
virtualFile.refresh(async = true, recursive = true)
```

**2. VFS Events Fire on EDT in Write Action:**
VFS listeners receive events on EDT within write action. Keep listeners fast or dispatch to background.

```kotlin
connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        // ⚠️ This runs on EDT in write action - keep it fast!
        events.forEach { event -> /* quick processing only */ }
    }
})
```

**Why critical:** Violating these rules causes deadlocks. VFS refresh with read lock is a guaranteed deadlock.

**Search for VFS details:** https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html

---

## Resources

**Documentation:**
- Plugin SDK: https://plugins.jetbrains.com/docs/intellij/welcome.html
- API Changes: https://plugins.jetbrains.com/docs/intellij/api-notable.html
- Threading: https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html
