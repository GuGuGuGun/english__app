package com.kaoyan.wordhelper.ui.screen

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.data.model.AIPresets
import com.kaoyan.wordhelper.data.model.PronunciationSource
import com.kaoyan.wordhelper.ui.viewmodel.AILabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AILabScreen(
    viewModel: AILabViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var showCostHintDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (showCostHintDialog) {
        AlertDialog(
            onDismissRequest = { showCostHintDialog = false },
            title = { Text(text = "AI 成本提示") },
            text = {
                Text(
                    text = "AI 请求消耗你自备服务商的额度。建议优先使用缓存内容，避免频繁点击重新生成。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showCostHintDialog = false },
                    modifier = Modifier.testTag("ai_cost_dialog_confirm")
                ) {
                    Text(text = "知道了")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(text = "隐私与风险说明") },
            text = {
                Text(
                    text = "API Key 仅用于向你配置的服务商发起请求，密钥保存在本机加密存储中，不会上传至开发者服务器。Root 设备仍存在额外泄露风险。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showPrivacyDialog = false },
                    modifier = Modifier.testTag("ai_privacy_dialog_confirm")
                ) {
                    Text(text = "知道了")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "实验室") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "启用 AI 增强", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "关闭后学习页和查词页的 AI 入口会隐藏",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.enabled,
                                onCheckedChange = viewModel::updateEnabled,
                                modifier = Modifier.testTag("ai_enabled_switch")
                            )
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "服务商预设", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.presets.forEach { preset ->
                                FilterChip(
                                    selected = uiState.selectedProviderName == preset.name,
                                    onClick = { viewModel.selectPreset(preset) },
                                    label = { Text(text = preset.name) }
                                )
                            }
                            FilterChip(
                                selected = uiState.selectedProviderName == AIPresets.CUSTOM_NAME,
                                onClick = viewModel::markCustomPreset,
                                label = { Text(text = AIPresets.CUSTOM_NAME) }
                            )
                        }
                        OutlinedTextField(
                            value = uiState.baseUrl,
                            onValueChange = viewModel::updateBaseUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_base_url_input"),
                            label = { Text(text = "Base URL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "模型与密钥", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = uiState.modelName,
                            onValueChange = viewModel::updateModelName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_model_input"),
                            label = { Text(text = "模型名称") },
                            placeholder = { Text(text = "如 glm-4-flash / gpt-3.5-turbo") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.apiKey,
                            onValueChange = viewModel::updateApiKey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_api_key_input"),
                            label = { Text(text = "API Key") },
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::testConnection,
                                enabled = !uiState.isSaving && !uiState.isTesting,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_test_connection")
                            ) {
                                if (uiState.isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(text = "测试连接")
                                }
                            }
                            Button(
                                onClick = viewModel::saveConfig,
                                enabled = !uiState.isSaving && !uiState.isTesting,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_save_config")
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(text = "保存配置")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "使用说明", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "1. AI 请求消耗你自己的服务商额度，请注意成本。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "2. API Key 仅保存在本机加密存储，不经过开发者服务器。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "3. Root 设备存在额外密钥泄露风险，请谨慎使用。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { showCostHintDialog = true },
                                modifier = Modifier.testTag("ai_cost_entry")
                            ) {
                                Text(text = "查看成本提示")
                            }
                            TextButton(
                                onClick = { showPrivacyDialog = true },
                                modifier = Modifier.testTag("ai_privacy_entry")
                            ) {
                                Text(text = "查看隐私说明")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "AI 生成内容仅供学习参考，请结合教材自行甄别。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "发音实验室", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (uiState.pronunciationEnabled) "功能状态：已开启" else "功能状态：已关闭",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.pronunciationEnabled,
                                onCheckedChange = viewModel::updatePronunciationEnabled,
                                modifier = Modifier.testTag("lab_pronunciation_switch")
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PronunciationSource.entries.forEach { source ->
                                FilterChip(
                                    selected = uiState.pronunciationSource == source,
                                    onClick = { viewModel.updatePronunciationSource(source) },
                                    label = { Text(text = source.label) },
                                    enabled = uiState.pronunciationEnabled,
                                    modifier = Modifier.testTag("lab_pronunciation_source_${source.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = when (uiState.pronunciationSource) {
                                PronunciationSource.FREE_DICTIONARY ->
                                    "优先来源：Free Dictionary（查询失败会自动回退 Youdao）。"
                                PronunciationSource.YOUDAO ->
                                    "优先来源：Youdao（dict.youdao.com）。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "说明：仅在开关开启时，学习页和查词详情中的发音按钮才显示；内置词库音频始终优先使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "认词自动发音", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "认词模式每次切到新单词时自动播放发音",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.recognitionAutoPronounceEnabled,
                                onCheckedChange = viewModel::updateRecognitionAutoPronounceEnabled,
                                enabled = uiState.pronunciationEnabled,
                                modifier = Modifier.testTag("lab_pronunciation_auto_recognition_switch")
                            )
                        }
                        Text(
                            text = "补充：发音资源来自公开接口，个别词可能无音频或网络波动导致播放失败。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "算法实验室", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (uiState.algorithmV4Enabled) "当前版本：V4 验证" else "当前版本：V3 稳定",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.algorithmV4Enabled,
                                onCheckedChange = viewModel::updateAlgorithmV4Enabled,
                                modifier = Modifier.testTag("lab_algorithm_switch")
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "ML自适应学习", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (uiState.mlAdaptiveEnabled) {
                                        "已开启：按个人遗忘风险动态微调复习间隔"
                                    } else {
                                        "已关闭：仅使用基础调度策略"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.mlAdaptiveEnabled,
                                onCheckedChange = viewModel::updateMlAdaptiveEnabled,
                                modifier = Modifier.testTag("lab_ml_adaptive_switch")
                            )
                        }
                        Text(
                            text = "当前训练样本：${uiState.mlSampleCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("lab_ml_sample_count")
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "ML增强细则",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.testTag("lab_ml_details")
                        )
                        Text(
                            text = "1) 特征输入：当前使用 12 维特征（EF、学习时段、星期、会话进度、间隔对数、历史正确率、响应时长、连续正确、距上次复习时长、全局保留率等）进行遗忘概率预测。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "2) 冷启动混合：样本<50 以人群先验为主；50-200 线性混合先验与个人模型；>200 逐步切换为个人模型主导。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "3) 调度边界：以 90% 目标保留率微调间隔，单次调整限制在原间隔 ±50%，并且最小间隔为 1 天；EF 限定在 1.30-3.00。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "4) 在线训练节奏：每次复习即时单步更新；每 10 个样本做一次 mini-batch 修正；样本上限 5000，超限自动裁剪旧样本。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "5) 指标更新：近期保留率/准确率按窗口样本动态计算；响应阈值按“均值+1.5σ”动态估计（范围 3000-15000ms），未启用 ML 时回退 6000ms。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "6) 安全回退：ML 关闭或置信度极低时，直接使用基础调度，不改动原有学习路径。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "V3 稳定：已按天为记忆单位推进（最小间隔 1 天），节奏更平滑。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "V4 验证：结合答题耗时动态降级评分，纠偏更激进。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "新词打乱记忆", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (uiState.newWordsShuffleEnabled) {
                                        "已开启：新词会按随机顺序穿插学习"
                                    } else {
                                        "已关闭：新词按原始顺序加入学习队列"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.newWordsShuffleEnabled,
                                onCheckedChange = viewModel::updateNewWordsShuffleEnabled,
                                modifier = Modifier.testTag("lab_new_words_shuffle_switch")
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "算法说明书",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.testTag("lab_algorithm_manual")
                        )
                        Text(
                            text = "1) 时间单位：V3 与 V4 均以“天”为记忆间隔单位，刷新时间点为次日 04:00。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "2) 认词评分：不认识(AGAIN) / 模糊(HARD) / 认识(GOOD)。V3 学习阶段会映射到 1 天或 2 天间隔。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "3) V3 核心：按 Ease 因子进行保守增长；满足间隔>=21天、复习次数>=2、Ease>=2.3 判定为已掌握。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "4) V4 核心：间隔会贴近艾宾浩斯阶梯(1/2/6/14/30天)，并在短时重复复习时触发衰减保护。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "5) 拼写模式：失败会重置到学习态；重试成功/有提示/完美拼写会对应不同增长幅度。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "6) 版本切换仅影响后续调度策略，不会回滚已产生的历史记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
