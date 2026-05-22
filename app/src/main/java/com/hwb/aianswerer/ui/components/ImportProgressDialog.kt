package com.hwb.aianswerer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.R
import com.hwb.aianswerer.rag.KbRepository

/**
 * 文档导入进度对话框（Phase 5.5）
 *
 * 用于 KnowledgeBaseActivity 在 SAF 选择文件后展示解析 → 切片 → 向量化 → 落库 进度。
 *
 * 设计：
 * - 当 [step] 为 null 表示未在导入状态，调用方可直接不渲染。
 * - 进度未知（total<=0）时显示不确定 LinearProgressIndicator，已知时显示具体百分比。
 * - 不允许在导入过程中被点击外部关闭（dismissOnClickOutside=false 由 [AlertDialog] 默认即可）。
 */
@Composable
fun ImportProgressDialog(
    fileName: String,
    step: KbRepository.ImportStep?,
    done: Int,
    total: Int,
    onCancel: (() -> Unit)? = null
) {
    if (step == null) return

    AlertDialog(
        onDismissRequest = { /* 导入中禁止外部 dismiss */ },
        title = { Text(text = stringResource(R.string.kb_import_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                val stepText = when (step) {
                    KbRepository.ImportStep.PARSE -> stringResource(R.string.kb_import_step_parse)
                    KbRepository.ImportStep.CHUNK -> stringResource(R.string.kb_import_step_chunk)
                    KbRepository.ImportStep.EMBED -> stringResource(R.string.kb_import_step_embed)
                    KbRepository.ImportStep.SAVE -> stringResource(R.string.kb_import_step_save)
                }
                Text(
                    text = if (total > 0) "$stepText  $done / $total" else stepText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                if (total > 0) {
                    val progress = (done.toFloat() / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.kb_import_cancel))
                }
            }
        }
    )
}
