package com.dima.bbvideoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.bbvideoplayer.data.VideoRepository
import com.dima.bbvideoplayer.ui.components.BounceButton
import com.dima.bbvideoplayer.ui.components.ParentBackButton
import com.dima.bbvideoplayer.ui.components.ParentScreenHeader
import com.dima.bbvideoplayer.ui.theme.CardSurface
import com.dima.bbvideoplayer.ui.theme.DashboardBackground
import com.dima.bbvideoplayer.ui.theme.FolderBlue
import com.dima.bbvideoplayer.ui.theme.RedButton
import kotlinx.coroutines.launch

/**
 * Parent settings screen: player control placement (phone only), PIN change
 * and the destructive "clear library" action — deliberately moved out of the
 * dashboard so the main screen stays a lean management surface.
 */
@Composable
fun ParentSettingsScreen(
    videoRepository: VideoRepository,
    onBack: () -> Unit,
    showControlsSideSetting: Boolean = true,
    showPinControls: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val controlsSide by videoRepository.controlsSide.collectAsStateWithLifecycle(
        initialValue = VideoRepository.CONTROLS_SIDE_LEFT
    )
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())

    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePinDialog by rememberSaveable { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ParentScreenHeader(
                title = "Настройки",
                rightSlot = { ParentBackButton(onClick = onBack) }
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (showControlsSideSetting) {
                SettingsSectionCard {
                    SettingsTitle("Расположение кнопок плеера")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SideOptionButton(
                            label = "Слева",
                            value = VideoRepository.CONTROLS_SIDE_LEFT,
                            selected = controlsSide == VideoRepository.CONTROLS_SIDE_LEFT,
                            onSelect = { side -> coroutineScope.launch { videoRepository.saveControlsSide(side) } }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        SideOptionButton(
                            label = "Справа",
                            value = VideoRepository.CONTROLS_SIDE_RIGHT,
                            selected = controlsSide == VideoRepository.CONTROLS_SIDE_RIGHT,
                            onSelect = { side -> coroutineScope.launch { videoRepository.saveControlsSide(side) } }
                        )
                    }
                }
            }

            if (showPinControls) {
                SettingsSectionCard {
                    SettingsTitle("Родительский ПИН")
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsActionButton("Сменить ПИН") { showChangePinDialog = true }
                }
            }

            SettingsSectionCard {
                SettingsTitle("Библиотека")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Папок в библиотеке: ${watchedFolders.size}",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsActionButton(
                    text = "Удалить все папки из библиотеки",
                    enabled = watchedFolders.isNotEmpty()
                ) { showClearAllDialog = true }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            content = content
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )
}

@Composable
private fun SettingsActionButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = CardSurface,
        modifier = Modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = FolderBlue,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = CardSurface,
        modifier = Modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) RedButton else Color.White.copy(alpha = 0.35f),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SideOptionButton(
    label: String,
    value: String,
    selected: Boolean,
    onSelect: (String) -> Unit
) {
    BounceButton(
        text = label,
        onClick = { onSelect(value) },
        backgroundColor = if (selected) FolderBlue else CardSurface,
        width = 88.dp,
        height = 40.dp,
        fontSize = 12.sp
    )
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
                Text("Сохранить", color = if (pinValid) FolderBlue else Color.Gray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
