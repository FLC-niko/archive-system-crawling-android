package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class EnterChatFragment : Action {

    private companion object {
        private const val CHAT_ID = "com.tencent.mm:id/f30"
        private const val CHAT_TEXT = "微信"
    }

    override val actionName: String = "EnterChatFragment"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val chat = root.findNodeOrNull {
            viewIdResourceName == CHAT_ID &&
                    text?.toString() == CHAT_TEXT
        } ?: return actionName
        val clickable = chat.findSuperNodeOrNull {
            isClickable
        } ?: return actionName
        clickable.click()
        return "HomingChatList"
    }

}