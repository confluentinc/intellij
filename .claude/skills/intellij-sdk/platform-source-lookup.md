# IntelliJ Platform Source Lookup

Detailed reference for finding IntelliJ Platform source code on GitHub. The summary version of this
guidance lives in `CLAUDE.md` (which reaches all agent types including Explore); this file provides
the comprehensive reference for the main conversation.

## Version-Pinned GitHub Lookup

The correct branch to use is derived from the `since-build` value in `resources/META-INF/plugin.xml`:

```xml
<idea-version since-build="253.28294"/>
```

Take the first 3 digits → branch `253`. This maps to IntelliJ IDEA 2025.3.

**Base URL pattern:**

```
https://github.com/JetBrains/intellij-community/blob/253/platform/{module}/src/com/intellij/{package-path}/{ClassName}.java
```

## Class → Module Mapping

### platform/platform-api/src/

Public UI and action APIs — the most commonly referenced module:

| Package prefix          | Contains                                                       |
| ----------------------- | -------------------------------------------------------------- |
| `openapi/ui/`           | MasterDetailsComponent, JBList, JBTable, JBTabbedPane, popups  |
| `openapi/actionSystem/` | AnAction, ActionGroup, ActionManager, PopupHandler, DataKey    |
| `openapi/wm/`           | ToolWindow, ToolWindowManager, StatusBar, WindowManager        |
| `openapi/options/`      | Configurable, SearchableConfigurable (settings pages)          |
| `openapi/fileChooser/`  | FileChooser, FileChooserDescriptor                             |
| `openapi/ui/popup/`     | JBPopup, JBPopupFactory, ListPopup                             |
| `ui/`                   | Various UI utilities, ColoredTreeCellRenderer, SimpleTextAttrs |

### platform/core-api/src/

Core abstractions for projects, files, and the PSI model:

| Package prefix        | Contains                                |
| --------------------- | --------------------------------------- |
| `openapi/project/`    | Project, ProjectManager                 |
| `openapi/vfs/`        | VirtualFile, VirtualFileSystem, VfsUtil |
| `openapi/module/`     | Module, ModuleManager                   |
| `openapi/roots/`      | ProjectRootManager, ContentEntry        |
| `psi/`                | PsiElement, PsiFile, PsiManager         |
| `openapi/util/`       | Key, UserDataHolder, TextRange          |
| `openapi/extensions/` | ExtensionPointName, PluginDescriptor    |

### platform/editor-ui-api/src/

Editor-related APIs:

| Package prefix        | Contains                                     |
| --------------------- | -------------------------------------------- |
| `openapi/editor/`     | Editor, Document, CaretModel, ScrollingModel |
| `openapi/fileEditor/` | FileEditorManager, FileEditor                |

### platform/ide-core/src/

IDE-level interfaces:

| Package prefix         | Contains                           |
| ---------------------- | ---------------------------------- |
| `openapi/application/` | Application, ApplicationManager    |
| `openapi/progress/`    | ProgressIndicator, ProgressManager |
| `openapi/fileTypes/`   | FileType, FileTypeManager          |
| `notification/`        | Notification, NotificationGroup    |

### platform/platform-impl/src/

Implementation classes (avoid depending on these — they're internal):

| Package prefix     | Contains                              |
| ------------------ | ------------------------------------- |
| `openapi/wm/impl/` | ToolWindowImpl, ToolWindowManagerImpl |
| `ui/`              | Internal UI implementation details    |

## Worked Examples

### Looking up MasterDetailsComponent

1. Class is in `com.intellij.openapi.ui` → `openapi/ui/` → module `platform-api`
2. URL: `https://github.com/JetBrains/intellij-community/blob/253/platform/platform-api/src/com/intellij/openapi/ui/MasterDetailsComponent.java`

### Looking up AnAction

1. Class is in `com.intellij.openapi.actionSystem` → `openapi/actionSystem/` → module `platform-api`
2. URL: `https://github.com/JetBrains/intellij-community/blob/253/platform/platform-api/src/com/intellij/openapi/actionSystem/AnAction.java`

### Looking up ToolWindow

1. Interface in `com.intellij.openapi.wm` → `openapi/wm/` → module `platform-api`
2. URL: `https://github.com/JetBrains/intellij-community/blob/253/platform/platform-api/src/com/intellij/openapi/wm/ToolWindow.java`

## When GitHub Doesn't Have the Class

Some classes come from bundled plugins or third-party libraries, not `intellij-community`:

- **Bundled plugin classes** (e.g., from Git, Database Tools, Kotlin plugin) — search in the
  `JetBrains/intellij-community` repo under `plugins/` or check other JetBrains repos
- **Third-party libraries bundled in the IDE** — use WebSearch to find the upstream repo
- **`com.jetbrains.*` classes** — some are in `intellij-community`, others in the proprietary
  `intellij-ultimate` repo (not public). Use WebSearch as fallback.

## What NOT to Do

- **Never search `~/.gradle/caches/`** for platform source code
- **Never `unzip`, `jar tf`, or `javap`** platform JARs — this produces compiled bytecode, not
  readable source, and wastes significant time and context window
- **Never use `find` or `ls` on Gradle cache directories** — even listing them is wasteful
- **Never decompile** when the source is freely available on GitHub
