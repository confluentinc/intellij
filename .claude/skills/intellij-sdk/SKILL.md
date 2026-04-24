---
name: intellij-sdk
description:
  IntelliJ Platform SDK lookup for plugin development. Use when the user asks about IntelliJ Platform
  APIs, extension points, threading rules, PSI, VFS, services, UI components, or how to implement
  plugin features. Triggers on questions like "how do I create a tool window", "what's the API for X",
  "IntelliJ threading rules", or checking platform API compatibility.
user-invocable: false
allowed-tools:
  - Read
  - Grep
  - Glob
  - WebFetch
  - WebSearch
---

# IntelliJ Platform SDK Lookup

This skill dynamically looks up IntelliJ Platform SDK documentation and APIs for plugin development.

## Sources

### 1. IntelliJ Platform SDK Documentation

The official SDK docs at `https://plugins.jetbrains.com/docs/intellij/` are the primary reference.
URLs use hyphenated topic names:

```
https://plugins.jetbrains.com/docs/intellij/{topic-name}.html
```

#### Documentation Map

| Section                | Entry Point                      | Use For                                             |
| ---------------------- | -------------------------------- | --------------------------------------------------- |
| Fundamentals           | `fundamentals.html`              | Threading, messaging, coroutines, disposables       |
| Action System          | `action-system.html`             | Actions, menus, toolbars, keyboard shortcuts        |
| User Interface         | `user-interface-components.html` | Tool windows, dialogs, popups, notifications        |
| UI DSL (Kotlin)        | `kotlin-ui-dsl-version-2.html`   | Declarative UI building with Kotlin DSL             |
| Persistence            | `persistence.html`               | Settings, state, storage                            |
| Virtual File System    | `virtual-file-system.html`       | File access, VFS events, file types                 |
| Editors                | `editors.html`                   | Editor API, documents, carets, selections           |
| PSI                    | `psi.html`                       | Program Structure Interface, code analysis          |
| Services               | `plugin-services.html`           | Service lifecycle, scopes, dependency injection     |
| Threading              | `general-threading-rules.html`   | Read/write locks, EDT, background tasks             |
| Coroutine Scopes       | `coroutine-scopes.html`          | Service scopes, intersection scopes                 |
| Coroutine Dispatchers  | `coroutine-dispatchers.html`     | EDT, Default, IO dispatchers                        |
| Coroutine Read Actions | `coroutine-read-actions.html`    | Read/write actions in coroutine context             |
| Testing                | `testing-plugins.html`           | Test framework, fixtures, light vs heavy tests      |
| Extension Points       | `plugin-extensions.html`         | Declaring and using extension points                |
| Dynamic Plugins        | `dynamic-plugins.html`           | Hot-reloading, dynamic loading constraints          |
| Notifications          | `notifications.html`             | Notification groups, balloons, sticky notifications |
| Tool Windows           | `tool-windows.html`              | Creating and managing tool windows                  |
| Run Configurations     | `run-configurations.html`        | Custom run/debug configurations                     |
| Settings               | `settings.html`                  | Configurable settings pages                         |
| API Changes            | `api-notable.html`               | Breaking changes, deprecations by version           |

### 2. IntelliJ Platform Explorer

Search for real-world usage examples of extension points and APIs across open-source plugins:

```
https://plugins.jetbrains.com/intellij-platform-explorer/
```

Use WebSearch to find specific examples:

```
site:plugins.jetbrains.com intellij-platform-explorer {extension-point-or-API}
```

### 3. IntelliJ Community Source (GitHub)

The platform source code on GitHub is useful for understanding API contracts and finding interfaces:

```
https://github.com/JetBrains/intellij-community/tree/master/platform/
```

Key source packages:

- `platform/platform-api/src/` - Public platform APIs
- `platform/editor-ui-api/src/` - Editor APIs
- `platform/core-api/src/` - Core APIs (PSI, VFS, projects)
- `platform/ide-core/src/` - IDE core interfaces

To look up a specific class or interface on GitHub:

```
https://github.com/search?q=repo%3AJetBrains%2Fintellij-community+path%3Aplatform+filename%3A{ClassName}.java&type=code
```

