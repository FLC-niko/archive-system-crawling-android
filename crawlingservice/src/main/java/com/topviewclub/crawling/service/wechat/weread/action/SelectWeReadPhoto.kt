package com.topviewclub.crawling.service.wechat.weread.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

/** 选择二维码目录中的最新图片，并进入微信读书授权确认页。 */
class SelectWeReadPhoto : Action {
    override val actionName: String = "SelectWeReadPhoto"

    override fun execute(service: AutoOperationService, event: AccessibilityEvent): String {
        val root = service.rootInActiveWindow ?: return actionName
        val photo = root.findNodeOrNull {
            contentDescription?.toString()?.startsWith("图片1") == true
        } ?: return actionName
        return if (photo.parent?.click() == true) {
            Thread.sleep(1000L)
            "ConfirmWeReadLogin"
        } else actionName
    }
}