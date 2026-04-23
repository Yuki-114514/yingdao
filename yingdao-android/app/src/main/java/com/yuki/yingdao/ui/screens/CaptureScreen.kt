package com.yuki.yingdao.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuki.yingdao.data.ClipReview
import com.yuki.yingdao.data.ShotStatus
import com.yuki.yingdao.ui.YingDaoUiState
import com.yuki.yingdao.ui.components.MetricChip
import com.yuki.yingdao.ui.components.StatusPill
import com.yuki.yingdao.ui.theme.OverlayScrim

private enum class CameraCapturePhase {
    Idle,
    Recording,
    Finalizing,
}

@Composable
fun CaptureScreen(
    innerPadding: PaddingValues,
    uiState: YingDaoUiState,
    onSelectShot: (String) -> Unit,
    onRecordedClip: (String, Double) -> Unit,
    onApprove: () -> Unit,
    onRetake: () -> Unit,
    onSkip: () -> Unit,
    onOpenReview: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val project = uiState.activeProject ?: return
    val plan = project.directorPlan ?: return
    val currentShot = uiState.selectedShot ?: plan.shotTasks.first()
    val review = currentShot.latestReview
    val keyTip = currentShot.successChecklist.firstOrNull() ?: currentShot.compositionHint
    val clipCount = project.clips.size

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var capturePhase by remember { mutableStateOf(CameraCapturePhase.Idle) }
    var captureMessage by remember { mutableStateOf("准备好后就能开始拍这一条。") }
    var hasCameraPermission by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
    var hasAudioPermission by remember { mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasCameraPermission = granted[Manifest.permission.CAMERA] == true ||
            context.hasPermission(Manifest.permission.CAMERA)
        hasAudioPermission = granted[Manifest.permission.RECORD_AUDIO] == true ||
            context.hasPermission(Manifest.permission.RECORD_AUDIO)
        captureMessage = when {
            !hasCameraPermission -> "打开相机权限后，才能进入真实拍摄。"
            hasAudioPermission -> "相机和麦克风都准备好了。"
            else -> "相机已就绪，当前先拍画面。"
        }
    }

    val requestCapturePermissions = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            ),
        )
    }

    val finalizeRecording = {
        if (capturePhase == CameraCapturePhase.Recording) {
            capturePhase = CameraCapturePhase.Finalizing
            captureMessage = "正在收尾并整理素材，稍等一下。"
            activeRecording?.stop()
            activeRecording = null
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            requestCapturePermissions()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasCameraPermission = context.hasPermission(Manifest.permission.CAMERA)
                    hasAudioPermission = context.hasPermission(Manifest.permission.RECORD_AUDIO)
                    if (!hasCameraPermission) {
                        cameraProvider?.unbindAll()
                        videoCapture = null
                        captureMessage = "打开相机权限后，才能进入真实拍摄。"
                    }
                }
                Lifecycle.Event.ON_STOP -> finalizeRecording()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasCameraPermission, lifecycleOwner, previewView) {
        val targetPreviewView = previewView
        if (!hasCameraPermission || targetPreviewView == null) {
            return@LaunchedEffect
        }
        bindVideoUseCases(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = targetPreviewView,
            onReady = { provider, boundVideoCapture ->
                cameraProvider = provider
                videoCapture = boundVideoCapture
                if (capturePhase == CameraCapturePhase.Idle) {
                    captureMessage = if (hasAudioPermission) {
                        "相机已就绪，直接拍就可以。"
                    } else {
                        "相机已就绪，当前先拍画面。"
                    }
                }
            },
            onError = { message ->
                cameraProvider = null
                videoCapture = null
                captureMessage = message
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            cameraProvider?.unbindAll()
        }
    }

    val recordButtonEnabled = when (capturePhase) {
        CameraCapturePhase.Idle -> hasCameraPermission && videoCapture != null
        CameraCapturePhase.Recording -> true
        CameraCapturePhase.Finalizing -> false
    }
    val showReviewSheet = review != null && capturePhase == CameraCapturePhase.Idle && !uiState.isReviewingClip
    val isImmersiveCapture = hasCameraPermission && !showReviewSheet

    EnableSystemCameraMode(enabled = isImmersiveCapture)

    BackHandler(enabled = capturePhase != CameraCapturePhase.Idle) {
        finalizeRecording()
    }

    fun startRecording() {
        val currentVideoCapture = videoCapture ?: return
        capturePhase = CameraCapturePhase.Recording
        captureMessage = if (hasAudioPermission) {
            "正在录制 ${currentShot.title}。"
        } else {
            "正在录制 ${currentShot.title}，当前不收环境声。"
        }
        activeRecording = startVideoRecording(
            context = context,
            videoCapture = currentVideoCapture,
            enableAudio = hasAudioPermission,
            onStart = {
                captureMessage = if (hasAudioPermission) {
                    "正在拍摄，拍够后点停止。"
                } else {
                    "正在拍摄，当前不收环境声。"
                }
            },
            onFinalize = { uri, durationSec ->
                activeRecording = null
                capturePhase = CameraCapturePhase.Idle
                captureMessage = "这一条拍完了。"
                onRecordedClip(uri, durationSec)
            },
            onError = { message ->
                activeRecording = null
                capturePhase = CameraCapturePhase.Idle
                captureMessage = message
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = this
                    }
                },
                update = { updatedView ->
                    previewView = updatedView
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OverlayScrim.copy(alpha = 0.72f),
                            Color.Transparent,
                            OverlayScrim.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        if (!hasCameraPermission) {
            PermissionCard(
                onRequestPermission = requestCapturePermissions,
                onBack = onBack,
            )
            return@Box
        }

        if (uiState.isReviewingClip) {
            ReviewLoadingSheet(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        } else if (showReviewSheet) {
            ReviewSheet(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                review = review,
                reviewError = uiState.reviewError,
                hasAudioPermission = hasAudioPermission,
                onRequestAudioPermission = requestCapturePermissions,
                onApprove = onApprove,
                onRetake = onRetake,
                onOpenReview = onOpenReview,
                onBack = onBack,
            )
        } else {
            CameraHud(
                modifier = Modifier.fillMaxSize(),
                currentShotTitle = currentShot.title,
                currentShotGoal = currentShot.goal,
                currentShotOrder = currentShot.orderIndex,
                totalShots = plan.shotTasks.size,
                clipCount = clipCount,
                keyTip = keyTip,
                capturePhase = capturePhase,
                captureMessage = captureMessage,
                hasAudioPermission = hasAudioPermission,
                recordButtonEnabled = recordButtonEnabled,
                onBack = onBack,
                onSkip = onSkip,
                onOpenReview = onOpenReview,
                onRecord = {
                    if (capturePhase == CameraCapturePhase.Recording) {
                        finalizeRecording()
                    } else {
                        startRecording()
                    }
                },
            )
        }
    }
}

@Composable
private fun CameraHud(
    modifier: Modifier,
    currentShotTitle: String,
    currentShotGoal: String,
    currentShotOrder: Int,
    totalShots: Int,
    clipCount: Int,
    keyTip: String,
    capturePhase: CameraCapturePhase,
    captureMessage: String,
    hasAudioPermission: Boolean,
    recordButtonEnabled: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onOpenReview: () -> Unit,
    onRecord: () -> Unit,
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (capturePhase != CameraCapturePhase.Recording) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text("返回", color = Color.White)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.36f)),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            text = "第$currentShotOrder / $totalShots 条",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = currentShotTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = currentShotGoal,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "先抓：$keyTip",
                        color = Color.White.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = when (capturePhase) {
                    CameraCapturePhase.Idle -> if (hasAudioPermission) "准备拍摄" else "准备拍摄 · 当前静音"
                    CameraCapturePhase.Recording -> "录制中"
                    CameraCapturePhase.Finalizing -> "正在整理"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = captureMessage,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = capturePhase == CameraCapturePhase.Idle,
                    onClick = onSkip,
                ) {
                    Text("跳过", color = if (capturePhase == CameraCapturePhase.Idle) Color.White else Color.White.copy(alpha = 0.3f))
                }

                Button(
                    enabled = recordButtonEnabled,
                    onClick = onRecord,
                    modifier = Modifier
                        .size(88.dp)
                        .semantics {
                            contentDescription = when (capturePhase) {
                                CameraCapturePhase.Idle -> "开始录制"
                                CameraCapturePhase.Recording -> "停止录制"
                                CameraCapturePhase.Finalizing -> "正在整理录制内容"
                            }
                        },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.Black.copy(alpha = 0.4f),
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(
                                when (capturePhase) {
                                    CameraCapturePhase.Idle -> 56.dp
                                    CameraCapturePhase.Recording -> 28.dp
                                    CameraCapturePhase.Finalizing -> 24.dp
                                },
                            )
                            .background(
                                color = when (capturePhase) {
                                    CameraCapturePhase.Idle -> MaterialTheme.colorScheme.error
                                    CameraCapturePhase.Recording -> MaterialTheme.colorScheme.error
                                    CameraCapturePhase.Finalizing -> Color.Gray
                                },
                                shape = when (capturePhase) {
                                    CameraCapturePhase.Recording -> RoundedCornerShape(8.dp)
                                    else -> CircleShape
                                },
                            ),
                    )
                }

                TextButton(
                    enabled = capturePhase == CameraCapturePhase.Idle && clipCount > 0,
                    onClick = onOpenReview,
                ) {
                    Text(
                        text = "素材",
                        color = if (capturePhase == CameraCapturePhase.Idle && clipCount > 0) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.3f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewLoadingSheet(
    modifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "AI 正在点评这一条",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "我在看稳定度、主体清晰度、构图和情绪点，马上给你结果。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewSheet(
    modifier: Modifier,
    review: ClipReview,
    reviewError: String?,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onApprove: () -> Unit,
    onRetake: () -> Unit,
    onOpenReview: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "拍后反馈 · ${review.score}分",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack) {
                    Text("返回")
                }
            }
            Text(
                text = review.suggestion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricChip(label = "稳定", value = "${review.stabilityScore}")
                MetricChip(label = "主体", value = "${review.subjectScore}")
                MetricChip(label = "构图", value = "${review.compositionScore}")
                MetricChip(label = "情绪", value = "${review.emotionScore}")
            }
            Text(
                text = if (review.usable) {
                    review.keepReason
                } else {
                    "${review.retakeReason}\n\n问题点：${review.issues.joinToString(" / ")}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasAudioPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "当前这条不含环境声。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRequestAudioPermission) {
                        Text("打开麦克风")
                    }
                }
            }
            if (review.retakeReason.isBlank().not() && review.usable.not()) {
                Text(
                    text = "建议重拍：${review.retakeReason}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reviewError != null) {
                Text(
                    text = reviewError,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onApprove, enabled = review.usable) {
                    Text("通过这条")
                }
                TextButton(onClick = onRetake) {
                    Text("再拍一条")
                }
                TextButton(onClick = onOpenReview) {
                    Text("去挑片")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "先打开相机权限",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "打开后就能直接进入纯拍摄界面，尽量不让多余内容干扰你拍这一条。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequestPermission) {
                    Text("打开权限")
                }
                TextButton(onClick = onBack) {
                    Text("返回")
                }
            }
        }
    }
}

