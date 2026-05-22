package com.hwb.aianswerer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.db.entities.KbDocument
import com.hwb.aianswerer.db.entities.KnowledgeBase
import com.hwb.aianswerer.parser.DocumentParserFactory
import com.hwb.aianswerer.rag.KbRepository
import com.hwb.aianswerer.ui.components.ImportProgressDialog
import com.hwb.aianswerer.ui.components.TopBarWithBack
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import com.hwb.aianswerer.utils.LanguageUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 知识库管理 Activity（Phase 5.1）
 *
 * 单 Activity，内部用 `selectedKb` state 切换两个视图：
 *  - selectedKb == null → 知识库列表（FAB 新建、点击进入 / 长按删除 / Switch 启用）
 *  - selectedKb != null → 该库的文档列表（FAB 导入文档、列表项可删除）
 *
 * 不进行 RetrievalEngine 重建——KbRepository 内部已在导入/删除后调用 invalidate()。
 */
class KnowledgeBaseActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAnswererTheme {
                KnowledgeBaseScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun KnowledgeBaseScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var kbs by remember { mutableStateOf<List<KbRepository.KbWithStats>>(emptyList()) }
    var selectedKb by remember { mutableStateOf<KnowledgeBase?>(null) }
    var docs by remember { mutableStateOf<List<KbDocument>>(emptyList()) }

    // 导入进度
    var importStep by remember { mutableStateOf<KbRepository.ImportStep?>(null) }
    var importDone by remember { mutableIntStateOf(0) }
    var importTotal by remember { mutableIntStateOf(0) }
    var importFileName by remember { mutableStateOf("") }

    // 弹窗
    var showCreateDialog by remember { mutableStateOf(false) }
    var kbToDelete by remember { mutableStateOf<KnowledgeBase?>(null) }
    var docToDelete by remember { mutableStateOf<KbDocument?>(null) }

    // 初次加载 / 选择库变化 时刷新
    LaunchedEffect(Unit) {
        kbs = KbRepository.listKnowledgeBases(context)
    }
    LaunchedEffect(selectedKb) {
        selectedKb?.let {
            docs = KbRepository.listDocuments(context, it.id)
        }
    }

