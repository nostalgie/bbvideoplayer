package com.dima.bbvideoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.bbvideoplayer.data.VideoEntry
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.ui.components.BounceButton
import com.dima.bbvideoplayer.ui.components.VerticalScrollbar
import com.dima.bbvideoplayer.ui.screens.dashboard.buildVideosByParentPath
import com.dima.bbvideoplayer.ui.screens.dashboard.isPathWithinWatchedFolders
import com.dima.bbvideoplayer.ui.screens.dashboard.parentBrowsePath
import com.dima.bbvideoplayer.ui.screens.dashboard.videoCountForFolder
import com.dima.bbvideoplayer.ui.screens.filepicker.listSubdirectories
import com.dima.bbvideoplayer.ui.theme.CardSurface
import com.dima.bbvideoplayer.ui.theme.DashboardBackground
import com.dima.bbvideoplayer.ui.theme.ExitRed
import com.dima.bbvideoplayer.ui.theme.FolderBlue
import com.dima.bbvideoplayer.ui.theme.GreenPrimary
import com.dima.bbvideoplayer.ui.theme.RedButton
import com.dima.bbvideoplayer.utils.abbreviateFolderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BUTTON_WIDTH = 88.dp
private val BUTTON_HEIGHT = 40.dp
private val BUTTON_FONT_SIZE = 12.sp

private val FOLDER_ROW_ICON_SIZE = 20.sp
private val FOLDER_ROW_VERTICAL_PADDING = 10.dp
private val FOLDER_ROW_HORIZONTAL_PADDING = 12.dp

