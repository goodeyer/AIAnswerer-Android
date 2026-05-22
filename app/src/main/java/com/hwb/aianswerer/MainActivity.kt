package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.config.AppConfig
import com.hwb.aianswerer.ui.components.TopBarWithMenu
import com.hwb.aianswerer.ui.dialogs.LanguageSelectionDialog
import com.hwb.aianswerer.ui.dialogs.ModelSetupReminderDialog
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import com.hwb.aianswerer.utils.LanguageUtil

/**
 * 主界面Activity
 * 负责权限管理、答题模式控制和用户界面展示
 */
class MainActivity : ComponentActivity() {

    private var isAnswerModeActive by mutableStateOf(false)
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private lateinit var defaultQuestionType: String
    private var selectedQuestionTypes by mutableStateOf<Set<String>>(emptySet())
    private var questionScope by mutableStateOf("")
    private var cropMode by mutableStateOf(AppConfig.CROP_MODE_FULL)

    // Dialog状态管理
    private var showLanguageDialog by mutableStateOf(false)
    private var showModelSetupDialog by mutableStateOf(false)
    private var dialogQueue = mutableStateListOf<String>()

    companion object {
        const val DIALOG_LANGUAGE = "language"
        const val DIALOG_MODEL_SETUP = "model_setup"
    }

    /**
     * 应用语言配置
     * 在Activity创建前应用用户选择的语言设置
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.attachBaseContext(newBase))
    }

    // 截图权限请求
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            screenCaptureResultCode = result.resultCode
            screenCaptureData = result.data

            // 检查悬浮窗权限
            if (checkOverlayPermission()) {
                startAnswerMode()
            } else {
                requestOverlayPermission()
            }
        } else {
            Toast.makeText(
                this,
                getString(R.string.toast_permission_capture_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 悬浮窗权限请求
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            // 如果已经有截图权限，直接启动
            if (screenCaptureResultCode != null) {
                startAnswerMode()
            } else {
                requestScreenCapturePermission()
            }
        } else {
            Toast.makeText(
                this,
                getString(R.string.toast_permission_overlay_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 加载答题设置
        selectedQuestionTypes = AppConfig.getQuestionTypes()
        questionScope = AppConfig.getQuestionScope()
        cropMode = AppConfig.getCropMode()

        // 检查并添加Dialog到队列
        checkAndQueueDialogs()

        setContent {
            AIAnswererTheme {
                MainScreen(
                    isAnswerModeActive = isAnswerModeActive,
                    selectedQuestionTypes = selectedQuestionTypes,
                    questionScope = questionScope,
                    cropMode = cropMode,
                    showLanguageDialog = showLanguageDialog,
                    showModelSetupDialog = showModelSetupDialog,
                    onToggleAnswerMode = {
                        if (isAnswerModeActive) {
                            stopAnswerMode()
                        } else {
                            checkAndRequestPermissions()
                        }
                    },
                    onQuestionTypesChanged = { types ->
                        selectedQuestionTypes = types
                        AppConfig.saveQuestionTypes(types)
                    },
                    onQuestionScopeChanged = { scope ->
                        questionScope = scope
                        AppConfig.saveQuestionScope(scope)
                    },
                    onCropModeChanged = { mode ->
                        cropMode = mode
                        AppConfig.saveCropMode(mode)
                    },
                    onLanguageDialogDismiss = { dismissLanguageDialog() },
                    onLanguageConfirmed = { handleLanguageConfirmed() },
                    onModelSetupDismiss = { dismissModelSetupDialog() },
                    onGoToSettings = { navigateToModelSettings() },
                    onMenuItemClick = { menuItem ->
                        when (menuItem) {
                            MenuItem.SETTINGS -> {
                                startActivity(Intent(this, SettingsActivity::class.java))
                            }

                            MenuItem.ABOUT -> {
                                startActivity(Intent(this, AboutActivity::class.java))
                            }

                            MenuItem.KNOWLEDGE_BASE -> {
                                startActivity(Intent(this, KnowledgeBaseActivity::class.java))
                            }

                            MenuItem.RAG_SETTINGS -> {
                                startActivity(Intent(this, RagSettingsActivity::class.java))
                            }
                        }
                    }
                )
            }
        }
    }

    /**
     * 检查并请求所需权限
     */
    private fun checkAndRequestPermissions() {
        // 首先检查模型是否已配置
        if (!AppConfig.isApiConfigValid()) {
            Toast.makeText(
                this,
                getString(R.string.toast_model_not_configured),
                Toast.LENGTH_LONG
            ).show()
            // 显示模型设置提醒Dialog
            if (!dialogQueue.contains(DIALOG_MODEL_SETUP)) {
                dialogQueue.add(DIALOG_MODEL_SETUP)
                processDialogQueue()
            }
            return
        }

        // 先检查悬浮窗权限
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        // 再检查截图权限
        requestScreenCapturePermission()
    }

    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    /**
     * 请求截图权限
     */
    private fun requestScreenCapturePermission() {
        val screenCaptureManager = ScreenCaptureManager(this)
        val intent = screenCaptureManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    /**
     * 启动答题模式
     */
    private fun startAnswerMode() {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            if (screenCaptureResultCode != null && screenCaptureData != null) {
                putExtra("resultCode", screenCaptureResultCode!!)
                putExtra("data", screenCaptureData)
            }
            // 传递答题设置
            putStringArrayListExtra("questionTypes", ArrayList(selectedQuestionTypes))
            putExtra("questionScope", questionScope)
            putExtra("cropMode", cropMode)
        }

        // Android 8.0+ 使用 startForegroundService，否则使用 startService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        isAnswerModeActive = true
        Toast.makeText(this, getString(R.string.toast_mode_started), Toast.LENGTH_SHORT).show()

        // 将应用移至后台
        moveTaskToBack(true)
    }

