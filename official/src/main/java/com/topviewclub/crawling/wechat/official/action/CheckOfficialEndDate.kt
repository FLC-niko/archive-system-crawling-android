package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.wechat.official.officialTimeFormat
import com.topviewclub.crawling.wechat.official.OfficialPageDetector
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logW
import android.os.Handler

class CheckOfficialEndDate : Action {

    companion object {
        private const val PUBLISH_DATE_ID = "com.tencent.mm:id/ac5"
        private const val THE_END_TEXT = OfficialPageDetector.THE_END_TEXT
        private const val MAX_CONSECUTIVE_BACK_REQUESTS = 2
    }

    override val actionName: String = "CheckOfficialEndDate"

    private var lastServiceTag: String? = null

    @Volatile
    private var captureInFlight = false

    @Volatile
    private var pendingNextAction: String? = null

    @Volatile
    private var motionWakeScheduled = false

    @Volatile
    private var emptyDateRetryCount = 0

    // 防止 Check → BackTo → Check 乒乓：BackTo 刚确认过列表页后不得再次无进展地转回。
    @Volatile
    private var consecutiveBackRequests = 0

    override fun reset() {
        captureInFlight = false
        pendingNextAction = null
        motionWakeScheduled = false
        emptyDateRetryCount = 0
        consecutiveBackRequests = 0
    }

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        if (lastServiceTag != service.serviceTag) {
            lastServiceTag = service.serviceTag
            reset()
        }
        val motionRemaining = OfficialListMotionGate.remainingMs()
        if (motionRemaining > 0L) {
            if (!motionWakeScheduled) {
                motionWakeScheduled = true
                Handler(service.mainLooper).postDelayed({
                    motionWakeScheduled = false
                    service.resumeCurrentAction()
                }, motionRemaining)
            }
            return actionName
        }
        pendingNextAction?.let { next ->
            pendingNextAction = null
            logI(actionName, "OCR 页面判断完成，下一步: $next")
            service.resumeCurrentAction()
            return next
        }

        service.resumeServiceDelay(event, 100L)
        val root = service.rootInActiveWindow
        // 优先使用当前 Activity：延迟探针携带的旧事件类名可能仍是文章 WebView，
        // 会误导 isArticleDetailPage 判定“仍在文章页”，造成 Check/BackTo 死循环。
        val pageClass = service.currentWechatActivity
            ?.takeIf { it.isNotBlank() }
            ?: event.className?.toString().orEmpty()

