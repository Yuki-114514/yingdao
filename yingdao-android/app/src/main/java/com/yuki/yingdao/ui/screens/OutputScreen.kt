package com.yuki.yingdao.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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

@Composable
fun OutputScreen(
    innerPadding: PaddingValues,
    uiState: YingDaoUiState,
    onBackToHome: () -> Unit,
    onBack: () -> Unit,
) {
    val project = uiState.activeProject
    val suggestion = project?.assemblySuggestion
    val isPhotoProject = project?.brief?.mediaType == MediaType.Photo

    if (uiState.isBuildingAssembly) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(
                title = if (isPhotoProject) "正在整理这组照片" else "正在整理成片思路",
                subtitle = if (isPhotoProject) {
                    "我在根据你已经通过的照片，给你排顺序、标题和文案。"
                } else {
                    "我在根据你已经通过的镜头，给你排顺序、标题和文案。"
                },
            )
            HighlightCard(
                title = "稍等一下",
                body = if (isPhotoProject) {
                    "这一步会把现有照片和缺失画面一起考虑进去。"
                } else {
                    "这一步会把现有素材和缺失镜头一起考虑进去。"
                },
            )
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }
        return
    }

    if (uiState.assemblyError != null || project == null || suggestion == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(
                title = "这次还没生成出片建议",
                subtitle = uiState.assemblyError ?: "当前还没有可展示的成片建议。",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
                Button(onClick = onBackToHome) {
                    Text("回到首页")
                }
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
                title = if (isPhotoProject) "把这组照片顺一顺" else "把片子顺一顺",
                subtitle = if (isPhotoProject) {
                    "先把挑图顺序、标题和配文想清楚，这组照片就更完整了。"
                } else {
                    "先把顺序、标题和字幕想清楚，这条片就更像样了。"
                },
            )
        }
        item {
            HighlightCard(
                title = if (isPhotoProject) "推荐组图结构" else "推荐成片结构",
                body = suggestion.editingDirection,
            )
        }
        item {
            Text(
                if (isPhotoProject) "推荐照片顺序" else "推荐镜头顺序",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(suggestion.orderedClipIds, key = { it }) { clipId ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isPhotoProject) "推荐放进组图的一张照片" else "推荐放进成片的一条素材",
                        fontWeight = FontWeight.SemiBold,
                    )
                    suggestion.selectionReasonByClipId[clipId]?.let { reason ->
                        Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text("标题建议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(suggestion.titleOptions, key = { it }) { option ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(option)
                }
            }
        }
        item {
            Text(
                if (isPhotoProject) "配文草稿" else "字幕 / 旁白草稿",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(suggestion.captionDraft, key = { it }) { line ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            if (suggestion.missingShotIds.isNotEmpty()) {
                HighlightCard(
                    title = if (isPhotoProject) "仍可补拍的照片" else "仍可补强的镜头",
                    body = if (isPhotoProject) {
                        "如果你还想再补一轮，建议先补这些缺口：${suggestion.missingBeatLabels.joinToString(" / ")}。把它们补上，这组照片会更完整。"
                    } else {
                        "如果你还想再补一轮，建议先补这些缺口：${suggestion.missingBeatLabels.joinToString(" / ")}。把它们补上，整条片会更完整。"
                    },
                )
            }
        }
        item {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
                Button(onClick = onBackToHome) {
                    Text("回到首页")
                }
            }
        }
    }
}

