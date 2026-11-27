package io.legado.app.ui.book.read.trace

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.util.WeakHashMap

/**
 * 涓嶄慨鏀?legado 浠讳綍鐜版湁 Activity 鐨勬帴鍏ョ偣銆? *
 * 閫氳繃 AndroidManifest 閲屾敞鍐屼竴涓?ContentProvider锛屽湪杩涚▼鍚姩鏃舵敞鍐? * ActivityLifecycleCallbacks锛屾娴嬪埌闃呰椤靛氨鎸備笂鎮诞灞傘€? */
class TraceInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            private val controllers = WeakHashMap<Activity, TraceController>()

            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name != READER_ACTIVITY) return
                val controller = controllers[activity]
                    ?: TraceController(activity).also { controllers[activity] = it }
                controller.attach()
            }

            override fun onActivityPaused(activity: Activity) {
                controllers[activity]?.hide()
            }

            override fun onActivityDestroyed(activity: Activity) {
                controllers.remove(activity)?.dispose()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val READER_ACTIVITY = "io.legado.app.ui.book.read.ReadBookActivity"
    }
}
