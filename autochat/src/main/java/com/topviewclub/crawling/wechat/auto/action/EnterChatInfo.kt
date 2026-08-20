package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.click
import com.topviewclub.crawling.service.findNodeOrNull

class EnterChatInfo : Action {

    private companion object {
        private const val CHAT_INFO_DES = "聊天信息"
        private const val BACK_ID = "com.tencent.mm:id/fz"
    }

    override val actionName: String = "EnterChatInfo"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val back = root.findNodeOrNull {
            viewIdResourceName == BACK_ID
        } ?: return actionName
        val target = root.findNodeOrNull {
            contentDescription?.toString() == CHAT_INFO_DES
        }
        return if (target == null) {
            back.click()
            "EnterPersonalChat"
        } else {
            target.click()
            "CheckPersonalChat"
        }
    }

}