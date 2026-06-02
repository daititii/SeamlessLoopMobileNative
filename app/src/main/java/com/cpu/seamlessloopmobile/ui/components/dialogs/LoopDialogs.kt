package com.cpu.seamlessloopmobile.ui.components.dialogs

import androidx.compose.runtime.*
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.viewmodel.MusicDialog
import com.cpu.seamlessloopmobile.ui.components.common.LoopEditDialog
import java.util.Locale

@Composable
fun LoopEditDialogWrapper(
    dialog: MusicDialog.LoopEdit,
    onDismiss: () -> Unit
) {
    var samplesValue by remember { mutableStateOf(dialog.initialSamples.toString()) }
    var timeValue by remember { 
        val sr = NativeAudio.getSampleRate()
        val seconds = dialog.initialSamples.toDouble() / if(sr > 0) sr else 44100
        mutableStateOf(String.format(Locale.US, "%.3f", seconds))
    }

    LoopEditDialog(
        visible = true,
        isStart = dialog.isStart,
        samplesValue = samplesValue,
        timeValue = timeValue,
        onValueSamplesChange = { samplesValue = it; timeValue = "" },
        onValueTimeChange = { timeValue = it; samplesValue = "" },
        onDismiss = onDismiss,
        onConfirm = {
            val newSamples = samplesValue.toLongOrNull()
            val newTime = timeValue.toDoubleOrNull()
            
            if (newSamples != null) {
                dialog.onConfirm(newSamples)
            } else if (newTime != null) {
                val sr = NativeAudio.getSampleRate()
                dialog.onConfirm((newTime * sr).toLong())
            }
            onDismiss()
        }
    )
}
