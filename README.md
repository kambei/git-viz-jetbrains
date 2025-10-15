# GitViz Horizontal

A simple JetBrains IDE plugin that displays your Git history horizontally. It uses JGit under the hood to read repository data and renders a scrollable, interactive timeline inside a tool window.

## Features

- **Horizontal Git History:** Visualizes the commit history as a horizontal directed acyclic graph (DAG).
- **Rich Commit Info:** Each commit shows the short SHA, author, message, and any associated branches or tags.
- **Interactive Graph:**
    - **Zoom:** Use the mouse wheel to zoom in and out.
    - **Pan:** Click and drag with the left mouse button to move the view.
    - **Details:** Click on a commit's author, message, or node to see more details in a popup.
- **Advanced Filtering:**
    - Filter by branch, tag, author, or commit message substring.
    - Filter dropdowns are auto-populated from your repository.
- **Configurable Limits:** Control the number of commits and branches displayed to optimize performance.
- **Scales to Large Repos:** Renders the full history without artificial limits.

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

## Recent Changes

- **v1.6.0:** Zoom with the mouse wheel (without needing the Ctrl key).
- **v1.5.0:** Pan the graph by clicking and dragging.
- **v1.2.0:** Added advanced filtering by branch, tag, author, and message.
- **v1.1.0:** Added configurable limits for commits and branches.

## Development

Core entry point: `dev.kambei.gitviz.HorizontalGitToolWindowFactory`

Key files:
- `src/main/resources/META-INF/plugin.xml` – plugin metadata and tool window registration
- `src/main/kotlin/dev/kambei/gitviz/HorizontalGitToolWindowFactory.kt` – UI and JGit integration

## License

This project is licensed under the terms of the MIT License. See the [LICENSE](LICENSE) file for details.

<br>

## Support Me

If you find this application helpful, consider supporting me on Ko-fi!

[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/kambei)
