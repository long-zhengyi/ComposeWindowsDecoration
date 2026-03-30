package cn.longzhengyi.windowsdecoration.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import cn.longzhengyi.windowsdecoration.BorderlessTitleBarScaffold
import cn.longzhengyi.windowsdecoration.windowCloseButton
import cn.longzhengyi.windowsdecoration.windowDragArea
import cn.longzhengyi.windowsdecoration.windowMaximizeButton
import cn.longzhengyi.windowsdecoration.windowMinimizeButton

val TITLE_BAR_HEIGHT = 40.dp

/**
 * 基于 [BorderlessTitleBarScaffold] 实现的自绘标题栏。
 *
 * Win32 管道（helper 安装、isMaximized 追踪）由脚手架处理，
 * 此函数仅负责布局和 UI 绘制。
 *
 * @see BorderlessTitleBarScaffold
 */
@Composable
fun FrameWindowScope.CustomBorderlessTitleBar(
    title: String,
    windowState: WindowState,
    onClose: () -> Unit,
) {
    BorderlessTitleBarScaffold(windowState = windowState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TITLE_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surface)
                .windowDragArea(helper),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 标题文字区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 16.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }

            // 最小化按钮
            TitleBarButton(
                text = "─",
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                onClick = { minimize() },
                modifier = Modifier.windowMinimizeButton(helper),
            )

            // 最大化/还原按钮
            TitleBarButton(
                text = if (isMaximized) "❐" else "□",
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                onClick = { toggleMaximize() },
                modifier = Modifier.windowMaximizeButton(helper),
            )

            // 关闭按钮
            TitleBarButton(
                text = "✕",
                hoverColor = Color(0xFFE81123),
                hoverTextColor = Color.White,
                onClick = onClose,
                modifier = Modifier.windowCloseButton(helper),
            )
        }
    }
}

@Composable
private fun TitleBarButton(
    text: String,
    hoverColor: Color,
    hoverTextColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .width(46.dp)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .background(if (isHovered) hoverColor else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        val textColor = if (isHovered && hoverTextColor != null) {
            hoverTextColor
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
        )
    }
}
