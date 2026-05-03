package com.yuki.yingdao.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuki.yingdao.data.MediaType
import com.yuki.yingdao.data.TimePressure
import com.yuki.yingdao.ui.YingDaoUiState
import com.yuki.yingdao.ui.components.HighlightCard
import com.yuki.yingdao.ui.components.SectionTitle

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun NewProjectScreen(
    innerPadding: PaddingValues,
    uiState: YingDaoUiState,
    onUpdateDraft: (
        title: String?,
        theme: String?,
        style: String?,
        durationSec: Int?,
        castCount: Int?,
        locations: List<String>?,
        needCaption: Boolean?,
        needVoiceover: Boolean?,
        shootGoal: String?,
        mood: String?,
        highlightSubject: String?,
        soloMode: Boolean?,
        timePressure: TimePressure?,
        mediaType: MediaType?,
    ) -> Unit,
    onGeneratePlan: () -> Unit,
    onBack: () -> Unit,
) {
    val draft = uiState.draft
    val durationOptions = listOf(30, 60, 90)
    val styleOptions = listOf("生活感", "干净明亮", "胶片感", "治愈", "真实记录", "轻复古", "清新温暖", "电影感")
    val locationOptions = listOf("家里", "街边", "咖啡店", "公园", "商场", "餐桌", "旅途中", "校园", "图书馆", "操场")
    val moodOptions = listOf("温和", "松弛", "安静", "热闹", "明亮", "治愈", "轻快")
    var customLocationText by remember { mutableStateOf("") }
    val castCountText = draft.castCount.toString()
    val timePressureOptions = listOf(
        TimePressure.High to "赶时间",
        TimePressure.Medium to "正常节奏",
        TimePressure.Low to "慢慢拍",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionTitle(
            title = "先定你想拍的感觉",
            subtitle = "把你想记录的人、场景和情绪告诉影导，我来帮你排好拍摄顺序。",
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.title,
            onValueChange = { onUpdateDraft(it, null, null, null, null, null, null, null, null, null, null, null, null, null) },
            label = { Text("项目标题") },
            singleLine = true,
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.theme,
            onValueChange = { onUpdateDraft(null, it, null, null, null, null, null, null, null, null, null, null, null, null) },
            label = { Text("想记录的主题") },
            supportingText = { Text("例如：美食探店 / 宠物日常 / 城市散步 / 今日穿搭") },
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("拍摄方式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.mediaType == MediaType.Photo,
                    onClick = { onUpdateDraft(null, null, null, null, null, null, null, null, null, null, null, null, null, MediaType.Photo) },
                    label = { Text("照片") },
                )
                FilterChip(
                    selected = draft.mediaType == MediaType.Video,
                    onClick = { onUpdateDraft(null, null, null, null, null, null, null, null, null, null, null, null, null, MediaType.Video) },
                    label = { Text("视频") },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("风格", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                styleOptions.forEach { style ->
                    FilterChip(
                        selected = draft.style == style,
                        onClick = { onUpdateDraft(null, null, style, null, null, null, null, null, null, null, null, null, null, null) },
                        label = { Text(style) },
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.style,
                onValueChange = { onUpdateDraft(null, null, it, null, null, null, null, null, null, null, null, null, null, null) },
                label = { Text("自定义风格") },
                singleLine = true,
            )
        }

        if (draft.mediaType == MediaType.Video) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("目标时长", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durationOptions.forEach { duration ->
                        FilterChip(
                            selected = draft.durationSec == duration,
                            onClick = { onUpdateDraft(null, null, null, duration, null, null, null, null, null, null, null, null, null, null) },
                            label = { Text("${duration}秒") },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("主要场景", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                locationOptions.forEach { location ->
                    val selected = draft.locations.contains(location)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newLocations = if (selected) {
                                draft.locations - location
                            } else {
                                draft.locations + location
                            }
                            onUpdateDraft(null, null, null, null, null, newLocations, null, null, null, null, null, null, null, null)
                        },
                        label = { Text(location) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = customLocationText,
                    onValueChange = { customLocationText = it },
                    label = { Text("自定义场景") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val location = customLocationText.trim()
                        if (location.isNotEmpty() && !draft.locations.contains(location)) {
                            onUpdateDraft(null, null, null, null, null, draft.locations + location, null, null, null, null, null, null, null, null)
                        }
                        customLocationText = ""
                    },
                ) {
                    Text("添加")
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.highlightSubject,
            onValueChange = {
                onUpdateDraft(null, null, null, null, null, null, null, null, null, null, it, null, null, null)
            },
            label = { Text("这组素材最想拍清什么") },
            supportingText = { Text("例如：食物细节 / 宠物表情 / 路上的风景 / 今天的自己") },
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.shootGoal,
            onValueChange = {
                onUpdateDraft(null, null, null, null, null, null, null, null, it, null, null, null, null, null)
            },
            label = { Text("你希望最后拍成什么") },
            supportingText = { Text("例如：拍出一组今天就能分享的日常照片") },
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("希望成片情绪", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                moodOptions.forEach { mood ->
                    FilterChip(
                        selected = draft.mood == mood,
                        onClick = {
                            onUpdateDraft(null, null, null, null, null, null, null, null, null, mood, null, null, null, null)
                        },
                        label = { Text(mood) },
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.mood,
                onValueChange = {
                    onUpdateDraft(null, null, null, null, null, null, null, null, null, it, null, null, null, null)
                },
                label = { Text("自定义成片情绪") },
                singleLine = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("拍摄节奏", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timePressureOptions.forEach { (timePressure, label) ->
                    FilterChip(
                        selected = draft.timePressure == timePressure,
                        onClick = {
                            onUpdateDraft(null, null, null, null, null, null, null, null, null, null, null, null, timePressure, null)
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("出镜人数", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { castCount ->
                    FilterChip(
                        selected = draft.castCount == castCount,
                        onClick = {
                            onUpdateDraft(null, null, null, null, castCount, null, null, null, null, null, null, null, null, null)
                        },
                        label = { Text("${castCount}人") },
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = castCountText,
                onValueChange = { value ->
                    value.toIntOrNull()?.takeIf { it in 1..20 }?.let { castCount ->
                        onUpdateDraft(null, null, null, null, castCount, null, null, null, null, null, null, null, null, null)
                    }
                },
                label = { Text("自定义出镜人数") },
                supportingText = { Text("请输入 1-20 的人数") },
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("一个人拍")
                Text("适合自拍、三脚架或请朋友帮你补少量画面", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = draft.soloMode,
                onCheckedChange = {
                    onUpdateDraft(null, null, null, null, null, null, null, null, null, null, null, it, null, null)
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("生成字幕建议")
                Text("适合直接输出封面文案和字幕草稿", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = draft.needCaption,
                onCheckedChange = { onUpdateDraft(null, null, null, null, null, null, it, null, null, null, null, null, null, null) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("生成旁白建议")
                Text("适合后续做轻量故事线、短片或图文说明", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = draft.needVoiceover,
                onCheckedChange = { onUpdateDraft(null, null, null, null, null, null, null, it, null, null, null, null, null, null) },
            )
        }

        HighlightCard(
            title = "接下来你会拿到什么",
            body = "影导会先帮你排好拍摄顺序，再把照片或视频拆成一步步能照着拍的任务，并告诉你每一张为什么值得拍。",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, enabled = !uiState.isGeneratingPlan) {
                Text("返回")
            }
            Button(onClick = onGeneratePlan, enabled = !uiState.isGeneratingPlan) {
                Text(if (uiState.isGeneratingPlan) "正在生成..." else "生成导演方案")
            }
        }
    }
}
