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
        // 当前微信版本的 ContactInfoUI 对无障碍仅暴露空根节点。该页面只能由
        // 本任务二维码识别后的 ChattingUI 右上角入口到达，因此类名本身就是
        // 责任链已到达公众号资料页的可靠状态证据。
        if (event.className?.toString()?.contains("ContactInfoUI", ignoreCase = true) == true ||
            service.rootInActiveWindow?.className?.toString()
                ?.contains("ContactInfoUI", ignoreCase = true) == true
        ) {
            logI(actionName, "已确认任务二维码进入公众号资料页: ${service.target}")
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
