package com.dima.bbvideoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.bbvideoplayer.data.VideoLibraryService
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.ui.components.FolderCard
import com.dima.bbvideoplayer.ui.components.ParentBackButton
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
import com.dima.bbvideoplayer.utils.abbreviateFolderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PICKER_ROW_CORNER = 10.dp
private val PICKER_ROW_VERTICAL_PADDING = 10.dp
private val PICKER_ROW_HORIZONTAL_PADDING = 12.dp
private val PICKER_MIN_CARD_WIDTH = 160.dp

/**
 * Playback-only parent screen: pick a folder, pick a video, play it.
 * Deliberately free of any library management — adding, removing and
 * settings live on the dashboard / settings screens.
 */
@Composable
fun PlaybackPickerScreen(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    onBack: () -> Unit,
    onPlayVideo: (Int) -> Unit
) {
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryState by videoLibraryService.libraryState.collectAsStateWithLifecycle()

    var browsePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPlayPath by rememberSaveable { mutableStateOf<String?>(null) }
    val videoListState = rememberLazyListState()
    val allVideos = libraryState.videos

    LaunchedEffect(watchedFolders, browsePath) {
        if (browsePath != null && !isPathWithinWatchedFolders(browsePath!!, watchedFolders)) {
            browsePath = null
        }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParentScreenHeader(
                title = "Что посмотреть",
                rightSlot = {
                    Text(
                        text = "Папки (${watchedFolders.size}) · Видео (${allVideos.size})",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ParentBackButton(onClick = onBack)
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            when {
                watchedFolders.isEmpty() -> Text(
                    text = "Библиотека пуста — сначала добавьте папки на главном экране",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )

                browsePath != null -> PickerBrowseView(
                    browsePath = browsePath!!,
                    watchedFolders = watchedFolders,
                    allVideos = allVideos,
                    videoListState = videoListState,
                    onBrowsePathChange = { browsePath = it },
                    onPlayVideo = { pendingPlayPath = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )

                else -> PickerFolderGrid(
                    watchedFolders = watchedFolders,
                    allVideos = allVideos,
                    onBrowsePathChange = { browsePath = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PickerFolderGrid(
    watchedFolders: List<String>,
    allVideos: List<com.dima.bbvideoplayer.data.VideoEntry>,
    onBrowsePathChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PICKER_MIN_CARD_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
    }
}

@Composable
private fun PickerBrowseView(
    browsePath: String,
    watchedFolders: List<String>,
    allVideos: List<com.dima.bbvideoplayer.data.VideoEntry>,
    videoListState: LazyListState,
    onBrowsePathChange: (String?) -> Unit,
    onPlayVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val videosByParentPath = remember(allVideos) { buildVideosByParentPath(allVideos) }

    var subdirectories by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingSubdirs by remember { mutableStateOf(false) }

    LaunchedEffect(browsePath) {
        loadingSubdirs = true
        subdirectories = emptyList() // stale rows from the previous folder must not show
        subdirectories = withContext(Dispatchers.IO) { listSubdirectories(browsePath) }
        loadingSubdirs = false
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PickerBrowseBar(
                currentPath = browsePath,
                onNavigateUp = { onBrowsePathChange(parentBrowsePath(browsePath, watchedFolders)) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = videoListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (loadingSubdirs) {
                    item(key = "loading") {
                        Text(
                            text = "Загрузка...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                items(
                    items = subdirectories,
                    key = { it }
                ) { subdirPath ->
                    PickerSubfolderRow(
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
                    PickerVideoRow(
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
            }
            VerticalScrollbar(
                state = videoListState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun PickerBrowseBar(
    currentPath: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onNavigateUp,
        shape = RoundedCornerShape(PICKER_ROW_CORNER),
        color = CardSurface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PICKER_ROW_HORIZONTAL_PADDING,
                    vertical = PICKER_ROW_VERTICAL_PADDING
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = 18.sp)
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
private fun PickerSubfolderRow(
    folderName: String,
    onEnter: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(PICKER_ROW_CORNER),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PICKER_ROW_HORIZONTAL_PADDING,
                    vertical = PICKER_ROW_VERTICAL_PADDING
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = 18.sp)
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
                fontSize = 18.sp,
                color = FolderBlue
            )
        }
    }
}

@Composable
private fun PickerVideoRow(
    fileName: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(PICKER_ROW_CORNER),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PICKER_ROW_HORIZONTAL_PADDING, vertical = 8.dp),
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
