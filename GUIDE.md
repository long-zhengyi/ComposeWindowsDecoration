# 详细使用指南

> 快速上手请参阅 [README](README.md)，实现原理请参阅 [IMPLEMENTATION.md](IMPLEMENTATION.md)。

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin Multiplatform | 2.3.0 |
| Compose Multiplatform | 1.10.0 |
| JNA (Java Native Access) | 5.16.0 |

## 核心原理

采用 **"保留装饰窗口 + Win32 API 精细控制"**，而不是 `isUndecorated = true`：

1. 保留 `WS_CAPTION` 窗口样式，通过 DWM 扩展帧保留阴影和边框
2. 通过 `WM_NCCALCSIZE` 消除非客户区（视觉上移除系统标题栏）
3. 通过 `WM_NCHITTEST` 实现拖拽、系统按钮命中和边框调整大小
4. 子类化 SkiaLayer 窗口过程，将 NC 鼠标消息转发为客户区消息，让 Compose 组件正常交互

## 项目结构

```
borderless-titlebar/                    # 库模块（JVM Desktop）
└── src/jvmMain/kotlin/cn/longzhengyi/windowsdecoration/
    ├── BorderlessTitleBarScaffold.kt    # 脚手架：自动安装 helper + 跟踪最大化状态
    ├── TitleBarScope.kt                # 标题栏作用域（helper、isMaximized、窗口操作）
    ├── BorderlessWindowHelper.kt       # 核心 Win32 处理
    ├── BorderlessWindowModifiers.kt    # Modifier 扩展 API + rememberBorderlessWindowHelper
    ├── skialayer/
    │   └── SkiaLayerWindowProcedure.kt # SkiaLayer 子类化（NC 鼠标事件转发）
    ├── win32/
    │   └── Win32Api.kt                 # Win32 常量/结构体/JNA 接口
    └── utils/
        └── GenerateAutoId.kt           # 区域 ID 自动分配

sample/                                 # 示例模块
└── src/jvmMain/kotlin/cn/longzhengyi/windowsdecoration/sample/
    ├── Main.kt                         # 入口
    ├── CustomBorderlessTitleBar.kt     # 基于脚手架的标题栏用例
    └── SampleApp.kt                    # 示例应用内容
```

## 使用方式

### 方式一：BorderlessTitleBarScaffold（推荐）

脚手架自动完成 helper 安装和最大化状态追踪，你只需在 `TitleBarScope` 内布局 UI 并标记区域：

```kotlin
@Composable
fun FrameWindowScope.MyTitleBar(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    BorderlessTitleBarScaffold(windowState = windowState) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .windowDragArea(helper),       // 标记整行为拖拽背板
        ) {
            Text("My App", Modifier.weight(1f))

            IconButton(
                onClick = { minimize() },
                modifier = Modifier.windowMinimizeButton(helper),
            ) { /* ... */ }

            IconButton(
                onClick = { toggleMaximize() },
                modifier = Modifier.windowMaximizeButton(helper),
            ) { /* ... */ }

            IconButton(
                onClick = onClose,
                modifier = Modifier.windowCloseButton(helper),
            ) { /* ... */ }
        }
    }
}
```

`TitleBarScope` 提供的属性和方法：

| 成员 | 说明 |
|------|------|
| `helper` | `BorderlessWindowHelper?`，自动注入，传给 Modifier 扩展 |
| `isMaximized` | 当前窗口是否最大化（自动追踪） |
| `minimize()` | 最小化窗口 |
| `maximize()` | 最大化窗口 |
| `restore()` | 还原窗口 |
| `toggleMaximize()` | 切换最大化/还原 |

### 方式二：直接使用 Helper

不使用脚手架，自己管理 helper 生命周期和最大化状态：

```kotlin
@Composable
fun FrameWindowScope.MyTitleBar(windowState: WindowState) {
    val helper = rememberBorderlessWindowHelper()
    var isMaximized by remember { mutableStateOf(false) }

    LaunchedEffect(helper) {
        snapshotFlow { windowState.placement }
            .collect {
                delay(50)
                isMaximized = helper?.isMaximized() ?: false
            }
    }

    Row(Modifier.windowDragArea(helper)) {
        SearchBox(Modifier.windowInteractiveArea(helper, "search")) // 镂空：阻止拖拽
        Spacer(Modifier.weight(1f))
        MinBtn(Modifier.windowMinimizeButton(helper))               // 镂空：最小化
        MaxBtn(Modifier.windowMaximizeButton(helper))               // 镂空：最大化
        CloseBtn(Modifier.windowCloseButton(helper))                // 镂空：关闭
    }
}
```

## Modifier 扩展 API

| Modifier | 作用 | 支持多个 |
|----------|------|----------|
| `windowDragArea(helper, id?)` | 拖拽背板（整个标题栏行） | ✅ |
| `windowMinimizeButton(helper)` | 最小化按钮区域 | — |
| `windowMaximizeButton(helper)` | 最大化按钮区域（Win11 Snap Layout） | — |
| `windowCloseButton(helper)` | 关闭按钮区域 | — |
| `windowInteractiveArea(helper, id?)` | 交互区域（镂空背板，阻止拖拽） | ✅ |

所有 Modifier 安全接受 `null`（helper 尚未安装完成时不生效），内部通过 `onGloballyPositioned` + `boundsInWindow()` 自动上报坐标。

支持多个的 Modifier 通过 `id` 区分实例，默认自动分配。组件退出 composition 时自动注销区域。

## "背板 + 镂空" 命中模型

- 整个标题栏标记为拖拽背板：`windowDragArea`
- 标题栏内交互组件标记为镂空区域：`windowInteractiveArea`
- 系统按钮区域：`windowMinimizeButton` / `windowMaximizeButton` / `windowCloseButton`
- 命中优先级：**边框调整大小 > 系统按钮 > 交互区域 > 拖拽背板 > 客户区**

## 窗口操作 API

通过 `TitleBarScope` 内的便捷方法，或直接调用 `helper`：

```kotlin
// TitleBarScope 内
minimize()
toggleMaximize()

// 直接调用 helper
helper?.minimize()
helper?.maximize()
helper?.restore()
helper?.toggleMaximize()
helper?.isMaximized()
```

## 运行示例

```shell
# Windows
.\gradlew.bat :sample:run

# macOS / Linux（示例可运行但无边框功能仅 Windows 生效）
./gradlew :sample:run
```
