# Borderless TitleBar for Compose Desktop

[**English Documentation**](README.md)

为 Compose Desktop 窗口提供 **Windows 原生体验的无边框自定义标题栏**——阴影、圆角、Aero Snap、Snap Layout 全部保留，标题栏 UI 完全自绘。

## 环境要求

| 项目 | 要求 |
|------|------|
| 平台 | **Windows 10 / 11**（其他平台会静默降级，保留原生标题栏） |
| Kotlin | 2.3.0+ |
| Compose Multiplatform | 1.10.0+ |
| JNA | 5.16.0+（`net.java.dev.jna:jna` + `jna-platform`） |

> Windows 11 专属特性：圆角窗口、Snap Layout（最大化按钮悬停布局菜单）。Windows 10 下这些特性不可用，其余功能正常。

## 安装

将 `borderless-titlebar` 模块作为依赖引入：

```kotlin
// settings.gradle.kts
include(":borderless-titlebar")

// your-app/build.gradle.kts
dependencies {
    implementation(project(":borderless-titlebar"))
}
```

库模块需要的依赖（已在其 `build.gradle.kts` 中声明）：Compose UI、Compose Foundation、JNA、kotlinx-coroutines-swing。

## 快速开始

在 `FrameWindowScope` 内使用 `BorderlessTitleBarScaffold`，三步完成：

```kotlin
import cn.longzhengyi.windowsdecoration.*
import cn.longzhengyi.windowsdecoration.windowhelper.*

@Composable
fun FrameWindowScope.MyTitleBar(windowState: WindowState, onClose: () -> Unit) {
    // 1. 用脚手架包裹标题栏（自动安装无边框支持 + 追踪最大化状态）
    BorderlessTitleBarScaffold(windowState = windowState) {

        // 2. 自由布局标题栏，用 windowDragArea 标记可拖拽区域
        Row(
            Modifier.fillMaxWidth().height(40.dp).windowDragArea(helper),
        ) {
            Text("My App", Modifier.weight(1f).padding(start = 16.dp))

            // 3. 用对应 Modifier 标记系统按钮
            IconButton({ minimize() }, Modifier.windowMinimizeButton(helper)) { /* icon */ }
            IconButton({ toggleMaximize() }, Modifier.windowMaximizeButton(helper)) { /* icon */ }
            IconButton(onClose, Modifier.windowCloseButton(helper)) { /* icon */ }
        }
    }
}
```

然后在 `Window` 中调用：

```kotlin
Window(onCloseRequest = ::exitApplication, state = windowState) {
    Column(Modifier.fillMaxSize()) {
        MyTitleBar(windowState, onClose = ::exitApplication)
        // 你的应用内容
    }
}
```

完整可运行示例见 [`sample/`](sample/) 模块。

## API 参考

### BorderlessTitleBarScaffold

```kotlin
@Composable
fun FrameWindowScope.BorderlessTitleBarScaffold(
    windowState: WindowState,
    content: @Composable TitleBarScope.() -> Unit,
)
```

脚手架自动完成：Win32 窗口过程子类化、SkiaLayer 消息转发、最大化状态追踪。`content` 内通过 `TitleBarScope` 接收者访问以下成员：

| 成员 | 类型 | 说明 |
|------|------|------|
| `helper` | `BorderlessWindowHelper?` | 传给 Modifier 扩展，安装完成前为 `null` |
| `isMaximized` | `Boolean` | 当前是否最大化（自动更新） |
| `minimize()` | 函数 | 最小化窗口 |
| `maximize()` | 函数 | 最大化窗口 |
| `restore()` | 函数 | 还原窗口 |
| `toggleMaximize()` | 函数 | 切换最大化/还原 |

### Modifier 扩展

| Modifier | 作用 | 必须 |
|----------|------|------|
| `Modifier.windowDragArea(helper)` | 标记可拖拽区域（标题栏行） | **是** |
| `Modifier.windowMinimizeButton(helper)` | 标记最小化按钮 | 推荐 |
| `Modifier.windowMaximizeButton(helper)` | 标记最大化按钮（触发 Snap Layout） | 推荐 |
| `Modifier.windowCloseButton(helper)` | 标记关闭按钮 | 推荐 |
| `Modifier.windowInteractiveArea(helper, id?)` | 标记交互组件（搜索框等），阻止该区域触发拖拽 | 按需 |

所有 Modifier **安全接受 `null`**——helper 尚未安装完成时自动跳过，无需判空。

### rememberBorderlessWindowHelper()

如果不使用脚手架，可直接调用此函数获取 helper，自行管理状态。详见 [GUIDE.md](GUIDE.md)。

## 注意事项

1. **`windowDragArea` 是必须的**——不标记则窗口无法拖拽
2. **标题栏内的交互组件**（搜索框、下拉菜单等）需要用 `windowInteractiveArea` 标记，否则点击会触发窗口拖拽而非组件交互
3. **系统按钮 Modifier 推荐标记**——不标记 `windowMaximizeButton` 则 Win11 Snap Layout 不会生效；不标记按钮区域则鼠标悬停时系统无法识别为按钮（影响无障碍和系统行为）
4. **非 Windows 平台**会静默降级：标题栏 UI 正常渲染但无边框效果，原生标题栏保留

## 运行示例

```shell
.\gradlew.bat :sample:run
```

## 许可证

[Apache License 2.0](LICENSE)

## 致谢

- [Compose Fluent UI](https://github.com/compose-fluent/compose-fluent-ui) — 架构设计思路参考
- [JNA](https://github.com/java-native-access/jna) — Win32 API 调用（Apache-2.0 / LGPL-2.1）
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) — UI 框架（Apache-2.0）