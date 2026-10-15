package com.topviewclub.crawling.wechat.official.action

import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.log.logI
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.tap

/** 微信会记住上次选中的贴图/视频页；每次进入目标号先明确选中“文章”。 */
class SelectOfficialArticleTab : Action {
    override val actionName = "SelectOfficialArticleTab"
    private var capturing = false
    private var selected = false
    private var pendingFailure: String? = null
    private var attempts = 0
    private var nextAttemptAt = 0L

    override fun reset() {
        capturing = false
        selected = false
        pendingFailure = null
        attempts = 0
        nextAttemptAt = 0L
    }

    override fun execute(service: AutoOperationService, event: AccessibilityEvent): String {
        pendingFailure?.let { throw ActionException(it) }
        if (selected) {
            reset()
            service.resumeCurrentAction()
            return "HomingOfficialList"
        }
        if (capturing) return actionName
        if (SystemClock.uptimeMillis() < nextAttemptAt) {
            service.resumeServiceDelay(event, 400L)
            return actionName
        }
        if (attempts >= 5) throw ActionException("OFFICIAL_ARTICLE_TAB_UNAVAILABLE")
        attempts++
        nextAttemptAt = SystemClock.uptimeMillis() + 700L
        val height = service.resources.displayMetrics.heightPixels
        val roots = listOfNotNull(service.rootInActiveWindow) + service.windows.mapNotNull { it.root }
        val node = roots.asSequence().filter { it.packageName?.toString() == "com.tencent.mm" }
            .mapNotNull { root -> root.findNodeOrNull {
                (text?.toString() == "文章" || contentDescription?.toString() == "文章") &&
                    Rect().also(::getBoundsInScreen).centerY() < height * 0.55f
            } }.firstOrNull()
        if (node != null && (node.isSelected || node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) ||
                node.parent?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true)) {
            finishAfterSettle(service)
            return actionName
        }
        capturing = true
        val accepted = OfficialScreenReader.recognize(service, onSuccess = { lines ->
            val headerLines = lines.filter { it.bounds.top > height * 0.04f && it.bounds.bottom < height * 0.55f }
            val tabLine = headerLines.firstOrNull { line ->
                val compact = line.text.replace(Regex("\\s+"), "")
                compact.contains("全部") && compact.contains("文章") &&
                    listOf("贴图", "视频", "服务", "视频号").any { compact.contains(it) }
            }
            val symbols = tabLine?.symbols.orEmpty().filter { it.text.isNotBlank() }
            val symbolText = symbols.joinToString("") { it.text }
            val index = symbolText.indexOf("文章")
            val target = if (index >= 0 && symbols.all { it.text.length == 1 } && index + 1 < symbols.size) {
                Rect(symbols[index].bounds).apply { union(symbols[index + 1].bounds) }
            } else if (headerLines.any { it.text.trim() == "全部" }) {
                headerLines.firstOrNull { it.text.trim() == "文章" }?.bounds
            } else null
            if (target != null && service.tap(target.centerX().toFloat(), target.centerY().toFloat())) {
                logI(actionName, "OCR 字符边界选中文章页签: $target")
                finishAfterSettle(service)
            } else {
                capturing = false
                if (attempts >= 5) pendingFailure = "OFFICIAL_ARTICLE_TAB_UNAVAILABLE"
                service.resumeServiceDelay(event, 700L)
            }
        }, onFailure = {
            capturing = false
            if (attempts >= 5) pendingFailure = "OFFICIAL_ARTICLE_TAB_UNAVAILABLE"
            service.resumeServiceDelay(event, 700L)
        })
        if (!accepted) capturing = false
        return actionName
    }

    private fun finishAfterSettle(service: AutoOperationService) {
        capturing = true
        Handler(service.mainLooper).postDelayed({
            selected = true
            capturing = false
            logI(actionName, "文章页签切换已等待稳定，进入历史文章列表")
            service.resumeCurrentAction()
        }, 700L)
    }
}