    // SAF 文件选择
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || selectedKb == null) return@rememberLauncherForActivityResult
        val kbId = selectedKb!!.id
        val fileName = queryFileName(context, uri) ?: "unknown"
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in DocumentParserFactory.supportedExtensions) {
            Toast.makeText(
                context,
                context.getString(R.string.kb_toast_unsupported_type, ext),
                Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        // 获得持久权限（部分 provider 需）
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // 非 persistable 也无妨
        }
        importFileName = fileName
        importDone = 0
        importTotal = 0
        importStep = KbRepository.ImportStep.PARSE
        scope.launch {
            // 查询 sizeBytes（SAF 提供 OpenableColumns.SIZE）
            val sizeBytes = queryFileSize(context, uri) ?: 0L
            val result = KbRepository.importDocument(
                ctx = context,
                kbId = kbId,
                uri = uri,
                fileName = fileName,
                sizeBytes = sizeBytes,
                progress = object : KbRepository.ProgressCallback {
                    override fun onStep(step: KbRepository.ImportStep, done: Int, total: Int) {
                        importStep = step
                        importDone = done
                        importTotal = total
                    }
                }
            )
            importStep = null
            when (result) {
                is KbRepository.ImportResult.Success -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.kb_toast_import_done, result.chunkCount),
                        Toast.LENGTH_SHORT
                    ).show()
                    docs = KbRepository.listDocuments(context, kbId)
                }
                is KbRepository.ImportResult.Unsupported -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.kb_toast_unsupported_type, result.ext),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is KbRepository.ImportResult.Failure -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.kb_toast_import_failed, result.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopBarWithBack(
                title = selectedKb?.let { it.name } ?: stringResource(R.string.kb_title),
                onBackClick = {
                    if (selectedKb != null) selectedKb = null
                    else onBackClick()
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedKb == null) {
                        showCreateDialog = true
                    } else {
                        // SAF 不支持精确扩展过滤；MIME 通配 + 应用层校验
                        pickFileLauncher.launch(arrayOf("*/*"))
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            if (selectedKb == null) {
                // ---- 库列表 ----
                if (kbs.isEmpty()) {
                    EmptyHint(text = stringResource(R.string.kb_empty))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(kbs, key = { it.kb.id }) { stats ->
                            val kb = stats.kb
                            KbRow(
                                kb = kb,
                                docCount = stats.docCount,
                                chunkCount = stats.chunkCount,
                                onClick = { selectedKb = kb },
                                onToggleEnabled = { enabled ->
                                    scope.launch {
                                        KbRepository.setKbEnabled(context, kb.id, enabled)
                                        kbs = KbRepository.listKnowledgeBases(context)
                                    }
                                },
                                onPriorityChange = { p ->
                                    scope.launch {
                                        KbRepository.setKbPriority(context, kb.id, p)
                                        kbs = KbRepository.listKnowledgeBases(context)
                                    }
                                },
                                onDelete = { kbToDelete = kb }
                            )
                        }
                    }
                }
            } else {
                // ---- 文档子页 ----
                Text(
                    text = stringResource(R.string.kb_docs_hint, docs.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                HorizontalDivider()
                if (docs.isEmpty()) {
                    EmptyHint(text = stringResource(R.string.kb_doc_empty))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(docs, key = { it.id }) { doc ->
                            DocRow(doc = doc, onDelete = { docToDelete = doc })
                        }
                    }
                }
            }
        }
    }

    // ---- 新建知识库对话框 ----
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.kb_create)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.kb_name_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        Toast.makeText(context, R.string.kb_toast_name_empty, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    scope.launch {
                        val r = KbRepository.createKnowledgeBase(context, trimmed)
                        if (r.isSuccess) {
                            kbs = KbRepository.listKnowledgeBases(context)
                            showCreateDialog = false
                        } else {
                            val msg = r.exceptionOrNull()?.message ?: ""
                            Toast.makeText(
                                context,
                                context.getString(R.string.kb_create_failed, msg),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) { Text(stringResource(R.string.kb_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.kb_cancel))
                }
            }
        )
    }

    // ---- 删除知识库确认 ----
    kbToDelete?.let { kb ->
        AlertDialog(
            onDismissRequest = { kbToDelete = null },
            title = { Text(stringResource(R.string.kb_confirm_delete_title)) },
            text = { Text(stringResource(R.string.kb_confirm_delete_msg, kb.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        KbRepository.deleteKnowledgeBase(context, kb.id)
                        kbs = KbRepository.listKnowledgeBases(context)
                        kbToDelete = null
                    }
                }) { Text(stringResource(R.string.kb_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { kbToDelete = null }) {
                    Text(stringResource(R.string.kb_cancel))
                }
            }
        )
    }

    // ---- 删除文档确认 ----
    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text(stringResource(R.string.kb_confirm_delete_title)) },
            text = { Text(stringResource(R.string.kb_confirm_delete_msg, doc.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        KbRepository.deleteDocument(context, doc.id)
                        selectedKb?.let { docs = KbRepository.listDocuments(context, it.id) }
                        docToDelete = null
                    }
                }) { Text(stringResource(R.string.kb_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text(stringResource(R.string.kb_cancel))
                }
            }
        )
    }

    // ---- 进度对话框 ----
    ImportProgressDialog(
        fileName = importFileName,
        step = importStep,
        done = importDone,
        total = importTotal,
        onCancel = null // 暂不支持取消（OkHttp 请求中段会让 DB 状态不一致）
    )
}

@Composable
private fun KbRow(
    kb: KnowledgeBase,
    docCount: Int,
    chunkCount: Int,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = kb.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = kb.enabled, onCheckedChange = onToggleEnabled)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.kb_action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.kb_meta, kb.embedModel, docCount, chunkCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.kb_priority_label) + ": ${kb.priority}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = kb.priority.toFloat(),
                onValueChange = { onPriorityChange(it.toInt().coerceIn(0, 100)) },
                valueRange = 0f..100f
            )
        }
    }
}

@Composable
private fun DocRow(doc: KbDocument, onDelete: () -> Unit) {
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${doc.fileType.uppercase()} · ${doc.chunkCount} chunks · ${df.format(Date(doc.importedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.kb_action_delete),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable(onClick = onDelete)
        )
    }
    HorizontalDivider()
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    val resolver = context.contentResolver
    val cursor = resolver.query(uri, null, null, null, null) ?: return null
    cursor.use { c ->
        if (!c.moveToFirst()) return null
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx < 0) return null
        return c.getString(idx)
    }
}

private fun queryFileSize(context: Context, uri: Uri): Long? {
    val resolver = context.contentResolver
    val cursor = resolver.query(uri, null, null, null, null) ?: return null
    cursor.use { c ->
        if (!c.moveToFirst()) return null
        val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (idx < 0) return null
        if (c.isNull(idx)) return null
        return c.getLong(idx)
    }
}