@Composable
private fun EnableSystemCameraMode(enabled: Boolean) {
    val view = LocalView.current
    val activity = view.context as? Activity ?: return

    DisposableEffect(activity, view, enabled) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun bindVideoUseCases(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onReady: (ProcessCameraProvider, VideoCapture<Recorder>) -> Unit,
    onError: (String) -> Unit,
) {
    val applicationContext = context.applicationContext
    val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
    cameraProviderFuture.addListener(
        {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.FHD, Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                        ),
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture,
                )
                onReady(cameraProvider, videoCapture)
            } catch (exception: Exception) {
                onError("相机初始化失败：${exception.localizedMessage ?: "未知错误"}")
            }
        },
        ContextCompat.getMainExecutor(applicationContext),
    )
}

@SuppressLint("MissingPermission")
private fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    enableAudio: Boolean,
    onStart: () -> Unit,
    onFinalize: (String, Double) -> Unit,
    onError: (String) -> Unit,
): Recording {
    val contentValues = ContentValues().apply {
        put(
            MediaStore.MediaColumns.DISPLAY_NAME,
            "YingDao-${System.currentTimeMillis()}",
        )
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YingDao")
        }
    }
    val outputOptions = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    ).setContentValues(contentValues)
        .build()

    val pendingRecording = videoCapture.output.prepareRecording(context, outputOptions).let { recording ->
        if (enableAudio) recording.withAudioEnabled() else recording
    }

    return pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
        when (event) {
            is VideoRecordEvent.Start -> onStart()
            is VideoRecordEvent.Finalize -> {
                if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                    val durationSec = event.recordingStats.recordedDurationNanos / 1_000_000_000.0
                    onFinalize(event.outputResults.outputUri.toString(), durationSec)
                } else {
                    onError(
                        buildString {
                            append("录制失败")
                            event.cause?.localizedMessage
                                ?.takeIf { it.isNotBlank() }
                                ?.let { detail -> append("：").append(detail) }
                        },
                    )
                }
            }
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
