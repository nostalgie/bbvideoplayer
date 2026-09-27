package com.dima.bbvideoplayer.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.DisplayMetrics
import android.view.OrientationEventListener
import android.view.Surface

object OrientationHelper {

    fun displayRotationIsLandscape(rotation: Int): Boolean =
        rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

    fun sensorAngleIsLandscape(angle: Int): Boolean =
        angle in 45..134 || angle in 225..314

    fun requestedOrientationForDisplayRotation(rotation: Int): Int = when (rotation) {
        Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun requestedOrientationForSensorLandscape(isLandscape: Boolean): Int =
        if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

    fun lockToDisplayRotation(activity: Activity) {
        val rotation = currentDisplayRotation(activity) ?: return
        activity.requestedOrientation = requestedOrientationForDisplayRotation(rotation)
    }

    fun restoreFreeRotation(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    fun isWindowPortrait(windowWidth: Int, windowHeight: Int): Boolean =
        windowHeight >= windowWidth

    fun isWindowPortrait(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            isWindowPortrait(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay?.getRealMetrics(metrics)
            isWindowPortrait(metrics.widthPixels, metrics.heightPixels)
        }
    }

    /** Surface rotation on every API level (display/currentWindowMetrics require API 30). */
    private fun currentDisplayRotation(activity: Activity): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay?.rotation
        }

    /** True when the device is held one way but the activity window is laid out the other. */
    fun needsOrientationCorrection(displayRotation: Int, windowWidth: Int, windowHeight: Int): Boolean =
        displayRotationIsLandscape(displayRotation) ==
            isWindowPortrait(windowWidth, windowHeight)

    fun needsOrientationCorrection(activity: Activity): Boolean {
        val rotation = currentDisplayRotation(activity) ?: return false
        return displayRotationIsLandscape(rotation) == isWindowPortrait(activity)
    }

    fun needsOrientationCorrection(sensorLandscape: Boolean, windowPortrait: Boolean): Boolean =
        sensorLandscape == windowPortrait

    /**
     * Listens for the first reliable accelerometer reading and rotates the activity if
     * the window still does not match how the device is held (common on Honor cold start).
     */
    fun listenForStartupCorrection(
        activity: Activity,
        onDone: () -> Unit = {}
    ): OrientationEventListener? {
        val listener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN) return
                val sensorLandscape = sensorAngleIsLandscape(angle)
                if (!needsOrientationCorrection(sensorLandscape, isWindowPortrait(activity))) {
                    disable()
                    onDone()
                    return
                }
                disable()
                activity.requestedOrientation =
                    requestedOrientationForSensorLandscape(sensorLandscape)
                activity.window.decorView.post {
                    restoreFreeRotation(activity)
                    onDone()
                }
            }
        }
        return if (listener.canDetectOrientation()) listener.apply { enable() } else null
    }
}
