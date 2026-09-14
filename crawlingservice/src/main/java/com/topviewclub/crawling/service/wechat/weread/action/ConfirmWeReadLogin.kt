package com.topviewclub.crawling.service.wechat.weread.action

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logW
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.wechat.action.tapPickerRatio
import com.topviewclub.crawling.service.wechat.action.uiClassName

/**
 * 仅在确认页点击授权，支持自绘 View / MMWebView 坐标兜底。
 *
 * 工业级生命周期与防误触保障：
 * 1. 严格前置特征校验：仅在检测到“微信读书”文字或处于 WebView/LiteApp 授权窗口时才允许点击，严禁在未知界面盲点；
 * 2. 授权完成后，自动点击左上角叉叉或发送 BACK 返回键退出授权页，彻底复位至微信根主页，杜绝悬挂 MMWebViewUI。
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

        private val AUTH_BUTTON_LABELS = setOf("允许", "确认登录", "同意", "登录")
    }

    private var confirmClicked = false

    override fun execute(service: AutoOperationService, event: AccessibilityEvent): String {
        val root = service.rootInActiveWindow
        val uiClass = event.uiClassName().ifBlank { root?.className?.toString().orEmpty() }

        // 若已点击过授权，执行退出 WebView 复位并声明成功
        if (confirmClicked) {
            logI(actionName, "已完成授权点击，正在退出 WebView 并复位至微信根页面...")
            Thread.sleep(800L)
            service.tapPickerRatio(CLOSE_X_RATIO, CLOSE_Y_RATIO)
            Thread.sleep(500L)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(500L)
            confirmClicked = false
            return AutoOperationService.ActionType.ActionSuccess
        }

        // 1. 前置特征校验：避免在前序选图未完成或在其他非授权页面盲点误报
        val hasWeReadText = root?.findNodeOrNull {
            val t = text?.toString() ?: contentDescription?.toString() ?: ""
            t.contains("微信读书") || t.contains("weread", ignoreCase = true)
        } != null

        val isAuthWindow = uiClass.contains("WebView", ignoreCase = true) ||
                uiClass.contains("LiteApp", ignoreCase = true) ||
                uiClass.contains("AppBrand", ignoreCase = true)

        // 2. 节点优先匹配（经典 View 层次）
        val button = root?.findNodeOrNull {
            val value = text?.toString() ?: contentDescription?.toString() ?: return@findNodeOrNull false
            value in AUTH_BUTTON_LABELS
        }
        if (button != null) {
            val clicked = button.click() || button.parent?.click() == true
            if (clicked) {
                logI(actionName, "已通过原生节点点击授权按钮: ${button.text ?: button.contentDescription}")
                confirmClicked = true
                Thread.sleep(1500L)
                return actionName // 下一个周期执行关闭退出并复位主页
            }
        }

        // 3. 自绘 View / MMWebView 坐标兜底（必须满足前置特征，严禁无特征盲点）
        if (hasWeReadText || isAuthWindow) {
            logI(actionName, "在授权页面(uiClass=$uiClass, hasWeRead=$hasWeReadText)采用比率坐标点击登录按钮")
            val tapped = service.tapPickerRatio(CONFIRM_X_RATIO, CONFIRM_Y_RATIO)
            if (tapped) {
                confirmClicked = true
                Thread.sleep(1500L)
                return actionName // 下一个周期执行关闭退出并复位主页
            }
        }

        logW(actionName, "尚未进入有效的微信读书授权页面 (uiClass=$uiClass)，保持等待")
        return actionName
    }
}