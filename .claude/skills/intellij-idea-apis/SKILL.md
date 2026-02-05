---
name: intellij-idea-apis
description: IntelliJ IDEA Platform API usage patterns - Actions, Editor, UI, notifications, and common tasks
allowed-tools:
  - Read
  - Grep
  - Glob
  - WebFetch
  - WebSearch
---

# IntelliJ IDEA Platform APIs

Specific API usage patterns for common tasks.

## Actions

Add menu items, toolbar buttons, keyboard shortcuts.

```kotlin
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // Perform action
    }

    override fun update(e: AnActionEvent) {
        // Control when action is enabled/visible
        e.presentation.isEnabled = e.project != null
    }
}
```

**Register in plugin.xml:**
```xml
<actions>
    <action id="MyAction" class="com.example.MyAction" text="My Action">
        <add-to-group group-id="ToolsMenu" anchor="first"/>
        <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt M"/>
    </action>
</actions>
```

**Docs:** https://plugins.jetbrains.com/docs/intellij/basic-action-system.html

## Editor

```kotlin
// Get editor
val editor = e.getData(CommonDataKeys.EDITOR)
val editor = FileEditorManager.getInstance(project).selectedTextEditor

// Document operations (requires write action)
val document = editor.document
WriteCommandAction.runWriteCommandAction(project) {
    document.setText("new content")
    document.insertString(offset, "text")
}

// Caret and selection
val caret = editor.caretModel.primaryCaret
val offset = caret.offset
caret.moveToOffset(newOffset)

val selectedText = editor.selectionModel.selectedText
editor.selectionModel.setSelection(startOffset, endOffset)
```

**Docs:** https://plugins.jetbrains.com/docs/intellij/editors.html

## PSI Navigation

```kotlin
// Get PSI from editor
val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)

// Navigate PSI tree
psiFile.accept(object : PsiRecursiveElementVisitor() {
    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        // Process element
    }
})

// Find elements
val ktFile = psiFile as? KtFile
val classes = ktFile?.declarations?.filterIsInstance<KtClass>()
val elements = PsiTreeUtil.collectElementsOfType(psiFile, KtClass::class.java)

// Element at caret
val element = psiFile.findElementAt(editor.caretModel.offset)
```

## Tool Windows

```kotlin
class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = toolWindow.contentManager.factory.createContent(
            MyToolWindowPanel(project),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
}
```

**Register:**
```xml
<extensions defaultExtensionNs="com.intellij">
    <toolWindow id="MyToolWindow" anchor="right"
                factoryClass="com.example.MyToolWindowFactory"/>
</extensions>
```

**Docs:** https://plugins.jetbrains.com/docs/intellij/tool-windows.html

## Dialogs

```kotlin
class MyDialog(project: Project) : DialogWrapper(project) {
    init {
        init()
        title = "My Dialog"
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Label:") {
                textField()
                    .bindText(::myProperty.toMutableProperty())
            }
        }
    }
}

// Show
if (MyDialog(project).showAndGet()) {
    // User clicked OK
}
```

**UI DSL:** https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html

## Notifications

```kotlin
// Simple
Notifications.Bus.notify(
    Notification(
        "MyNotificationGroup",
        "Title",
        "Message",
        NotificationType.INFORMATION
    ),
    project
)

// With actions
Notification("GroupId", "Title", "Message", NotificationType.WARNING)
    .addAction(NotificationAction.createSimple("Fix") {
        // Handle click
    })
    .notify(project)
```

**Docs:** https://plugins.jetbrains.com/docs/intellij/notifications.html

## Persistent State

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "MySettings",
    storages = [Storage("my-settings.xml")]
)
class MySettings : PersistentStateComponent<MySettings.State> {
    data class State(
        var myString: String = "",
        var myInt: Int = 0
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    companion object {
        fun getInstance(project: Project) = project.service<MySettings>()
    }
}

// Usage - auto-saved
MySettings.getInstance(project).state.myString = "value"
```

**Docs:** https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html

## Message Bus

```kotlin
// Define topic
interface MyListener {
    fun onEvent(data: String)
}

object MyTopic {
    val TOPIC = Topic.create("MyTopic", MyListener::class.java)
}

// Subscribe
connection.subscribe(MyTopic.TOPIC, object : MyListener {
    override fun onEvent(data: String) { println("Received: $data") }
})

// Publish
project.messageBus.syncPublisher(MyTopic.TOPIC).onEvent("Hello")
```

**Docs:** https://plugins.jetbrains.com/docs/intellij/messaging-infrastructure.html

## Finding Files

```kotlin
// By path
val file = LocalFileSystem.getInstance().findFileByPath("/absolute/path")

// Relative to project
val file = project.baseDir.findFileByRelativePath("src/Main.kt")

// Iterate project files
ProjectFileIndex.getInstance(project).iterateContent { virtualFile ->
    // Process each file
    true  // continue
}

// Find by name
FilenameIndex.getVirtualFilesByName(
    "MyFile.kt",
    GlobalSearchScope.projectScope(project)
)
```

## Common Tasks

### Background task with progress

```kotlin
ProgressManager.getInstance().run(
    object : Task.Backgroundable(project, "Task Title") {
        override fun run(indicator: ProgressIndicator) {
            indicator.text = "Processing..."
            indicator.fraction = 0.5
            // Do work
        }
    }
)
```

### Balloon notification

```kotlin
JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder("Message", MessageType.INFO, null)
    .createBalloon()
    .show(RelativePoint.getCenterOf(component), Balloon.Position.above)
```

### Navigate to file

```kotlin
val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return
FileEditorManager.getInstance(project).openFile(file, true)
```

### Run configurations

```kotlin
class MyConfigurationType : ConfigurationType {
    override fun getDisplayName() = "My Run Config"
    override fun getId() = "MY_RUN_CONFIG"
    override fun getConfigurationFactories() = arrayOf(MyFactory(this))
}
```

**Docs:** https://plugins.jetbrains.com/docs/intellij/run-configurations.html

## Documentation

- **SDK:** https://plugins.jetbrains.com/docs/intellij/
- **Platform Explorer:** https://plugins.jetbrains.com/intellij-platform-explorer/
- **GitHub:** https://github.com/JetBrains/intellij-community