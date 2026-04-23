package com.yuki.yingdao.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuki.yingdao.ui.YingDaoUiState
import com.yuki.yingdao.ui.components.HighlightCard
import com.yuki.yingdao.ui.components.SectionTitle
import com.yuki.yingdao.ui.components.StatusPill

@Composable
fun PlanScreen(
    innerPadding: PaddingValues,
    uiState: YingDaoUiState,
    onSelectShot: (String) -> Unit,
    onBack: () -> Unit,
    onStartShooting: () -> Unit,
) {
    val project = uiState.activeProject
    val plan = project?.directorPlan

    if (uiState.isGeneratingPlan) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(
                title = "正在生成拍摄方案",
                subtitle = "我在根据你的主题、情绪和拍摄节奏整理镜头顺序。",
            )
            HighlightCard(
                title = "稍等一下",
                body = "通常很快就能给你一版能直接开拍的方案。",
            )
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }
        return
    }

    if (uiState.planError != null || project == null || plan == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(
                title = "这次还没生成出来",
                subtitle = uiState.planError ?: "当前还没有可展示的方案。",
            )
            TextButton(onClick = onBack) {
                Text("返回重试")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionTitle(
                title = "先看拍摄顺序",
                subtitle = "先把这条片子的节奏和重点看一眼，拍起来会更顺。",
            )
        }
        item {
            HighlightCard(
                title = "故事主线",
                body = plan.storyLogline,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("结构节拍", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                plan.beatSummary.forEachIndexed { index, beat ->
                    Text("${index + 1}. $beat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionTitle(
                title = "跟着这些镜头拍",
                subtitle = "建议先按顺序完成，后面再补最关键的镜头。",
            )
        }
        items(plan.shotTasks, key = { it.id }) { shot ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectShot(shot.id) },
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${shot.orderIndex}. ${shot.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatusPill(status = shot.status)
                    }
                    Text(
                        text = shot.goal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("镜头类型：${shot.shotType} · 建议时长：${shot.durationSuggestSec}秒")
                    Text("这一条为什么要拍：${shot.whyThisShotMatters}")
                    Text("构图：${shot.compositionHint}")
                    Text("动作：${shot.actionHint}")
                    Text("过关标准：${shot.successChecklist.joinToString(" / ")}")
                    Text(
                        text = "难点提醒：${shot.difficultyHint}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
                Button(onClick = onStartShooting) {
                    Text("开始拍摄")
                }
            }
        }
    }
}

