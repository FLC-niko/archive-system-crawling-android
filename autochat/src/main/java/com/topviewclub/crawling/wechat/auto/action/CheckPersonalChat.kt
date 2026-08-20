package com.topviewclub.crawling.wechat.auto.action

import android.view.accessibility.AccessibilityEvent
import com.topviewclub.crawling.service.AutoOperationService
import com.topviewclub.crawling.service.action.Action
import com.topviewclub.crawling.service.back
import com.topviewclub.crawling.service.findNodeOrNull

class CheckPersonalChat : Action {

    private companion object {
        private const val CHAT_INFO_TEXT = "聊天信息"
        private const val BACK_ID = "com.tencent.mm:id/fz"
    }

    override val actionName: String = "CheckPersonalChat"

    override fun execute(
        service: AutoOperationService,
        event: AccessibilityEvent
    ): String {
        val root = service.rootInActiveWindow ?: return actionName
        val target = root.findNodeOrNull {
            text?.toString()?.let {
                it.length >= 4 && it.substring(0, 4) == CHAT_INFO_TEXT
            } ?: false
        } ?: return actionName
        return if (target.text.length == 4) {
            // 个人
            Thread.sleep(1000L)
            "EnterPersonalInfo"
        } else {
            // 群组
            service.back()
            Thread.sleep(1000L)
            "BackToChatFragment"
        }
    }

}