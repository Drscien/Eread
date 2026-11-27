package com.eread.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 常驻录屏截屏服务：授权一次后，多次抓帧不再弹窗。默认不启动。 */
class ProjectionService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    @Volatile
    private var latest: Bitmap? = null

    @Volatile
    private var latestAt = 0L
    private var pending: Request? = null
    private val lock = Any()
    private val imageThread = HandlerThread("eread-projection").apply { start() }
    private val imageHandler = Handler(imageThread.looper)

    private class Request(val callback: (Bitmap?) -> Unit, val fallback: Bitmap?)
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (projection != null) return START_NOT_STICKY
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (code == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val token = runCatching { manager.getMediaProjection(code, data) }.getOrNull()
        if (token == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = token
        token.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, imageHandler)
        val wm = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader = imageReader
        imageReader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bitmap = runCatching { toBitmap(image, width, height) }.getOrNull()
            image.close()
            if (bitmap == null) return@setOnImageAvailableListener
            val copy: Bitmap?
            val req: Request?
            synchronized(lock) {
                latest?.recycle()
                latest = bitmap
                latestAt = System.currentTimeMillis()
                copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                req = pending
                pending = null
            }
            req?.let {
                it.callback(copy)
                it.fallback?.recycle()
            }
        }, handler)
        val dm = getSystemService(DisplayManager::class.java)
        display = projection?.createVirtualDisplay(
            "eread-capture",
            width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, handler,
        )
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("capture", "录屏截屏", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ProjectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(this, "capture")
            .setContentTitle("Eread 录屏截屏已开启")
            .setContentText("人物速查识别时使用 · 点按停止")
            .setSmallIcon(R.drawable.ic_search)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(91, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(91, notification)
        }
    }

    private fun toBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        if (cropped !== bitmap) bitmap.recycle()
        if (cropped.width > 1080) {
            val scaled = Bitmap.createScaledBitmap(
                cropped, 1080, (cropped.height * (1080f / cropped.width)).toInt(), true,
            )
            if (scaled !== cropped) cropped.recycle()
            return scaled
        }
        return cropped
    }

    private fun grab(callback: (Bitmap?) -> Unit) {
        synchronized(lock) {
            pending?.let { old ->
                pending = null
                old.callback(old.fallback)
            }
            val frame = latest
            val age = System.currentTimeMillis() - latestAt
            // 画面超过 2 秒没有更新时，不再回退旧帧，避免把上一页当成当前页
            val fallback = if (frame != null && age < 2_000) frame.copy(Bitmap.Config.ARGB_8888, false) else null
            val request = Request(callback, fallback)
            pending = request
            handler.postDelayed({
                val expired = synchronized(lock) {
                    if (pending === request) {
                        pending = null
                        true
                    } else false
                }
                if (expired) request.callback(request.fallback)
            }, 800)
        }
    }

    override fun onDestroy() {
        instance = null
        latest?.recycle()
        latest = null
        pending?.let {
            it.fallback?.recycle()
            it.callback(null)
        }
        pending = null
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
        imageThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.eread.companion.PROJECTION_STOP"

        @Volatile
        var instance: ProjectionService? = null
            private set

        fun ready(): Boolean = instance != null

        suspend fun capture(): Bitmap? = suspendCancellableCoroutine { cont ->
            val service = instance
            if (service == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            service.grab { bmp -> if (cont.isActive) cont.resume(bmp) }
        }
    }
}
