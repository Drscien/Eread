package com.eread.companion

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Ocr {
    private val client by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        client.process(image)
            .addOnSuccessListener { result ->
                val sb = StringBuilder()
                result.textBlocks.forEach { block ->
                    block.lines.forEach { line -> sb.append(line.text).append('\n') }
                }
                if (cont.isActive) cont.resume(sb.toString())
            }
            .addOnFailureListener { if (cont.isActive) cont.resume("") }
    }
}
