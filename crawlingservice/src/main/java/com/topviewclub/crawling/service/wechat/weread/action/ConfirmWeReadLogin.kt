package com.topviewclub.crawling.service.wechat.weread.action

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logW
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.tap
import com.topviewclub.crawling.service.wechat.RecognizedScreenLine
import com.topviewclub.crawling.service.wechat.WechatScreenReader
import com.topviewclub.crawling.service.wechat.action.tapPickerRatio
import com.topviewclub.crawling.service.wechat.action.uiClassName

/**
 * 仅在确认页加载完成后点击授权，支持文本节点 / OCR 双通道定位与坐标兜底。
 *
 * 稳定性要点：
 * 1. 微信读书 WebView 首屏加载需要十几秒，MMWebViewUI 一出现并不代表"登录确认"
 *    页面已经渲染完成；在页面就绪前绝不点击，更不会直接把授权页关掉。
 * 2. 通过节点文本 + MLKit OCR 识别「登录确认 / 24小时后自动退出登录 / 微信读书网页版」
 *    判定页面就绪，识别「登录失败 / 重新扫码」判定失败并立即复位。
 * 3. 点击授权后等待结果窗口（允许一次防抖动补点），再把 WebView 关闭复位；
 *    登录结果由服务端轮询 getLoginInfo 判定，不依赖本动作猜测。
 * 4. 长时间无法就绪（例如弱网）时主动关闭授权页并结束任务，让服务端发起下一轮，
 *    避免责任链永久悬挂。
 */
class ConfirmWeReadLogin : Action {
    override val actionName: String = "ConfirmWeReadLogin"

    private companion object {
        // 1080x2460 对应 (540, 2087)
        private const val CONFIRM_X_RATIO = 0.500f
        private const val CONFIRM_Y_RATIO = 0.848f

        // 页面左上角关闭叉叉比率 (1080x2460 对应 60, 135)
        private const val CLOSE_X_RATIO = 0.055f
        private const val CLOSE_Y_RATIO = 0.055f

        /** 等待授权页渲染完成的最长时间，超过则复位让服务端重试。 */
        private const val PAGE_READY_TIMEOUT_MS = 45_000L

        /** 点击授权后留给登录接口与页面跳转的结果窗口。 */
        private const val CONFIRM_SETTLE_MS = 6_000L

        /** 防抖动补点：首次点击后仍未离开页面时允许再点一次的最小间隔。 */
        private const val CONFIRM_RETRY_INTERVAL_MS = 2_500L

        private const val MAX_CONFIRM_TAPS = 2
        private const val PROBE_DELAY_MS = 900L
        private const val OCR_MIN_INTERVAL_MS = 1_500L

        private val AUTH_BUTTON_LABELS = setOf("允许", "确认登录", "同意", "登录")
        private val READY_MARKERS = listOf("登录确认", "24小时后自动退出登录", "微信读书网页版")
        private val FAILURE_MARKERS = listOf("登录失败", "重新扫码")
    }

    private var confirmTaps = 0
    private var lastConfirmAt = 0L
    private var driveStartedAt = 0L
    private var ocrInFlight = false
    private var lastOcrAt = 0L

    @Volatile
    private var ocrLines: List<RecognizedScreenLine> = emptyList()

