package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class ScrollChatList : Action {

    private companion object {
        private const val LIST_VIEW_ID = "com.tencent.mm:id/gkw"
    }

    override val actionName: String = "ScrollChatList"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val listView = root.findNodeOrNull {
            viewIdResourceName == LIST_VIEW_ID
        } ?: return actionName
        listView.scrollBackward()
        Thread.sleep(1000L)
        return "CheckNumberOfUnreadMessages"
    }

}