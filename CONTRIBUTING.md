## Greetings contributor!

👾Thanks for your intent to contribute to our codebase. We look forward to working with you! 
This project is an open-source IntelliJ plugin for data streaming, managed by Confluent.

There are a few “rules of engagement” we’d like you to know before getting into technical details:

## Building trust with the maintainers of this repo 🤝

Please do not open a PR that does not have an associated issue. Before getting started with anything,  
read our [AI_POLICY.md](./AI_POLICY.md)

If you would like to be assigned an issue, either [create an issue](https://github.com/confluentinc/intellij/issues/new) 
that you’ve run into (check for [pre-existing issues](https://github.com/confluentinc/intellij/issues) first) 
or comment on an issue, asking to be assigned.

It would also help us all understand the context from which you’re coming if you can identify yourself and the capacity
in which you use the extension/plugin. If you’re a first-time contributor, just getting to know the codebase, 
and that’s your main goal, let us know and we may reassign you to a ticket which is a good first issue.

Lastly, we ask that you self-review your PRs. Is there any weirdness, like commented-out blocks, repeated code present?
Is it introducing new libraries that need not be introduced? Is there any preliminary work that should be broken into a
new branch? Check that the tests pass locally. Do they increase the coverage? 
Did you clicktest both happy and sad paths? When you create your PR description, consider including the following:

- PR title that is legible from something like a changelog 
- Summary of changes, readable and not overly full of bullet points 
- Before/after screenshots and an optional demo video for significant UI changes
- Steps to clicktest both happy and sad paths 
- Related ticket(s) 
- A link or description of how this will be used later on, if part of a multi-branch contribution

## Ways To Contribute

In addition to making PRs, we welcome you to file bug reports, make documentation fixes (a great first contribution!), 
make feature requests, and engage with us in GitHub discussions.

## Setting up your development environment

For tool installation and getting started with a local build, etc, see our README.md

## Short helpful links

File an [issue](https://github.com/confluentinc/intellij/issues/new)
Official [IntelliJ SDK Docs](https://plugins.jetbrains.com/docs/intellij/)

## Codebase Structure

This is the backbone of our codebase:

```
intellij/                                                                                                                                                                              
├── resources/              # plugin.xml, icons, i18n strings, templates                                                                                                               
├── src/io/confluent/intellijplugin/                                                                                                                                                   
│   ├── core/               # Plugin infrastructure: RFS, connections, settings, UI, monitoring, tables                                                                                
│   ├── client/             # Kafka admin client wrappers (BdtKafkaAdminClient, KafkaClientBuilder)                                                                                    
│   ├── consumer/           # Consumer editor, client, models (filters, groups, limits)                                                                                                
│   ├── producer/           # Producer editor, client, models                                                                                                                          
│   ├── common/             # Shared editor components, serializers, config storage                                                                                                    
│   ├── registry/           # Schema Registry integration (Confluent + AWS Glue)                                                                                                       
│   ├── aws/                # AWS/MSK auth (IAM, SSO, profiles, credentials)                                                                                                           
│   ├── ccloud/             # Confluent Cloud OAuth, API client, resource fetching                                                                                                     
│   ├── toolwindow/         # Kafka tool window (actions, config, controllers)                                                                                                         
│   ├── scaffold/           # Project scaffolding (actions, client, UI)                                                                                                                
│   ├── telemetry/          # Sentry + Segment analytics                                                                                                                               
│   ├── completion/         # Code completion for producer configs       
│   ├── spring/             # Spring framework integration                                                                                                                             
│   └── util/               # CSV export, code generation utilities                                                                                                                    
├── test/                   # Mirrors src structure + test fixtures in test/resources/                                                                                                 
├── build.gradle.kts        # Build config (JDK 21+, IntelliJ 2025.3+)                                                                                                                 
└── Makefile                # Build shortcuts
```

Static resources like icons and templates are in resources, core logic is in [`src/io/confluent/intellijplugin/`](./src/io/confluent/intellijplugin).

We use the Kotlin DSL (preferred) and Swing for UI components. 
Text copy is kept in [KafkaBundle.properties](/resources/messages/KafkaBundle.properties). All styling is in the component properties.

Auto-generated files are in `src/build/generated`, don’t check them in!

## Testing
We unit-test everything we add to this codebase, and PRs must pass the SonarQube quality gate for test coverage 
before being merged. You can use the `/intellij-unit-tests` Claude skill to add unit tests to your PR.

## Troubleshooting
If upon building the plugin or running a command that involves building the plugin like `./gradlew runIde`, you run into this error:

```bash
FAILURE: Build failed with an exception.

* What went wrong:
  Execution failed for task ':compileJava'.
> Java compilation initialization error
error: invalid source release: 21
```

Run `sdk env install` to reconcile your Java version.

## Reach Out
You can ping the [Confluent Community Slack](https://cnfl.io/slack-dp) with any questions.

Let’s build together!

_____________________________
_With the exception of the codebase markdown map which was generated by Claude, this document has been hand-crafted with love by the Confluent maintenance 🔧 team_


