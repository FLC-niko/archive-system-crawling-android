package com.topviewclub.crawling.xuexi.action

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

internal class EnterAccountHome : Action {

    override val actionName: String = "EnterAccountHome"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        return if (match(root, service.target)) "Empty0" else actionName
    }

    private fun match(root: AccessibilityNodeInfo, targetAccount: String): Boolean {
        val target = root.findNodeOrNull {
            contentDescription?.toString() == targetAccount
        } ?: return false
        return target.click()
    }

}