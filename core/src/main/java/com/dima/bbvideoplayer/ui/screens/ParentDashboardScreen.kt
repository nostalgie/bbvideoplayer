package com.dima.bbvideoplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.bbvideoplayer.data.LibraryState
import com.dima.bbvideoplayer.data.VideoEntry
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.ui.components.FolderCard
import com.dima.bbvideoplayer.ui.components.ParentScreenHeader
import com.dima.bbvideoplayer.ui.components.VerticalScrollbar
import com.dima.bbvideoplayer.ui.screens.dashboard.buildVideosByParentPath
import com.dima.bbvideoplayer.ui.screens.dashboard.isPathWithinWatchedFolders
import com.dima.bbvideoplayer.ui.screens.dashboard.parentBrowsePath
import com.dima.bbvideoplayer.ui.screens.dashboard.videoCountForFolder
import com.dima.bbvideoplayer.ui.screens.filepicker.listSubdirectories
import com.dima.bbvideoplayer.ui.theme.CardSurface
import com.dima.bbvideoplayer.ui.theme.DashboardBackground
import com.dima.bbvideoplayer.ui.theme.FolderBlue
import com.dima.bbvideoplayer.ui.theme.GreenPrimary
import com.dima.bbvideoplayer.ui.theme.RedButton
import com.dima.bbvideoplayer.utils.FolderArt
import com.dima.bbvideoplayer.utils.abbreviateFolderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BUTTON_HEIGHT = 40.dp

private val FOLDER_ROW_ICON_SIZE = 20.sp
private val FOLDER_ROW_VERTICAL_PADDING = 10.dp
private val FOLDER_ROW_HORIZONTAL_PADDING = 12.dp

private val GridMinCardWidth = 160.dp
private val GridSpacing = 10.dp

/**
 * Library-management surface of the parent area: add/remove folders, folder
 * covers, scan status. Playing videos happens on [PlaybackPickerScreen],
 * PIN / controls side / clear-all on [ParentSettingsScreen].
 */
