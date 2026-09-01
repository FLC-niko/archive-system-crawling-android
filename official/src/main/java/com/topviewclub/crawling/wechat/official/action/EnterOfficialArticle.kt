package com.topviewclub.crawling.wechat.official.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.wechat.official.officialTimeFormat
import com.topviewclub.crawling.wechat.official.OfficialPageDetector
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.common.log.logI
import android.os.Handler

class EnterOfficialArticle : Action {

    private data class ArticleCandidate(
        val line: RecognizedScreenLine,
        val publishDate: Long,
    )

    private data class ClickedArticle(
        val publishDate: Long,
        val normalizedTitle: String,
    )

    companion object {
        private const val PUBLISH_DATE_ID = "com.tencent.mm:id/ac5"
        private const val SEARCH_DATE_ID = "com.tencent.mm:id/eo"
        private const val SEARCH_DESC = "搜索"
    }

    override val actionName: String = "EnterOfficialArticle"

    private var stepInternal = 0
    private val clickedArticles = mutableListOf<ClickedArticle>()

    @Volatile
    private var captureInFlight = false

    @Volatile
    private var pendingNextAction: String? = null

    @Volatile
    private var articleOpening = false

    @Volatile
    private var openingArticle: ClickedArticle? = null

    @Volatile
    private var openingAttemptId = 0

    @Volatile
    private var motionWakeScheduled = false

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        pendingNextAction?.let { next ->
            pendingNextAction = null
            service.resumeCurrentAction()
            return next
        }
        if (articleOpening) {
            val pageClass = event.className?.toString().orEmpty()
            if (pageClass.contains("WebView", ignoreCase = true) ||
                pageClass.contains("TmplWebViewMMUI", ignoreCase = true)
            ) {
                articleOpening = false
                openingArticle = null
                openingAttemptId++
                logI(actionName, "已确认文章 WebView 加载完成: $pageClass")
                service.resumeCurrentAction()
                return "OpenMoreEnum"
            }
            return actionName
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

        val root = service.rootInActiveWindow
        if (root == null || root.childCount == 0) {
            recognizeAndOpenArticle(service)
            return actionName
        }
        val recyclerView = root.findNodeOrNull {
            className == CLS_RECYCLER_VIEW
        } ?: run {
            recognizeAndOpenArticle(service)
            return actionName
        }
        // 识别公众号列表中能点击的 ViewGroup
        val targets = recyclerView.findNodes {
            className == CLS_VIEW_GROUP && isClickable
        }

        service.resumeServiceDelay(event, 0L)
        return if (targets.isEmpty()) {
            // 理论上不会走这里，除非这个公众号压根没有文章
            "WriteOfficialArticle"
        } else if (stepInternal < targets.size) {
            if (stepInternal == 0 && !isBeforePublishDate(service.startDate, root)) {
                // 已经到达目标日期
                return "WriteOfficialArticle"
            }

            // 当前页面还有结点没被按
            if (targets[stepInternal].click()) {
                stepInternal++
                "OpenMoreEnum"
            } else {
                actionName
            }
        } else {
            // 当前页面结点已全部按完
            if (!recyclerView.scrollForward()) {
                return "WriteOfficialArticle"
            }
            Thread.sleep(1000L)
            stepInternal = 0
            return actionName
        }


    }

