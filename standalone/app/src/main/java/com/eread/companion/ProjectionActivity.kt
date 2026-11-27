package com.eread.companion

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** 透明 Activity：只负责向系统申请一次截屏授权。 */
class ProjectionActivity : Activity() {

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        request()
    }

    private fun request() {
        if (launched) return
        launched = true
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) return
        if (resultCode != RESULT_OK || data == null) {
            finish()
            return
        }
        val service = Intent(this, ProjectionService::class.java)
            .putExtra("code", resultCode)
            .putExtra("data", data)
        runCatching { startForegroundService(service) }
        finish()
    }

    companion object {
        private const val REQ = 4401
    }
}
