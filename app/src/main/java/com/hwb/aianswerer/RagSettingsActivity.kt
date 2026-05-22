package com.hwb.aianswerer

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.TopBarWithBack
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import com.hwb.aianswerer.utils.LanguageUtil

/**
 * RAG 设置页（Phase 5.2）
 *
 * 提供以下配置：
 *  - 总开关 (ragEnabled)
 *  - 检索 topK (1..20)
 *  - 阈值 (0.0..1.0)
 *  - 单次最大上下文字符数 (200..8000)
 *  - Embedding URL / Model / Key（独立或沿用 chat key）
 *
 * 所有改动立即 commit 到 MMKV（点 "保存" 时统一落盘并 Toast 反馈）。
 * 不在此页触发 RetrievalEngine 重建——仅影响后续检索调用的读取行为。
 */
class RagSettingsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAnswererTheme {
                RagSettingsScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun RagSettingsScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(AppConfig.getRagEnabled()) }
    var topK by remember { mutableIntStateOf(AppConfig.getRagTopK()) }
    var threshold by remember { mutableFloatStateOf(AppConfig.getRagThreshold()) }
    var maxContext by remember { mutableIntStateOf(AppConfig.getRagMaxContext()) }
    var embedUrl by remember { mutableStateOf(AppConfig.getEmbedUrl()) }
    var embedModel by remember { mutableStateOf(AppConfig.getEmbedModel()) }
    var useChatKey by remember { mutableStateOf(AppConfig.getEmbedUseChatKey()) }
    // 读独立 key（不要走 getEmbedKey 的回落逻辑，避免回显 chat key）
    var embedKey by remember { mutableStateOf(AppConfig.getEmbedKeyRaw()) }

    Scaffold(
        topBar = {
            TopBarWithBack(
                title = stringResource(R.string.rag_settings_title),
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            // ---- 开关 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.rag_enabled),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text(
                text = stringResource(R.string.rag_enabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ---- topK ----
            Text(
                text = stringResource(R.string.rag_top_k_label) + ": $topK",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = topK.toFloat(),
                onValueChange = { topK = it.toInt().coerceIn(1, 20) },
                valueRange = 1f..20f,
                steps = 19
            )

            Spacer(Modifier.height(8.dp))

            // ---- threshold ----
            Text(
                text = stringResource(R.string.rag_threshold_label) + ": ${"%.2f".format(threshold)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = threshold,
                onValueChange = { threshold = (it * 100).toInt() / 100f },
                valueRange = 0f..1f
            )

            Spacer(Modifier.height(8.dp))

            // ---- maxContext ----
            Text(
                text = stringResource(R.string.rag_max_context_label) + ": $maxContext",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = maxContext.toFloat(),
                onValueChange = { maxContext = (it.toInt() / 100) * 100 },
                valueRange = 200f..8000f
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ---- Embedding URL ----
            OutlinedTextField(
                value = embedUrl,
                onValueChange = { embedUrl = it },
                label = { Text(stringResource(R.string.rag_embed_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(Modifier.height(8.dp))

            // ---- Embedding Model ----
            OutlinedTextField(
                value = embedModel,
                onValueChange = { embedModel = it },
                label = { Text(stringResource(R.string.rag_embed_model)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            // ---- use chat key ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(R.string.rag_embed_use_chat_key))
                Switch(checked = useChatKey, onCheckedChange = { useChatKey = it })
            }

            Spacer(Modifier.height(8.dp))

            // ---- embed key（独立）----
            OutlinedTextField(
                value = embedKey,
                onValueChange = { embedKey = it },
                label = { Text(stringResource(R.string.rag_embed_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(Modifier.height(24.dp))

            // ---- 保存 ----
            Button(
                onClick = {
                    val urlOk = embedUrl.trim().isNotEmpty()
                    if (!urlOk) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.rag_toast_url_invalid),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    AppConfig.saveRagEnabled(enabled)
                    AppConfig.saveRagTopK(topK)
                    AppConfig.saveRagThreshold(threshold)
                    AppConfig.saveRagMaxContext(maxContext)
                    AppConfig.saveEmbedUrl(embedUrl.trim())
                    AppConfig.saveEmbedModel(embedModel.trim())
                    AppConfig.saveEmbedUseChatKey(useChatKey)
                    AppConfig.saveEmbedKey(embedKey.trim())
                    Toast.makeText(
                        context,
                        context.getString(R.string.kb_save_done),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.kb_save))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
