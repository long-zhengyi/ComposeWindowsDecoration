# TestNoDecoration —— 实现原理说明

## 一、项目概述

本项目是一个基于 **Compose Multiplatform** 的跨平台桌面应用，核心目标是在 **Windows 平台 (JVM Desktop)** 上实现**无边框自定义标题栏窗口**，同时保留系统原生能力：

- 窗口阴影与边框
- Windows 11 圆角
- Aero Snap（拖拽吸附）
- Windows 11 Snap Layout（最大化按钮悬停弹出布局菜单）
- 窗口边缘拖拽调整大小

### 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Kotlin Multiplatform | 2.3.0 | 语言与跨平台框架 |
| Compose Multiplatform | 1.10.0 | 声明式 UI 框架 |
| JNA (Java Native Access) | 5.16.0 | 调用 Win32 API |
| Skiko (SkiaLayer) | 随 Compose 分发 | Compose 底层渲染引擎 |

### 项目结构

```
composeApp/src/
├── commonMain/         # 跨平台共享代码（App、Greeting 等）
├── jvmMain/            # JVM Desktop 平台代码
│   └── kotlin/org/example/project/
│       ├── main.kt                     # 程序入口，窗口创建与 Helper 安装
│       ├── TitleBar.kt                 # 自绘标题栏 Compose 组件
│       └── win32/
│           ├── Win32Api.kt             # Win32 常量、结构体、JNA 接口定义
│           ├── BorderlessWindowHelper.kt   # 核心：JFrame 窗口过程子类化
│           ├── BorderlessWindowModifiers.kt # Compose Modifier 扩展与一键安装 API
│           └── SkiaLayerWindowProcedure.kt # SkiaLayer Canvas 窗口过程子类化
├── androidMain/        # Android 平台代码
└── iosMain/            # iOS 平台代码
```

---

## 二、核心问题与解决思路

### 2.1 问题背景

Compose Desktop 底层使用 Swing `JFrame` 作为窗口容器，`SkiaLayer`（Skia 渲染层）作为内容画布。默认情况下，`JFrame` 使用操作系统提供的窗口装饰（标题栏、边框、系统按钮）。

如果简单地调用 `jFrame.isUndecorated = true` 去除装饰，会导致：
- 丢失窗口阴影
- 丢失 Windows 11 圆角
- 丢失 Aero Snap 和 Snap Layout 支持
- 需要完全自行实现窗口拖拽和调整大小逻辑

### 2.2 解决思路

本项目采用的方案是 **保留装饰窗口 + Win32 API 精细控制**：

1. **不使用 `isUndecorated`**，保留 `WS_CAPTION` 样式以获得系统阴影和边框
2. 通过 **`WM_NCCALCSIZE`** 将非客户区大小设为 0，视觉上移除系统标题栏
3. 通过 **DWM (Desktop Window Manager)** 扩展帧，保留阴影效果
4. 通过 **`WM_NCHITTEST`** 精确控制命中测试，告诉系统哪些区域是标题栏、按钮、边框
5. 通过 **子类化 SkiaLayer** 窗口过程，将非客户区鼠标消息转发为客户区消息，使 Compose 按钮正常响应

---

## 三、启动流程

```
main()
  └─ application { Window { ... } }
       ├─ 创建 JFrame + Compose 内容
       ├─ rememberBorderlessWindowHelper()
       │     内部通过 LaunchedEffect + SwingUtilities.invokeLater
       │     创建并安装 BorderlessWindowHelper
       ├─ LaunchedEffect: 设置最小窗口尺寸 (400×300)
       ├─ LaunchedEffect(helper): 监听 windowState.placement 变化 → 更新 isMaximized
       └─ Column {
              CustomTitleBar(...)   ← 自绘标题栏，通过 Modifier 上报区域坐标
              App()                 ← 应用内容
          }
```

### 关键步骤详解

