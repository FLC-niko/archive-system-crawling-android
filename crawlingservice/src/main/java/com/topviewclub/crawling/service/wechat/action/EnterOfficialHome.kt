package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.log.logI
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException

/**
 * 进入公众号主页
 * */
internal class EnterOfficialHome : Action {

    private companion object {
        private const val NETWORK_ERROR = "当前网络不可用"
        private const val NETWORK_CONNECT_ERROR = "无网络连接，请检查网络设置"
        private const val SCAN_COMPLETED = "扫描完成"
        private const val SETTINGS = "设置"
        private const val FOLLOW = "关注"
        private const val CHAT_INFO_X_RATIO = 0.93f
        private const val CHAT_INFO_Y_RATIO = 0.057f

        private val chatInfoLabels = setOf(
            "聊天信息",
            "公众号信息",
            "公众号详情",
            "更多",
        )
    }

    override val actionName: String = "EnterOfficialHome"

    private var waitCount = 0
    private val retryState = PickerRetryState(actionName, maxAttempts = 8)
    private var lastUiSignature: String? = null
    private var chattingContextSeen = false

    @Volatile
    private var pendingNextAction: String? = null

    @Volatile
    private var ocrInFlight = false

    @Volatile
    private var lastArticleTapTime = 0L

    @Volatile
    private var lastOcrAttemptTime = 0L

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        // 0. 若已通过 OCR 确认进入公众号主页，直接转入下一步
        pendingNextAction?.let { next ->
            pendingNextAction = null
            retryState.reset()
            ocrInFlight = false
            chattingContextSeen = false
            logI(actionName, "已确认进入公众号主页/下一步: $next")
            service.resumeCurrentAction()
            return next
        }

        val root = service.rootInActiveWindow
        val uiSignature = listOf(
            event.packageName?.toString().orEmpty(),
            event.uiClassName(),
            root?.className?.toString().orEmpty(),
            root?.childCount ?: -1,
        ).joinToString("|")
        if (uiSignature != lastUiSignature) {
            lastUiSignature = uiSignature
            logI(actionName, "等待公众号主页: $uiSignature")
        }

        val currentClassName = root?.className?.toString().orEmpty()
        val currentWechatActivity = service.currentWechatActivity.orEmpty()
        val contactInfoVisible = event.uiClassName().contains("ContactInfoUI", ignoreCase = true) ||
                currentClassName.contains("ContactInfoUI", ignoreCase = true) ||
                currentWechatActivity.contains("ContactInfoUI", ignoreCase = true)
        if (contactInfoVisible) {
            retryState.reset()
            chattingContextSeen = false
            ocrInFlight = false
            logI(actionName, "已确认进入公众号 ContactInfoUI")
            return "CheckTargetAccount"
        }

        val isArticleWebView = event.uiClassName().contains("WebView", ignoreCase = true) ||
                currentClassName.contains("WebView", ignoreCase = true) ||
                currentWechatActivity.contains("WebView", ignoreCase = true)
        if (isArticleWebView) {
            logI(actionName, "处于 WebView 界面，执行 OCR 页面识别与入口探测")

            // 1. 优先尝试无障碍节点：部分 WebView 暴露了公众号名称或关注按钮
            val targetNode = root?.findNodeOrNull {
                val t = text?.toString() ?: contentDescription?.toString() ?: ""
                t == service.target || (service.target.isNotBlank() && t.contains(service.target))
            }
            if (targetNode != null && clickTarget(targetNode)) {
                logI(actionName, "已通过网页节点点击公众号名称: ${service.target}")
                retryState.scheduleProbe(service, event)
                return actionName
            }

            // 2. OCR 精准识别页面类型与作者入口
            val now = System.currentTimeMillis()
            if (now - lastOcrAttemptTime < 600L) {
                return actionName
            }

            if (!ocrInFlight) {
                ocrInFlight = true
                lastOcrAttemptTime = now
                val requested = com.topviewclub.crawling.service.wechat.WechatScreenReader.recognize(
                    service = service,
                    onSuccess = { lines ->
                        ocrInFlight = false
                        // 判定是否已经在公众号主页/列表页
                        val isListPage = isOfficialProfileOrList(lines)
                        if (isListPage) {
                            logI(actionName, "OCR 确认当前已处于公众号主页/历史列表页，转入 CheckTargetAccount")
                            pendingNextAction = "CheckTargetAccount"
                            service.resumeCurrentAction()
                            return@recognize
                        }

                        // 处于文章详情页，寻找公众号作者入口进行精准点击
                        val tapNow = System.currentTimeMillis()
                        if (tapNow - lastArticleTapTime < 1500L) {
                            logI(actionName, "距上次点击未满1.5秒，等待页面过渡响应")
                            service.resumeCurrentAction()
                            return@recognize
                        }

                        val tapped = tapOfficialAuthorEntry(service, lines)
                        if (tapped) {
                            lastArticleTapTime = tapNow
                            service.resumeServiceDelay(event, 800L)
                        } else {
                            com.topviewclub.common.log.logW(actionName, "未在文章页识别到明确作者入口，尝试比率点击")
                            val attempts = retryState.currentAttempts
                            val (xRatio, yRatio) = when (attempts % 3) {
                                0 -> 0.15f to 0.207f // 顶部作者文字中心
                                1 -> 0.20f to 0.947f // 底部常驻栏作者名称中心
                                else -> 0.93f to 0.057f // 右上角菜单
                            }
                            retryState.dispatch(service, event, xRatio, yRatio)
                            lastArticleTapTime = tapNow
                            service.resumeServiceDelay(event, 800L)
                        }
                    },
                    onFailure = { err ->
                        ocrInFlight = false
                        if (err.message?.contains("errorCode=3") == true) {
                            retryState.scheduleProbe(service, event)
                        } else {
                            com.topviewclub.common.log.logW(actionName, "OCR 识别失败: ${err.message}，降级按比率点击")
                            val (xRatio, yRatio) = 0.15f to 0.207f
                            retryState.dispatch(service, event, xRatio, yRatio)
                            retryState.scheduleProbe(service, event)
                        }
                    }
                )
                if (!requested) {
                    ocrInFlight = false
                }
            }
            return actionName
        }