    /**
     * 停止答题模式
     */
    private fun stopAnswerMode() {
        stopService(Intent(this, FloatingWindowService::class.java))
        isAnswerModeActive = false
        screenCaptureResultCode = null
        screenCaptureData = null
        Toast.makeText(this, getString(R.string.toast_mode_stopped), Toast.LENGTH_SHORT).show()
    }

    // ========== Dialog管理方法 ==========

    /**
     * 检查并添加Dialog到队列
     */
    private fun checkAndQueueDialogs() {
        when {
            AppConfig.isFirstLaunch() -> {
                dialogQueue.add(DIALOG_LANGUAGE)
            }

            !AppConfig.isApiConfigValid() -> {
                dialogQueue.add(DIALOG_MODEL_SETUP)
            }
        }
        processDialogQueue()
    }

    /**
     * 处理Dialog队列，确保一个Dialog显示完成后才显示下一个
     */
    private fun processDialogQueue() {
        if (dialogQueue.isNotEmpty()) {
            val nextDialog = dialogQueue.first()
            when (nextDialog) {
                DIALOG_LANGUAGE -> showLanguageDialog = true
                DIALOG_MODEL_SETUP -> showModelSetupDialog = true
            }
        }
    }

    /**
     * 关闭语言选择Dialog
     */
    private fun dismissLanguageDialog() {
        showLanguageDialog = false
        dialogQueue.remove(DIALOG_LANGUAGE)
        processDialogQueue()
    }