1. **`main.kt`** 使用 Compose `Window` API 创建窗口
2. 调用 `rememberBorderlessWindowHelper()` 一键安装无边框支持（内部通过 `LaunchedEffect` + `SwingUtilities.invokeLater` 在 EDT 线程执行）
3. `CustomTitleBar` 通过 Modifier 扩展（`windowDragArea`、`windowMinimizeButton`、`windowMaximizeButton`、`windowCloseButton`）在每次布局时将各区域的**精确像素坐标**自动上报给 `BorderlessWindowHelper`
4. `BorderlessWindowHelper` 使用这些坐标进行 Win32 命中测试

---

## 四、BorderlessWindowHelper 详解

BorderlessWindowHelper 采用 **“背板 + 镂空”** 命中测试模型：

- 将整个标题栏行标记为可拖拽背板（`HTCAPTION`）
- 在背板上放置的系统按钮和交互组件会自动镂空背板，阻止拖拽
- 命中测试优先级（高 → 低）：调整大小边框 → 系统按钮 → 交互区域（镂空） → 拖拽背板 → 客户区

外部通过 Modifier 扩展或区域注册 API 声明各区域，详见 [4.3 Modifier 扩展与窗口操作 API](#43-modifier-扩展与窗口操作-api)。

### 4.1 install() 安装流程

`install()` 在 EDT 线程上执行，共 6 个步骤：

#### 步骤 1：修改窗口样式

```kotlin
val newStyle = (currentStyle.toLong() or WS_CAPTION) and WS_SYSMENU.inv()
```

- **保留 `WS_CAPTION`**：使 DWM 为窗口绘制阴影和边框
- **移除 `WS_SYSMENU`**：隐藏系统菜单按钮（最小化/最大化/关闭），因为我们会自己绘制

#### 步骤 2：DWM 扩展帧

```kotlin
val margins = MARGINS().apply {
    cxLeftWidth = 0; cxRightWidth = -1
    cyTopHeight = 0; cyBottomHeight = -1
}
dwmApi.DwmExtendFrameIntoClientArea(hWnd, margins)
```

`cxRightWidth = -1` 和 `cyBottomHeight = -1` 表示将 DWM 帧扩展到整个客户区。这样即使 `WM_NCCALCSIZE` 返回 0（消除非客户区），DWM 仍然会为窗口绘制阴影。

#### 步骤 3：子类化窗口过程

```kotlin
val callbackPointer = CallbackReference.getFunctionPointer(callback)
originalWndProc = user32.SetWindowLongPtrW(hWnd, GWL_WNDPROC, callbackPointer)
```

使用 JNA 的 `SetWindowLongPtrW` 替换 JFrame 的窗口过程（WndProc），使所有 Win32 消息先经过自定义的 `handleMessage()` 处理。`originalWndProc` 保存原始过程指针，以便对未处理的消息进行转发。

> **注意**：`wndProcCallbackRef` 字段虽标注 `@Suppress("unused")`，实际上是**防止 GC 回收回调对象**的关键引用。如果该引用被回收，JNA 回调指针将变为悬空指针，导致崩溃。

#### 步骤 4：触发帧更新

```kotlin
user32.SetWindowPos(hWnd, null, 0, 0, 0, 0,
    SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE)
```

`SWP_FRAMECHANGED` 标志强制系统重新计算窗口帧，使步骤 1-2 的更改立即生效。

#### 步骤 5：Windows 11 圆角

```kotlin
val cornerPref = Memory(4).apply { setInt(0, DWMWCP_ROUND) }
dwmApi.DwmSetWindowAttribute(hWnd, DWMWA_WINDOW_CORNER_PREFERENCE, cornerPref, 4)
```

设置 DWM 窗口属性 `DWMWA_WINDOW_CORNER_PREFERENCE = 33`，请求圆角样式。此 API 仅 Windows 11 (Build 22000+) 支持，较早版本会静默失败。

#### 步骤 6：子类化 SkiaLayer

由于 Compose 首次渲染可能尚未完成，`SkiaLayer` 组件可能还未创建，因此采用**延迟重试**策略：

```kotlin
private fun tryInstallSkiaLayerProcedure() {
    val skiaLayer = findComponent(jFrame, SkiaLayer::class.java)
    if (skiaLayer != null) {
        installSkiaLayerProcedure(skiaLayer)
    } else {
        Timer(200) { /* 200ms 后重试 */ }.apply { isRepeats = false; start() }
    }
}
```

使用递归的 `findComponent()` 在 JFrame 组件树中查找 `SkiaLayer` 实例。

---

### 4.2 Win32 消息处理（handleMessage）

#### WM_NCCALCSIZE —— 消除非客户区与最大化修正

```kotlin
WM_NCCALCSIZE -> {
    if (wParam.toInt() != 0) {
        updateWindowInfo()
        // 最大化时，系统会将窗口扩展到超出工作区域（超出部分 = 边框厚度）
        // 需要将客户区向内收缩，使内容刚好填满工作区域
        if (isMaximizedState) {
            val borderX = frameX + padding
            val borderY = frameY + padding
            val ptr = Pointer(lParam.toLong())
            ptr.setInt(0, ptr.getInt(0) + borderX)   // left
            ptr.setInt(4, ptr.getInt(4) + borderY)   // top
            ptr.setInt(8, ptr.getInt(8) - borderX)   // right
            ptr.setInt(12, ptr.getInt(12) - borderY) // bottom
        }
    }
    return LRESULT(0)
}
```

当 `wParam != 0` 时，系统传入 `NCCALCSIZE_PARAMS` 结构体，期望程序修改客户区矩形。**返回 0** 意味着客户区 = 窗口区域，即非客户区大小为 0，从视觉上移除系统标题栏和边框装饰（`WS_CAPTION` 样式仍在，DWM 仍绘制阴影）。

**最大化修正**：窗口最大化时，系统会将窗口扩展到超出显示器工作区域，超出量等于边框厚度（`frameX + padding` / `frameY + padding`）。如果不处理，客户区内容会溢出屏幕可见范围。通过直接修改 `NCCALCSIZE_PARAMS` 中的矩形坐标，将客户区向内收缩，使内容刚好填满工作区域。

#### WM_NCHITTEST —— 命中测试（核心）

```kotlin
WM_NCHITTEST -> {
    updateWindowInfo()
    val screenX = (lp.toInt() and 0xFFFF).toShort().toInt()
    val screenY = ((lp.toInt() shr 16) and 0xFFFF).toShort().toInt()
    val result = computeHitTestFromScreen(screenX, screenY)
    currentHitResult = result
    return LRESULT(result.toLong())
}
```

这是整个方案的**核心机制**。系统在每次鼠标移动时发送 `WM_NCHITTEST`，我们返回不同的值告诉系统当前位置的区域类型：

| 返回值 | 含义 | 系统行为 |
|--------|------|----------|
| `HTCLIENT` (1) | 客户区 | 正常鼠标事件 |
| `HTCAPTION` (2) | 标题栏 | 拖拽移动窗口、双击最大化 |
| `HTMINBUTTON` (8) | 最小化按钮 | 系统处理最小化 |
| `HTMAXBUTTON` (9) | 最大化按钮 | 触发 Snap Layout 菜单 (Win11) |
| `HTCLOSE` (20) | 关闭按钮 | 系统处理关闭 |
| `HTLEFT/HTRIGHT/...` | 边框区域 | 拖拽调整大小 |

#### 命中测试计算逻辑 (computeHitTest)

```
输入: 窗口相对坐标 (x, y)
  │
  ├─ 非最大化状态？
  │    ├─ 在边框区域？ → 返回 HTLEFT/HTRIGHT/HTTOP/HTBOTTOM/HTTOPLEFT/...
  │    └─ 不在边框
  │
  ├─ 在最大化按钮区域？   → 返回 HTMAXBUTTON  (触发 Snap Layout)
  ├─ 在最小化按钮区域？   → 返回 HTMINBUTTON
  ├─ 在关闭按钮区域？     → 返回 HTCLOSE
  ├─ 在交互区域（镂空）？ → 返回 HTCLIENT     (阻止拖拽，事件交给 Compose)
  ├─ 在标题栏背板区域？   → 返回 HTCAPTION    (支持拖拽)
  └─ 其他区域             → 返回 HTCLIENT     (正常客户区)
```

边框区域使用 DPI 感知的系统度量值（`SM_CXFRAME` / `SM_CYFRAME`）计算，确保在不同 DPI 缩放下行为一致。

#### WM_NCACTIVATE —— 阻止非客户区重绘

```kotlin
WM_NCACTIVATE -> return LRESULT(1)
```

直接返回 1 阻止系统在窗口激活/失活时重绘非客户区（因为我们已经不需要系统绘制的非客户区了）。

#### WM_GETMINMAXINFO —— 最大化尺寸约束

```kotlin
WM_GETMINMAXINFO -> {
    // 获取显示器工作区域（排除任务栏）
    val work = mi.rcWork
    mmi.ptMaxPosition.x = work.left - mi.rcMonitor.left
    mmi.ptMaxPosition.y = work.top - mi.rcMonitor.top
    mmi.ptMaxSize.x = work.right - work.left
    mmi.ptMaxSize.y = work.bottom - work.top
}
```

由于我们消除了非客户区，系统默认的最大化行为会让窗口覆盖整个屏幕（包括任务栏）。通过处理 `WM_GETMINMAXINFO`，将最大化位置和大小限制在**显示器工作区域**（排除任务栏），实现正确的最大化行为。

#### WM_SIZE —— 窗口尺寸变化

缓存窗口宽高，并转发给原始窗口过程。

#### WM_NCMOUSEMOVE —— 非客户区鼠标移动转发

当 JFrame 收到非客户区鼠标移动消息时，通过 `PostMessageW` 转发给 SkiaLayer 的 Canvas 窗口，确保 Compose 能收到鼠标悬停事件。

### 4.3 Modifier 扩展与窗口操作 API

#### BorderlessWindowModifiers.kt

提供 Compose 声明式 API，封装区域注册和 Helper 安装逻辑：

| 函数 | 用途 |
|------|------|
| `rememberBorderlessWindowHelper()` | 在 Compose Window 作用域内一键创建并安装 Helper，返回可空值（异步安装完成后触发重组） |
| `Modifier.windowDragArea(helper)` | 标记拖拽背板区域（整个标题栏行） |
| `Modifier.windowMinimizeButton(helper)` | 标记最小化按钮区域（镂空背板） |
| `Modifier.windowMaximizeButton(helper)` | 标记最大化按钮区域（镂空背板，Win11 触发 Snap Layout） |
| `Modifier.windowCloseButton(helper)` | 标记关闭按钮区域（镂空背板） |
| `Modifier.windowInteractiveArea(helper, id)` | 标记交互区域（镂空背板，支持多个） |

所有 Modifier 扩展均安全接受 `null`（helper 尚未安装完成时不生效），内部通过 `onGloballyPositioned` + `boundsInWindow()` 实现坐标自动上报。

#### 窗口操作 API

BorderlessWindowHelper 还提供窗口操作方法，供标题栏按钮调用：

| 方法 | 用途 |
|------|------|
| `isMaximized()` | 查询当前窗口是否最大化（调用 `IsZoomed`） |
| `minimize()` | 最小化窗口（`ShowWindow(SW_MINIMIZE)`） |
| `maximize()` | 最大化窗口（`ShowWindow(SW_SHOWMAXIMIZED)`） |
| `restore()` | 还原窗口（`ShowWindow(SW_RESTORE)`） |
| `toggleMaximize()` | 切换最大化/还原状态 |

在 `main.kt` 中的使用方式：

```kotlin
onMinimize = { helper?.minimize() },
onToggleMaximize = { helper?.toggleMaximize() },
onClose = { exitApplication() },
```

---

## 五、SkiaLayerWindowProcedure 详解

### 5.1 为什么需要子类化 SkiaLayer？

Compose Desktop 的渲染架构：

```
JFrame (顶层窗口)
  └─ SkiaLayer (Skia 渲染层)
       └─ Canvas (实际绘制内容的子窗口)
```

当 JFrame 的 `WM_NCHITTEST` 返回 `HTMAXBUTTON` 等非客户区值时，系统会将后续鼠标事件作为**非客户区消息**发送（如 `WM_NCMOUSEMOVE` 而非 `WM_MOUSEMOVE`）。但 Compose 只能处理**客户区消息**（`WM_MOUSEMOVE` / `WM_LBUTTONDOWN` / `WM_LBUTTONUP`）。

因此需要在 SkiaLayer 的 Canvas 层子类化窗口过程，进行**消息转换**。

### 5.2 消息处理逻辑

#### WM_NCHITTEST —— 选择性穿透

```kotlin
WM_NCHITTEST -> {
    val hitResult = lParamToClientHitTest(lParam)
    return when (hitResult) {
        HTCLIENT, HTMAXBUTTON, HTMINBUTTON, HTCLOSE -> LRESULT(hitResult.toLong())
        else -> LRESULT(HTTRANSPARENT.toLong())
    }
}
```

- **按钮区域和客户区**：返回对应 HT 值，SkiaLayer 自己处理
- **其他区域**（标题栏拖拽、边框调整大小）：返回 `HTTRANSPARENT`，使消息**穿透**到父窗口 JFrame，由 JFrame 的窗口过程处理

这实现了一个精妙的分层：
- SkiaLayer 处理 Compose 按钮交互
- JFrame 处理窗口拖拽和调整大小

#### NC 鼠标消息 → 客户区消息转发

```kotlin
WM_NCMOUSEMOVE  → SendMessageW(contentHandle, WM_MOUSEMOVE, ...)
WM_NCLBUTTONDOWN → SendMessageW(contentHandle, WM_LBUTTONDOWN, ...)
WM_NCLBUTTONUP   → SendMessageW(contentHandle, WM_LBUTTONUP, ...)
```

将非客户区鼠标事件转换为普通客户区鼠标事件，使 Compose 的标题栏按钮能正常响应悬停和点击。

### 5.3 坐标转换

```kotlin
private fun lParamToClientHitTest(lParam: LPARAM): Int {
    // 1. 从 lParam 解析屏幕坐标
    val screenX = (lp and 0xFFFF).toShort().toInt()
    val screenY = ((lp shr 16) and 0xFFFF).toShort().toInt()
    // 2. 转换为 SkiaLayer 窗口的客户区坐标
    user32.ScreenToClient(windowHandle, point)
    // 3. 调用 hitTest 回调（由 BorderlessWindowHelper 提供）
    return hitTest(point.x.toFloat(), point.y.toFloat())
}
```

`lParam` 中的坐标是屏幕绝对坐标，需要通过 `ScreenToClient` 转换为 SkiaLayer 窗口的相对坐标，再传递给 `BorderlessWindowHelper` 的 `computeHitTest` 进行命中判断。

---

## 六、自绘标题栏 (CustomTitleBar)

### 6.1 布局结构

```
Row (高度 40dp, 填满宽度)
  ├─ Box (weight=1f) ← 标题文字，占据剩余空间
  ├─ TitleBarButton "─"  ← 最小化 (46dp 宽)
  ├─ TitleBarButton "□/❐" ← 最大化/还原 (46dp 宽)
  └─ TitleBarButton "✕"  ← 关闭 (46dp 宽, 悬停红色背景)
```

### 6.2 坐标上报机制——Modifier 扩展 API

坐标上报通过 `BorderlessWindowModifiers.kt` 中的 Modifier 扩展实现，内部调用 `onGloballyPositioned`：

```kotlin
// TitleBar.kt 中
Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(TITLE_BAR_HEIGHT)
        .windowDragArea(helper),           // 标记整行为拖拽背板
) {
    // ...
    TitleBarButton(modifier = Modifier.windowMinimizeButton(helper))
    TitleBarButton(modifier = Modifier.windowMaximizeButton(helper))
    TitleBarButton(modifier = Modifier.windowCloseButton(helper))
}
```

Modifier 扩展安全接受 `null`（安装完成前 helper 为 `null`，此时 Modifier 不生效）。`boundsInWindow()` 返回组件在窗口中的**精确像素坐标** (`Rect`)，自动注册到 `BorderlessWindowHelper` 的对应区域。

除系统按钮外，还支持 `windowInteractiveArea(helper, id)` 注册交互区域（镂空拖拽背板），用于搜索框、下拉菜单等需要阻止拖拽的组件。

这样 Win32 层的命中测试就能精确知道 Compose 各区域的位置，无需硬编码坐标。

### 6.3 按钮交互

按钮使用 Compose 的 `hoverable` + `clickable` 修饰符实现悬停高亮和点击响应。关闭按钮悬停时背景变为红色 (`#E81123`)，与 Windows 系统风格一致。

---

## 七、Win32 API 层 (Win32Api.kt)

### 7.1 JNA 接口

通过 JNA (Java Native Access) 调用 Win32 原生 API，定义了两个接口：

- **`User32Ex`**：封装 `user32.dll` 中的窗口管理函数
- **`DwmApi`**：封装 `dwmapi.dll` 中的 DWM 相关函数

### 7.2 关键结构体

| 结构体 | 用途 |
|--------|------|
| `RECT` | 矩形区域（窗口位置、显示器区域） |
| `MARGINS` | DWM 帧边距 |
| `MINMAXINFO` | 窗口最大化/最小化约束 |
| `MONITORINFO` | 显示器信息（工作区域、分辨率） |

### 7.3 回调接口

```kotlin
interface WndProcCallback : StdCallLibrary.StdCallCallback {
    fun callback(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
}
```

JNA 的 `StdCallCallback` 接口，用于定义可传递给 `SetWindowLongPtrW` 的窗口过程回调。

---

## 八、数据流与交互时序

### 8.1 鼠标悬停到最大化按钮的完整流程

```
1. 用户移动鼠标到最大化按钮位置
2. Win32 → JFrame WndProc: WM_NCHITTEST
3. handleMessage() → computeHitTestFromScreen()
   → 屏幕坐标转窗口坐标 → computeHitTest() → performButtonHitTest()
   → maximizeButtonArea.contains(x, y) == true → 返回 HTMAXBUTTON
4. 系统识别为最大化按钮区域
   → Win11: 显示 Snap Layout 悬停菜单
   → 后续鼠标事件作为 NC 消息发送
5. Win32 → SkiaLayer Canvas WndProc: WM_NCHITTEST
   → hitResult == HTMAXBUTTON → 返回 HTMAXBUTTON（不穿透）
6. Win32 → SkiaLayer Canvas WndProc: WM_NCMOUSEMOVE
   → 转发为 WM_MOUSEMOVE → Compose 收到 hover 事件 → 按钮高亮
7. 用户点击
8. Win32 → SkiaLayer Canvas WndProc: WM_NCLBUTTONDOWN
   → 转发为 WM_LBUTTONDOWN → Compose 收到 click 事件 → 执行 onToggleMaximize
```

### 8.2 标题栏拖拽的完整流程

```
1. 用户在标题栏空白区域按下鼠标
2. Win32 → JFrame WndProc: WM_NCHITTEST → 返回 HTCAPTION
3. Win32 → SkiaLayer Canvas WndProc: WM_NCHITTEST
   → hitResult == HTCAPTION → 返回 HTTRANSPARENT（穿透给父窗口）
4. 系统识别 HTCAPTION → 进入 DefWindowProc 的模态拖拽循环
   → 支持 Aero Snap（拖到屏幕边缘吸附）
```

### 8.3 窗口边缘拖拽调整大小

```
1. 鼠标移到窗口边缘
2. WM_NCHITTEST → computeHitTest()
   → 坐标在 frameX/frameY 范围内 → 返回 HTLEFT/HTRIGHT/HTTOP/...
3. 系统自动处理调整大小（光标变化、拖拽逻辑）
```

---

## 九、DPI 感知

项目使用 Per-Monitor DPI 感知 API：

```kotlin
dpi = user32.GetDpiForWindow(hWnd)
frameX = user32.GetSystemMetricsForDpi(SM_CXFRAME, dpi)
frameY = user32.GetSystemMetricsForDpi(SM_CYFRAME, dpi)
edgeX = user32.GetSystemMetricsForDpi(SM_CXEDGE, dpi)
edgeY = user32.GetSystemMetricsForDpi(SM_CYEDGE, dpi)
padding = user32.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
```

在每次 `WM_NCHITTEST` 和 `WM_NCCALCSIZE` 时刷新 DPI 和系统度量值，确保在多显示器不同 DPI 环境下正确工作。

---

## 十、关键设计决策与权衡

### 10.1 为什么不用 `isUndecorated = true`？

| 方案 | 阴影 | 圆角 | Aero Snap | Snap Layout | 复杂度 |
|------|------|------|-----------|-------------|--------|
| `isUndecorated = true` | ❌ | ❌ | ❌ | ❌ | 低 |
| 保留装饰 + WM_NCCALCSIZE | ✅ | ✅ | ✅ | ✅ | 高 |

### 10.2 为什么需要两层窗口过程子类化？

- **JFrame 层**：处理窗口级消息（`WM_NCCALCSIZE`、`WM_NCHITTEST`、`WM_GETMINMAXINFO`）
- **SkiaLayer 层**：处理渲染层的消息穿透和 NC→客户区消息转发

如果只子类化 JFrame，SkiaLayer 的 Canvas 会拦截鼠标消息，Compose 无法正常响应按钮交互。

### 10.3 GC 防护

```kotlin
@Suppress("unused")
private var wndProcCallbackRef: WndProcCallback? = null
```

JNA 回调对象必须被强引用持有，否则 JVM GC 会回收该对象，导致 native 回调指针悬空，引发进程崩溃。

### 10.4 坐标系统

项目中涉及三套坐标系统：

1. **屏幕坐标**：Win32 消息 `lParam` 中的绝对屏幕坐标
2. **窗口坐标**：相对于窗口左上角的坐标（用于命中测试）
3. **Compose 坐标**：`boundsInWindow()` 返回的像素坐标（与窗口坐标一致）

JFrame 层使用 `GetWindowRect` + 减法转换，SkiaLayer 层使用 `ScreenToClient` 转换。

---

## 十一、已知限制

1. **仅 Windows 平台**：Win32 API 子类化仅适用于 Windows，Android/iOS 平台不受影响
2. **SkiaLayer 查找依赖延迟**：首次安装时 SkiaLayer 可能未创建，使用 200ms 定时器重试（仅重试一次）
3. **DWM 圆角仅 Win11**：`DWMWA_WINDOW_CORNER_PREFERENCE` 属性在 Windows 10 及以下版本不可用
4. **单显示器最大化**：`WM_GETMINMAXINFO` 处理基于当前显示器工作区域，跨显示器场景由系统自动处理
