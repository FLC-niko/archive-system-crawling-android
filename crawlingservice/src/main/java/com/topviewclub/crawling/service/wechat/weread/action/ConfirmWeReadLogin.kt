package com.topviewclub.crawling.service.wechat.weread.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

/** 仅在确认页出现微信读书特征后点击授权，避免误点其他页面按钮。 */
class ConfirmWeReadLogin : Action {
    override val actionName: String = "ConfirmWeReadLogin"

    override fun execute(service: AutoOperationService, event: AccessibilityEvent): String {
        val root = service.rootInActiveWindow ?: return actionName
        val hasWeRead = root.findNodeOrNull {
            text?.toString()?.contains("微信读书") == true ||
                contentDescription?.toString()?.contains("微信读书") == true
        } != null
        if (!hasWeRead) return actionName
        val button = root.findNodeOrNull {
            val value = text?.toString() ?: contentDescription?.toString() ?: return@findNodeOrNull false
            value == "允许" || value == "确认登录" || value == "同意"
        } ?: return actionName
        return if (button.click()) {
            Thread.sleep(1500L)
            AutoOperationService.ActionType.ActionSuccess
        } else actionName
    }
}