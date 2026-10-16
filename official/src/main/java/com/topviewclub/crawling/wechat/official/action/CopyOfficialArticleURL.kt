package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.back
import com.topviewclub.crawling.service.tap
import com.topviewclub.common.log.logI
import android.os.Handler

class CopyOfficialArticleURL : Action {

    private companion object {
        private const val COPY_URL_TEXT = "复制链接"
        private const val FLOATING_TEXT = "浮窗"
        private const val SEARCH_TEXT = "搜索页面内容"
        private const val ITEM_ID = "com.tencent.mm:id/ko8"
        private const val MAX_OCR_MISSES = 3
        private const val MAX_MENU_REOPEN_ATTEMPTS = 2
        private const val MAX_RECOGNITION_ATTEMPTS = 9
        private val MENU_MARKERS = listOf(
            "浮窗",
            "搜索页面内容",
            "转发给朋友",
            "收藏",
            "在浏览器打开",
            "调整字体",
        )
    }

    override val actionName: String = "CopyOfficialArticleURL"

    @Volatile
    private var captureInFlight = false

    @Volatile
    private var copyRequested = false

    @Volatile
    private var pendingNext = false

    @Volatile
    private var ocrMisses = 0

    @Volatile
    private var pendingReopen = false

    @Volatile
    private var reopenScheduled = false

    @Volatile
    private var reopenAttempts = 0

    @Volatile
    private var pendingFailure: String? = null

    @Volatile
    private var recognitionAttempts = 0

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val generation = service.currentTaskGeneration()
        pendingFailure?.let { error ->
            resetState()
            throw ActionException(error)
        }
        if (pendingNext) {
            resetState()
            service.resumeCurrentAction(generation)
            return "GetOfficialArticleURL"
        }
        if (pendingReopen) {
            pendingReopen = false
            ocrMisses = 0
            service.resumeCurrentAction(generation)
            return "OpenMoreEnum"
        }
        if (reopenScheduled) return actionName
        val roots = service.windows.asSequence().mapNotNull { it.root }.toList()
        val hasArticleMenu = roots.any { root ->
            root.findNodeOrNull {
                val label = text?.toString().orEmpty()
                (label == FLOATING_TEXT || label == SEARCH_TEXT) &&
                        (viewIdResourceName == ITEM_ID || viewIdResourceName.isNullOrEmpty())
            } != null
        }
        val copyUrlButton = if (hasArticleMenu) {
            roots.asSequence().mapNotNull { root ->
                root.findNodeOrNull {
                    text?.toString() == COPY_URL_TEXT &&
                            (viewIdResourceName == ITEM_ID || viewIdResourceName.isNullOrEmpty())
                }
            }.firstOrNull()
        } else {
            null
        }

        if (copyUrlButton != null) {
            val clicked = copyUrlButton.click() ||
                    copyUrlButton.parent?.click() == true ||
                    copyUrlButton.parent?.parent?.click() == true
            if (clicked) {
                resetState()
                service.resumeServiceDelay(event, 0L)
                return "GetOfficialArticleURL"
            }
        }
        if (!captureInFlight && !copyRequested) {
            if (recognitionAttempts >= MAX_RECOGNITION_ATTEMPTS) {
                pendingFailure = "OFFICIAL_COPY_LINK_UNAVAILABLE"
                service.resumeCurrentAction(generation)
                return actionName
            }
            recognitionAttempts++
            captureInFlight = true
            OfficialScreenReader.recognize(
                service = service,
                onSuccess = { lines ->
                    if (!service.isTaskGenerationActive(generation)) return@recognize
                    val target = lines.firstOrNull {
                        it.text.replace(" ", "").contains(COPY_URL_TEXT)
                    }
                    val dispatched = target?.let {
                        service.tap(
                            it.bounds.centerX().toFloat(),
                            it.bounds.centerY().toFloat(),
                        )
                    } == true
                    copyRequested = dispatched
                    captureInFlight = false
                    if (dispatched) {
                        ocrMisses = 0
                    } else {
                        ocrMisses++
                    }
                    logI(
                        actionName,
                        "OCR 复制链接 accepted=$dispatched, miss=$ocrMisses/$MAX_OCR_MISSES, " +
                                "menu=${lines.joinToString("/") { it.text }}",
                    )
                    if (!dispatched && ocrMisses >= MAX_OCR_MISSES) {
                        reopenAttempts++
                        if (reopenAttempts >= MAX_MENU_REOPEN_ATTEMPTS) {
                            pendingFailure = "OFFICIAL_COPY_LINK_UNAVAILABLE"
                            service.resumeCurrentAction(generation)
                            return@recognize
                        }
                        reopenScheduled = true
                        val menuVisible = lines.any { line ->
                            MENU_MARKERS.any { marker ->
                                line.text.replace(" ", "").contains(marker)
                            }
                        }
                        if (menuVisible) {
                            service.back()
                            logI(actionName, "复制链接连续未识别，关闭残留菜单后重开")
                        } else {
                            logI(actionName, "复制链接连续未识别，当前为正文，重新打开菜单")
                        }
                        Handler(service.mainLooper).postDelayed({
                            if (!service.isTaskGenerationActive(generation)) return@postDelayed
                            reopenScheduled = false
                            pendingReopen = true
                            copyRequested = false
                            service.resumeCurrentAction(generation)
                        }, if (menuVisible) 500L else 200L)
                        return@recognize
                    }
                    Handler(service.mainLooper).postDelayed({
                        if (!service.isTaskGenerationActive(generation)) return@postDelayed
                        pendingNext = dispatched
                        copyRequested = false
                        service.resumeCurrentAction(generation)
                    }, if (dispatched) 500L else 700L)
                },
                onFailure = {
                    if (!service.isTaskGenerationActive(generation)) return@recognize
                    captureInFlight = false
                    ocrMisses++
                    Handler(service.mainLooper).postDelayed(
                        { if (service.isTaskGenerationActive(generation)) service.resumeCurrentAction(generation) },
                        700L,
                    )
                },
            )
        }
        return actionName
    }

    private fun resetState() {
        captureInFlight = false
        copyRequested = false
        pendingNext = false
        pendingReopen = false
        reopenScheduled = false
        reopenAttempts = 0
        pendingFailure = null
        recognitionAttempts = 0
        ocrMisses = 0
    }

    override fun reset() = resetState()

}
