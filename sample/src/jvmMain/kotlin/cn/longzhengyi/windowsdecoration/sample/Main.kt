package cn.longzhengyi.windowsdecoration.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Borderless TitleBar Sample",
        state = windowState,
    ) {
        // 设置最小窗口尺寸
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(400, 300)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 自绘标题栏（使用BorderlessTitleBarScaffold自动管理无边框支持 + 窗口状态，内部仅自绘UI）
            CustomBorderlessTitleBar(
                title = "Borderless TitleBar Sample",
                windowState = windowState,
                onClose = { exitApplication() },
            )

            // 应用内容
            SampleApp()
        }
    }
}