    /**
     * 处理语言选择确认后的操作
     */
    private fun handleLanguageConfirmed() {
        dismissLanguageDialog()

        // 如果是首次启动，语言选择完成后添加模型设置提醒
        if (dialogQueue.isEmpty() && !AppConfig.isApiConfigValid()) {
            dialogQueue.add(DIALOG_MODEL_SETUP)
        }

        // 重启Activity以应用语言设置
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * 关闭模型设置提醒Dialog
     */
    private fun dismissModelSetupDialog() {
        showModelSetupDialog = false
        dialogQueue.remove(DIALOG_MODEL_SETUP)
        processDialogQueue()
    }

    /**
     * 跳转到模型设置页面
     */
    private fun navigateToModelSettings() {
        dismissModelSetupDialog()
        startActivity(Intent(this, ModelSettingsActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isAnswerModeActive) {
            stopAnswerMode()
        }
    }
}

/**
 * 菜单项枚举
 */
enum class MenuItem {
    SETTINGS,
    ABOUT,
    KNOWLEDGE_BASE,
    RAG_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun MainScreen(
    isAnswerModeActive: Boolean = false,
    selectedQuestionTypes: Set<String> = setOf("单选题"),
    questionScope: String = "",
    cropMode: String = AppConfig.CROP_MODE_FULL,
    showLanguageDialog: Boolean = false,
    showModelSetupDialog: Boolean = false,
    onToggleAnswerMode: () -> Unit = {},
    onQuestionTypesChanged: (Set<String>) -> Unit = {},
    onQuestionScopeChanged: (String) -> Unit = {},
    onCropModeChanged: (String) -> Unit = {},
    onLanguageDialogDismiss: () -> Unit = {},
    onLanguageConfirmed: () -> Unit = {},
    onModelSetupDismiss: () -> Unit = {},
    onGoToSettings: () -> Unit = {},
    onMenuItemClick: (MenuItem) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopBarWithMenu(
                title = stringResource(R.string.main_title),
                menuContent = {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_settings)) },
                        onClick = {
                            onMenuItemClick(MenuItem.SETTINGS)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_knowledge_base)) },
                        onClick = {
                            onMenuItemClick(MenuItem.KNOWLEDGE_BASE)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_rag_settings)) },
                        onClick = {
                            onMenuItemClick(MenuItem.RAG_SETTINGS)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_about)) },
                        onClick = {
                            onMenuItemClick(MenuItem.ABOUT)
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            // 性能优化：提前获取Context，避免滚动时频繁重组
            val context = LocalContext.current

            // 使用Box布局实现固定底部按钮
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 96.dp), // 底部padding为按钮高度(56dp) + 按钮padding(16dp*2) + 额外间距(8dp)
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // 添加顶部间距，使内容更美观
                    Spacer(modifier = Modifier.height(16.dp))

                    // 状态提示
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAnswerModeActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.status_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isAnswerModeActive)
                                    stringResource(R.string.status_running)
                                else stringResource(R.string.status_stopped),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isAnswerModeActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    // 功能说明
                    UsageGuideCard(context = context)

                    Spacer(modifier = Modifier.height(8.dp))

                    // 本次答题设置卡片
                    SessionSettingsCard(
                        selectedQuestionTypes = selectedQuestionTypes,
                        questionScope = questionScope,
                        cropMode = cropMode,
                        onQuestionTypesChanged = onQuestionTypesChanged,
                        onQuestionScopeChanged = onQuestionScopeChanged,
                        onCropModeChanged = onCropModeChanged,
                        enabled = !isAnswerModeActive
                    )
                }

                // 固定在底部的切换按钮
                Button(
                    onClick = onToggleAnswerMode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAnswerModeActive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(
                        text = if (isAnswerModeActive)
                            stringResource(R.string.button_stop_mode)
                        else stringResource(R.string.button_start_mode),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Dialog组件放置在Scaffold外部，确保它们正确显示在最上层
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = onLanguageDialogDismiss,
            onLanguageConfirmed = onLanguageConfirmed
        )
    }

    if (showModelSetupDialog) {
        ModelSetupReminderDialog(
            onDismiss = onModelSetupDismiss,
            onGoToSettings = onGoToSettings
        )
    }
}

/**
 * 可展开/收起的使用说明卡片
 * @param context Android上下文，用于打开链接
 */
@Composable
fun UsageGuideCard(context: Context) {
    // 展开/收起状态
    var isExpanded by remember { mutableStateOf(false) }

    // 展开图标的旋转动画
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_icon_rotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题行，包含展开/收起按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.usage_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 展开/收起图标
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(rotationZ = rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 始终显示的内容
            FeatureItem(stringResource(R.string.usage_step_0), context)
            FeatureItem(stringResource(R.string.usage_step_1), context)
            FeatureItem(stringResource(R.string.usage_step_2), context)
            FeatureItem(stringResource(R.string.usage_step_3), context)
            FeatureItem(stringResource(R.string.usage_step_4), context)
            FeatureItem(stringResource(R.string.usage_step_5), context)

            // 可展开的其余内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    FeatureItem(
                        context = context,
                        text = stringResource(R.string.usage_step_6_text),
                        urlText = stringResource(R.string.link_close_screen_protection),
                        url = stringResource(R.string.usage_step_6_url)
                    )
                    FeatureItem(
                        context = context,
                        text = stringResource(R.string.usage_step_7_text),
                        urlText = stringResource(R.string.usage_step_7_link),
                        url = stringResource(R.string.usage_step_7_url)
                    )
                    FeatureItem(
                        context = context,
                        text = stringResource(R.string.usage_step_8)
                    )
                }
            }
        }
    }
}

/**
 * 功能说明列表项
 * @param text 主要文本内容
 * @param context Android上下文，用于打开链接
 * @param urlText 可选的链接文本
 * @param url 可选的链接URL
 */
