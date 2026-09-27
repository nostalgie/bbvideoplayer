package com.dima.bbvideoplayer

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityIntentTest {

    @Test
    fun launcherCategory_isLauncherIntent() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        assertThat(MainActivity.isLauncherIntent(intent)).isTrue()
    }

    @Test
    fun leanbackLauncherCategory_isLauncherIntent() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        assertThat(MainActivity.isLauncherIntent(intent)).isTrue()
    }

    @Test
    fun homeIntent_isNotLauncherIntent() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        assertThat(MainActivity.isLauncherIntent(intent)).isFalse()
    }

    @Test
    fun nullIntent_isNotLauncherIntent() {
        assertThat(MainActivity.isLauncherIntent(null)).isFalse()
    }
}
