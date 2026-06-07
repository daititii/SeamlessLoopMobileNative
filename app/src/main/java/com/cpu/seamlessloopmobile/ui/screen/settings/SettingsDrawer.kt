package com.cpu.seamlessloopmobile.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 从 MainScreen 中完美抽取出来的侧边设置抽屉组件喵！(๑•̀ㅂ•́)و✧
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    isVisible: Boolean,
    onClose: () -> Unit,
    onRescan: (android.content.Context) -> Unit,
    onSyncPc: () -> Unit,
    onExportDatabase: () -> Unit,
    seamlessLoopCountLimit: Int,
    onSeamlessLoopCountLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 可点击遮罩背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
            )

            // 侧边设置抽屉（滑入滑出）
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { -it }
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { -it }
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .align(Alignment.CenterStart)
            ) {
                ModalDrawerSheet(modifier = Modifier.fillMaxSize()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    SettingsScreen(
                        onClose = onClose,
                        onRescan = { onRescan(context) },
                        onSyncPc = onSyncPc,
                        onExportDatabase = onExportDatabase,
                        seamlessLoopCountLimit = seamlessLoopCountLimit,
                        onSeamlessLoopCountLimitChange = onSeamlessLoopCountLimitChange
                    )
                }
            }
        }
    }
}