@Composable
fun FeatureItem(
    text: String,
    context: Context,
    urlText: String? = null,
    url: String? = null
) {
    // 获取主题颜色和样式
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val bodyMediumStyle = MaterialTheme.typography.bodyMedium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 如果有链接参数，构建带链接的文本
        if (urlText != null && url != null) {
            val annotatedString = remember(text, urlText, url, primaryColor) {
                buildAnnotatedString {
                    // 添加普通文本
                    append(text)

                    // 添加可点击的链接文本
                    pushStringAnnotation(
                        tag = "URL",
                        annotation = url
                    )
                    withStyle(
                        style = SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(urlText)
                    }
                    pop()
                }
            }

            ClickableText(
                text = annotatedString,
                style = bodyMediumStyle.copy(
                    color = onSurfaceColor
                ),
                onClick = { offset ->
                    // 检查点击位置是否在URL上
                    annotatedString.getStringAnnotations(
                        tag = "URL",
                        start = offset,
                        end = offset
                    ).firstOrNull()?.let { annotation ->
                        // 打开外部浏览器
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                MyApplication.getString(R.string.toast_unable_to_open_link),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        } else {
            // 没有链接，直接显示文本
            Text(
                text = text,
                style = bodyMediumStyle,
                color = onSurfaceColor
            )
        }
    }
}

/**
 * 本次答题设置卡片
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionSettingsCard(
    selectedQuestionTypes: Set<String>,
    questionScope: String,
    cropMode: String,
    onQuestionTypesChanged: (Set<String>) -> Unit,
    onQuestionScopeChanged: (String) -> Unit,
    onCropModeChanged: (String) -> Unit,
    enabled: Boolean = true
) {
    // 所有可选的题型
    val allQuestionTypes = listOf(
        stringResource(R.string.question_type_single),
        stringResource(R.string.question_type_multiple),
        stringResource(R.string.question_type_uncertain),
        stringResource(R.string.question_type_blank),
        stringResource(R.string.question_type_essay)
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.session_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.session_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 题型选择标签
            Text(
                text = stringResource(R.string.question_type_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // 题型多选Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy((-6).dp)
            ) {
                allQuestionTypes.forEach { type ->
                    val isSelected = selectedQuestionTypes.contains(type)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newTypes = if (isSelected) {
                                // 至少保留一个题型
                                if (selectedQuestionTypes.size > 1) {
                                    selectedQuestionTypes - type
                                } else {
                                    selectedQuestionTypes
                                }
                            } else {
                                selectedQuestionTypes + type
                            }
                            onQuestionTypesChanged(newTypes)
                        },
                        label = {
                            Text(
                                text = type,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        enabled = enabled,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = enabled,
                            selected = isSelected,
                            borderColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = if (isSelected) 2.dp else 1.dp,
                            selectedBorderWidth = 2.dp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 截图识别模式选择
            Text(
                text = stringResource(R.string.crop_mode_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                // 全屏识别
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            onCropModeChanged(AppConfig.CROP_MODE_FULL)
                        }
                        .padding(vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = cropMode == AppConfig.CROP_MODE_FULL,
                        onClick = { onCropModeChanged(AppConfig.CROP_MODE_FULL) },
                        enabled = enabled
                    )
                    Text(
                        text = stringResource(R.string.crop_mode_full),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // 部分识别（每次）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            onCropModeChanged(AppConfig.CROP_MODE_EACH)
                        }
                        .padding(vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = cropMode == AppConfig.CROP_MODE_EACH,
                        onClick = { onCropModeChanged(AppConfig.CROP_MODE_EACH) },
                        enabled = enabled
                    )
                    Text(
                        text = stringResource(R.string.crop_mode_each),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // 部分识别（单次）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            onCropModeChanged(AppConfig.CROP_MODE_ONCE)
                        }
                        .padding(vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = cropMode == AppConfig.CROP_MODE_ONCE,
                        onClick = { onCropModeChanged(AppConfig.CROP_MODE_ONCE) },
                        enabled = enabled
                    )
                    Text(
                        text = stringResource(R.string.crop_mode_once),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 题目内容范围输入
            OutlinedTextField(
                value = questionScope,
                onValueChange = onQuestionScopeChanged,
                label = { Text(stringResource(R.string.question_scope_label)) },
                placeholder = { Text(stringResource(R.string.question_scope_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = enabled
            )
        }
    }
}