        val chattingVisible = event.uiClassName().contains("ChattingUI", ignoreCase = true) ||
                currentClassName.contains("ChattingUI", ignoreCase = true)
        if (chattingVisible) chattingContextSeen = true
        if (chattingVisible || chattingContextSeen) {
            val chatInfo = root?.findNodeOrNull {
                val label = contentDescription?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: text?.toString().orEmpty()
                label in chatInfoLabels
            }
            val clicked = chatInfo?.let(::clickTarget) == true
            val dispatched = if (clicked) {
                retryState.scheduleProbe(service, event)
                true
            } else {
                // 新版微信的公众号 ChattingUI 右上角人形入口可能不暴露节点，
                // 使用无障碍服务按物理屏幕坐标点击其中心。
                retryState.dispatch(
                    service,
                    event,
                    CHAT_INFO_X_RATIO,
                    CHAT_INFO_Y_RATIO,
                )
            }
            logI(
                actionName,
                "打开公众号信息: accessibilityTarget=${chatInfo != null}, accepted=$dispatched",
            )
            return actionName
        }

        if (root == null) {
            retryState.scheduleProbe(service, event)
            return actionName
        }
        isMatchNetworkError(root)
        checkFrequencyLimit(root)

        // 新版微信可能已经直接进入公众号资料页。资料页同时包含“公众号”和
        // 当前目标名称，无需再猜测性点击顶部菜单。
        if (root.findNodeOrNull { text?.toString() == "公众号" } != null &&
            root.findNodeOrNull { text?.toString() == service.target } != null
        ) {
            retryState.reset()
            logI(actionName, "已确认进入目标公众号资料页")
            return "CheckTargetAccount"
        }

