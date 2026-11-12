package org.rabicraft.app.data.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Offline OCR through ML Kit's bundled Latin model: recognition runs entirely on the device and the
 * model ships inside the APK, so there is nothing to download and no permission to request. The
 * recogniser reads the bitmap the viewer already renders; nothing leaves the phone.
 */
class OcrEngine {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(pageIndex: Int, bitmap: Bitmap): OcrPageResult? =
        withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val result: Text = Tasks.await(recognizer.process(image), 60, TimeUnit.SECONDS)
                val words = mutableListOf<OcrWord>()
                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            val box = element.boundingBox ?: continue
                            if (element.text.isBlank() || bitmap.width <= 0 || bitmap.height <= 0) continue
                            words.add(
                                OcrWord(
                                    text = element.text,
                                    left = box.left.toFloat() / bitmap.width,
                                    top = box.top.toFloat() / bitmap.height,
                                    right = box.right.toFloat() / bitmap.width,
                                    bottom = box.bottom.toFloat() / bitmap.height
                                )
                            )
                        }
                    }
                }
                OcrPageResult(pageIndex = pageIndex, text = result.text, words = words)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
    }
}
