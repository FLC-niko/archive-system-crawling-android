package com.topviewclub.crawling.wechat.official.action

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.AutoOperationService

internal data class RecognizedScreenLine(
    val text: String,
    val bounds: Rect,
)

/**
 * 通过无障碍服务自己的截图 API 读取微信自绘页面，再在设备本机完成中文 OCR。
 * 截图只用于状态和坐标检测；后续点击仍全部由 dispatchGesture 执行。
 */
internal object OfficialScreenReader {

    private const val TAG = "OfficialScreenReader"

    private val recognizer by lazy {
        TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build(),
        )
    }

    fun recognize(
        service: AutoOperationService,
        onSuccess: (List<RecognizedScreenLine>) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFailure(UnsupportedOperationException("无障碍截图需要 Android 11 或更高版本"))
            return false
        }

        return runCatching {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(
                    screenshot: AccessibilityService.ScreenshotResult,
                ) {
                    val bitmap = runCatching {
                        val wrapped = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace,
                        ) ?: error("无法读取无障碍截图 HardwareBuffer")
                        wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    }.also {
                        screenshot.hardwareBuffer.close()
                    }.getOrElse {
                        onFailure(it)
                        return
                    }

                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { result ->
                            val lines = result.textBlocks
                                .flatMap { it.lines }
                                .mapNotNull { line ->
                                    val bounds = line.boundingBox ?: return@mapNotNull null
                                    RecognizedScreenLine(line.text.trim(), Rect(bounds))
                                }
                                .filter { it.text.isNotBlank() }
                                .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
                            logI(TAG, "OCR lines=${lines.size}")
                            onSuccess(lines)
                        }
                        .addOnFailureListener {
                            logE(TAG, "OCR 失败: ${it.message}")
                            onFailure(it)
                        }
                        .addOnCompleteListener {
                            bitmap.recycle()
                        }
                }

                override fun onFailure(errorCode: Int) {
                    onFailure(IllegalStateException("无障碍截图失败: errorCode=$errorCode"))
                }
                },
            )
            true
        }.getOrElse {
            onFailure(it)
            false
        }
    }
}
