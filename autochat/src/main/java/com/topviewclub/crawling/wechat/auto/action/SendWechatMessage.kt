package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class SendWechatMessage : Action {

    private companion object {
        private const val SEND_TEXT = "发送"
    }

    override val actionName: String = "SendWechatMessage"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val send = root.findNodeOrNull {
            text?.toString() == SEND_TEXT
                    && isClickable
                    && className == CLS_BUTTON
        } ?: return actionName
        send.click()
        Thread.sleep(1000L)
        return "BackToChatFragment"
    }
}