@Composable
fun ParentDashboardScreen(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    onBackToKidMode: () -> Unit,
    onNavigateToFilePicker: () -> Unit = {},
    onPlayVideo: (Int) -> Unit = {},
    onExit: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryState by videoLibraryService.libraryState.collectAsStateWithLifecycle()

    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePinDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPlayPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoveFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var showUnsupported by rememberSaveable { mutableStateOf(false) }
    var browsePath by rememberSaveable { mutableStateOf<String?>(null) }

    val videoListState = rememberLazyListState()
    val allVideos = libraryState.videos

    LaunchedEffect(watchedFolders, browsePath) {
        if (browsePath != null && !isPathWithinWatchedFolders(browsePath!!, watchedFolders)) {
            browsePath = null
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onSave = { newPin ->
                coroutineScope.launch { videoRepository.saveParentPin(newPin) }
                showChangePinDialog = false
            },
            onDismiss = { showChangePinDialog = false }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = "Удалить все") },
            text = { Text(text = "Вы уверены? Все папки будут удалены из библиотеки.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        browsePath = null
                        coroutineScope.launch { videoRepository.clearAll() }
                    }
                ) {
                    Text("Да", color = RedButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Нет")
                }
            }
        )
    }

    pendingRemoveFolder?.let { folderPath ->
        AlertDialog(
            onDismissRequest = { pendingRemoveFolder = null },
            title = { Text(text = "Удалить папку") },
            text = { Text(text = "Убрать папку из библиотеки?\n$folderPath") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (browsePath != null &&
                            (browsePath == folderPath || browsePath!!.startsWith("$folderPath/"))
                        ) {
                            browsePath = null
                        }
                        coroutineScope.launch {
                            videoRepository.removeWatchedFolder(folderPath)
                        }
                        pendingRemoveFolder = null
                    }
                ) {
                    Text("Да", color = RedButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveFolder = null }) {
                    Text("Нет")
                }
            }
        )
    }

    if (pendingPlayPath != null) {
        AlertDialog(
            onDismissRequest = { pendingPlayPath = null },
            title = { Text(text = "Воспроизвести") },
            text = { Text(text = "Включить это видео?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = pendingPlayPath
                        pendingPlayPath = null
                        // Resolve at confirm time: a rescan may have reordered the list.
                        val index = allVideos.indexOfFirst { it.filePath == path }
                        if (index >= 0) onPlayVideo(index)
                    }
                ) {
                    Text("Да", color = GreenPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlayPath = null }) {
                    Text("Нет")
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(12.dp)
    ) {
        val isPortrait = maxHeight > maxWidth

        Column(modifier = Modifier.fillMaxSize()) {
        ScanStatusBar(
            libraryState = libraryState,
            onRefresh = { videoLibraryService.scanNow() }
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (isPortrait) {
            DashboardActionButtons(
                folderCount = watchedFolders.size,
                horizontal = true,
                onBackToKidMode = onBackToKidMode,
                onAddFolders = onNavigateToFilePicker,
                onClearAll = { showClearAllDialog = true },
                onChangePin = { showChangePinDialog = true },
                onExit = onExit,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            DashboardVideoList(
                watchedFolders = watchedFolders,
                allVideos = allVideos,
                browsePath = browsePath,
                onBrowsePathChange = { browsePath = it },
                unsupportedFiles = libraryState.unsupportedFiles,
                inaccessibleFolders = libraryState.inaccessibleFolders,
                showUnsupported = showUnsupported,
                onToggleUnsupported = { showUnsupported = !showUnsupported },
                videoListState = videoListState,
                onRequestAddFolders = onNavigateToFilePicker,
                onPlayVideo = { pendingPlayPath = it },
                onRemoveFolder = { pendingRemoveFolder = it },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardVideoList(
                    watchedFolders = watchedFolders,
                    allVideos = allVideos,
                    browsePath = browsePath,
                    onBrowsePathChange = { browsePath = it },
                    unsupportedFiles = libraryState.unsupportedFiles,
                    inaccessibleFolders = libraryState.inaccessibleFolders,
                    showUnsupported = showUnsupported,
                    onToggleUnsupported = { showUnsupported = !showUnsupported },
                    videoListState = videoListState,
                    onRequestAddFolders = onNavigateToFilePicker,
                    onPlayVideo = { pendingPlayPath = it },
                    onRemoveFolder = { pendingRemoveFolder = it },
                    modifier = Modifier.weight(1f)
                )
                DashboardActionButtons(
                    folderCount = watchedFolders.size,
                    horizontal = false,
                    onBackToKidMode = onBackToKidMode,
                    onAddFolders = onNavigateToFilePicker,
                    onClearAll = { showClearAllDialog = true },
                    onChangePin = { showChangePinDialog = true },
                    onExit = onExit
                )
            }
        }
        }
    }
}

@Composable
private fun ScanStatusBar(
    libraryState: com.dima.bbvideoplayer.data.LibraryState,
    onRefresh: () -> Unit
) {
    val timeText = libraryState.lastScanTime?.let { ts ->
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                libraryState.isScanning -> "Обновление библиотеки..."
                timeText != null -> "Обновлено: $timeText"
                else -> "Библиотека не сканировалась"
            },
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        TextButton(onClick = onRefresh, enabled = !libraryState.isScanning) {
            Text("Обновить", color = FolderBlue, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DashboardActionButtons(
    folderCount: Int,
    horizontal: Boolean,
    onBackToKidMode: () -> Unit,
    onAddFolders: () -> Unit,
    onClearAll: () -> Unit,
    onChangePin: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (horizontal) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashboardActionButton("Назад", onBackToKidMode, GreenPrimary, fillWidth = true, modifier = Modifier.weight(1f))
            DashboardActionButton("Добавить", onAddFolders, FolderBlue, fillWidth = true, modifier = Modifier.weight(1f))
            DashboardActionButton(
                "Удалить все",
                { if (folderCount > 0) onClearAll() },
                if (folderCount > 0) RedButton else Color.Gray,
                if (folderCount > 0) Color.White else Color.White.copy(alpha = 0.4f),
                fillWidth = true,
                modifier = Modifier.weight(1f)
            )
            DashboardActionButton("ПИН", onChangePin, FolderBlue, fillWidth = true, modifier = Modifier.weight(1f))
            DashboardActionButton("Выход", onExit, ExitRed, fillWidth = true, modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardActionButton("Назад", onBackToKidMode, GreenPrimary)
            DashboardActionButton("Добавить", onAddFolders, FolderBlue)
            DashboardActionButton(
                "Удалить все",
                { if (folderCount > 0) onClearAll() },
                if (folderCount > 0) RedButton else Color.Gray,
                if (folderCount > 0) Color.White else Color.White.copy(alpha = 0.4f)
            )
            DashboardActionButton("ПИН", onChangePin, FolderBlue)
            DashboardActionButton("Выход", onExit, ExitRed)
        }
    }
}

@Composable
private fun ChangePinDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    val pinValid = newPin.length in 4..6 && newPin.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Сменить ПИН") },
        text = {
            Column {
                Text(text = "Новый ПИН (4-6 цифр). Он понадобится для входа в родительский режим.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { value ->
                        newPin = value.filter { it.isDigit() }.take(6)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newPin) },
                enabled = pinValid
            ) {
                Text("Сохранить", color = if (pinValid) GreenPrimary else Color.Gray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun DashboardActionButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color = Color.White,
    fillWidth: Boolean = false,
    modifier: Modifier = Modifier
) {
    BounceButton(
        text = text,
        onClick = onClick,
        backgroundColor = backgroundColor,
        textColor = textColor,
        width = if (fillWidth) Dp.Unspecified else BUTTON_WIDTH,
        height = BUTTON_HEIGHT,
        fontSize = BUTTON_FONT_SIZE,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier
    )
}

@Composable
private fun DashboardVideoList(
    watchedFolders: List<String>,
    allVideos: List<VideoEntry>,
    browsePath: String?,
    onBrowsePathChange: (String?) -> Unit,
    unsupportedFiles: List<String>,
    inaccessibleFolders: List<String>,
    showUnsupported: Boolean,
    onToggleUnsupported: () -> Unit,
    videoListState: LazyListState,
    onRequestAddFolders: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val videosByParentPath = remember(allVideos) { buildVideosByParentPath(allVideos) }

    var subdirectories by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingSubdirs by remember { mutableStateOf(false) }

    LaunchedEffect(browsePath) {
        val path = browsePath ?: run {
            subdirectories = emptyList()
            loadingSubdirs = false
            return@LaunchedEffect
        }
        loadingSubdirs = true
        subdirectories = emptyList() // stale rows from the previous folder must not show
        subdirectories = withContext(Dispatchers.IO) { listSubdirectories(path) }
        loadingSubdirs = false
    }

    Column(modifier = modifier) {
        if (watchedFolders.isNotEmpty()) {
            Text(
                text = "Папки (${watchedFolders.size}), видео (${allVideos.size}):",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (inaccessibleFolders.isNotEmpty() && browsePath == null) {
            Text(
                text = "⚠️ Недоступно папок: ${inaccessibleFolders.size}",
                fontSize = 13.sp,
                color = RedButton,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (watchedFolders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нажмите, чтобы добавить папки",
                    color = FolderBlue,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onRequestAddFolders() }.padding(16.dp)
                )
            }
        } else {
            if (browsePath != null) {
                FolderBrowseBar(
                    currentPath = browsePath,
                    onNavigateUp = {
                        onBrowsePathChange(parentBrowsePath(browsePath, watchedFolders))
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = videoListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (browsePath == null) {
                        items(
                            items = watchedFolders.sorted(),
                            key = { it }
                        ) { folderPath ->
                            RootFolderRow(
                                folderName = abbreviateFolderPath(folderPath),
                                videoCount = videoCountForFolder(allVideos, folderPath),
                                onEnter = { onBrowsePathChange(folderPath) },
                                onRemove = { onRemoveFolder(folderPath) }
                            )
                        }
                    } else {
                        if (loadingSubdirs) {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.widget.ProgressBar(ctx).apply {
                                                indeterminateTintList =
                                                    android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        items(
                            items = subdirectories,
                            key = { it }
                        ) { subdirPath ->
                            SubfolderRow(
                                folderName = File(subdirPath).name,
                                onEnter = { onBrowsePathChange(subdirPath) }
                            )
                        }

                        val videosHere = videosByParentPath[browsePath].orEmpty()
                            .sortedBy { it.fileName.lowercase() }

                        items(
                            items = videosHere,
                            key = { it.filePath }
                        ) { video ->
                            VideoListItem(
                                fileName = video.fileName,
                                onClick = {
                                    onPlayVideo(video.filePath)
                                }
                            )
                        }

                        if (!loadingSubdirs && subdirectories.isEmpty() && videosHere.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = "Папка пуста",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    if (browsePath == null && unsupportedFiles.isNotEmpty()) {
                        item(key = "unsupported-toggle") {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onToggleUnsupported) {
                                Text(
                                    text = if (showUnsupported) {
                                        "Скрыть неподдерживаемые (${unsupportedFiles.size})"
                                    } else {
                                        "Неподдерживаемые (${unsupportedFiles.size})"
                                    },
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (showUnsupported) {
                            items(
                                items = unsupportedFiles,
                                key = { it }
                            ) { path ->
                                Text(
                                    text = "⚠️ ${path.substringAfterLast('/')}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(state = videoListState)
            }
        }
    }
}

@Composable
private fun FolderBrowseBar(
    currentPath: String,
    onNavigateUp: () -> Unit
) {
    Surface(
        onClick = onNavigateUp,
        shape = RoundedCornerShape(8.dp),
        color = CardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FOLDER_ROW_HORIZONTAL_PADDING,
                    vertical = FOLDER_ROW_VERTICAL_PADDING
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = FOLDER_ROW_ICON_SIZE)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = File(currentPath).name.ifEmpty { abbreviateFolderPath(currentPath) },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = FolderBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RootFolderRow(
    folderName: String,
    videoCount: Int,
    onEnter: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FOLDER_ROW_HORIZONTAL_PADDING,
                    vertical = FOLDER_ROW_VERTICAL_PADDING
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = FOLDER_ROW_ICON_SIZE)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = folderName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FolderBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$videoCount",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 8.dp)
            )
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onRemove,
                    shape = RoundedCornerShape(8.dp),
                    color = RedButton.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        color = RedButton,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubfolderRow(
    folderName: String,
    onEnter: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FOLDER_ROW_HORIZONTAL_PADDING,
                    vertical = FOLDER_ROW_VERTICAL_PADDING
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = FOLDER_ROW_ICON_SIZE)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = folderName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "›",
                fontSize = FOLDER_ROW_ICON_SIZE,
                color = FolderBlue
            )
        }
    }
}

@Composable
private fun VideoListItem(
    fileName: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🎬", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
