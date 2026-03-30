# Borderless TitleBar for Compose Desktop

[**中文文档 / Chinese Documentation**](README_zh.md)

A library for **Compose Desktop (Windows)** that provides borderless custom title bars while preserving native window behaviors — shadow, rounded corners, Aero Snap, Snap Layout, and edge resizing all work out of the box.

## Requirements

| Item | Requirement |
|------|-------------|
| Platform | **Windows 10 / 11** (other platforms gracefully degrade — native title bar is preserved) |
| Kotlin | 2.3.0+ |
| Compose Multiplatform | 1.10.0+ |
| JNA | 5.16.0+ (`net.java.dev.jna:jna` + `jna-platform`) |

> Windows 11 exclusive features: rounded corners, Snap Layout (hover menu on maximize button). These are unavailable on Windows 10; all other features work normally.

## Installation

Add the `borderless-titlebar` module as a dependency:

```kotlin
// settings.gradle.kts
include(":borderless-titlebar")

// your-app/build.gradle.kts
dependencies {
    implementation(project(":borderless-titlebar"))
}
```

Transitive dependencies (already declared in the module's `build.gradle.kts`): Compose UI, Compose Foundation, JNA, kotlinx-coroutines-swing.

## Quick Start

Use `BorderlessTitleBarScaffold` inside a `FrameWindowScope` — three steps:

```kotlin
import cn.longzhengyi.windowsdecoration.*

@Composable
fun FrameWindowScope.MyTitleBar(windowState: WindowState, onClose: () -> Unit) {
    // 1. Wrap your title bar with the scaffold (auto-installs borderless support + tracks maximized state)
    BorderlessTitleBarScaffold(windowState = windowState) {

        // 2. Lay out your title bar freely; mark the drag region with windowDragArea
        Row(
            Modifier.fillMaxWidth().height(40.dp).windowDragArea(helper),
        ) {
            Text("My App", Modifier.weight(1f).padding(start = 16.dp))

            // 3. Mark system buttons with the corresponding Modifiers
            IconButton({ minimize() }, Modifier.windowMinimizeButton(helper)) { /* icon */ }
            IconButton({ toggleMaximize() }, Modifier.windowMaximizeButton(helper)) { /* icon */ }
            IconButton(onClose, Modifier.windowCloseButton(helper)) { /* icon */ }
        }
    }
}
```

Then call it inside your `Window`:

```kotlin
Window(onCloseRequest = ::exitApplication, state = windowState) {
    Column(Modifier.fillMaxSize()) {
        MyTitleBar(windowState, onClose = ::exitApplication)
        // Your app content
    }
}
```

See the [`sample/`](sample/) module for a full runnable example.

## API Reference

### BorderlessTitleBarScaffold

```kotlin
@Composable
fun FrameWindowScope.BorderlessTitleBarScaffold(
    windowState: WindowState,
    content: @Composable TitleBarScope.() -> Unit,
)
```

The scaffold automatically handles Win32 window procedure subclassing, SkiaLayer message forwarding, and maximized state tracking. Inside `content`, the `TitleBarScope` receiver provides:

| Member | Type | Description |
|--------|------|-------------|
| `helper` | `BorderlessWindowHelper?` | Pass to Modifier extensions; `null` until installation completes |
| `isMaximized` | `Boolean` | Whether the window is currently maximized (auto-updated) |
| `minimize()` | Function | Minimize the window |
| `maximize()` | Function | Maximize the window |
| `restore()` | Function | Restore the window |
| `toggleMaximize()` | Function | Toggle maximize / restore |

### Modifier Extensions

| Modifier | Purpose | Required |
|----------|---------|----------|
| `Modifier.windowDragArea(helper)` | Mark as draggable region (title bar row) | **Yes** |
| `Modifier.windowMinimizeButton(helper)` | Mark as minimize button | Recommended |
| `Modifier.windowMaximizeButton(helper)` | Mark as maximize button (enables Snap Layout) | Recommended |
| `Modifier.windowCloseButton(helper)` | Mark as close button | Recommended |
| `Modifier.windowInteractiveArea(helper, id?)` | Mark as interactive component (search box, etc.) to prevent drag | As needed |

All Modifiers **safely accept `null`** — they are no-ops until the helper finishes installing. No null-checks needed.

### rememberBorderlessWindowHelper()

If you prefer not to use the scaffold, call this function directly to obtain a helper and manage state yourself. See [GUIDE.md](GUIDE.md) for details.

## Important Notes

1. **`windowDragArea` is required** — without it the window cannot be dragged
2. **Interactive components inside the title bar** (search boxes, dropdowns, etc.) must be marked with `windowInteractiveArea`, otherwise clicks will trigger window dragging instead of component interaction
3. **System button Modifiers are recommended** — without `windowMaximizeButton`, Win11 Snap Layout won't activate; unmarked buttons won't be recognized by the system (affects accessibility and native behavior)
4. **Non-Windows platforms** degrade gracefully: the title bar UI renders normally but without borderless effects; the native title bar is preserved

## Run the Sample

```shell
.\gradlew.bat :sample:run
```

## License

[Apache License 2.0](LICENSE)

## Acknowledgments

- [Compose Fluent UI](https://github.com/compose-fluent/compose-fluent-ui) — architectural inspiration
- [JNA](https://github.com/java-native-access/jna) — Win32 API calls (Apache-2.0 / LGPL-2.1)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) — UI framework (Apache-2.0)