    override fun execute(service: AutoOperationService, event: AccessibilityEvent): String {
        val now = System.currentTimeMillis()
        if (driveStartedAt == 0L) driveStartedAt = now

        val root = service.rootInActiveWindow
        val uiClass = event.uiClassName().ifBlank { root?.className?.toString().orEmpty() }
        val nodeText = collectNodeText(root)
        val ocrText = ocrLines.joinToString(" ") { it.text }
        val screenText = "$nodeText $ocrText"

        // 1. 失败页（服务端风控或二维码失效）：立即复位，交给服务端下一轮重试。
        if (FAILURE_MARKERS.any { screenText.contains(it) }) {
            logW(actionName, "检测到微信读书登录失败页，立即关闭 WebView 复位")
            closeAuthorizationPage(service)
            reset()
            return AutoOperationService.ActionType.ActionSuccess
        }

        // 2. 已点击授权：等待结果窗口；页面若仍在确认页允许一次防抖动补点。
        if (confirmTaps > 0) {
            val sinceTap = now - lastConfirmAt
            if (sinceTap >= CONFIRM_SETTLE_MS) {
                logI(actionName, "授权点击后等待 ${sinceTap}ms，退出 WebView（登录结果由服务端轮询判定）")
                closeAuthorizationPage(service)
                reset()
                return AutoOperationService.ActionType.ActionSuccess
            }
            val pageReady = isAuthReady(nodeText, ocrText)
            if (confirmTaps < MAX_CONFIRM_TAPS && pageReady && sinceTap >= CONFIRM_RETRY_INTERVAL_MS) {
                logI(actionName, "授权页仍在，执行防抖动补点 (${confirmTaps + 1}/$MAX_CONFIRM_TAPS)")
                tapConfirm(service, event)
                return actionName
            }
            service.resumeServiceDelay(event, PROBE_DELAY_MS)
            return actionName
        }

        // 3. 原生节点优先：经典 View 层次可直接命中授权按钮。
        val button = root?.findNodeOrNull {
            val value = text?.toString() ?: contentDescription?.toString() ?: return@findNodeOrNull false
            value in AUTH_BUTTON_LABELS
        }
        if (button != null) {
            val clicked = button.click() || button.parent?.click() == true
            if (clicked) {
                logI(actionName, "已通过原生节点点击授权按钮: ${button.text ?: button.contentDescription}")
                confirmTaps++
                lastConfirmAt = now
                service.resumeServiceDelay(event, OCR_MIN_INTERVAL_MS)
                return actionName
            }
        }

        // 4. 页面就绪判定：只有在确认页真正渲染后才能点击。
        if (isAuthReady(nodeText, ocrText)) {
            tapConfirm(service, event)
            return actionName
        }

        // 5. 超时仍未就绪：复位让服务端发起新一轮，避免悬挂。
        if (now - driveStartedAt > PAGE_READY_TIMEOUT_MS) {
            logW(actionName, "等待授权页就绪超时(${PAGE_READY_TIMEOUT_MS}ms, uiClass=$uiClass)，关闭 WebView 让服务端重试")
            closeAuthorizationPage(service)
            reset()
            return AutoOperationService.ActionType.ActionSuccess
        }

        // 6. 页面加载中：持续等待并通过 OCR 观测渲染进度。
        requestOcr(service)
        logI(actionName, "授权页加载中(uiClass=$uiClass)，继续等待 …")
        service.resumeServiceDelay(event, PROBE_DELAY_MS)
        return actionName
    }

    private fun tapConfirm(service: AutoOperationService, event: AccessibilityEvent) {
        // 精确匹配底部按钮文本“登录”；OCR 可能把整块区域识别成
        // “24小时后自动退出登录”，绝不能把这种说明文字当成按钮（取最下方的“登录/含登录”行）。
        val loginLine = ocrLines.lastOrNull { it.text.trim() == "登录" }
            ?: ocrLines
                .filter { it.text.trim().endsWith("登录") && !it.text.contains("退出") }
                .maxByOrNull { it.bounds.centerY() }
        val tapped = if (loginLine != null) {
            logI(actionName, "OCR 定位登录按钮: '${loginLine.text}' @ ${loginLine.bounds}")
            service.tap(loginLine.bounds.centerX().toFloat(), loginLine.bounds.centerY().toFloat())
        } else {
            logI(actionName, "按确认页比率坐标点击登录按钮")
            service.tapPickerRatio(CONFIRM_X_RATIO, CONFIRM_Y_RATIO)
        }
        if (tapped) {
            confirmTaps++
            lastConfirmAt = System.currentTimeMillis()
            service.resumeServiceDelay(event, CONFIRM_RETRY_INTERVAL_MS)
        }
    }

    private fun isAuthReady(nodeText: String, ocrText: String): Boolean =
        READY_MARKERS.any { nodeText.contains(it) || ocrText.contains(it) }

    private fun requestOcr(service: AutoOperationService) {
        val now = System.currentTimeMillis()
        if (ocrInFlight || now - lastOcrAt < OCR_MIN_INTERVAL_MS) return
        ocrInFlight = true
        lastOcrAt = now
        val accepted = WechatScreenReader.recognize(
            service,
            onSuccess = { lines ->
                ocrLines = lines
                ocrInFlight = false
                logI(actionName, "OCR ${lines.size} 行: " + lines.joinToString("|") { it.text }.take(160))
                service.resumeCurrentAction()
            },
            onFailure = { error ->
                ocrInFlight = false
                logW(actionName, "OCR 识别失败: ${error.message}")
            },
        )
        if (!accepted) {
            logW(actionName, "无障碍截图请求未被接受，等待下一轮")
            ocrInFlight = false
        }
    }

    private fun collectNodeText(root: AccessibilityNodeInfo?): String {
        val builder = StringBuilder()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(' ') }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(' ') }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return builder.toString()
    }

    private fun closeAuthorizationPage(service: AutoOperationService) {
        service.tapPickerRatio(CLOSE_X_RATIO, CLOSE_Y_RATIO)
        Thread.sleep(500L)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(500L)
    }

    override fun reset() {
        confirmTaps = 0
        lastConfirmAt = 0L
        driveStartedAt = 0L
        ocrLines = emptyList()
        ocrInFlight = false
    }
}