    private fun recognizeAndOpenArticle(service: AutoOperationService) {
        if (captureInFlight) return
        captureInFlight = true
        val root = service.rootInActiveWindow
        val accepted = OfficialScreenReader.recognize(
            service = service,
            onSuccess = { lines ->
                // 检测是否仍在文章页面（包括元数据、正文及底部UI元素）
                val stillInArticle = OfficialPageDetector.isArticleDetailPage(lines, root)

                if (stillInArticle) {
                    logI(actionName, "OCR 检测到文章特有元素，页面仍在文章中，转回返回动作")
                    pendingNextAction = "BackToOfficialArticleList"
                    captureInFlight = false
                    service.resumeCurrentAction()
                    return@recognize
                }

                val candidates = articleCandidates(
                    lines,
                    service.startDate,
                    service.endDate,
                )
                val target = candidates.firstOrNull {
                    !wasClicked(it.publishDate, it.line.text)
                }
                if (target == null) {
                    val visibleDates = lines.mapNotNull { OfficialPageDetector.extractDateOrNull(it.text) }
                    pendingNextAction = if (
                        visibleDates.any { it < service.startDate } ||
                        lines.any { it.text.contains(OfficialPageDetector.THE_END_TEXT) }
                    ) {
                        "WriteOfficialArticle"
                    } else if (OfficialPageDetector.isOfficialListPage(lines, root)) {
                        "ScrollOfficialList"
                    } else {
                        // 既未识别到可点击文章，也无列表特征：转回返回动作防止在文章正文内乱滑
                        "BackToOfficialArticleList"
                    }
                    captureInFlight = false
                    service.resumeCurrentAction()
                    return@recognize
                }

                val clickedArticle = ClickedArticle(
                    target.publishDate,
                    normalizeTitle(target.line.text),
                )
                val dispatched = service.tap(
                    target.line.bounds.centerX().toFloat(),
                    target.line.bounds.centerY().toFloat(),
                )
                if (dispatched) {
                    clickedArticles += clickedArticle
                    articleOpening = true
                    openingArticle = clickedArticle
                    val attemptId = ++openingAttemptId
                    Handler(service.mainLooper).postDelayed({
                        if (articleOpening &&
                            openingAttemptId == attemptId &&
                            openingArticle == clickedArticle
                        ) {
                            clickedArticles.remove(clickedArticle)
                            articleOpening = false
                            openingArticle = null
                            logI(
                                actionName,
                                "文章页未在限定时间打开，撤销本次点击并重试: " +
                                        clickedArticle.normalizedTitle,
                            )
                            service.resumeCurrentAction()
                        }
                    }, 2500L)
                }
                logI(
                    actionName,
                    "OCR 点击文章 accepted=$dispatched, text=${target.line.text}, " +
                            "date=${target.publishDate}, point=" +
                            "${target.line.bounds.centerX()},${target.line.bounds.centerY()}",
                )
                captureInFlight = false
                Handler(service.mainLooper).postDelayed({
                    if (!dispatched) {
                        articleOpening = false
                        service.resumeCurrentAction()
                    }
                }, 500L)
            },
            onFailure = {
                captureInFlight = false
                Handler(service.mainLooper).postDelayed(
                    { service.resumeCurrentAction() },
                    500L,
                )
            },
        )
        if (!accepted) captureInFlight = false
    }

    private fun articleCandidates(
        lines: List<RecognizedScreenLine>,
        startDate: Long,
        endDate: Long,
    ): List<ArticleCandidate> {
        var sectionDate: Long? = null
        var waitingForArticleTitle = false
        return buildList {
            lines.forEach { line ->
                OfficialPageDetector.extractDateOrNull(line.text)?.let {
                    sectionDate = it
                    waitingForArticleTitle = true
                    return@forEach
                }
                val date = sectionDate ?: return@forEach
                val text = line.text.trim()
                if (date !in startDate..endDate) return@forEach
                if (line.bounds.centerY() !in 760..2350) return@forEach
                if (text.contains("阅读") || text.matches(Regex(".*赞\\s*\\d+.*"))) {
                    // 一篇文章的标题可能被 OCR 拆成多行；阅读量行代表该卡片结束，
                    // 后续若仍属于同一日期，可以再接受下一篇文章标题。
                    waitingForArticleTitle = true
                    return@forEach
                }
                if (!waitingForArticleTitle) return@forEach
                if (text.length < 7) return@forEach
                if (text.length < 20 && text.contains("小e", ignoreCase = true)) {
                    // 文章返回列表时偶尔会残留“社区资讯码任小e”悬浮文案；它不
                    // 属于列表卡片，点击后只会重新打开刚采集的文章。
                    return@forEach
                }
                if (line.bounds.width() < 280) return@forEach
                add(ArticleCandidate(line, date))
                waitingForArticleTitle = false
            }
        }
    }

    private fun wasClicked(publishDate: Long, text: String): Boolean {
        val normalized = normalizeTitle(text)
        return clickedArticles.any { clicked ->
            clicked.publishDate == publishDate &&
                    titlesLikelySame(clicked.normalizedTitle, normalized)
        }
    }

    private fun normalizeTitle(text: String): String =
        text.replace(Regex("[^\\p{L}\\p{N}]"), "")
            .replace('黃', '黄')
            .replace('裏', '里')
            .replace('臺', '台')
            .take(40)

    /** OCR 偶尔产生一两个异体字或错字，不能因此把同一篇文章重复采集。 */
    private fun titlesLikelySame(left: String, right: String): Boolean {
        if (left == right) return true
        if (left.length < 8 || right.length < 8) return false
        val maxLength = maxOf(left.length, right.length)
        val allowedDistance = maxOf(2, maxLength / 8)
        if (kotlin.math.abs(left.length - right.length) > allowedDistance) return false

        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length] <= allowedDistance
    }

    private fun isBeforePublishDate(startDate: Long, root: AccessibilityNodeInfo): Boolean {
        // 非正值，一直抓
        if (startDate == Long.MIN_VALUE) return true

        val targets = root.findNodes {
            className == CLS_TEXT_VIEW
                    && viewIdResourceName == PUBLISH_DATE_ID
        }

        // 屏幕上刚好没有发布时间，则继续获取
        if (targets.isEmpty()) return true

        targets.first().text?.toString()?.let {
            val time = officialTimeFormat(it)
            return time >= startDate
        }

        return true
    }

}
