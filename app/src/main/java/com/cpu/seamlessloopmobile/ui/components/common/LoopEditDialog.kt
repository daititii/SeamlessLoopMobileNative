package com.cpu.seamlessloopmobile.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpu.seamlessloopmobile.ui.theme.SeamlessLoopColors

/**
 * 循环点手动数值编辑弹窗喵！(๑•̀ㅂ•́)و✧
 * 从原 FineTuneComponents 优雅抽离，并已完美消除硬编码颜色。
 */
@Composable
fun LoopEditDialog(
    visible: Boolean,
    isStart: Boolean,
    samplesValue: String,
    timeValue: String,
    onValueSamplesChange: (String) -> Unit,
    onValueTimeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { 
                Text(
                    text = if (isStart) "修改循环起点 (A)" else "修改循环终点 (B)", 
                    color = SeamlessLoopColors.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = samplesValue,
                        onValueChange = onValueSamplesChange,
                        label = { Text("采样数 (Samples)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SeamlessLoopColors.White, 
                            unfocusedTextColor = SeamlessLoopColors.White,
                            focusedBorderColor = SeamlessLoopColors.PurpleAccent,
                            unfocusedBorderColor = SeamlessLoopColors.Gray.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = timeValue,
                        onValueChange = onValueTimeChange,
                        label = { Text("时间 (Seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SeamlessLoopColors.White, 
                            unfocusedTextColor = SeamlessLoopColors.White,
                            focusedBorderColor = SeamlessLoopColors.PurpleAccent,
                            unfocusedBorderColor = SeamlessLoopColors.Gray.copy(alpha = 0.5f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("确定", color = SeamlessLoopColors.PurpleAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = SeamlessLoopColors.Gray)
                }
            },
            containerColor = SeamlessLoopColors.DarkBgGradientStart,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
