package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.wechat.WechatOperationService
import com.topviewclub.common.log.logI

/**
 * 检查二维码与配置的用户名是否匹配
 * */
internal class CheckTargetAccount : Action {

    override val actionName: String = "CheckTargetAccount"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val isContactInfo = event.className?.toString()?.contains("ContactInfoUI", ignoreCase = true) == true ||
            service.rootInActiveWindow?.className?.toString()?.contains("ContactInfoUI", ignoreCase = true) == true ||
            service.currentWechatActivity?.contains("ContactInfoUI", ignoreCase = true) == true

        val inWebView = event.className?.toString()?.contains("WebView", ignoreCase = true) == true ||
            service.rootInActiveWindow?.className?.toString()?.contains("WebView", ignoreCase = true) == true ||
            service.currentWechatActivity?.contains("WebView", ignoreCase = true) == true

        if (isContactInfo || inWebView) {
            logI(actionName, "已确认任务进入公众号主页: target=${service.target}, contactInfo=$isContactInfo, webView=$inWebView")
            service.resumeServiceDelay(event, 0L)
            return (service as WechatOperationService).firstlyTargetActionName
        }
        val root = service.rootInActiveWindow ?: return actionName
        return if (match(service.target, root))
            (service as WechatOperationService).firstlyTargetActionName
        else actionName
    }

    private fun match(
        targetAccount: String,
        root: AccessibilityNodeInfo
    ): Boolean {
        if (root.findNodeOrNull { text?.toString() == "公众号" } == null) return false
        if (root.findNodeOrNull { text?.toString() == targetAccount } == null) {
            throw ActionException(TaskResultType.QRCODE_SCAN_EXCEPTION)
        }
        return true
    }

}