### 4. Critical Rules (Supporting File)

For threading rules, PSI safety, Kotlin pitfalls, and other rules that must never be violated, see
[critical-rules.md](critical-rules.md). **Always consult this file when generating code that involves
threading, PSI access, VFS listeners, or service definitions.**

## Process

### 1. Determine the Project's Target Platform Version

Read the project's `build.gradle.kts` and `plugin.xml` to find the target platform:

```bash
grep "since-build" resources/META-INF/plugin.xml
grep "intellij.platform" build.gradle.kts
```

The `since-build` value (e.g., `253.28294`) maps to an IDE version (253 = 2025.3). This determines
which APIs are available.

### 2. Choose the Right Source

**Use SDK documentation when:**

- Learning how an API works, its lifecycle, or intended usage patterns
- Understanding threading requirements for a specific subsystem
- Looking for implementation guides with step-by-step instructions
- Evaluating which extension point to use for a feature

**Use GitHub source when:**

- Need the exact method signature, parameters, or return type
- Checking if a class/method exists in the target platform version
- Understanding the API contract (Javadoc on interfaces)
- Finding all implementors of an interface

**Use Platform Explorer when:**

- Want real-world examples of how other plugins use an API
- Need to see how an extension point is typically implemented
- Looking for patterns and conventions

**Use critical-rules.md when:**

- Writing any code that touches threading, EDT, or background tasks
- Working with PSI (read/write actions, smart pointers)
- Defining services or extension points
- Setting up VFS listeners or message bus subscriptions

### 3. Fetch Documentation Pages

Use WebFetch to retrieve SDK documentation:

```
https://plugins.jetbrains.com/docs/intellij/{topic-name}.html
```

**Navigation strategy:**

1. Start with the most relevant topic from the Documentation Map above
2. Look for links to sub-pages in the fetched content
3. Follow links to specific subsections based on the user's question
4. Cross-reference with critical-rules.md for safety constraints

### 4. Search for Specific APIs

When the user asks about a specific API (e.g., `ToolWindow`, `AnAction`, `PersistentStateComponent`):

1. Identify the relevant documentation topic from the map above
2. Fetch that documentation page for usage guidance
3. If the exact type signature is needed, search GitHub:
   - Use WebSearch: `site:github.com/JetBrains/intellij-community {ClassName} path:platform`
   - Or fetch the source file directly if the path is known
4. Read critical-rules.md for any applicable safety rules
5. Present the API with both usage guidance and safety constraints

### 5. Check API Compatibility

When the user needs to verify an API works with their target platform:

1. Determine the target platform version from step 1
2. Check the API notable changes page for deprecations:
   ```
   https://plugins.jetbrains.com/docs/intellij/api-notable.html
   ```
3. Use WebSearch for version-specific changes:
   `site:plugins.jetbrains.com "api notable" {API-name}`

## Output Format

### API Lookup

```
## API: [name]

### Usage
[Summary from SDK docs - what it does, when to use it]

### Implementation
[Code example from docs or Platform Explorer]

### Threading/Safety
[Relevant rules from critical-rules.md]

### Documentation
- SDK: [link to docs page]
- Source: [link to GitHub source if applicable]
```

### Extension Point Lookup

```
## Extension Point: [name]

### Purpose
[What this EP enables]

### Registration (plugin.xml)
[XML snippet]

### Implementation
[Kotlin implementation example]

### Documentation
- SDK: [link]
- Platform Explorer: [link to examples]
```

## Tips

- SDK doc pages are rendered from markdown; some pages use JavaScript rendering and may return
  limited content via WebFetch. Fall back to WebSearch if a page returns empty.
- The GitHub source is the ground truth for API signatures. Prefer it over docs when precision
  matters.
- Always check critical-rules.md before providing code that involves threading, PSI, or services.
  These rules prevent common bugs that are hard to diagnose.
- The Platform Explorer shows real plugins' source code. Use it to validate that your recommended
  approach matches how production plugins actually work.
- When in doubt about API stability, check `@ApiStatus.Internal` and `@ApiStatus.Experimental`
  annotations in the source. Internal APIs can change without notice.
