# GitViz Horizontal

A simple JetBrains IDE plugin that displays your Git history horizontally with compact commit cards showing:

- Short SHA and author
- Commit message
- Branch, tag, and remote labels

It uses JGit under the hood to read repository data and renders a scrollable horizontal timeline inside a tool window.

## Features

- Beautiful horizontal DAG graph of commits (nodes with smooth curved edges)
- Shows branches, tags, and remotes as badges near nodes
- Displays author and short message as labels
- Limits button to choose how many latest commits and latest branches to show (defaults: 500 commits, 20 branches)
- Scales to the full history of your repo (no artificial 200-commit limit)

## Requirements

- A JetBrains IDE (IntelliJ IDEA, or other IntelliJ-based IDEs) compatible with the Gradle IntelliJ Plugin
- JDK 17+ recommended
- Git repository present in your project

## Getting Started

### Run the plugin in a sandbox IDE

```bash
./gradlew runIde
```

This launches a sandbox IDE instance with the plugin installed.

### Build a distribution ZIP

```bash
./gradlew buildPlugin
```

The ZIP artifact will be created under `build/distributions/`. You can install it into your IDE via:
- Settings/Preferences > Plugins > Gear icon > Install Plugin from Disk…

### Publish to JetBrains Marketplace

1. Create a JetBrains Marketplace account and a plugin listing with ID `dev.kambei.gitviz`.
2. Set an environment variable with your Marketplace token:
   - On macOS/Linux: `export MARKETPLACE_TOKEN=your-token`
   - On Windows (PowerShell): `$Env:MARKETPLACE_TOKEN = "your-token"`
3. Publish using Gradle:

```bash
./gradlew publishPlugin
```

Notes:
- The plugin icon is provided at `src/main/resources/META-INF/pluginIcon.svg`.
- Compatibility is declared via `<idea-version since-build="242.0"/>` in `plugin.xml` (IDE 2024.2+).
- You can change the release channel in `build.gradle.kts` under `intellijPlatform.publishing.channels`.

## Using the plugin

- Open a project that contains a Git repository.
- Open the tool window named "GitViz Horizontal" (id: GitViz Horizontal). It is anchored at the bottom by default.
- The view will populate with commit cards you can scroll through. The status label at the top shows how many commits were loaded.

## Notes & Limitations

- The plugin reads the complete log via JGit. Very large repositories may take time to render on first load.

## Development

Core entry point: `dev.kambei.gitviz.HorizontalGitToolWindowFactory`

Key files:
- `src/main/resources/META-INF/plugin.xml` – plugin metadata and tool window registration
- `src/main/kotlin/dev/kambei/gitviz/HorizontalGitToolWindowFactory.kt` – UI and JGit integration

## Recent change

- Removed the previous 200-commit limit so the tool now shows the full Git history.

## License

This project is licensed under the terms of the MIT License. See the [LICENSE](LICENSE) file for details.

## Troubleshooting

- "Git repo not found or error": Ensure the project root is within a Git repository. The status label will display the exception message for quick diagnosis.
- Icons: The plugin uses platform icons. If an icon cannot be resolved in your IDE/version, it will not impact functionality.
