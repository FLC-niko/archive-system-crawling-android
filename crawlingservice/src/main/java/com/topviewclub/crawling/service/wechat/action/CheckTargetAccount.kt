package com.topviewclub.crawling.service.wechat.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.wechat.WechatOperationService

/**
 * 检查二维码与配置的用户名是否匹配
 * */
internal class CheckTargetAccount : Action {

    override val actionName: String = "CheckTargetAccount"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
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