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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    ) -> Unit,
    onGeneratePlan: () -> Unit,
    onBack: () -> Unit,
) {
    val draft = uiState.draft
    val durationOptions = listOf(30, 60, 90)
    val styleOptions = listOf("清新温暖", "青春纪实", "轻松日常", "电影感")
    val locationOptions = listOf("操场", "图书馆", "食堂", "教室", "宿舍", "校园小路")
    val moodOptions = listOf("温和", "松弛", "安静", "热闹", "明亮")
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
            onValueChange = { onUpdateDraft(it, null, null, null, null, null, null, null, null, null, null, null, null) },
            label = { Text("项目标题") },
            singleLine = true,
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.theme,
            onValueChange = { onUpdateDraft(null, it, null, null, null, null, null, null, null, null, null, null, null) },
            label = { Text("视频主题") },
            supportingText = { Text("例如：校园散步的一天 / 我的社团日常") },
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("风格", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                styleOptions.forEach { style ->
                    FilterChip(
                        selected = draft.style == style,
                        onClick = { onUpdateDraft(null, null, style, null, null, null, null, null, null, null, null, null, null) },
                        label = { Text(style) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("目标时长", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                durationOptions.forEach { duration ->
                    FilterChip(
                        selected = draft.durationSec == duration,
                        onClick = { onUpdateDraft(null, null, null, duration, null, null, null, null, null, null, null, null, null) },
                        label = { Text("${duration}秒") },
                    )
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
                            onUpdateDraft(null, null, null, null, null, newLocations, null, null, null, null, null, null, null)
                        },
                        label = { Text(location) },
                    )
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.highlightSubject,
            onValueChange = {
                onUpdateDraft(null, null, null, null, null, null, null, null, null, null, it, null, null)
            },
            label = { Text("这条片最想拍清什么") },
            supportingText = { Text("例如：放学后的自己 / 图书馆里的学习状态") },
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.shootGoal,
            onValueChange = {
                onUpdateDraft(null, null, null, null, null, null, null, null, it, null, null, null, null)
            },
            label = { Text("你希望最后拍成什么") },
            supportingText = { Text("例如：拍出一条今天就能分享的校园短片") },
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("希望成片情绪", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                moodOptions.forEach { mood ->
                    FilterChip(
                        selected = draft.mood == mood,
                        onClick = {
                            onUpdateDraft(null, null, null, null, null, null, null, null, null, mood, null, null, null)
                        },
                        label = { Text(mood) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("拍摄节奏", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timePressureOptions.forEach { (timePressure, label) ->
                    FilterChip(
                        selected = draft.timePressure == timePressure,
                        onClick = {
                            onUpdateDraft(null, null, null, null, null, null, null, null, null, null, null, null, timePressure)
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
                            onUpdateDraft(null, null, null, null, castCount, null, null, null, null, null, null, null, null)
                        },
                        label = { Text("${castCount}人") },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("一个人拍")
                Text("适合自拍或请同学帮你补少量镜头", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = draft.soloMode,
                onCheckedChange = {
                    onUpdateDraft(null, null, null, null, null, null, null, null, null, null, null, it, null)
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
                onCheckedChange = { onUpdateDraft(null, null, null, null, null, null, it, null, null, null, null, null, null) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("生成旁白建议")
                Text("适合后续做轻量故事线和片头文案", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = draft.needVoiceover,
                onCheckedChange = { onUpdateDraft(null, null, null, null, null, null, null, it, null, null, null, null, null) },
            )
        }

        HighlightCard(
            title = "接下来你会拿到什么",
            body = "影导会先帮你排好故事推进，再把镜头拆成一步步能照着拍的任务，并告诉你每一条为什么值得拍。",
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
