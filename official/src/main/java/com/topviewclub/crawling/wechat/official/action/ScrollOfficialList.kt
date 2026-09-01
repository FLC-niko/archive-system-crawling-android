package com.topviewclub.crawling.wechat.official.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import android.os.Handler
import android.os.SystemClock

/** 阻止积压的无障碍事件在列表仍滚动时提前触发 OCR 或坐标点击。 */
internal object OfficialListMotionGate {
    @Volatile
    private var stableAfter = 0L

    @Volatile
    private var scrollGeneration = 0

    @Volatile
    private var inspectedGeneration = 0

    @Synchronized
    fun markMovingFor(durationMs: Long) {
        scrollGeneration++
        stableAfter = SystemClock.uptimeMillis() + durationMs
    }

    fun markStable() {
        stableAfter = 0L
    }

    fun remainingMs(): Long =
        (stableAfter - SystemClock.uptimeMillis()).coerceAtLeast(0L)

    @Synchronized
    fun markInspected() {
        inspectedGeneration = scrollGeneration
    }

    fun canStartNextScroll(): Boolean =
        inspectedGeneration == scrollGeneration
}

class ScrollOfficialList : Action {

    private companion object {
        // 保留约四分之三屏的重叠区域，确保边界处的日期和文章标题会在相邻
        // 两次 OCR 中至少完整出现一次。慢速手势也能显著减少 RecyclerView 惯性。
        private const val SCROLL_START_Y = 1850f
        private const val SCROLL_END_Y = 1230f
        private const val SCROLL_DURATION_MS = 700L
        private const val SETTLE_DELAY_MS = 700L
    }

    override val actionName: String = "ScrollOfficialList"

    @Volatile
    private var scrolling = false

    @Volatile
    private var scrollCompleted = false

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        if (scrollCompleted) {
            scrollCompleted = false
            service.resumeCurrentAction()
            return "CheckOfficialEndDate"
        }
        val pageClass = event.className?.toString().orEmpty()
        if (pageClass.contains("WebView", ignoreCase = true) ||
            pageClass.contains("TmplWebViewMMUI", ignoreCase = true) ||
            pageClass.contains("MMWebView", ignoreCase = true)
        ) {
            logI(actionName, "检测到仍在 WebView 中，拦截下滑手势，转回 CheckOfficialEndDate")
            service.resumeServiceDelay(event, 0L)
            return "CheckOfficialEndDate"
        }

        val root = service.rootInActiveWindow
        if (root == null || root.childCount == 0) {
            if (!scrolling) {
                // 无论积压了多少窗口事件，一次滑动后都必须先完成一次稳定 OCR。
                // 这条约束保证日期标题不会未经检查就被连续两次手势推离屏幕。
                if (!OfficialListMotionGate.canStartNextScroll()) {
                    service.resumeServiceDelay(event, 100L)
                    return "CheckOfficialEndDate"
                }
                scrolling = true
                OfficialListMotionGate.markMovingFor(
                    SCROLL_DURATION_MS + SETTLE_DELAY_MS + 150L,
                )
                service.scroll(
                    540f,
                    SCROLL_START_Y,
                    540f,
                    SCROLL_END_Y,
                    startTime = 0L,
                    duration = SCROLL_DURATION_MS,
                    callback = object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription) {
                            // dispatchGesture 完成后 RecyclerView 仍会惯性滚动约
                            // 0.5-1 秒；必须等坐标稳定后再截图 OCR。
                            Handler(service.mainLooper).postDelayed({
                                scrolling = false
                                scrollCompleted = true
                                service.resumeCurrentAction()
                            }, SETTLE_DELAY_MS)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription) {
                            scrolling = false
                            OfficialListMotionGate.markStable()
                            service.resumeCurrentAction()
                        }
                    },
                )
                logI(
                    actionName,
                    "列表节点不可见，提交无障碍小步下滑手势: " +
                            "$SCROLL_START_Y->$SCROLL_END_Y",
                )
            }
            return actionName
        }
        val recyclerView = root.findNodeOrNull {
            className == CLS_RECYCLER_VIEW
        } ?: return actionName
        recyclerView.scrollForward()
        Thread.sleep(200L)
        service.resumeServiceDelay(event, 0L)
        return "CheckOfficialEndDate"
    }

}
