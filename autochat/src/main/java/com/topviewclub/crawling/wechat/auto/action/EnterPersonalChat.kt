package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.*
import com.topviewclub.crawling.service.action.Action

class EnterPersonalChat : Action {

    private companion object {
        private const val LIST_VIEW_ID = "com.tencent.mm:id/gkw"
        private const val CHAT_ITEM_ID = "com.tencent.mm:id/btg"
        private const val RED_NUMBER_POINT_ID = "com.tencent.mm:id/kn6"
    }

    override val actionName: String = "EnterPersonalChat"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val listView = root.findNodeOrNull {
            viewIdResourceName == LIST_VIEW_ID
                    && className == CLS_LIST_VIEW
        } ?: return actionName

        val targets = listView.findNodes {
            viewIdResourceName == CHAT_ITEM_ID
                    && className == CLS_LINEAR_LAYOUT
                    && findNodeOrNull { viewIdResourceName == RED_NUMBER_POINT_ID } != null
        }

        return if (targets.isNotEmpty()) {
            val target = targets.last()
            target.click()
            "EnterChatInfo"
        } else {
            "ScrollChatList"
        }
    }

}