        return if (match(root)) {
            retryState.reset()
            "CheckTargetAccount"
        } else {
            retryState.scheduleProbe(service, event)
            actionName
        }
    }

    private fun isMatchNetworkError(root: AccessibilityNodeInfo) {
        root.findNodeOrNull { text == NETWORK_ERROR }?.let {
            throw ActionException(TaskResultType.NETWORK_EXCEPTION)
        }
        root.findNodeOrNull { text == NETWORK_CONNECT_ERROR }?.let {
            throw ActionException(TaskResultType.NETWORK_EXCEPTION)
        }
        root.findNodeOrNull {
            val t = text?.toString() ?: return@findNodeOrNull false
            if (t.length < 4) return@findNodeOrNull false
            t.substring(0, 4) == SCAN_COMPLETED
        }?.let {
            if (waitCount >= 8) {
                throw ActionException(TaskResultType.NETWORK_EXCEPTION)
            } else {
                waitCount++
                Thread.sleep(1000L)
            }
        }
    }

    private fun checkFrequencyLimit(root: AccessibilityNodeInfo) {
        val freqNode = root.findNodeOrNull {
            val t = text?.toString() ?: contentDescription?.toString() ?: ""
            t.contains("操作太过于频繁") || t.contains("操作过于频繁") || (t.contains("频繁") && t.contains("稍后再试"))
        }
        if (freqNode != null) {
            val confirmNode = root.findNodeOrNull {
                val t = text?.toString() ?: contentDescription?.toString() ?: ""
                t == "确定" || t == "我知道了" || t == "好的"
            }
            confirmNode?.click() ?: confirmNode?.parent?.click()
            com.topviewclub.common.log.logW(actionName, "检测到公众号名片码高频扫码风控弹窗 (${freqNode.text})，已尝试消除弹窗")
            throw ActionException(TaskResultType.FREQUENCY_LIMIT_EXCEPTION)
        }
    }

    private fun match(root: AccessibilityNodeInfo): Boolean {
        val settings = root.findNodeOrNull {
            contentDescription == SETTINGS
        }

        // 已关注
        if (settings != null) {
            if (settings.click()) {
                return true
            }
            return false
        }

        // 未关注
        val follow = root.findNodeOrNull {
            text == FOLLOW
        } ?: return false

        // 关注
        follow.click()
        return false
    }

    private fun clickTarget(target: AccessibilityNodeInfo): Boolean =
        target.click() || target.parent?.click() == true || target.parent?.parent?.click() == true

    private fun isOfficialProfileOrList(lines: List<com.topviewclub.crawling.service.wechat.RecognizedScreenLine>): Boolean {
        if (lines.isEmpty()) return false
        if (lines.any { it.text.contains("已无更多订阅消息") }) return true

        // 公众号主页特征 Tab 栏：全部 + (文章 | 视频号 | 服务 | 贴图)
        val hasTabs = lines.any { line ->
            val t = line.text.replace(" ", "")
            t.contains("全部") && (t.contains("文章") || t.contains("服务") || t.contains("视频号") || t.contains("贴图")) ||
                    t.contains("全部消息")
        }
        if (hasTabs) return true

        // 包含主页特有的操作按钮
        val hasActionButtons = lines.any { line ->
            val t = line.text.replace(" ", "")
            t == "已关注" || t == "发消息" || t == "关注公众号"
        }
        val hasArticleMarkers = lines.any { line ->
            val t = line.text.replace(" ", "")
            t.contains("写留言") || t.contains("听全文") || t.contains("喜欢此内容的人还喜欢") || t.contains("AI摘要")
        }
        if (hasActionButtons && !hasArticleMarkers) return true

        return false
    }

    private fun tapOfficialAuthorEntry(
        service: AutoOperationService,
        lines: List<com.topviewclub.crawling.service.wechat.RecognizedScreenLine>,
    ): Boolean {
        val target = service.target

        // 1. 优先寻找包含目标名称（或前缀匹配）的行
        val targetLine = lines.firstOrNull {
            it.text.contains(target) || (target.length >= 4 && it.text.contains(target.substring(0, 4)))
        }
        if (targetLine != null) {
            val bounds = targetLine.bounds
            val tapX = if (bounds.top < 1200) {
                (bounds.left + Math.min(bounds.width() * 0.25f, 150f)).toFloat()
            } else {
                bounds.centerX().toFloat()
            }
            val tapY = bounds.centerY().toFloat()
            logI(actionName, "通过目标公众号名称精准点击: target=$target at ($tapX, $tapY)")
            return service.tap(tapX, tapY)
        }

        // 2. 底部常驻栏 "+关注" 按钮左侧为公众号名称/头像
        val followLine = lines.firstOrNull { it.text.contains("+关注") || (it.text == "关注" && it.bounds.top > 2000) }
        if (followLine != null) {
            val tapX = (followLine.bounds.left - 120f).coerceAtLeast(150f)
            val tapY = followLine.bounds.centerY().toFloat()
            logI(actionName, "通过底部关注按钮左侧作者区域点击: ($tapX, $tapY)")
            return service.tap(tapX, tapY)
        }

        // 3. 顶部文章发布日期行，作者通常在行首
        val dateLine = lines.firstOrNull { line ->
            line.bounds.top in 300..1000 &&
                    (line.text.contains(Regex("""\d{4}年|\d{1,2}月\d{1,2}日""")) || line.text.contains("昨天") || line.text.contains("今天"))
        }
        if (dateLine != null) {
            val tapX = (dateLine.bounds.left + 80f).toFloat()
            val tapY = dateLine.bounds.centerY().toFloat()
            logI(actionName, "通过顶部日期行首作者区域点击: ($tapX, $tapY)")
            return service.tap(tapX, tapY)
        }

        return false
    }

}
