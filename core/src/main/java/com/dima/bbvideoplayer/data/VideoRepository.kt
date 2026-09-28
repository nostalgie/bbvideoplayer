package com.dima.bbvideoplayer.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dima.bbvideoplayer.utils.extractParentFolderPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Repository for watched folder paths and kid-mode folder selection.
 *
 * Videos are discovered dynamically by [VideoLibraryService] from watched folders.
 * Migrates legacy `video_uris` / `selected_videos` keys on first read.
 */
class VideoRepository(private val context: Context) {

    companion object {
        /** Initial parent PIN used until the parent changes it in the dashboard. */
        const val DEFAULT_PARENT_PIN = "1111"

        /** Stored values for the player controls side setting (phone edition). */
        const val CONTROLS_SIDE_LEFT = "left"
        const val CONTROLS_SIDE_RIGHT = "right"

        private val WATCHED_FOLDERS_KEY = stringPreferencesKey("watched_folders")
        private val EXPANDED_FOLDERS_KEY = stringPreferencesKey("expanded_folders")
        private val SELECTED_FOLDERS_KEY = stringPreferencesKey("selected_folders")
        private val PARENT_PIN_KEY = stringPreferencesKey("parent_pin")
        private val CONTROLS_SIDE_KEY = stringPreferencesKey("controls_side")

        private val LEGACY_VIDEO_URIS_KEY = stringPreferencesKey("video_uris")
        private val LEGACY_SELECTED_VIDEOS_KEY = stringPreferencesKey("selected_videos")
        private const val LEGACY_SEPARATOR = "|"
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun serialize(paths: List<String>): String {
        val array = JSONArray()
        paths.forEach { array.put(it) }
        return array.toString()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun deserialize(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        if (raw.trimStart().startsWith("[")) {
            val array = JSONArray(raw)
            return (0 until array.length()).map { array.getString(it) }
        }
        return raw.split(LEGACY_SEPARATOR).filter { it.isNotBlank() }
    }

    private fun parseCommaSet(raw: String): Set<String> =
        if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()

    private fun serializeSet(paths: Set<String>): String = serialize(paths.toList())

    /** Reads the JSON format, falling back to the legacy comma-joined one. */
    private fun parsePathSet(raw: String): Set<String> =
        if (raw.isBlank()) emptySet()
        else if (raw.trimStart().startsWith("[")) deserialize(raw).toSet()
        else parseCommaSet(raw)

    private fun migrateLegacyData(prefs: MutablePreferences) {
        val legacyUris = deserialize(prefs[LEGACY_VIDEO_URIS_KEY] ?: "")
        if (legacyUris.isEmpty()) return

        val watchedFolders = legacyUris
            .mapNotNull { extractParentFolderPath(it) }
            .distinct()

        if (watchedFolders.isEmpty()) {
            // content:// (or otherwise unresolvable) legacy library: keep the legacy
            // keys so the data is not destroyed and a future migration can still run.
            return
        }

        val legacySelected = parseCommaSet(prefs[LEGACY_SELECTED_VIDEOS_KEY] ?: "")
        val selectedFolders = legacySelected
            .mapNotNull { extractParentFolderPath(it) }
            .toSet()

        prefs[WATCHED_FOLDERS_KEY] = serialize(watchedFolders)
        if (selectedFolders.isNotEmpty()) {
            prefs[SELECTED_FOLDERS_KEY] = serializeSet(selectedFolders)
        }
        prefs.remove(LEGACY_VIDEO_URIS_KEY)
        prefs.remove(LEGACY_SELECTED_VIDEOS_KEY)
    }

    private fun readWatchedFolders(prefs: Preferences): List<String> =
        deserialize(prefs[WATCHED_FOLDERS_KEY] ?: "")

    val watchedFolders: Flow<List<String>> = context.videoPrefsDataStore.data.map { prefs ->
        readWatchedFolders(prefs)
    }

    val expandedFolders: Flow<Set<String>> = context.videoPrefsDataStore.data.map { prefs ->
        parsePathSet(prefs[EXPANDED_FOLDERS_KEY] ?: "")
    }

    val selectedFolders: Flow<Set<String>> = context.videoPrefsDataStore.data.map { prefs ->
        parsePathSet(prefs[SELECTED_FOLDERS_KEY] ?: "")
    }

    val parentPin: Flow<String> = context.videoPrefsDataStore.data.map { prefs ->
        prefs[PARENT_PIN_KEY]?.takeIf { it.isNotBlank() } ?: DEFAULT_PARENT_PIN
    }

    suspend fun saveParentPin(pin: String) {
        context.videoPrefsDataStore.edit { it[PARENT_PIN_KEY] = pin }
    }

    /**
     * Which screen side the phone player controls sit on. Only "right" is
     * stored; anything absent or unrecognized reads as the default left.
     */
    val controlsSide: Flow<String> = context.videoPrefsDataStore.data.map { prefs ->
        if (prefs[CONTROLS_SIDE_KEY] == CONTROLS_SIDE_RIGHT) CONTROLS_SIDE_RIGHT else CONTROLS_SIDE_LEFT
    }

    suspend fun saveControlsSide(side: String) {
        context.videoPrefsDataStore.edit { prefs ->
            prefs[CONTROLS_SIDE_KEY] = if (side == CONTROLS_SIDE_RIGHT) CONTROLS_SIDE_RIGHT else CONTROLS_SIDE_LEFT
        }
    }

    suspend fun ensureMigrated() {
        context.videoPrefsDataStore.edit { prefs ->
            if (prefs[WATCHED_FOLDERS_KEY].isNullOrBlank() &&
                !prefs[LEGACY_VIDEO_URIS_KEY].isNullOrBlank()
            ) {
                migrateLegacyData(prefs)
            }
        }
    }

    suspend fun addWatchedFolder(path: String) {
        context.videoPrefsDataStore.edit { prefs ->
            val existing = readWatchedFolders(prefs).toMutableList()
            if (path !in existing) {
                existing.add(path)
                prefs[WATCHED_FOLDERS_KEY] = serialize(existing)
            }
        }
    }

    suspend fun addWatchedFolders(paths: List<String>) {
        if (paths.isEmpty()) return
        context.videoPrefsDataStore.edit { prefs ->
            val existing = readWatchedFolders(prefs).toMutableList()
            val newPaths = paths.filter { it !in existing }
            if (newPaths.isNotEmpty()) {
                existing.addAll(newPaths)
                prefs[WATCHED_FOLDERS_KEY] = serialize(existing)
            }
        }
    }

    suspend fun removeWatchedFolder(path: String) {
        context.videoPrefsDataStore.edit { prefs ->
            val folders = readWatchedFolders(prefs).filter { it != path }
            prefs[WATCHED_FOLDERS_KEY] = serialize(folders)
            val selected = parsePathSet(prefs[SELECTED_FOLDERS_KEY] ?: "") - path
            prefs[SELECTED_FOLDERS_KEY] = serializeSet(selected)
        }
    }

    suspend fun clearAll() {
        context.videoPrefsDataStore.edit { prefs ->
            prefs.remove(WATCHED_FOLDERS_KEY)
            prefs.remove(SELECTED_FOLDERS_KEY)
            prefs.remove(EXPANDED_FOLDERS_KEY)
            prefs.remove(LEGACY_VIDEO_URIS_KEY)
            prefs.remove(LEGACY_SELECTED_VIDEOS_KEY)
        }
    }

    suspend fun saveExpandedFolders(folders: Set<String>) {
        context.videoPrefsDataStore.edit { prefs ->
            prefs[EXPANDED_FOLDERS_KEY] = serializeSet(folders)
        }
    }

    suspend fun saveSelectedFolders(selected: Set<String>) {
        context.videoPrefsDataStore.edit { prefs ->
            prefs[SELECTED_FOLDERS_KEY] = serializeSet(selected)
        }
    }

    suspend fun toggleFolderSelection(folderPath: String) {
        context.videoPrefsDataStore.edit { prefs ->
            val current = parsePathSet(prefs[SELECTED_FOLDERS_KEY] ?: "")
            val updated = if (folderPath in current) current - folderPath else current + folderPath
            prefs[SELECTED_FOLDERS_KEY] = serializeSet(updated)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun setLegacyDataForMigration(videoUris: List<String>, selectedVideos: Set<String>) {
        context.videoPrefsDataStore.edit { prefs ->
            prefs[LEGACY_VIDEO_URIS_KEY] = serialize(videoUris)
            prefs[LEGACY_SELECTED_VIDEOS_KEY] = selectedVideos.joinToString(",")
            prefs.remove(WATCHED_FOLDERS_KEY)
            prefs.remove(SELECTED_FOLDERS_KEY)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun legacyStateForTest(): Pair<List<String>, Set<String>> {
        val prefs = context.videoPrefsDataStore.data.first()
        return deserialize(prefs[LEGACY_VIDEO_URIS_KEY] ?: "") to
            parseCommaSet(prefs[LEGACY_SELECTED_VIDEOS_KEY] ?: "")
    }
}

/**
 * Top-level singleton DataStore for video preferences: one instance per file per
 * process. A class-member delegate would create a new DataStore per VideoRepository
 * and the instances would race on the same file.
 */
private val Context.videoPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "video_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)
