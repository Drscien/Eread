package com.eread.companion

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.content.ComponentName
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors

/** 借无障碍的 takeScreenshot 截一帧，无需 MediaProjection 的每次授权。 */
class OcrService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: OcrService? = null
            private set

        fun isEnabled(context: android.content.Context): Boolean {
            val enabled = runCatching {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            }.getOrDefault(0)
            if (enabled != 1) return false
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val target = ComponentName(context, OcrService::class.java)
            return flat.split(':').any { raw ->
                raw.isNotBlank() && ComponentName.unflattenFromString(raw) == target
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private val captureExecutor = Executors.newSingleThreadExecutor()

    fun capture(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            callback(null)
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, captureExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hw = result.hardwareBuffer
                val bmp = runCatching {
                    Bitmap.wrapHardwareBuffer(hw, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull()
                runCatching { hw.close() }
                callback(bmp)
            }

            override fun onFailure(errorCode: Int) {
                callback(null)
            }
        })
    }
}
