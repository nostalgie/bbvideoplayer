package com.dima.bbvideoplayer.utils

import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class HuaweiStorageHelperTest {

    @Test
    fun isHuaweiDevice_detectsHuaweiAndHonor() {
        ShadowBuild.setManufacturer("HUAWEI")
        assertThat(HuaweiStorageHelper.isHuaweiDevice()).isTrue()

        ShadowBuild.setManufacturer("HONOR")
        assertThat(HuaweiStorageHelper.isHuaweiDevice()).isTrue()

        ShadowBuild.setManufacturer("samsung")
        assertThat(HuaweiStorageHelper.isHuaweiDevice()).isFalse()
    }

    @Test
    fun isSdCardPath_internalStorageIsNotSdCard() {
        assertThat(isSdCardPath("/storage/emulated/0")).isFalse()
    }

    @Test
    fun isSdCardPath_uuidPattern() {
        assertThat(isSdCardPath("/storage/1234-5678")).isTrue()
    }

    @Test
    fun isSdCardPath_sdcard1Path() {
        assertThat(isSdCardPath("/storage/sdcard1")).isTrue()
    }

    @Test
    fun isSdCardPath_extSdCardPath() {
        assertThat(isSdCardPath("/storage/extSdCard")).isTrue()
    }
}
