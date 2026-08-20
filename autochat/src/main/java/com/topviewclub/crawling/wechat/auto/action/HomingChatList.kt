package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class HomingChatList : Action {

    private companion object {
        private const val CHAT_LIST_VIEW_ID = "com.tencent.mm:id/gkw"
    }

    override val actionName: String = "HomingChatList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName

        val listView = root.findNodeOrNull {
            viewIdResourceName == CHAT_LIST_VIEW_ID
                    && className == CLS_LIST_VIEW
        } ?: return actionName

        return if (!listView.scrollForward()) {
            Thread.sleep(1000L)
            "CheckNumberOfUnreadMessages"
        } else {
            "Empty0"
        }
    }

}