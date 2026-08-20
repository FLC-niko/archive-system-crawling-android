package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

internal class EnterWechatLauncher : Action {

    private companion object {
        private const val BACK_IMAGE_DES = "关闭"
    }

    override val actionName: String = "EnterWechatLauncher"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val target = service.rootInActiveWindow?.findNodeOrNull {
            contentDescription?.toString() == BACK_IMAGE_DES
        } ?: return actionName
        target.click()
        return "EnterChatFragment"
    }

}