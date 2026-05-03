package com.yuki.yingdao.ui.screens

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
import com.yuki.yingdao.data.MediaType
import com.yuki.yingdao.ui.YingDaoUiState
import com.yuki.yingdao.ui.components.HighlightCard
import com.yuki.yingdao.ui.components.SectionTitle
import com.yuki.yingdao.ui.components.StatusPill

@Composable
fun ReviewScreen(
    innerPadding: PaddingValues,
    uiState: YingDaoUiState,
    onBack: () -> Unit,
    onBuildAssembly: () -> Unit,
) {
    val project = uiState.activeProject ?: return
    val plan = project.directorPlan ?: return
    val usableCount = project.clips.count { it.review?.usable == true }
    val approvedClipCount = plan.shotTasks.count { it.status == com.yuki.yingdao.data.ShotStatus.Approved }
    val missingShots = plan.shotTasks.filter { it.status != com.yuki.yingdao.data.ShotStatus.Approved && it.status != com.yuki.yingdao.data.ShotStatus.Skipped }
    val isPhotoProject = project.brief.mediaType == MediaType.Photo

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionTitle(
                title = "挑一挑哪些能留",
                subtitle = if (isPhotoProject) {
                    "先看哪些照片已经够用，哪些照片还差一点。"
                } else {
                    "先看哪些素材已经够用，哪些镜头还差一点。"
                },
            )
        }
        item {
            HighlightCard(
                title = "当前整理结果",
                body = if (isPhotoProject) {
                    "已拍 ${project.clips.size} 张照片，其中 ${usableCount} 张可直接进入候选。${if (missingShots.isEmpty()) "所有关键照片都已覆盖。" else "仍有 ${missingShots.size} 张照片建议补拍。"}"
                } else {
                    "已拍 ${project.clips.size} 条素材，其中 ${usableCount} 条可直接进入候选片段。${if (missingShots.isEmpty()) "所有关键镜头都已覆盖。" else "仍有 ${missingShots.size} 条镜头建议补拍。"}"
                },
            )
        }
        if (missingShots.isNotEmpty()) {
            item {
                HighlightCard(
                    title = "当前故事还缺什么",
                    body = "还没补齐的故事节点：${missingShots.map { it.beatLabel }.distinct().joinToString(" / ")}。",
                )
            }
        }
        if (missingShots.isNotEmpty()) {
            item {
                SectionTitle(title = if (isPhotoProject) "建议补拍照片" else "建议补拍镜头")
            }
            items(missingShots, key = { it.id }) { shot ->
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(shot.title, fontWeight = FontWeight.SemiBold)
                        Text(shot.goal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SectionTitle(title = "素材清单") }
        items(project.clips, key = { it.id }) { clip ->
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(clip.thumbnailLabel, fontWeight = FontWeight.SemiBold)
                    if (clip.mediaType == MediaType.Photo) {
                        Text("类型：照片")
                    } else {
                        Text("时长：${clip.durationSec}秒")
                    }
                    clip.review?.let { review ->
                        Text("评分：${review.score}")
                        Text(review.suggestion, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (review.usable) review.keepReason else review.retakeReason,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("下一步：${review.nextAction}")
                    }
                }
            }
        }

        if (uiState.assemblyError != null) {
            item {
                HighlightCard(
                    title = "这次没顺出来",
                    body = uiState.assemblyError,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack, enabled = !uiState.isBuildingAssembly) {
                    Text("返回")
                }
                Button(
                    onClick = onBuildAssembly,
                    enabled = approvedClipCount > 0 && !uiState.isBuildingAssembly,
                ) {
                    Text(if (uiState.isBuildingAssembly) "正在生成..." else "生成出片建议")
                }
            }
        }
    }
}