@Composable
fun ParentDashboardScreen(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    onBackToKidMode: () -> Unit,
    onNavigateToFilePicker: () -> Unit = {},
    onNavigateToPlayback: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onPlayVideo: (Int) -> Unit = {},
    onExit: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryState by videoLibraryService.libraryState.collectAsStateWithLifecycle()

    var pendingRemoveFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPlayPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showUnsupported by rememberSaveable { mutableStateOf(false) }
    var browsePath by rememberSaveable { mutableStateOf<String?>(null) }

    val videoListState = rememberLazyListState()
    val allVideos = libraryState.videos

    LaunchedEffect(watchedFolders, browsePath) {
        if (browsePath != null && !isPathWithinWatchedFolders(browsePath!!, watchedFolders)) {
            browsePath = null
        }
    }

    // Inside a folder, the system Back walks one folder up; at the root it
    // falls through to the default (leave the dashboard).
    val goUpInBrowse = {
        val current = browsePath
        if (current != null) {
            browsePath = parentBrowsePath(current, watchedFolders)
        }
    }
    androidx.activity.compose.BackHandler(enabled = browsePath != null, onBack = goUpInBrowse)

    pendingPlayPath?.let { playPath ->
        AlertDialog(
            onDismissRequest = { pendingPlayPath = null },
            title = { Text(text = "Воспроизвести") },
            text = { Text(text = "Включить это видео?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPlayPath = null
                        // Resolve at confirm time: a rescan may have reordered the list.
                        val index = allVideos.indexOfFirst { it.filePath == playPath }
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

    pendingRemoveFolder?.let { folderPath ->
        AlertDialog(
            onDismissRequest = { pendingRemoveFolder = null },
            title = { Text(text = "Убрать папку") },
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: title on its own line; scan status shares the counters
            // line so a long title can never push "Обновить" off-screen.
            ParentScreenHeader(title = "Родительский раздел")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Папки (${watchedFolders.size}) · Видео (${allVideos.size})",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                ScanStatusBadge(libraryState = libraryState, onRefresh = { videoLibraryService.scanNow() })
            }

            DashboardActionButtons(
                folderCount = watchedFolders.size,
                onBackToKidMode = onBackToKidMode,
                onNavigateToPlayback = onNavigateToPlayback,
                onAddFolders = onNavigateToFilePicker,
                onNavigateToSettings = onNavigateToSettings,
                onExit = onExit,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (inaccessibleFoldersVisible(libraryState, watchedFolders, browsePath)) {
                Text(
                    text = "⚠️ Недоступно папок: ${libraryState.inaccessibleFolders.size}",
                    fontSize = 13.sp,
                    color = RedButton,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            when {
                watchedFolders.isEmpty() -> EmptyLibraryPlaceholder(
                    onRequestAddFolders = onNavigateToFilePicker,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )

                browsePath != null -> BrowseListView(
                    browsePath = browsePath!!,
                    watchedFolders = watchedFolders,
                    allVideos = allVideos,
                    libraryState = libraryState,
                    showUnsupported = showUnsupported,
                    onToggleUnsupported = { showUnsupported = !showUnsupported },
                    videoListState = videoListState,
                    onBrowsePathChange = { browsePath = it },
                    onRemoveRootFolder = { pendingRemoveFolder = it },
                    onPlayVideo = { pendingPlayPath = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )

                else -> FolderGridView(
                    watchedFolders = watchedFolders,
                    allVideos = allVideos,
                    unsupportedFiles = libraryState.unsupportedFiles,
                    showUnsupported = showUnsupported,
                    onToggleUnsupported = { showUnsupported = !showUnsupported },
                    onBrowsePathChange = { browsePath = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

private fun inaccessibleFoldersVisible(
    libraryState: LibraryState,
    watchedFolders: List<String>,
    browsePath: String?
): Boolean = browsePath == null && libraryState.inaccessibleFolders.isNotEmpty() && watchedFolders.isNotEmpty()

/**
 * Root level of the library: watched folders as cover-art cards in an
 * adaptive grid (2 columns on a portrait phone, 3+ on a tablet or TV).
 */
@Composable
private fun FolderGridView(
    watchedFolders: List<String>,
    allVideos: List<VideoEntry>,
    unsupportedFiles: List<String>,
    showUnsupported: Boolean,
    onToggleUnsupported: () -> Unit,
    onBrowsePathChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GridMinCardWidth),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
        state = rememberLazyGridState(),
        modifier = modifier
    ) {
        items(
            items = watchedFolders.sorted(),
            key = { it }
        ) { folderPath ->
            val sampleVideo = allVideos
                .filter { it.sourceFolder == folderPath }
                .minByOrNull { it.fileName.lowercase() }
            FolderCard(
                title = File(folderPath).name.ifEmpty { folderPath },
                videoCount = videoCountForFolder(allVideos, folderPath).toString(),
                folderPath = folderPath,
                sampleVideoPath = sampleVideo?.filePath,
                onClick = { onBrowsePathChange(folderPath) }
            )
        }

        if (unsupportedFiles.isNotEmpty()) {
            item(key = "unsupported-toggle", span = { GridItemSpan(maxLineSpan) }) {
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
                    key = { it },
                    span = { GridItemSpan(maxLineSpan) }
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
}

/**
 * Inside a watched folder: management view of its contents (subfolders and
 * videos are informational only — playing happens on the playback screen),
 * plus the remove-from-library and cover actions for the root folder.
 */
@Composable
private fun BrowseListView(
    browsePath: String,
    watchedFolders: List<String>,
    allVideos: List<VideoEntry>,
    libraryState: LibraryState,
    showUnsupported: Boolean,
    onToggleUnsupported: () -> Unit,
    videoListState: LazyListState,
    onBrowsePathChange: (String?) -> Unit,
    onRemoveRootFolder: (String) -> Unit,
    onPlayVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val videosByParentPath = remember(allVideos) { buildVideosByParentPath(allVideos) }
    val rootFolder = remember(browsePath, watchedFolders) {
        watchedFolders.find { browsePath == it || browsePath.startsWith("$it/") }
    }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val pickCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val folder = rootFolder ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            coroutineScope.launch {
                withContext(Dispatchers.IO) { FolderArt.setCustomCover(context.applicationContext, folder, uri) }
            }
        }
    }

    var subdirectories by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingSubdirs by remember { mutableStateOf(false) }

    LaunchedEffect(browsePath) {
        loadingSubdirs = true
        subdirectories = emptyList() // stale rows from the previous folder must not show
        subdirectories = withContext(Dispatchers.IO) { listSubdirectories(browsePath) }
        loadingSubdirs = false
    }

    Column(modifier = modifier) {
        BrowseBarRow(
            currentPath = browsePath,
            rootFolder = rootFolder,
            onNavigateUp = { onBrowsePathChange(parentBrowsePath(browsePath, watchedFolders)) },
            onRemove = onRemoveRootFolder,
            onSetCover = rootFolder?.let { _ -> {
                pickCoverLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } }
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = videoListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                    onClick = { onPlayVideo(video.filePath) }
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

            // The unsupported list belongs to the root level only; when the
            // user browses a root folder directly it stays visible at the end.
            if (rootFolder == browsePath && libraryState.unsupportedFiles.isNotEmpty()) {
                item(key = "unsupported-toggle") {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onToggleUnsupported) {
                        Text(
                            text = if (showUnsupported) {
                                "Скрыть неподдерживаемые (${libraryState.unsupportedFiles.size})"
                            } else {
                                "Неподдерживаемые (${libraryState.unsupportedFiles.size})"
                            },
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                }
                if (showUnsupported) {
                    items(
                        items = libraryState.unsupportedFiles,
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
            VerticalScrollbar(
                state = videoListState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun EmptyLibraryPlaceholder(
    onRequestAddFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onRequestAddFolders).padding(32.dp)
        ) {
            Text(text = "📂", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Добавить папки с мультфильмами",
                color = FolderBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Выберите папки с видео — библиотека соберётся автоматически",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanStatusBadge(
    libraryState: LibraryState,
    onRefresh: () -> Unit
) {
    val timeText = libraryState.lastScanTime?.let { ts ->
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                libraryState.isScanning -> "Обновление..."
                timeText != null -> "Обновлено: $timeText"
                else -> "Библиотека не сканировалась"
            },
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(onClick = onRefresh, enabled = !libraryState.isScanning) {
            Text("Обновить", color = FolderBlue, fontSize = 13.sp)
        }
    }
}
@Composable
private fun DashboardActionButtons(
    folderCount: Int,
    onBackToKidMode: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onAddFolders: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ParentActionButton("Назад", onBackToKidMode, Modifier.weight(1f))
        ParentActionButton(
            "Смотреть",
            onNavigateToPlayback,
            Modifier.weight(1f),
            accent = true,
            enabled = folderCount > 0
        )
        ParentActionButton("Добавить", onAddFolders, Modifier.weight(1f))
        ParentActionButton("Настройки", onNavigateToSettings, Modifier.weight(1f))
        ParentActionButton("Выход", onExit, Modifier.weight(1f), destructive = true)
    }
}

/**
 * Strict, adult control-panel button for the parent dashboard: flat dark
 * surface, no bounce animation, single accent (blue) and red text reserved
 * for destructive actions.
 */
@Composable
private fun ParentActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        destructive -> RedButton
        accent -> Color.White
        else -> Color.White.copy(alpha = 0.85f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (accent && enabled) FolderBlue else CardSurface,
        modifier = modifier.height(BUTTON_HEIGHT)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun BrowseBarRow(
    currentPath: String,
    rootFolder: String?,
    onNavigateUp: () -> Unit,
    onRemove: (String) -> Unit,
    onSetCover: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderBrowseBar(
            currentPath = currentPath,
            onNavigateUp = onNavigateUp,
            modifier = Modifier.weight(1f)
        )
        if (onSetCover != null) {
            Surface(
                onClick = onSetCover,
                shape = RoundedCornerShape(12.dp),
                color = CardSurface
            ) {
                Text(
                    text = "🖼 Обложка",
                    fontSize = 12.sp,
                    color = FolderBlue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
        if (rootFolder != null) {
            Surface(
                onClick = { onRemove(rootFolder) },
                shape = RoundedCornerShape(12.dp),
                color = RedButton.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "Убрать из библиотеки",
                    fontSize = 12.sp,
                    color = RedButton,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderBrowseBar(
    currentPath: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onNavigateUp,
        shape = RoundedCornerShape(12.dp),
        color = CardSurface,
        modifier = modifier
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
            Text(
                text = "↰",
                fontSize = 18.sp,
                color = FolderBlue
            )
        }
    }
}

@Composable
private fun SubfolderRow(
    folderName: String,
    onEnter: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
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

/**
 * Video row in the management browse view: taps offer playback (with the
 * confirmation dialog), same as the playback picker.
 */
@Composable
private fun VideoListItem(
    fileName: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
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
