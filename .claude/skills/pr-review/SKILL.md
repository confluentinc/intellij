---
name: pr-review
description:
  Review pull requests for the Kafka IntelliJ plugin. Checks for IntelliJ Platform best practices,
  threading safety, proper service usage, localization, and project-specific patterns. Use when reviewing PRs,
  doing self-review before sharing with the team, or when user mentions "review PR", "help with PR", "review changes",
  "self-review", "review local changes", or "check my PR".
allowed-tools: Bash, Read, Grep, Glob
---

# Pull Request Review

Review pull requests for the Kafka IntelliJ Plugin following project conventions and IntelliJ
Platform best practices.

## Invocation

```
/pr-review [PR number or URL]
```

If no PR number is provided, review the current branch's changes against the main branch.

## Process

### 1. Gather PR Information

**If PR number/URL provided:**

```bash
# Get PR details if PR number or URL provided
gh pr view <PR_NUMBER> --json number,title,body,author,baseRefName,headRefName,additions,deletions,changedFiles,state,reviewDecision

# If PR number or URL not provided, try current branch
gh pr view --json number,title,body,author,baseRefName,headRefName,additions,deletions,changedFiles,state,reviewDecision

# Get the diff
gh pr diff <number>

# Get existing review comments if any
gh pr view <PR_NUMBER> --json reviews,comments

# Lookup GitHub issues referenced in the PR description
gh issue view <ISSUE_NUMBER> --json body,comments
```

**If reviewing local changes or current branch:**

```bash
# See what files changed
git diff --name-only HEAD~1  # or compare against main
git diff main --name-only

# See the actual changes
git diff main --stat
git diff main
```

### 2. Identify Changed Files

Categorize the changes:

- **Skip auto-generated files** (do not review):
  - `THIRD_PARTY_NOTICES.txt`
  - `build/generated/`
  - `gen/`
  - `mk-files/`
  - `.mk-include-timestamp`
  - `*.iml` files
  - `gradle/wrapper/`

- **Focus areas** (review carefully):
  - `src/io/confluent/intellijplugin/` - Plugin source code
  - `test/io/confluent/intellijplugin/` - Tests
  - `resources/META-INF/plugin.xml` - Plugin configuration
  - `resources/messages/KafkaBundle.properties` - Localization

### 3. Review Checkpoints

For each changed file, verify compliance with project standards:

#### Threading Model

- [ ] **No EDT blocking**: Long operations use `scope.launch(Dispatchers.Default)`, not EDT
- [ ] **Correct dispatcher usage**:
  - `Dispatchers.EDT` for UI updates
  - `Dispatchers.Default` for CPU-bound work
  - `Dispatchers.IO` for file/network I/O (narrow scope)
- [ ] **Coroutine scope injection**: Services inject `CoroutineScope` via constructor, never use
      `Application/Project.getCoroutineScope()`
- [ ] **Read/write actions for PSI**: `readAction {}` for reads, `writeAction {}` for modifications
- [ ] **VFS refresh outside read lock**: `virtualFile.refresh(async = true)` not in read actions
- [ ] **Fast VFS listeners**: Heavy work dispatched to background from VFS listeners

#### IDE Infrastructure

- [ ] **Logging**: Uses `thisLogger()`, not log4j directly
- [ ] **Disposables**: Resources registered with parent via `Disposer.register(parent, child)`
- [ ] **Network requests**: Uses `HttpConfigurable` for proxy support
- [ ] **No static state**: No static mutable state that persists across projects

#### Services & Extensions

- [ ] **Service registration**: Services in `plugin.xml` with correct scope (app/project)
- [ ] **Extension points**: Use correct extension points
- [ ] **No `object` for extensions**: Use `class` (except `FileType` subclasses)
- [ ] **Lightweight constructors**: Heavy initialization uses `lazy`
- [ ] **No Project in app services**: Project-scoped data passed as method parameters

#### Localization

- [ ] **All UI strings localized**: Uses `KafkaMessagesBundle.message("key")`
- [ ] **New strings in bundle**: Added to `resources/messages/KafkaBundle.properties`
- [ ] **Proper annotations**: `@Nls` for localized, `@NlsSafe` for technical identifiers

#### Data Models

- [ ] **Presentable naming**: Domain objects use `*Presentable` suffix
- [ ] **Rendering annotations**: Uses `@NoRendering`, `@LoadingRendering` appropriately
- [ ] **Table columns**: Defines `renderableColumns` companion property for table display

#### Testing

- [ ] **Test coverage**: New functionality has tests
- [ ] **Platform annotation**: Tests using IntelliJ APIs have `@TestApplication`
- [ ] **Naming convention**: Test methods use backtick syntax
- [ ] **Mocking**: Uses `mockito-kotlin` patterns

#### Code Quality

- [ ] **Exception handling**: Graceful handling with user-visible error messages
- [ ] **No secrets**: No `.env`, credentials, or sensitive data committed
- [ ] **Minimal changes**: Only necessary changes, no unrelated refactoring
- - [ ] **Precise imports**: No unused imports

### 4. Security Considerations

Flag any changes that:

- Handle user credentials or authentication tokens
- Process external input (network, files)
- Execute system commands
- Access file system outside project scope

### 5. Generate Review Summary

```markdown
## PR Review: [Title]

### Overview
[Brief summary of what the PR does]

### Findings

#### Issues (Must Fix)
- [ ] **[Category]**: [Description] — [File:Line]

#### Suggestions (Consider)
- [ ] **[Category]**: [Description] — [File:Line]

#### Positive Notes
- [What's done well]

### Checklist Compliance
- Threading: [Pass/Issues found]
- Localization: [Pass/Issues found]
- Testing: [Pass/Issues found]
- Documentation: [Pass/Issues found]

### Recommendation
[Approve / Request Changes / Needs Discussion]
```

## Review Categories

Use these category labels in findings:

| Category      | Description                                      |
| ------------- | ------------------------------------------------ |
| `threading`   | EDT blocking, wrong dispatcher, scope issues    |
| `psi-safety`  | Missing read/write actions, stale references    |
| `memory`      | Leaks, missing disposable registration          |
| `l10n`        | Missing localization, hardcoded strings         |
| `testing`     | Missing tests, wrong annotations                |
| `style`       | Naming, patterns, project conventions           |
| `security`    | Credential handling, input validation           |
| `performance` | Inefficient code, blocking operations           |
| `api`         | Using internal APIs, deprecated methods         |

## Example Output

```markdown
## PR Review: Add topic deletion confirmation dialog

### Overview
Adds a confirmation dialog before deleting Kafka topics, with an option to remember the choice.

### Findings

#### Issues (Must Fix)
- [ ] **threading**: Dialog shown from background thread without `withContext(Dispatchers.EDT)`
      — `src/io/confluent/intellijplugin/topic/DeleteTopicAction.kt:42`
- [ ] **l10n**: Hardcoded string "Are you sure?" should use `KafkaMessagesBundle.message()`
      — `src/io/confluent/intellijplugin/topic/DeleteTopicAction.kt:45`

#### Suggestions (Consider)
- [ ] **style**: Consider extracting dialog logic to a separate `ConfirmationDialogHelper` class
      — `src/io/confluent/intellijplugin/topic/DeleteTopicAction.kt:40-60`

#### Positive Notes
- Good use of `PersistentStateComponent` for remembering user preference
- Test coverage for both confirmation paths

### Checklist Compliance
- Threading: Issues found (1)
- Localization: Issues found (1)
- Testing: Pass
- Documentation: Pass

### Recommendation
Request Changes — Fix threading and localization issues before merging.
```