        val recyclerView = root?.findNodeOrNull { className == CLS_RECYCLER_VIEW }
        if (recyclerView == null) {
            recognizeEmptyAccessibilityPage(service, root, pageClass)
            return actionName
        }
        OfficialListMotionGate.markInspected()
        val end = root.findNodeOrNull {
            text?.toString() == THE_END_TEXT
        }
        // 已经到达推文页末尾
        if (end != null) {
            try {
                emptyDateRetryCount = 0
                consecutiveBackRequests = 0
                return AutoOperationService.ActionType.ActionSuccess
            } finally {
                service.resumeServiceDelay(event, 0L)
            }
        }
        try {
            consecutiveBackRequests = 0
            return if (isAfterPublishDate(service.endDate, root)) {
                emptyDateRetryCount = 0
                "EnterOfficialArticle"
            } else {
                "ScrollOfficialList"
            }
        } finally {
            service.resumeServiceDelay(event, 10L)
        }
    }

    private fun recognizeEmptyAccessibilityPage(
        service: AutoOperationService,
        root: AccessibilityNodeInfo?,
        pageClass: String,
    ) {
        if (captureInFlight) return
        captureInFlight = true
        val accepted = OfficialScreenReader.recognize(
            service = service,
            onSuccess = { lines ->
                OfficialListMotionGate.markInspected()
                val snapshot = lines.joinToString(" | ") {
                    "${it.text}@${it.bounds.top}"
                }
                logI(actionName, "OCR 页面: $snapshot")

                // 1. 深度检测是否仍在文章详情页（包括正文元数据与底部操作栏）
                val stillInArticle = OfficialPageDetector.isArticleDetailPage(
                    lines,
                    root,
                    pageClass,
                    currentActivity = service.currentWechatActivity,
                )
                if (stillInArticle) {
                    emptyDateRetryCount = 0
                    if (consecutiveBackRequests >= MAX_CONSECUTIVE_BACK_REQUESTS) {
                        // 返回动作刚确认过列表页却又被判成文章页：若无脑继续返回会
                        // 在 Check/BackTo 之间死循环，这里强制按列表滚动恢复推进。
                        consecutiveBackRequests = 0
                        logW(actionName, "连续返回请求无进展，按列表页强制滚动恢复")
                        pendingNextAction = "ScrollOfficialList"
                    } else {
                        consecutiveBackRequests++
                        logW(actionName, "OCR 检测到文章特有元素，页面仍在文章中，转回返回动作")
                        pendingNextAction = "BackToOfficialArticleList"
                    }
                    captureInFlight = false
                    service.resumeCurrentAction()
                    return@recognize
                }

                // 2. 检查是否已到达末尾
                if (lines.any { it.text.contains(THE_END_TEXT) }) {
                    logI(actionName, "OCR 检测到已到达历史消息末尾")
                    emptyDateRetryCount = 0
                    pendingNextAction = AutoOperationService.ActionType.ActionSuccess
                    captureInFlight = false
                    service.resumeCurrentAction()
                    return@recognize
                }

                // 3. 提取可见日期
                val visibleDates = lines.asSequence()
                    .mapNotNull { line ->
                        OfficialPageDetector.extractDateOrNull(line.text)?.let { date ->
                            line.bounds.top to date
                        }
                    }
                    .toList()

                if (visibleDates.isNotEmpty()) {
                    emptyDateRetryCount = 0
                    consecutiveBackRequests = 0
                    val oldestVisibleDate = visibleDates.maxByOrNull { it.first }?.second
                    pendingNextAction = if (service.endDate >= System.currentTimeMillis()) {
                        "EnterOfficialArticle"
                    } else if (oldestVisibleDate != null && oldestVisibleDate <= service.endDate) {
                        "EnterOfficialArticle"
                    } else {
                        "ScrollOfficialList"
                    }
                } else {
                    // 4. 屏幕中没有日期，检查是否是列表头部或需要防误滑保护
                    val isListPage = OfficialPageDetector.isOfficialListPage(
                        lines,
                        root,
                        pageClass,
                        currentActivity = service.currentWechatActivity,
                    )
                    if (isListPage) {
                        emptyDateRetryCount = 0
                        consecutiveBackRequests = 0
                        pendingNextAction = if (service.endDate >= System.currentTimeMillis()) {
                            "EnterOfficialArticle"
                        } else {
                            "ScrollOfficialList"
                        }
                    } else {
                        // 既非明确列表也无日期：防误滑保护，重试或转回返回动作
                        emptyDateRetryCount++
                        logW(actionName, "未检测到列表特征或日期 (尝试 $emptyDateRetryCount)，执行防御")
                        pendingNextAction = if (emptyDateRetryCount <= 2) {
                            "BackToOfficialArticleList"
                        } else {
                            emptyDateRetryCount = 0
                            "ScrollOfficialList"
                        }
                    }
                }

                captureInFlight = false
                service.resumeCurrentAction()
            },
            onFailure = {
                OfficialListMotionGate.markInspected()
                captureInFlight = false
                pendingNextAction = "BackToOfficialArticleList"
                service.resumeCurrentAction()
            },
        )
        if (!accepted) captureInFlight = false
    }

    private fun isAfterPublishDate(endDate: Long, root: AccessibilityNodeInfo): Boolean {
        // 大于当前时间
        if (endDate >= System.currentTimeMillis()) return true

        val targets = root.findNodes {
            className == CLS_TEXT_VIEW
                    && viewIdResourceName == PUBLISH_DATE_ID
        }

        // 屏幕上刚好没有发布时间，则继续下滚动
        if (targets.isEmpty()) return false

        targets.last().text?.toString()?.let {
            return officialTimeFormat(it) <= endDate
        }

        return false
    }

}
