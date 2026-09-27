package com.dima.bbvideoplayer.utils

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class StoragePermissionHelperTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun preR_requiresLegacyReadPermission() {
        assertThat(StoragePermissionHelper.requiredPermissions())
            .isEqualTo(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        assertThat(StoragePermissionHelper.needsManageAllFilesAccess()).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun r_range_hasNoGrantableDialogPermission_andNeedsManageAllFiles() {
        assertThat(StoragePermissionHelper.requiredPermissions()).isEmpty()
        assertThat(StoragePermissionHelper.needsManageAllFilesAccess()).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun sV2_range_hasNoGrantableDialogPermission_andNeedsManageAllFiles() {
        assertThat(StoragePermissionHelper.requiredPermissions()).isEmpty()
        assertThat(StoragePermissionHelper.needsManageAllFilesAccess()).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun t_plus_requiresReadMediaVideo_withoutManageAllFiles() {
        assertThat(StoragePermissionHelper.requiredPermissions())
            .isEqualTo(arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO))
        assertThat(StoragePermissionHelper.needsManageAllFilesAccess()).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun hasStoragePermission_reflectsReadMediaVideoGrant() {
        assertThat(StoragePermissionHelper.hasStoragePermission(ApplicationProvider.getApplicationContext()))
            .isFalse()
    }
}
