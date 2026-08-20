package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.findNodeOrNull
import com.topviewclub.crawling.service.findSuperNodeOrNull
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService

class CheckNumberOfUnreadMessages : Action {

    private companion object {
        private const val CHAT_ID = "com.tencent.mm:id/f30"
        private const val CHAT_TEXT = "微信"

        private const val UNREAD_MESSAGE_ID = "com.tencent.mm:id/l0n"
    }

    override val actionName: String = "CheckNumberOfUnreadMessages"

    private var countInternal = 0

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
        countInternal++
        clickable.findNodeOrNull {
            viewIdResourceName == UNREAD_MESSAGE_ID
        } ?: return if (AutoChatOperationService.prepareToShutdown || countInternal > 60) {
            AutoChatOperationService.prepareToShutdown = false
            AutoOperationService.ActionType.ActionSuccess
        } else {
            "Empty1"
        }
        return "EnterPersonalChat"
    }

}